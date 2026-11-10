import { expect, test } from '@playwright/test'
import { api, createProjectViaApi, loginViaUi, registerViaApi } from './support'

test('dragging a card to another column changes its status and survives a reload', async ({ page, request }) => {
  const owner = await registerViaApi(request, 'board')
  const { project } = await createProjectViaApi(request, owner, 'KANBAN')
  await api(request, owner.token, 'POST', `/api/projects/${project.id}/issues`, { type: 'TASK', title: 'Move me' })

  await loginViaUi(page, owner)
  await page.goto(`/projects/${project.id}/board`)

  const todo = page.locator('.board-column', { has: page.getByRole('heading', { name: /Cần làm/ }) })
  const doing = page.locator('.board-column', { has: page.getByRole('heading', { name: /Đang làm/ }) })
  const card = todo.locator('.board-card', { hasText: 'Move me' })
  await expect(card).toBeVisible()

  await card.dragTo(doing)
  await expect(doing.locator('.board-card', { hasText: 'Move me' })).toBeVisible()
  await expect(todo.locator('.board-card', { hasText: 'Move me' })).toHaveCount(0)

  await page.reload()
  await expect(doing.locator('.board-card', { hasText: 'Move me' })).toBeVisible()
})
