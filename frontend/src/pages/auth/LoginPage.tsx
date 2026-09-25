import { motion } from 'motion/react'
import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router'
import { useAuth } from '../../auth/AuthContext'
import { Button } from '../../components/ui/Button'
import { Callout } from '../../components/ui/Feedback'
import { TextField } from '../../components/ui/Form'
import { Icon } from '../../lib/icons'
import { item, list } from '../../lib/motion'

const REASONS: Record<string, string> = {
  SESSION_REPLACED: 'You were signed out because your account signed in on another device.',
  UNAUTHENTICATED: 'Your session has ended. Please sign in again.',
}

export default function LoginPage() {
  const { me, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const state = location.state as { from?: string; reason?: string } | null
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (me) return <Navigate to={me.mustChangePassword ? '/change-password' : '/'} replace />

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      const user = await login(username.trim(), password)
      navigate(user.mustChangePassword ? '/change-password' : state?.from ?? '/', { replace: true })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Sign in failed')
      setPassword('')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="auth-page">
      <aside className="auth-side">
        <div className="grid-art" />
        <motion.div className="row" initial={{ opacity: 0 }} animate={{ opacity: 1 }} style={{ position: 'relative' }}>
          <span className="brand-mark">
            <Icon.Cross />
          </span>
          <span className="brand-text">
            <strong>MRMS</strong>
            <span>Proposed for Directorate of Education, GNCT of Delhi</span>
          </span>
        </motion.div>
        <motion.h1 initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1, duration: 0.5 }}>
          Medical reimbursement, without the paper chase.
        </motion.h1>
        <motion.div className="points" variants={list} initial="initial" animate="animate">
          {[
            ['e-NAC', 'Item level dispensary certificates'],
            ['FIFO', 'First come, first served queues'],
            ['0', 'Files carried by hand'],
            ['100%', 'Actions on a tamper evident record'],
          ].map(([k, v]) => (
            <motion.div key={k} variants={item}>
              <strong>{k}</strong>
              <span>{v}</span>
            </motion.div>
          ))}
        </motion.div>
      </aside>

      <main className="auth-main">
        <motion.form className="auth-card stack" onSubmit={submit} variants={list} initial="initial" animate="animate" noValidate>
          <motion.div variants={item}>
            <div className="caps muted">Secure sign in</div>
            <h2 style={{ fontSize: 'var(--text-xl)', marginTop: 8 }}>Welcome</h2>
            <p className="muted" style={{ marginTop: 6 }}>
              Employees sign in with their Employee ID. Officials use the login ID issued by the Directorate.
            </p>
          </motion.div>

          {state?.reason && REASONS[state.reason] && (
            <Callout variant="dashed" title="Signed out">
              <p>{REASONS[state.reason]}</p>
            </Callout>
          )}

          <motion.div variants={item}>
            <TextField
              label="Employee ID / Login ID"
              name="username"
              autoComplete="username"
              autoCapitalize="characters"
              spellCheck={false}
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              required
              maxLength={40}
            />
          </motion.div>
          <motion.div variants={item}>
            <TextField
              label="Password"
              name="password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              maxLength={128}
            />
          </motion.div>

          {error && (
            <motion.div variants={item} initial="initial" animate="animate">
              <Callout variant="stripe" title="Could not sign in">
                <p>{error}</p>
              </Callout>
            </motion.div>
          )}

          <motion.div variants={item}>
            <Button type="submit" variant="primary" block loading={busy} disabled={!username || !password} icon={<Icon.Lock />}>
              Sign in
            </Button>
          </motion.div>
          {import.meta.env.DEV && <DemoAccounts onPick={(u) => { setUsername(u); setPassword('Demo@Pass2026') }} />}
          <motion.p variants={item} className="muted" style={{ fontSize: 'var(--text-xs)' }}>
            Forgot your password? Ask your Head of School or the Directorate administrator to issue a temporary one.
            Never share your password; officials will never ask for it.
          </motion.p>
          <motion.p variants={item} className="legal-links">
            <Link to="/privacy">Privacy notice</Link>
            <span aria-hidden="true">·</span>
            <span>Noncommercial use only. Not an official Government of NCT of Delhi system.</span>
          </motion.p>
        </motion.form>
      </main>
    </div>
  )
}

/**
 * Development builds only: one click fills a seeded demo account. Vite
 * removes this block from production builds (import.meta.env.DEV is false).
 */
function DemoAccounts({ onPick }: { onPick: (username: string) => void }) {
  const accounts: [string, string][] = [
    ['EMP1001', 'Employee'],
    ['HOS9900001', 'Head of School'],
    ['PHARM01', 'Pharmacist'],
    ['MO01', 'Medical Officer'],
    ['AUD01', 'PAO Auditor'],
    ['PAO01', 'PAO Officer'],
    ['ADMIN', 'Administrator'],
  ]
  return (
    <motion.div variants={item} className="panel" style={{ padding: 12 }}>
      <div className="caps muted" style={{ marginBottom: 8 }}>
        Demo accounts (development only)
      </div>
      <div className="row" style={{ gap: 6 }}>
        {accounts.map(([u, label]) => (
          <button key={u} type="button" className="btn btn-sm" onClick={() => onPick(u)} title={u}>
            {label}
          </button>
        ))}
      </div>
    </motion.div>
  )
}
