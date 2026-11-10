import { expect, test } from '@playwright/test'
import { api, createProjectViaApi, loginViaUi, registerViaApi } from './support'

test('sub-tasks show nested under their parent when it is expanded, and the backlog takes new work and status changes', async ({ page, request }) => {
  const owner = await registerViaApi(request, 'backlog')
  const { project } = await createProjectViaApi(request, owner)
  const parent = await api<{ id: string; key: string }>(request, owner.token, 'POST', `/api/projects/${project.id}/issues`, { type: 'TASK', title: 'Build the checkout' })
  for (const title of ['Design the form', 'Wire the payment API']) {
    await api(request, owner.token, 'POST', `/api/projects/${project.id}/issues`, { type: 'SUB_TASK', title, parentId: parent.id })
  }

  await loginViaUi(page, owner)
  await page.goto(`/projects/${project.id}/backlog`)

  // Sub-tasks are not rows of the backlog; they appear when the parent is opened.
  await expect(page.getByRole('link', { name: 'Build the checkout' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Design the form' })).toHaveCount(0)
  await page.getByRole('button', { name: 'Mở rộng việc con' }).click()
  await expect(page.getByRole('link', { name: 'Design the form' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Wire the payment API' })).toBeVisible()
  await page.getByRole('button', { name: 'Thu gọn việc con' }).click()
  await expect(page.getByRole('link', { name: 'Design the form' })).toHaveCount(0)

  // Quick add at the end of the backlog.
  await page.getByLabel('Tiêu đề').fill('Write the release notes')
  await page.getByRole('button', { name: 'Thêm', exact: true }).click()
  await expect(page.getByRole('link', { name: 'Write the release notes' })).toBeVisible()

  // Inline status change sticks after a reload.
  const row = page.locator('.issue-row', { hasText: 'Write the release notes' })
  await row.getByLabel('Trạng thái').selectOption('IN_PROGRESS')
  await page.reload()
  await expect(page.locator('.issue-row', { hasText: 'Write the release notes' }).getByLabel('Trạng thái')).toHaveValue('IN_PROGRESS')
})
