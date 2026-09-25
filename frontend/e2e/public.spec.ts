import { expect, test } from '@playwright/test'
import { signIn } from './support.ts'

test('sign in page states the licence and links the privacy notice', async ({ page }) => {
  await page.goto('/login')
  await expect(page.getByText(/Noncommercial use only/)).toBeVisible()
  await page.getByRole('link', { name: 'Privacy notice' }).click()
  await expect(page.getByRole('heading', { name: 'Privacy notice' })).toBeVisible()
  await expect(page.getByText(/Digital Personal Data Protection Act, 2023/).first()).toBeVisible()
})

test('a new user reads the privacy notice, then the guide, then reaches the dashboard', async ({ page }) => {
  await page.goto('/login')
  await page.getByLabel(/Employee ID \/ Login ID/i).fill('EMP2001')
  await page.getByLabel(/^Password/i).fill('Demo@Pass2026')
  await page.getByRole('button', { name: 'Sign in' }).click()

  await expect(page).toHaveURL(/\/welcome\/privacy/)
  const proceed = page.getByRole('button', { name: 'Continue' })
  await expect(proceed).toBeDisabled()
  await page.getByText(/I have read and understood the privacy notice/).click()
  await proceed.click()

  await expect(page).toHaveURL(/\/guide/)
  await expect(page.getByRole('heading', { name: /User guide: Employee/ })).toBeVisible()
  await page.getByRole('button', { name: 'Next' }).click()
  await expect(page.getByRole('heading', { name: 'Get an e-NAC for medicines' })).toBeVisible()
  await page.getByRole('button', { name: 'Skip the guide' }).click()

  await expect(page.getByRole('heading', { name: /Good (morning|afternoon|evening), Imran/ })).toBeVisible()
  // The guide stays in the menu
  await expect(page.getByRole('link', { name: 'User guide' })).toBeVisible()
})

test('an oversight officer lands on the time limits of the zone', async ({ page }) => {
  await signIn(page, 'DDE06')
  await expect(page).toHaveURL(/\/oversight/)
  await expect(page.getByRole('heading', { name: 'Time limits' })).toBeVisible()
  await expect(page.getByText('Zone 6')).toBeVisible()
})
