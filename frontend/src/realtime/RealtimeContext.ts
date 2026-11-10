import { createContext, useContext } from 'react'

export type RealtimeStatus = 'closed' | 'connecting' | 'open'

/** A frame from the server; `type` says what it is, the rest depends on the type. */
export type Frame = { type: string; [key: string]: unknown }

export type RealtimeApi = {
  status: RealtimeStatus
  /** Listens to frames of one type (also the synthetic `reconnected`); returns the function that stops listening. */
  on: (type: string, handler: (frame: Frame) => void) => () => void
  /** Sends a frame if the socket is open; returns whether it was sent. */
  send: (frame: object) => boolean
  /** Asks to hear about changes to a project; reference-counted, returns the function that gives it back. */
  subscribe: (projectId: string) => () => void
}

export const RealtimeContext = createContext<RealtimeApi>({
  status: 'closed',
  on: () => () => {},
  send: () => false,
  subscribe: () => () => {},
})

export const useRealtime = () => useContext(RealtimeContext)
