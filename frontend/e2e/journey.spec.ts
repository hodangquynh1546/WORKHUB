import { expect, test } from '@playwright/test'
import { PASSWORD, api, loginViaUi, registerViaApi, unique } from './support'

/** One person takes a team from nothing to a finished sprint, the way a real day goes. */
test('full journey: workspace, team, project, sprint, board, report, export, document, chat', async ({ page, request }) => {
  const email = `${unique('journey')}@example.com`
  const wsName = unique('Acme ')
  const teamName = unique('Platform ')
  const key = unique('J').toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 8)

  await test.step('register and create the workspace, team and project in the UI', async () => {
    await page.goto('/register')
    await page.getByLabel('Họ tên').fill('Trần Thị B')
    await page.getByLabel('Email').fill(email)
    await page.getByLabel('Mật khẩu (tối thiểu 8 ký tự)').fill(PASSWORD)
    await page.getByLabel('Nhập lại mật khẩu').fill(PASSWORD)
    await page.getByRole('button', { name: 'Đăng ký' }).click()
    await expect(page).toHaveURL(/\/workspaces/)

    await page.getByLabel('Tên workspace').fill(wsName)
    await page.getByRole('button', { name: 'Tạo workspace' }).click()
    await expect(page.getByRole('heading', { name: wsName })).toBeVisible()

    await page.getByLabel('Tạo team mới').fill(teamName)
    await page.getByRole('button', { name: 'Tạo team' }).click()
    await expect(page.getByRole('heading', { name: teamName })).toBeVisible()

    await page.getByRole('button', { name: 'Tạo dự án' }).click() // opens the form
    await page.getByLabel('Mã dự án').fill(key)
    await page.getByLabel('Tên dự án').fill('Checkout revamp')
    await page.getByRole('button', { name: 'Tạo dự án' }).last().click() // submits it
    await expect(page.getByRole('heading', { name: /Checkout revamp/ })).toBeVisible()
  })

  const projectId = page.url().match(/\/projects\/([0-9a-f-]+)/)![1]
  // The page keeps the access token in memory only; sign in again through the API for the setup calls.
  const login = await api<{ accessToken: string }>(request, null, 'POST', '/api/auth/login', { email, password: PASSWORD })
  const token = login.accessToken

  await test.step('plan a sprint with two issues and start it', async () => {
    const today = new Date()
    const iso = (offset: number) => new Date(today.getTime() + offset * 86_400_000).toISOString().slice(0, 10)
    const sprint = await api<{ id: string }>(request, token, 'POST', `/api/projects/${projectId}/sprints`, { name: 'Sprint 1', goal: 'Ship checkout', startDate: iso(0), endDate: iso(13), capacityPoints: 10 })
    const ids: string[] = []
    for (const title of ['Pay by card', 'Order confirmation email']) {
      const issue = await api<{ id: string }>(request, token, 'POST', `/api/projects/${projectId}/issues`, { type: 'TASK', title, storyPoints: 3 })
      ids.push(issue.id)
    }
    await api(request, token, 'POST', `/api/sprints/${sprint.id}/issues`, { issueIds: ids, includeChildren: false })
    await api(request, token, 'POST', `/api/sprints/${sprint.id}/start`)
  })

  await test.step('the board shows the sprint and a card is dragged to Done', async () => {
    await page.goto(`/projects/${projectId}/board`)
    const todo = page.locator('.board-column', { has: page.getByRole('heading', { name: /Cần làm/ }) })
    const done = page.locator('.board-column', { has: page.getByRole('heading', { name: /Hoàn thành/ }) })
    await expect(todo.locator('.board-card')).toHaveCount(2)
    await todo.locator('.board-card', { hasText: 'Pay by card' }).dragTo(done)
    await expect(done.locator('.board-card', { hasText: 'Pay by card' })).toBeVisible()
  })

  await test.step('the burndown report and the CSV export work', async () => {
    await page.goto(`/projects/${projectId}/reports`)
    await expect(page.getByRole('heading', { name: /Burndown/i }).first()).toBeVisible()
    await expect(page.locator('svg').first()).toBeVisible()

    await page.goto(`/projects/${projectId}/backlog`)
    await page.getByRole('button', { name: 'Xuất dữ liệu' }).click()
    const [download] = await Promise.all([page.waitForEvent('download'), page.getByRole('menuitem', { name: 'CSV' }).first().click()])
    expect(download.suggestedFilename()).toMatch(/\.csv$/)
  })

  await test.step('a document is written in the collaborative editor and saved to history', async () => {
    await page.goto(`/projects/${projectId}/documents`)
    await page.getByRole('button', { name: 'Tài liệu mới' }).click()
    await page.getByLabel('Tiêu đề').fill('Release plan')
    await page.getByRole('button', { name: /^Tạo/ }).last().click()
    await expect(page.getByRole('heading', { name: 'Release plan' })).toBeVisible()
    await page.getByRole('button', { name: /Chỉnh sửa/ }).click()
    const editor = page.locator('.cm-content')
    await editor.click()
    await page.keyboard.type('# Plan\n\nShip on Friday.')
    await expect(editor).toContainText('Ship on Friday.')
    await page.getByRole('button', { name: 'Xong' }).click()
    await expect(page.getByText('Ship on Friday.')).toBeVisible()
  })

  await test.step('a chat message reaches the project channel', async () => {
    await page.goto(`/projects/${projectId}/chat`)
    await page.getByPlaceholder(/Nhập tin nhắn/).fill('Sprint 1 started, good luck!')
    await page.keyboard.press('Enter')
    await expect(page.getByText('Sprint 1 started, good luck!')).toBeVisible()
  })
})

test('an assignee is notified in the bell, and a colleague sees live changes', async ({ page, request }) => {
  const owner = await registerViaApi(request, 'notif-owner')
  const dev = await registerViaApi(request, 'notif-dev')
  const ws = await api<{ id: string }>(request, owner.token, 'POST', '/api/workspaces', { name: unique('N ') })
  const team = await api<{ id: string }>(request, owner.token, 'POST', `/api/workspaces/${ws.id}/teams`, { name: unique('T ') })
  const invite = await api<{ inviteUrl: string }>(request, owner.token, 'POST', `/api/workspaces/${ws.id}/invitations`, { kind: 'LINK', role: 'MEMBER' })
  await api(request, dev.token, 'POST', '/api/invitations/accept', { token: invite.inviteUrl.split('/').pop() })
  const project = await api<{ id: string }>(request, owner.token, 'POST', `/api/teams/${team.id}/projects`, { key: unique('N').toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 8), name: 'Notify', methodology: 'KANBAN' })
  await api(request, owner.token, 'POST', `/api/projects/${project.id}/members`, { userId: dev.id, role: 'DEVELOPER' })

  await loginViaUi(page, dev)
  const subscribed = new Promise<void>((resolve) =>
    page.on('websocket', (ws) => ws.on('framesent', (frame) => String(frame.payload).includes('"subscribe"') && resolve())),
  )
  await page.goto(`/projects/${project.id}/backlog`)
  await subscribed

  // Created and assigned by someone else while this page is open: it appears without a reload, and a notification arrives.
  const issue = await api<{ id: string }>(request, owner.token, 'POST', `/api/projects/${project.id}/issues`, { type: 'TASK', title: 'Fix the login bug' })
  await expect(page.getByRole('link', { name: 'Fix the login bug' })).toBeVisible()
  await api(request, owner.token, 'PUT', `/api/issues/${issue.id}/assignee`, { assigneeId: dev.id })
  await page.getByRole('button', { name: /Thông báo/ }).click()
  await expect(page.getByText(/Fix the login bug/).last()).toBeVisible()
})
