import { expect, test } from '@playwright/test'
import { PASSWORD, api, createProjectViaApi, loginViaUi, registerViaApi } from './support'

const PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64')

test('files and images can be sent in the project chat', async ({ page, request }) => {
  const owner = await registerViaApi(request, 'chatfile')
  const { project } = await createProjectViaApi(request, owner)
  await loginViaUi(page, owner)
  await page.goto(`/projects/${project.id}/chat`)

  await page.getByLabel('Chọn tệp đính kèm').setInputFiles({ name: 'shot.png', mimeType: 'image/png', buffer: PNG })
  await page.getByPlaceholder(/Nhập tin nhắn/).fill('Here is the mock-up')
  await page.getByRole('button', { name: 'Gửi', exact: true }).click()
  await expect(page.getByText('Here is the mock-up')).toBeVisible()
  const image = page.getByRole('img', { name: 'shot.png' })
  await expect(image).toBeVisible()
  expect(await image.evaluate((el: HTMLImageElement) => el.naturalWidth)).toBeGreaterThan(0)

  // A plain file without a caption: shown as a download chip, and it really downloads.
  await page.getByLabel('Chọn tệp đính kèm').setInputFiles({ name: 'notes.txt', mimeType: 'text/plain', buffer: Buffer.from('release notes') })
  await page.getByRole('button', { name: 'Gửi', exact: true }).click()
  const chip = page.getByRole('button', { name: /notes\.txt/ }).last()
  await expect(chip).toBeVisible()
  const [download] = await Promise.all([page.waitForEvent('download'), chip.click()])
  expect(download.suggestedFilename()).toBe('notes.txt')
})

test('the owner hands the workspace to another member', async ({ page, request }) => {
  const owner = await registerViaApi(request, 'handoverowner')
  const heir = await registerViaApi(request, 'heir')
  const { workspace } = await createProjectViaApi(request, owner)
  const invite = await api<{ inviteUrl: string }>(request, owner.token, 'POST', `/api/workspaces/${workspace.id}/invitations`, { kind: 'LINK', role: 'MEMBER' })
  await api(request, heir.token, 'POST', '/api/invitations/accept', { token: invite.inviteUrl.split('/').pop() })

  await loginViaUi(page, owner)
  await page.goto(`/workspaces/${workspace.id}`)
  const card = page.locator('section.card', { has: page.getByRole('heading', { name: 'Chuyển quyền chủ sở hữu' }) }).last()
  await card.getByLabel('Chủ sở hữu mới').selectOption({ label: `${heir.fullName} (${heir.email})` })
  await card.getByLabel('Nhập mật khẩu của bạn để xác nhận').fill('wrong-password')
  page.once('dialog', (dialog) => dialog.accept())
  await card.getByRole('button', { name: 'Chuyển quyền' }).click()
  await expect(card.getByRole('alert')).toContainText('Mật khẩu hiện tại không đúng')

  await card.getByLabel('Nhập mật khẩu của bạn để xác nhận').fill(PASSWORD)
  page.once('dialog', (dialog) => dialog.accept())
  await card.getByRole('button', { name: 'Chuyển quyền' }).click()
  // The previous owner is an admin now: the ownership controls are gone and the role badge says so.
  await expect(page.locator('.badge', { hasText: 'Quản trị viên' }).first()).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Chuyển quyền chủ sở hữu' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Xóa workspace' })).toHaveCount(0)
})

test('deleting the account signs the person out for good', async ({ page, request }) => {
  const user = await registerViaApi(request, 'goodbye')
  await loginViaUi(page, user)

  await page.getByRole('button', { name: 'Tài khoản' }).click()
  await page.locator('summary', { hasText: 'Xóa tài khoản' }).click()
  await page.getByLabel('Nhập mật khẩu để xác nhận').fill(PASSWORD)
  page.once('dialog', (dialog) => dialog.accept())
  await page.getByRole('button', { name: 'Xóa tài khoản' }).last().click()
  await expect(page).toHaveURL(/\/login/)

  await page.getByLabel('Email').fill(user.email)
  await page.getByLabel('Mật khẩu', { exact: true }).fill(PASSWORD)
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('Email hoặc mật khẩu không đúng')
})

test('a workspace owner cannot delete the account while owning a workspace', async ({ page, request }) => {
  const owner = await registerViaApi(request, 'stillowner')
  await createProjectViaApi(request, owner)
  await loginViaUi(page, owner)

  await page.getByRole('button', { name: 'Tài khoản' }).click()
  await page.locator('summary', { hasText: 'Xóa tài khoản' }).click()
  await page.getByLabel('Nhập mật khẩu để xác nhận').fill(PASSWORD)
  page.once('dialog', (dialog) => dialog.accept())
  await page.getByRole('button', { name: 'Xóa tài khoản' }).last().click()
  await expect(page.getByRole('alert')).toContainText('chủ của workspace')
})
