import { expect, test } from '@playwright/test'
import { api, createProjectViaApi, loginViaUi, registerViaApi } from './support'

test('typing indicator, seen receipts and several files in one message work between two people', async ({ browser, request }) => {
  const alice = await registerViaApi(request, 'alice')
  const bob = await registerViaApi(request, 'bob')
  const { workspace, project } = await createProjectViaApi(request, alice)
  const invite = await api<{ inviteUrl: string }>(request, alice.token, 'POST', `/api/workspaces/${workspace.id}/invitations`, { kind: 'LINK', role: 'MEMBER' })
  await api(request, bob.token, 'POST', '/api/invitations/accept', { token: invite.inviteUrl.split('/').pop() })
  await api(request, alice.token, 'POST', `/api/projects/${project.id}/members`, { userId: bob.id, role: 'DEVELOPER' })

  const aliceContext = await browser.newContext()
  const bobContext = await browser.newContext()
  const a = await aliceContext.newPage()
  const b = await bobContext.newPage()
  await loginViaUi(a, alice)
  await loginViaUi(b, bob)
  await a.goto(`/projects/${project.id}/chat`)
  await b.goto(`/projects/${project.id}/chat`)
  const aBox = a.getByPlaceholder(/Nhập tin nhắn/)
  const bBox = b.getByPlaceholder(/Nhập tin nhắn/)
  await expect(aBox).toBeVisible()
  await expect(bBox).toBeVisible()
  await b.waitForTimeout(1000) // both sockets are connected and the pages have marked the channel read

  // Typing: Bob sees the waving dots while Alice types, and they go away when she stops.
  await aBox.pressSequentially('Hello Bob', { delay: 30 })
  await expect(b.locator('div.typing')).toContainText(`${alice.fullName} đang nhập`)
  await expect(b.locator('div.typing .typing-dots')).toBeVisible()
  await expect(b.locator('.typing-dots')).toHaveCount(0, { timeout: 8000 })

  // Seen: Alice sends, Bob has the channel open so it is read at once, and Alice sees "Đã xem" under her message.
  await aBox.press('Enter')
  await expect(b.locator('.msg-text', { hasText: 'Hello Bob' })).toBeVisible()
  await expect(a.locator('.msg-seen').first()).toContainText('Đã xem')
  await expect(a.locator('.msg-seen').first()).toHaveAttribute('title', new RegExp(bob.fullName))

  // Several files in one message.
  await a.getByLabel('Chọn tệp đính kèm').setInputFiles([
    { name: 'plan.txt', mimeType: 'text/plain', buffer: Buffer.from('plan') },
    { name: 'budget.csv', mimeType: 'text/csv', buffer: Buffer.from('a,b\n1,2') },
  ])
  await expect(a.getByText('plan.txt')).toBeVisible()
  await expect(a.getByText('budget.csv')).toBeVisible()
  await a.getByRole('button', { name: 'Gửi', exact: true }).click()
  await expect(b.getByRole('button', { name: /plan\.txt/ })).toBeVisible()
  await expect(b.getByRole('button', { name: /budget\.csv/ })).toBeVisible()
  await expect(b.locator('.msg-text', { hasText: '📎 2 tệp' })).toHaveCount(0) // the auto text is hidden under the files

  await aliceContext.close()
  await bobContext.close()
})

test('the conversation list shows who is typing even when that conversation is not open', async ({ browser, request }) => {
  const alice = await registerViaApi(request, 'alice2')
  const bob = await registerViaApi(request, 'bob2')
  const { workspace, project } = await createProjectViaApi(request, alice)
  const invite = await api<{ inviteUrl: string }>(request, alice.token, 'POST', `/api/workspaces/${workspace.id}/invitations`, { kind: 'LINK', role: 'MEMBER' })
  await api(request, bob.token, 'POST', '/api/invitations/accept', { token: invite.inviteUrl.split('/').pop() })
  await api(request, alice.token, 'POST', `/api/projects/${project.id}/members`, { userId: bob.id, role: 'DEVELOPER' })

  const aliceContext = await browser.newContext()
  const bobContext = await browser.newContext()
  const a = await aliceContext.newPage()
  const b = await bobContext.newPage()
  await loginViaUi(a, alice)
  await loginViaUi(b, bob)
  await a.goto(`/projects/${project.id}/chat`)
  await b.goto('/chat') // the list only, no conversation selected
  await expect(b.locator('.chat-item-main').first()).toBeVisible()
  await b.waitForTimeout(1000)

  await a.getByPlaceholder(/Nhập tin nhắn/).pressSequentially('typing now', { delay: 30 })
  const item = b.locator('.chat-item-main', { hasText: 'đang nhập' })
  await expect(item).toContainText(`${alice.fullName} đang nhập`)
  await expect(item.locator('.typing-dots')).toBeVisible()
  // It fades out by itself, and the preview comes back.
  await expect(b.locator('.chat-item-main', { hasText: 'đang nhập' })).toHaveCount(0, { timeout: 8000 })

  await aliceContext.close()
  await bobContext.close()
})
