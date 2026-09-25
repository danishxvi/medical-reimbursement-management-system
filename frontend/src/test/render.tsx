import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router'
import type { Me } from '../api/types'
import { ToastProvider } from '../components/ui/Toast'

/** Renders with the providers every screen expects: queries, toasts and a router. */
export function renderWithProviders(ui: ReactElement, { route = '/', path = '*' }: { route?: string; path?: string } = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <MemoryRouter initialEntries={[route]}>
          <Routes>
            <Route path={path} element={ui} />
            <Route path="*" element={<LocationProbe />} />
          </Routes>
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  )
}

/** Where a redirect landed, for assertions. */
function LocationProbe() {
  return <div data-testid="location">{window.location.pathname}</div>
}

export function demoUser(overrides: Partial<Me> = {}): Me {
  return {
    id: 1,
    username: 'EMP1001',
    fullName: 'Asha Verma',
    role: 'EMPLOYEE',
    roleLabel: 'Employee',
    schoolId: 1,
    dispensaryId: null,
    paoId: null,
    mustChangePassword: false,
    privacyAccepted: true,
    privacyNoticeVersion: '1.0',
    guideSeen: true,
    ...overrides,
  }
}
