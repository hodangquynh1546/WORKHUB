import { Awareness, applyAwarenessUpdate, encodeAwarenessUpdate, removeAwarenessStates } from 'y-protocols/awareness'
import * as Y from 'yjs'
import { api } from '../../../api/client'
import type { Frame, RealtimeApi } from '../../../realtime/RealtimeContext'
import { fromBase64, toBase64 } from './bytes'

/** Where an update came from: remote ones are never sent back to the server. */
const REMOTE = 'remote'

export type CollabStatus = 'connecting' | 'syncing' | 'ready' | 'offline' | 'reset' | 'error'

type ServerState = {
  epoch: number
  snapshot: string | null
  snapshotSeq: number
  updates: { seq: number; update: string }[]
  lastSeq: number
  empty: boolean
  canEdit: boolean
  content: string
}

const SNAPSHOT_AFTER_MS = 6000

/** The live session per document in this tab: a replaced session must not make the new one leave the room. */
const active = new Map<string, CollabSession>()

/**
 * One person's live copy of a document: a Yjs document plus awareness (cursors, names), kept in step with the
 * others through the server's relay. The server only stores and forwards bytes; all merging happens here, which is
 * why two people typing at once never overwrite each other. Edits made while offline stay in the local Yjs document
 * and are sent in full after reconnecting; the server drops what it already has by the CRDT's own rules.
 */
export class CollabSession {
  readonly doc = new Y.Doc()

  readonly text = this.doc.getText('content')

  readonly awareness = new Awareness(this.doc)

  status: CollabStatus = 'connecting'

  canEdit = false

  savedAt: Date | null = null

  error: string | null = null

  private epoch = 0

  private lastSeq = 0

  private pending = 0

  private changedSinceSnapshot = false

  private snapshotTimer: ReturnType<typeof setTimeout> | undefined

  private offs: (() => void)[] = []

  private destroyed = false

  private joinWaiter: ((frame: { epoch: number; canEdit: boolean; init: boolean }) => void) | null = null

  private seedContent = ''

  private readonly documentId: string

  private readonly realtime: () => RealtimeApi

  private readonly onChange: () => void

  private readonly onReset: () => void

  constructor(documentId: string, realtime: () => RealtimeApi, onChange: () => void, onReset: () => void) {
    this.documentId = documentId
    this.realtime = realtime
    this.onChange = onChange
    this.onReset = onReset
  }

  // ---- lifecycle ----

  async start(user: { id: string; name: string; color: string }) {
    active.set(this.documentId, this)
    this.awareness.setLocalStateField('user', { id: user.id, name: user.name, color: user.color, colorLight: `${user.color}33` })
    this.listen()
    // Attached before the first sync: seeding the document from its stored text is a local change that must be sent.
    this.doc.on('update', this.onLocalUpdate)
    this.awareness.on('update', this.onAwarenessUpdate)
    try {
      await this.sync(true)
      this.broadcastAwareness([this.doc.clientID])
    } catch (err) {
      this.fail(err)
    }
  }

  /** Stops listening and timing right away, but keeps the document alive for a final {@link flush}. */
  detach() {
    this.destroyed = true
    clearTimeout(this.snapshotTimer)
    this.offs.forEach((off) => off())
    this.offs = []
    this.doc.off('update', this.onLocalUpdate)
    this.awareness.off('update', this.onAwarenessUpdate)
  }

  destroy() {
    this.detach()
    // Leave the room only if no newer session of this tab took over: a late `leave` would remove the new one.
    if (active.get(this.documentId) === this) {
      active.delete(this.documentId)
      this.realtime().send({ type: 'doc.leave', documentId: this.documentId })
    }
    this.awareness.destroy()
    this.doc.destroy()
  }

  /** Changes not yet stored on the server (a reload now would lose them). */
  get unsaved() {
    return this.changedSinceSnapshot || this.pending > 0
  }

  /** Stores the working copy now if anything changed since the last snapshot. */
  async flush() {
    clearTimeout(this.snapshotTimer)
    if (this.changedSinceSnapshot && this.canEdit) await this.snapshot(false, null)
  }

  /** Adds a version to the history (with an optional note) after saving the working copy. */
  async saveVersion(note: string | null) {
    clearTimeout(this.snapshotTimer)
    return this.snapshot(true, note)
  }

  // ---- joining and catching up ----

  /** Joins the room and loads the state. Safe to repeat (after a reconnect): updates already applied are ignored by Yjs. */
  private async sync(first: boolean) {
    this.set('connecting')
    const joined = await this.join()
    this.epoch = joined.epoch
    this.canEdit = joined.canEdit
    this.set('syncing')
    const state = await api<ServerState>(`/api/documents/${this.documentId}/collab`)
    if (state.epoch !== joined.epoch) throw new Error('RESTART')
    this.seedContent = state.content
    this.doc.transact(() => {
      if (state.snapshot) Y.applyUpdate(this.doc, fromBase64(state.snapshot), REMOTE)
      for (const u of state.updates) Y.applyUpdate(this.doc, fromBase64(u.update), REMOTE)
    }, REMOTE)
    this.lastSeq = Math.max(this.lastSeq, state.lastSeq)
    if (joined.init) this.seed()
    // Somebody else was asked to seed an empty document: wait for their update (or a later `doc.init` for us).
    this.set(state.empty && !joined.init && state.content.length > 0 && this.text.length === 0 ? 'syncing' : 'ready')
    // After a reconnect, send everything we did while away; the server and the others skip what they have.
    if (!first && (this.pending > 0 || this.changedSinceSnapshot)) {
      this.pending++
      this.realtime().send({ type: 'doc.update', documentId: this.documentId, epoch: this.epoch, update: toBase64(Y.encodeStateAsUpdate(this.doc)) })
    }
    if (!first) this.broadcastAwareness([this.doc.clientID])
  }

  private async join() {
    for (let attempt = 0; attempt < 30; attempt++) {
      if (this.destroyed) throw new Error('DESTROYED')
      const joined = new Promise<{ epoch: number; canEdit: boolean; init: boolean }>((resolve, reject) => {
        this.joinWaiter = resolve
        setTimeout(() => reject(new Error('TIMEOUT')), 6000)
      })
      if (this.realtime().send({ type: 'doc.join', documentId: this.documentId })) {
        try {
          return await joined
        } catch {
          // no answer: try again
        }
      } else {
        await new Promise((r) => setTimeout(r, 500))
      }
    }
    throw new Error('Không kết nối được tới máy chủ thời gian thực. Hãy kiểm tra mạng rồi thử lại.')
  }

  /** The stored text becomes CRDT data exactly once, by the one editor the server picked. */
  private seed() {
    if (this.text.length === 0 && this.seedContent.length > 0) {
      this.doc.transact(() => this.text.insert(0, this.seedContent), 'seed')
    }
  }

  // ---- messages from the server ----

  private listen() {
    const mine = (frame: Frame) => frame.documentId === this.documentId
    const { on } = this.realtime()
    this.offs.push(
      on('doc.joined', (f) => {
        if (!mine(f)) return
        this.joinWaiter?.({ epoch: Number(f.epoch), canEdit: Boolean(f.canEdit), init: Boolean(f.init) })
        this.joinWaiter = null
      }),
      on('doc.update', (f) => {
        if (!mine(f) || Number(f.epoch) !== this.epoch) return
        Y.applyUpdate(this.doc, fromBase64(String(f.update)), REMOTE)
        this.lastSeq = Math.max(this.lastSeq, Number(f.seq))
        if (this.status === 'syncing' && this.text.length > 0) this.set('ready')
        this.touch()
      }),
      on('doc.ack', (f) => {
        if (!mine(f)) return
        this.pending = Math.max(0, this.pending - 1)
        this.lastSeq = Math.max(this.lastSeq, Number(f.seq))
      }),
      on('doc.awareness', (f) => {
        if (mine(f)) applyAwarenessUpdate(this.awareness, fromBase64(String(f.update)), REMOTE)
      }),
      on('doc.peer_left', (f) => {
        if (mine(f)) removeAwarenessStates(this.awareness, (f.clientIds as number[]) ?? [], REMOTE)
      }),
      on('doc.init', (f) => {
        if (!mine(f)) return
        this.seed()
        this.set('ready')
      }),
      on('doc.reset', (f) => {
        if (!mine(f)) return
        this.set('reset')
        this.onReset()
      }),
      on('doc.error', (f) => {
        if (mine(f)) this.fail(new Error(f.code === 'FORBIDDEN' ? 'Bạn không có quyền sửa tài liệu này.' : `Lỗi đồng bộ (${String(f.code)}).`))
      }),
      on('reconnected', () => {
        if (this.destroyed) return
        this.sync(false).catch((err) => this.fail(err))
      }),
    )
  }

  // ---- local changes ----

  private readonly onLocalUpdate = (update: Uint8Array, origin: unknown) => {
    this.touch()
    if (origin === REMOTE || !this.canEdit) return
    this.pending++
    this.realtime().send({ type: 'doc.update', documentId: this.documentId, epoch: this.epoch, update: toBase64(update) })
    if (this.realtime().status !== 'open') this.set('offline')
  }

  private readonly onAwarenessUpdate = (change: { added: number[]; updated: number[]; removed: number[] }, origin: unknown) => {
    if (origin === REMOTE) return
    this.broadcastAwareness([...change.added, ...change.updated, ...change.removed])
  }

  private broadcastAwareness(clients: number[]) {
    this.realtime().send({
      type: 'doc.awareness',
      documentId: this.documentId,
      clientId: this.doc.clientID,
      update: toBase64(encodeAwarenessUpdate(this.awareness, clients)),
    })
  }

  // ---- saving ----

  private touch() {
    this.changedSinceSnapshot = true
    this.onChange()
    clearTimeout(this.snapshotTimer)
    if (this.canEdit) this.snapshotTimer = setTimeout(() => void this.snapshot(false, null).catch(() => {}), SNAPSHOT_AFTER_MS)
  }

  private async snapshot(createVersion: boolean, note: string | null) {
    const result = await api<{ applied: boolean; version: number }>(`/api/documents/${this.documentId}/collab/snapshot`, {
      method: 'POST',
      body: {
        epoch: this.epoch,
        seq: this.lastSeq,
        state: toBase64(Y.encodeStateAsUpdate(this.doc)),
        text: this.text.toString(),
        createVersion,
        note,
      },
    })
    this.changedSinceSnapshot = false
    this.savedAt = new Date()
    this.onChange()
    return result
  }

  // ---- state ----

  private set(status: CollabStatus) {
    if (this.status !== status) {
      this.status = status
      this.onChange()
    }
  }

  private fail(err: unknown) {
    const message = err instanceof Error ? err.message : String(err)
    if (message === 'DESTROYED') return
    if (message === 'RESTART') {
      this.set('reset')
      this.onReset()
      return
    }
    this.error = message
    this.set('error')
  }
}
