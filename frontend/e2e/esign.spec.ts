import { expect, test } from '@playwright/test'
import { TEST_AADHAAR, apiPost, signIn, signOut, uploadPdf } from './support.ts'

/**
 * The Medical Officer countersigns an e-NAC with Aadhaar eSign: the signing
 * window opens the provider (the built in simulator), the officer signs with
 * an OTP, and the dialog finishes the countersignature by itself.
 */
test('Medical Officer countersigns with Aadhaar eSign in a signing window', async ({ page }) => {
  // Prepared through the API: an employee's prescription, decided by the pharmacist
  await signIn(page, 'EMP1002')
  const prescription = await uploadPdf(page, 'PRESCRIPTION', 'e2e-esign')
  const today = new Date().toISOString().substring(0, 10)
  await apiPost(page, '/api/nac', {
    dispensaryId: 1,
    prescriptionDate: today,
    prescribedBy: 'Dr. Demo',
    prescriptionDocumentId: prescription,
    items: [{ itemName: 'Tab. Amlodipine 5 mg', itemType: 'MEDICINE', quantity: '30 tablets' }],
  })
  await signOut(page)

  await signIn(page, 'PHARM01')
  const taken = await apiPost<{ id: number; items: { id: number }[] }>(page, '/api/nac/queue/take-next', {})
  await apiPost(page, `/api/nac/${taken.id}/pharmacist-review`, {
    decisions: taken.items.map((i) => ({ itemId: i.id, decision: 'NOT_AVAILABLE' })),
  })
  await signOut(page)

  // Under test: the countersignature through the screens
  await signIn(page, 'MO01')
  await page.getByRole('link', { name: 'Prescription queue' }).click()
  await page.getByRole('button', { name: 'Take next prescription' }).click()
  await page.getByRole('button', { name: 'Countersign and issue' }).click()
  await expect(page.getByText(/MRMS never sees your Aadhaar number/)).toBeVisible()

  const popupPromise = page.waitForEvent('popup')
  await page.getByRole('button', { name: 'Sign with Aadhaar eSign' }).click()
  const esp = await popupPromise

  // The provider's page: the Aadhaar number is typed only here
  await expect(esp.getByRole('heading', { name: 'Sign with Aadhaar' })).toBeVisible()
  await expect(esp.getByText(/Countersignature of e-NAC/)).toBeVisible()
  await esp.getByLabel(/Aadhaar number or Virtual ID/).fill(TEST_AADHAAR)
  await esp.getByLabel(/Name as in Aadhaar/).fill('Dr Neha Singh')
  await esp.getByLabel(/^OTP/).fill('000000')
  await esp.getByRole('button', { name: 'Sign' }).click()
  await expect(esp.getByText('The OTP is not correct')).toBeVisible()
  await esp.getByLabel(/Aadhaar number or Virtual ID/).fill(TEST_AADHAAR)
  await esp.getByLabel(/Name as in Aadhaar/).fill('Dr Neha Singh')
  await esp.getByLabel(/^OTP/).fill('123456')
  await esp.getByRole('button', { name: 'Sign' }).click()
  await esp.getByRole('button', { name: 'Return to MRMS' }).click()

  // Back in MRMS: the signature is verified and the certificate issued without further clicks
  await expect(page.getByText(/Certificate NAC\/DSP-D01\/.+ issued/)).toBeVisible({ timeout: 30_000 })
})
