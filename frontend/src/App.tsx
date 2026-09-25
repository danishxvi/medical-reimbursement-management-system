import { Suspense, lazy } from 'react'
import type { ReactNode } from 'react'
import { Outlet, createBrowserRouter } from 'react-router'
import { AuthProvider } from './auth/AuthContext'
import { RequireAuth, RoleGate } from './auth/Guards'
import { AppShell } from './components/layout/AppShell'
import { PageSkeleton } from './components/ui/Feedback'
import type { Role } from './api/types'

// Pages are split into separate chunks and loaded on first visit
const LoginPage = lazy(() => import('./pages/auth/LoginPage'))
const ChangePasswordPage = lazy(() => import('./pages/auth/ChangePasswordPage'))
const DashboardPage = lazy(() => import('./pages/DashboardPage'))
const ClaimsListPage = lazy(() => import('./pages/claims/ClaimsListPage'))
const OfficeClaimsPage = lazy(() => import('./pages/claims/ClaimsListPage').then((m) => ({ default: m.OfficeClaimsPage })))
const ClaimDetailPage = lazy(() => import('./pages/claims/ClaimDetailPage'))
const ClaimEditorPage = lazy(() => import('./pages/claims/ClaimEditorPage'))
const NacListPage = lazy(() => import('./pages/nac/NacPages').then((m) => ({ default: m.NacListPage })))
const NacDetailPage = lazy(() => import('./pages/nac/NacPages').then((m) => ({ default: m.NacDetailPage })))
const NacFormPage = lazy(() => import('./pages/nac/NacPages').then((m) => ({ default: m.NacFormPage })))
const QueuePage = lazy(() => import('./pages/queue/QueuePage'))
const BudgetPage = lazy(() => import('./pages/budget/BudgetPages'))
const PaoSchoolBudgetPage = lazy(() => import('./pages/budget/BudgetPages').then((m) => ({ default: m.PaoSchoolBudgetPage })))
const UsersPage = lazy(() => import('./pages/admin/AdminPages').then((m) => ({ default: m.UsersPage })))
const EmployeesPage = lazy(() => import('./pages/admin/AdminPages').then((m) => ({ default: m.EmployeesPage })))
const OrganisationPage = lazy(() => import('./pages/admin/AdminPages').then((m) => ({ default: m.OrganisationPage })))
const AuditPage = lazy(() => import('./pages/admin/AdminPages').then((m) => ({ default: m.AuditPage })))
const ProfilePage = lazy(() => import('./pages/AccountPages').then((m) => ({ default: m.ProfilePage })))
const NotificationsPage = lazy(() => import('./pages/AccountPages').then((m) => ({ default: m.NotificationsPage })))
const RatesPage = lazy(() => import('./pages/admin/RatesPage'))
const OversightPage = lazy(() => import('./pages/oversight/OversightPage'))
const EsignCompletePage = lazy(() => import('./pages/help/EsignCompletePage'))
const EsignLaunchPage = lazy(() => import('./pages/help/EsignLaunchPage'))
const GuidePage = lazy(() => import('./pages/help/GuidePage'))
const PrivacyNoticePage = lazy(() => import('./pages/help/PrivacyPages').then((m) => ({ default: m.PrivacyNoticePage })))
const PublicPrivacyPage = lazy(() => import('./pages/help/PrivacyPages').then((m) => ({ default: m.PublicPrivacyPage })))
const AcceptPrivacyPage = lazy(() => import('./pages/help/PrivacyPages').then((m) => ({ default: m.AcceptPrivacyPage })))
const NotFoundPage = lazy(() => import('./pages/AccountPages').then((m) => ({ default: m.NotFoundPage })))

function Loading() {
  return (
    <div className="content">
      <PageSkeleton />
    </div>
  )
}

function S({ children }: { children: ReactNode }) {
  return <Suspense fallback={<Loading />}>{children}</Suspense>
}

function only(roles: Role[], page: ReactNode) {
  return (
    <RoleGate roles={roles}>
      <S>{page}</S>
    </RoleGate>
  )
}

/** Root route: the auth context needs the router (it navigates on session loss). */
function Root() {
  return (
    <AuthProvider>
      <Outlet />
    </AuthProvider>
  )
}

const REVIEWERS: Role[] = ['HOS', 'PAO_AUDITOR', 'PAO_OFFICER']
const QUEUE_ROLES: Role[] = ['HOS', 'PAO_AUDITOR', 'PAO_OFFICER', 'PHARMACIST', 'MEDICAL_OFFICER']

export const router = createBrowserRouter([
  {
    element: <Root />,
    children: [
      { path: '/login', element: <S><LoginPage /></S> },
      { path: '/privacy', element: <S><PublicPrivacyPage /></S> },
      { path: '/esign/complete', element: <S><EsignCompletePage /></S> },
      { path: '/esign/launch', element: <S><EsignLaunchPage /></S> },
      { path: '/change-password', element: <RequireAuth><S><ChangePasswordPage /></S></RequireAuth> },
      { path: '/welcome/privacy', element: <RequireAuth><S><AcceptPrivacyPage /></S></RequireAuth> },
      {
        path: '/',
        element: (
          <RequireAuth>
            <AppShell />
          </RequireAuth>
        ),
        children: [
          { index: true, element: <S><DashboardPage /></S> },
          { path: 'claims', element: only(['EMPLOYEE'], <ClaimsListPage />) },
          { path: 'claims/new', element: only(['EMPLOYEE'], <ClaimEditorPage />) },
          { path: 'claims/:id', element: only(['EMPLOYEE', ...REVIEWERS], <ClaimDetailPage />) },
          { path: 'claims/:id/edit', element: only(['EMPLOYEE'], <ClaimEditorPage />) },
          { path: 'nac', element: only(['EMPLOYEE'], <NacListPage />) },
          { path: 'nac/new', element: only(['EMPLOYEE'], <NacFormPage />) },
          { path: 'nac/:id', element: only(['EMPLOYEE', 'PHARMACIST', 'MEDICAL_OFFICER'], <NacDetailPage />) },
          { path: 'nac/:id/resubmit', element: only(['EMPLOYEE'], <NacFormPage />) },
          { path: 'queue', element: only(QUEUE_ROLES, <QueuePage />) },
          { path: 'office-claims', element: only(REVIEWERS, <OfficeClaimsPage />) },
          { path: 'budget', element: only(REVIEWERS, <BudgetPage />) },
          { path: 'budget/schools/:schoolId', element: only(['PAO_AUDITOR', 'PAO_OFFICER'], <PaoSchoolBudgetPage />) },
          { path: 'profile', element: only(['EMPLOYEE'], <ProfilePage />) },
          { path: 'notifications', element: <S><NotificationsPage /></S> },
          { path: 'guide', element: <S><GuidePage /></S> },
          { path: 'privacy-notice', element: <S><PrivacyNoticePage /></S> },
          { path: 'admin/users', element: only(['ADMIN'], <UsersPage />) },
          { path: 'admin/employees', element: only(['ADMIN'], <EmployeesPage />) },
          { path: 'admin/organisation', element: only(['ADMIN'], <OrganisationPage />) },
          { path: 'admin/audit', element: only(['ADMIN'], <AuditPage />) },
          { path: 'admin/rates', element: only(['ADMIN'], <RatesPage />) },
          { path: 'oversight', element: only(['OVERSIGHT', 'ADMIN'], <OversightPage />) },
          { path: '*', element: <S><NotFoundPage /></S> },
        ],
      },
    ],
  },
])
