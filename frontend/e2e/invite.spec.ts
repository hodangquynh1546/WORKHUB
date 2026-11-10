import { expect, test } from '@playwright/test'
import { createProjectViaApi, loginViaUi, registerViaApi } from './support'

test('a project manager invites someone by link from the members page and the invitee lands in the project', async ({ browser, page, request }) => {
  const owner = await registerViaApi(request, 'owner')
  const invitee = await registerViaApi(request, 'invitee')
  const { project } = await createProjectViaApi(request, owner)

  await loginViaUi(page, owner)
  await page.goto(`/projects/${project.id}/members`)
  await expect(page.getByRole('heading', { name: 'Mời người mới vào dự án' })).toBeVisible()
  const card = page.locator('section.card', { has: page.getByRole('heading', { name: 'Mời người mới vào dự án' }) }).last()
  await card.getByLabel('Liên kết mời').check()
  await card.getByLabel('Vai trò').selectOption('QA_ENGINEER')
  await card.getByRole('button', { name: 'Tạo liên kết' }).click()
  const url = (await page.locator('.invite-url').textContent())!.trim()
  expect(url).toContain('/invitations/')

  // The invitee opens the link in their own browser session.
  const context = await browser.newContext()
  const other = await context.newPage()
  await loginViaUi(other, invitee)
  await other.goto(url)
  await other.getByRole('button', { name: 'Chấp nhận' }).click()
  await expect(other).toHaveURL(new RegExp(`/projects/${project.id}`))
  await other.goto(`/projects/${project.id}/members`)
  await expect(other.getByRole('table')).toContainText(invitee.fullName)
  // A member who is not a manager cannot invite further people.
  await expect(other.getByRole('heading', { name: 'Mời người mới vào dự án' })).toHaveCount(0)
  await context.close()

  // The owner sees the new member in the list.
  await page.reload()
  await expect(page.getByRole('table')).toContainText(invitee.fullName)
})
