import { expect, test } from '@playwright/test'
import { PASSWORD, loginViaUi, registerViaApi, unique } from './support'

test('registering through the form signs the user in', async ({ page }) => {
  const email = `${unique('reg')}@example.com`
  await page.goto('/register')
  await page.getByLabel('Họ tên').fill('Nguyễn Văn A')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Mật khẩu (tối thiểu 8 ký tự)').fill(PASSWORD)
  await page.getByLabel('Nhập lại mật khẩu').fill(PASSWORD)
  await page.getByRole('button', { name: 'Đăng ký' }).click()
  await expect(page).toHaveURL(/\/workspaces/)
  await expect(page.getByRole('heading', { name: 'Workspaces của bạn' })).toBeVisible()
})

test('the eye icon shows and hides the password', async ({ page }) => {
  await page.goto('/login')
  const password = page.getByLabel('Mật khẩu', { exact: true })
  await password.fill('secret-value')
  await expect(password).toHaveAttribute('type', 'password')
  await page.getByRole('button', { name: 'Hiện mật khẩu' }).click()
  await expect(password).toHaveAttribute('type', 'text')
  await page.getByRole('button', { name: 'Ẩn mật khẩu' }).click()
  await expect(password).toHaveAttribute('type', 'password')
})

test('a wrong password is refused and the right one signs in; an anonymous visitor is sent to the login', async ({ page, request }) => {
  const account = await registerViaApi(request, 'login')
  await page.goto('/workspaces')
  await expect(page).toHaveURL(/\/login/)

  await page.getByLabel('Email').fill(account.email)
  await page.getByLabel('Mật khẩu', { exact: true }).fill('not-the-password')
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('Email hoặc mật khẩu không đúng')

  await loginViaUi(page, account)
})
