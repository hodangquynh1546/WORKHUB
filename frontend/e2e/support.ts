import { expect, type APIRequestContext, type Page } from '@playwright/test'

export const PASSWORD = 'E2e-test-pass-1'

export type Account = { email: string; fullName: string; token: string; id: string }

let counter = 0
export const unique = (prefix: string) => `${prefix}${Date.now().toString(36)}${(counter++).toString(36)}`

/** Calls the API through the Vite proxy (same origin as the app). */
export async function api<T>(request: APIRequestContext, token: string | null, method: string, path: string, data?: unknown): Promise<T> {
  const response = await request.fetch(path, { method, data, headers: token ? { Authorization: `Bearer ${token}` } : undefined })
  expect(response.ok(), `${method} ${path} -> ${response.status()} ${await response.text()}`).toBeTruthy()
  const text = await response.text()
  return (text ? JSON.parse(text) : null) as T
}

export async function registerViaApi(request: APIRequestContext, label = 'user'): Promise<Account> {
  const email = `${unique(label)}@example.com`
  const fullName = `E2E ${label}`
  const res = await api<{ accessToken: string; user: { id: string } }>(request, null, 'POST', '/api/auth/register', { email, password: PASSWORD, fullName })
  return { email, fullName, token: res.accessToken, id: res.user.id }
}

export async function loginViaUi(page: Page, account: Account) {
  await page.goto('/login')
  await page.getByLabel('Email').fill(account.email)
  await page.getByLabel('Mật khẩu', { exact: true }).fill(PASSWORD)
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click()
  await expect(page).toHaveURL(/\/workspaces/)
}

export type Setup = { workspace: { id: string; name: string }; team: { id: string; name: string }; project: { id: string; key: string; name: string } }

/** An owner with a workspace, a team and a Scrum project, all created through the API. */
export async function createProjectViaApi(request: APIRequestContext, owner: Account, methodology: 'SCRUM' | 'KANBAN' = 'SCRUM'): Promise<Setup> {
  const workspace = await api<Setup['workspace']>(request, owner.token, 'POST', '/api/workspaces', { name: unique('Workspace ') })
  const team = await api<Setup['team']>(request, owner.token, 'POST', `/api/workspaces/${workspace.id}/teams`, { name: unique('Team ') })
  const key = unique('P').toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 8)
  const project = await api<Setup['project']>(request, owner.token, 'POST', `/api/teams/${team.id}/projects`, { key, name: unique('Project '), methodology })
  return { workspace, team, project }
}
