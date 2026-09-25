import { expect } from '@playwright/test'
import type { Page } from '@playwright/test'

/** Public demo password of the development profile. */
export const DEMO_PASSWORD = 'Demo@Pass2026'

/**
 * Signs in through the form. On a first sign in the privacy notice is
 * acknowledged and the guide skipped, the way a new user would.
 */
export async function signIn(page: Page, username: string): Promise<void> {
  await page.goto('/login')
  await page.getByLabel(/Employee ID \/ Login ID/i).fill(username)
  await page.getByLabel(/^Password/i).fill(DEMO_PASSWORD)
  await page.getByRole('button', { name: 'Sign in' }).click()

  // Whichever screen comes next: the privacy notice, the guide, or the app itself
  const notice = page.getByText(/I have read and understood the privacy notice/)
  const guide = page.getByRole('button', { name: 'Skip the guide' })
  const app = page.getByRole('button', { name: 'Sign out' })
  for (let step = 0; step < 3; step++) {
    await expect(notice.or(guide).or(app).first()).toBeVisible()
    if (await notice.isVisible()) {
      await notice.click()
      await page.getByRole('button', { name: 'Continue' }).click()
      await expect(notice).toBeHidden()
    } else if (await guide.isVisible()) {
      await guide.click()
      await expect(guide).toBeHidden()
    } else {
      return
    }
  }
}

export async function signOut(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page).toHaveURL(/\/login/)
}

/**
 * Calls the API with the page's session, as the app does (CSRF token from
 * its cookie). Used to prepare data quickly; the step under test always
 * goes through the screens.
 */
export async function apiPost<T>(page: Page, url: string, body: unknown): Promise<T> {
  await page.request.get('/api/auth/csrf')
  const cookies = await page.context().cookies()
  const token = cookies.find((c) => c.name === 'XSRF-TOKEN')?.value ?? ''
  const response = await page.request.post(url, { data: body, headers: { 'X-XSRF-TOKEN': decodeURIComponent(token) } })
  expect(response.ok(), `${url} answered ${response.status()}: ${await response.text()}`).toBeTruthy()
  return (await response.json().catch(() => undefined)) as T
}

export async function uploadPdf(page: Page, category: string, marker: string): Promise<string> {
  await page.request.get('/api/auth/csrf')
  const token = (await page.context().cookies()).find((c) => c.name === 'XSRF-TOKEN')?.value ?? ''
  const pdf = Buffer.from(`%PDF-1.4\n% ${marker}\n1 0 obj << /Type /Catalog >> endobj\n%%EOF`, 'latin1')
  const response = await page.request.post('/api/documents', {
    multipart: { category, file: { name: 'prescription.pdf', mimeType: 'application/pdf', buffer: pdf } },
    headers: { 'X-XSRF-TOKEN': decodeURIComponent(token) },
  })
  expect(response.ok()).toBeTruthy()
  return (await response.json()).id
}

/** A 12 digit test number with a valid Verhoeff check digit (not a real Aadhaar number). */
export const TEST_AADHAAR = '999900001231'
