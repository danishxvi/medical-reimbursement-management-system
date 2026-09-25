import { useQuery } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'motion/react'
import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { NavLink, useLocation, useNavigate, useOutlet } from 'react-router'
import { api } from '../../api/client'
import type { Office, Role } from '../../api/types'
import { useMe, useAuth } from '../../auth/AuthContext'
import { initials } from '../../lib/format'
import { Icon } from '../../lib/icons'

interface NavItem {
  to: string
  label: string
  icon: ReactNode
  end?: boolean
}

interface NavGroup {
  title: string
  items: NavItem[]
}

/** Navigation per role: each official only sees their own work. */
function navFor(role: Role): NavGroup[] {
  const home: NavItem = { to: '/', label: 'Dashboard', icon: <Icon.Grid />, end: true }
  const alerts: NavItem = { to: '/notifications', label: 'Notifications', icon: <Icon.Bell /> }
  switch (role) {
    case 'EMPLOYEE':
      return [
        { title: 'Overview', items: [home] },
        {
          title: 'Reimbursement',
          items: [
            { to: '/claims', label: 'My claims', icon: <Icon.File />, end: true },
            { to: '/claims/new', label: 'New claim', icon: <Icon.Plus /> },
            { to: '/nac', label: 'e-NAC certificates', icon: <Icon.Pill /> },
          ],
        },
        { title: 'Account', items: [{ to: '/profile', label: 'Profile and family', icon: <Icon.User /> }, alerts] },
      ]
    case 'HOS':
      return [
        { title: 'Overview', items: [home] },
        {
          title: 'School',
          items: [
            { to: '/queue', label: 'Verification queue', icon: <Icon.Queue /> },
            { to: '/office-claims', label: 'School claims', icon: <Icon.File /> },
            { to: '/budget', label: 'Budget and demand', icon: <Icon.Rupee /> },
          ],
        },
        { title: 'Account', items: [alerts] },
      ]
    case 'PHARMACIST':
    case 'MEDICAL_OFFICER':
      return [
        { title: 'Overview', items: [home] },
        { title: 'Dispensary', items: [{ to: '/queue', label: 'Prescription queue', icon: <Icon.Queue /> }] },
        { title: 'Account', items: [alerts] },
      ]
    case 'PAO_AUDITOR':
    case 'PAO_OFFICER':
      return [
        { title: 'Overview', items: [home] },
        {
          title: 'Pay and Accounts Office',
          items: [
            {
              to: '/queue',
              label: role === 'PAO_AUDITOR' ? 'Scrutiny queue' : 'Sanction queue',
              icon: <Icon.Queue />,
            },
            { to: '/office-claims', label: 'Claims at this PAO', icon: <Icon.File /> },
            { to: '/budget', label: role === 'PAO_OFFICER' ? 'Budgets and payments' : 'School budgets', icon: <Icon.Rupee /> },
          ],
        },
        { title: 'Account', items: [alerts] },
      ]
    case 'ADMIN':
      return [
        { title: 'Overview', items: [home] },
        {
          title: 'Administration',
          items: [
            { to: '/admin/users', label: 'Official accounts', icon: <Icon.Users /> },
            { to: '/admin/employees', label: 'Employees', icon: <Icon.User /> },
            { to: '/admin/organisation', label: 'Offices and schools', icon: <Icon.Building /> },
            { to: '/admin/audit', label: 'Audit trail', icon: <Icon.Audit /> },
          ],
        },
      ]
  }
}

export function AppShell() {
  const me = useMe()
  const { logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const outlet = useOutlet()

  // Each new page starts at the top
  useEffect(() => {
    window.scrollTo({ top: 0 })
  }, [location.pathname])

  const office = useQuery({ queryKey: ['my-office'], queryFn: () => api.get<Office>('/api/org/my-office'), staleTime: 300_000 })
  const unread = useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: () => api.get<{ count: number }>('/api/notifications/unread-count'),
    refetchInterval: 60_000,
  })
  const count = unread.data?.count ?? 0

  return (
    <div className="shell">
      <a className="sr-only" href="#main">
        Skip to content
      </a>
      <NavLink to="/" className="brand" aria-label="MRMS home">
        <span className="brand-mark">
          <Icon.Cross />
        </span>
        <span className="brand-text">
          <strong>MRMS</strong>
          <span>Medical reimbursement</span>
        </span>
      </NavLink>

      <header className="header">
        <div className="header-office">
          <span className="caps muted">{office.data?.type === 'DIRECTORATE' ? 'Directorate' : office.data?.type ?? 'Office'}</span>
          <strong>
            {office.data?.name ?? ' '}
            {office.data?.code ? ' (' + office.data.code + ')' : ''}
          </strong>
        </div>
        <div className="header-actions">
          <button className="icon-btn" type="button" onClick={() => navigate('/notifications')} aria-label={`Notifications, ${count} unread`}>
            <Icon.Bell />
            <AnimatePresence>
              {count > 0 && (
                <motion.span className="dot" initial={{ scale: 0 }} animate={{ scale: 1 }} exit={{ scale: 0 }}>
                  {count > 99 ? '99+' : count}
                </motion.span>
              )}
            </AnimatePresence>
          </button>
          <div className="user-chip">
            <span className="avatar" aria-hidden="true">
              {initials(me.fullName)}
            </span>
            <span className="who">
              <strong>{me.fullName}</strong>
              <span>
                {me.roleLabel} · {me.username}
              </span>
            </span>
          </div>
          <button className="icon-btn" type="button" onClick={() => logout()} aria-label="Sign out">
            <Icon.Logout />
          </button>
        </div>
      </header>

      <nav className="nav" aria-label="Main">
        {navFor(me.role).map((group) => (
          <div key={group.title}>
            <div className="nav-section caps">{group.title}</div>
            {group.items.map((it) => (
              <NavLink key={it.to} to={it.to} end={it.end} className={({ isActive }) => 'nav-link' + (isActive ? ' active' : '')}>
                {({ isActive }) => (
                  <>
                    {isActive && (
                      <motion.span layoutId="nav-active" className="nav-active-bg" transition={{ type: 'spring', stiffness: 500, damping: 40 }} />
                    )}
                    {it.icon}
                    <span>{it.label}</span>
                  </>
                )}
              </NavLink>
            ))}
          </div>
        ))}
        <div className="nav-footer">
          Session ends after 15 minutes of inactivity. Every action is recorded in a tamper evident audit trail.
        </div>
      </nav>

      <main className="main" id="main">
        <AnimatePresence mode="wait">
          <Frozen key={location.pathname}>{outlet}</Frozen>
        </AnimatePresence>
      </main>
    </div>
  )
}

/** Keeps the old page on screen while it animates out. */
function Frozen({ children }: { children: ReactNode }) {
  const [frozen] = useState(children)
  return <>{frozen}</>
}
