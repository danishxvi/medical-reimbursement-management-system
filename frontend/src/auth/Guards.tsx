import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import type { Role } from '../api/types'
import { PageSkeleton } from '../components/ui/Feedback'
import { useAuth } from './AuthContext'

/** Sends anonymous users to the login page and remembers where they were going. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { me, loading } = useAuth()
  const location = useLocation()
  if (loading) {
    return (
      <div style={{ padding: 48 }}>
        <PageSkeleton />
      </div>
    )
  }
  if (!me) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  // First sign in, in order: own password, privacy notice, role guide
  if (me.mustChangePassword) {
    return location.pathname === '/change-password' ? <>{children}</> : <Navigate to="/change-password" replace />
  }
  if (!me.privacyAccepted) {
    return location.pathname === '/welcome/privacy' ? <>{children}</> : <Navigate to="/welcome/privacy" replace />
  }
  if (!me.guideSeen && location.pathname !== '/guide' && location.pathname !== '/privacy-notice') {
    return <Navigate to="/guide" replace />
  }
  return <>{children}</>
}

/**
 * Hides a route from roles that may not use it. This is a convenience only:
 * the server enforces every permission independently.
 */
export function RoleGate({ roles, children }: { roles: Role[]; children: ReactNode }) {
  const { me } = useAuth()
  if (!me || !roles.includes(me.role)) return <Navigate to="/" replace />
  return <>{children}</>
}
