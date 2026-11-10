import { expect, test } from '@playwright/test'
import { api, createProjectViaApi, loginViaUi, registerViaApi } from './support'

test('deleting a team needs its name typed and removes it with its projects', async ({ page, request }) => {
  const owner = await registerViaApi(request, 'delteam')
  const { workspace, team } = await createProjectViaApi(request, owner)

  await loginViaUi(page, owner)
  await page.goto(`/teams/${team.id}`)
  await page.getByRole('button', { name: 'Xóa team' }).click()
  const confirm = page.getByRole('button', { name: 'Xóa vĩnh viễn' })
  await expect(confirm).toBeDisabled()
  await page.getByLabel(/Nhập ".*" để xác nhận/).fill('wrong name')
  await expect(confirm).toBeDisabled()
  await page.getByLabel(/Nhập ".*" để xác nhận/).fill(team.name)
  await confirm.click()

  await expect(page).toHaveURL(new RegExp(`/workspaces/${workspace.id}`))
  await expect(page.getByText(team.name)).toHaveCount(0)
})

test('only the owner sees the workspace delete zone, and deleting it sends them back to the list', async ({ page, browser, request }) => {
  const owner = await registerViaApi(request, 'delws')
  const admin = await registerViaApi(request, 'admin')
  const { workspace } = await createProjectViaApi(request, owner)
  const invite = await api<{ inviteUrl: string }>(request, owner.token, 'POST', `/api/workspaces/${workspace.id}/invitations`, { kind: 'LINK', role: 'ADMIN' })
  await api(request, admin.token, 'POST', '/api/invitations/accept', { token: invite.inviteUrl.split('/').pop() })

  const adminContext = await browser.newContext()
  const adminPage = await adminContext.newPage()
  await loginViaUi(adminPage, admin)
  await adminPage.goto(`/workspaces/${workspace.id}`)
  await expect(adminPage.getByRole('heading', { name: workspace.name })).toBeVisible()
  await expect(adminPage.getByRole('button', { name: 'Xóa workspace' })).toHaveCount(0)
  await adminContext.close()

  await loginViaUi(page, owner)
  await page.goto(`/workspaces/${workspace.id}`)
  await page.getByRole('button', { name: 'Xóa workspace' }).click()
  await page.getByLabel(/Nhập ".*" để xác nhận/).fill(workspace.name)
  await page.getByRole('button', { name: 'Xóa vĩnh viễn' }).click()
  await expect(page).toHaveURL(/\/workspaces$/)
  await expect(page.getByText(workspace.name)).toHaveCount(0)
})

test('a workspace and team can be renamed from their settings', async ({ page, request }) => {
  const owner = await registerViaApi(request, 'rename')
  const { workspace, team } = await createProjectViaApi(request, owner)
  await loginViaUi(page, owner)

  await page.goto(`/workspaces/${workspace.id}`)
  await page.getByLabel('Tên workspace').fill('Renamed workspace')
  await page.getByRole('button', { name: 'Lưu', exact: true }).click()
  await expect(page.getByText('Đã lưu thay đổi.')).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Renamed workspace' })).toBeVisible()

  await page.goto(`/teams/${team.id}`)
  await page.getByLabel('Tên team').fill('Renamed team')
  await page.getByRole('button', { name: 'Lưu', exact: true }).click()
  await expect(page.getByText('Đã lưu thay đổi.')).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Renamed team' })).toBeVisible()
})
