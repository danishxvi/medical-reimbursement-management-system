import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { api } from '../../api/client'
import { renderWithProviders } from '../../test/render'
import { SignConfirm } from './SignConfirm'

vi.mock('../../api/client', async (original) => {
  const actual = await original<typeof import('../../api/client')>()
  return { ...actual, api: { ...actual.api, get: vi.fn(), post: vi.fn() } }
})

function dialog(onConfirm = vi.fn(), ready = true) {
  renderWithProviders(
    <SignConfirm
      open
      title="Sign the certificate"
      confirmLabel="Certify and forward"
      onCancel={() => undefined}
      onConfirm={onConfirm}
      purpose="CLAIM_HOS_CERTIFY"
      subjectId={42}
      payload={{ remarks: null }}
      ready={ready}
    >
      <p>You are certifying claim MR/1.</p>
    </SignConfirm>,
  )
  return onConfirm
}

describe('SignConfirm', () => {
  it('in password mode signs with the password only once one is typed', async () => {
    vi.mocked(api.get).mockResolvedValue({ mode: 'PASSWORD', providerName: '', simulator: false })
    const onConfirm = dialog()
    const button = await screen.findByRole('button', { name: 'Certify and forward' })
    expect(button).toBeDisabled()
    await userEvent.type(screen.getByLabelText(/Your password/i), 'S3cret@Pass2026')
    await userEvent.click(button)
    expect(onConfirm).toHaveBeenCalledWith({ password: 'S3cret@Pass2026' })
  })

  it('stays disabled while the action is not ready (for example no rejection reason)', async () => {
    vi.mocked(api.get).mockResolvedValue({ mode: 'PASSWORD', providerName: '', simulator: false })
    dialog(vi.fn(), false)
    await userEvent.type(await screen.findByLabelText(/Your password/i), 'x')
    expect(screen.getByRole('button', { name: 'Certify and forward' })).toBeDisabled()
  })

  it('in eSign mode offers Aadhaar eSign and never asks for a password or an Aadhaar number', async () => {
    vi.mocked(api.get).mockResolvedValue({ mode: 'ESIGN', providerName: 'Test ESP', simulator: false })
    dialog()
    expect(await screen.findByRole('button', { name: /Sign with Aadhaar eSign/i })).toBeEnabled()
    expect(screen.queryByLabelText(/password/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/aadhaar/i)).not.toBeInTheDocument()
    expect(screen.getByText(/MRMS never sees your Aadhaar/i)).toBeInTheDocument()
  })

  it('starts the signing with exactly the payload the action will send', async () => {
    vi.mocked(api.get).mockResolvedValue({ mode: 'ESIGN', providerName: 'Test ESP', simulator: false })
    vi.mocked(api.post).mockResolvedValue({ txn: 'MRMS-1', espUrl: 'https://esp.example', fields: {}, documentInfo: 'Doc', providerName: 'Test ESP' })
    const open = vi.spyOn(window, 'open').mockReturnValue(null)
    dialog()
    await userEvent.click(await screen.findByRole('button', { name: /Sign with Aadhaar eSign/i }))
    await waitFor(() =>
      expect(api.post).toHaveBeenCalledWith('/api/esign/start', { purpose: 'CLAIM_HOS_CERTIFY', subjectId: '42', payload: { remarks: null } }),
    )
    expect(open).toHaveBeenCalled()
    // Pop up blocked: a plain link to the launch page is offered instead
    expect(await screen.findByRole('link', { name: /Open the eSign page/i })).toHaveAttribute('href', '/esign/launch?txn=MRMS-1')
  })
})
