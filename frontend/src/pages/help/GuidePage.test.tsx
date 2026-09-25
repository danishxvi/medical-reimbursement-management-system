import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { api } from '../../api/client'
import type { Me, Role } from '../../api/types'
import { demoUser, renderWithProviders } from '../../test/render'
import GuidePage from './GuidePage'
import { GUIDES } from './guides'

let me: Me = demoUser()

vi.mock('../../auth/AuthContext', () => ({
  useMe: () => me,
  useAuth: () => ({ me, loading: false }),
}))

vi.mock('../../api/client', async (original) => {
  const actual = await original<typeof import('../../api/client')>()
  return { ...actual, api: { ...actual.api, post: vi.fn().mockResolvedValue(undefined) } }
})

describe('Guide', () => {
  it('has a guide for every role, each ending with where to find help', () => {
    const roles: Role[] = ['EMPLOYEE', 'HOS', 'PHARMACIST', 'MEDICAL_OFFICER', 'PAO_AUDITOR', 'PAO_OFFICER', 'ADMIN', 'OVERSIGHT']
    roles.forEach((r) => {
      expect(GUIDES[r].steps.length).toBeGreaterThan(1)
      expect(GUIDES[r].steps.at(-1)?.title).toBe('Help is always here')
    })
  })

  it('walks through the steps and records the first run as finished', async () => {
    me = demoUser({ guideSeen: false })
    renderWithProviders(<GuidePage />)
    const steps = GUIDES.EMPLOYEE.steps
    expect(screen.getByRole('heading', { name: steps[0].title })).toBeInTheDocument()

    for (let i = 1; i < steps.length; i++) {
      await userEvent.click(screen.getByRole('button', { name: /Next/ }))
      expect(await screen.findByRole('heading', { name: steps[i].title })).toBeInTheDocument()
    }
    await userEvent.click(screen.getByRole('button', { name: /Start using MRMS/ }))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/auth/guide/seen'))
  })

  it('can be skipped on first sign in', async () => {
    me = demoUser({ role: 'HOS', guideSeen: false })
    renderWithProviders(<GuidePage />)
    await userEvent.click(screen.getByRole('button', { name: /Skip the guide/ }))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/auth/guide/seen'))
  })
})
