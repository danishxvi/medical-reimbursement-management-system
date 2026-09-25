import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Me } from '../api/types'
import { demoUser, renderWithProviders } from '../test/render'
import { RequireAuth } from './Guards'

let current: { me: Me | null; loading: boolean } = { me: null, loading: false }

vi.mock('./AuthContext', () => ({
  useAuth: () => current,
}))

function guarded(route: string) {
  return renderWithProviders(
    <RequireAuth>
      <div>Protected page</div>
    </RequireAuth>,
    { route, path: route },
  )
}

describe('RequireAuth: first sign in happens in a fixed order', () => {
  beforeEach(() => {
    current = { me: null, loading: false }
  })

  it('sends anonymous visitors to the sign in page', () => {
    guarded('/claims')
    expect(screen.getByTestId('location')).toBeInTheDocument()
    expect(screen.queryByText('Protected page')).not.toBeInTheDocument()
  })

  it('asks for a new password before anything else', () => {
    current = { me: demoUser({ mustChangePassword: true, privacyAccepted: false, guideSeen: false }), loading: false }
    guarded('/claims')
    expect(screen.queryByText('Protected page')).not.toBeInTheDocument()
  })

  it('then asks for the privacy notice, and lets that page itself through', () => {
    current = { me: demoUser({ privacyAccepted: false, guideSeen: false }), loading: false }
    guarded('/claims')
    expect(screen.queryByText('Protected page')).not.toBeInTheDocument()
    guarded('/welcome/privacy')
    expect(screen.getByText('Protected page')).toBeInTheDocument()
  })

  it('then opens the guide, while the notice stays readable', () => {
    current = { me: demoUser({ guideSeen: false }), loading: false }
    guarded('/claims')
    expect(screen.queryByText('Protected page')).not.toBeInTheDocument()
    guarded('/guide')
    expect(screen.getAllByText('Protected page')).toHaveLength(1)
  })

  it('opens every page once onboarding is complete', () => {
    current = { me: demoUser(), loading: false }
    guarded('/claims')
    expect(screen.getByText('Protected page')).toBeInTheDocument()
  })
})
