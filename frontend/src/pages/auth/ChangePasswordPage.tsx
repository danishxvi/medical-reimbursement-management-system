import { useQueryClient } from '@tanstack/react-query'
import { motion } from 'motion/react'
import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { api } from '../../api/client'
import { useAuth, useMe } from '../../auth/AuthContext'
import { Button } from '../../components/ui/Button'
import { ErrorCallout } from '../../components/ui/Feedback'
import { TextField } from '../../components/ui/Form'
import { useToast } from '../../components/ui/Toast'
import { Icon } from '../../lib/icons'
import { item, list } from '../../lib/motion'

/** Live checklist that mirrors the server's password policy. */
function rules(password: string, username: string) {
  return [
    { ok: password.length >= 12, label: 'At least 12 characters' },
    { ok: /[A-Z]/.test(password), label: 'An upper case letter' },
    { ok: /[a-z]/.test(password), label: 'A lower case letter' },
    { ok: /\d/.test(password), label: 'A digit' },
    { ok: /[^A-Za-z0-9]/.test(password), label: 'A symbol such as @ # $ %' },
    { ok: !!password && !password.toLowerCase().includes(username.toLowerCase()), label: 'Does not contain your ID' },
  ]
}

export default function ChangePasswordPage() {
  const me = useMe()
  const { logout } = useAuth()
  const navigate = useNavigate()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)
  const checks = useMemo(() => rules(next, me.username), [next, me.username])
  const valid = checks.every((c) => c.ok) && next === confirm && current.length > 0

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await api.post('/api/auth/change-password', { currentPassword: current, newPassword: next })
      await queryClient.invalidateQueries({ queryKey: ['me'] })
      toast.ok('Password changed. Other devices have been signed out.')
      navigate('/', { replace: true })
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="auth-main" style={{ minHeight: '100vh' }}>
      <motion.form className="auth-card stack" onSubmit={submit} variants={list} initial="initial" animate="animate" style={{ maxWidth: 460 }}>
        <motion.div variants={item}>
          <div className="caps muted">{me.mustChangePassword ? 'First sign in' : 'Security'}</div>
          <h2 style={{ fontSize: 'var(--text-xl)', marginTop: 8 }}>Set a new password</h2>
          <p className="muted" style={{ marginTop: 6 }}>
            {me.mustChangePassword
              ? 'Your account was created with a temporary password. Choose your own to continue.'
              : 'Choose a strong password you have not used before.'}
          </p>
        </motion.div>
        <motion.div variants={item}>
          <TextField label="Current password" type="password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} required />
        </motion.div>
        <motion.div variants={item}>
          <TextField label="New password" type="password" autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} required />
        </motion.div>
        <motion.ul variants={item} className="checklist" style={{ gridTemplateColumns: '1fr 1fr' }} aria-live="polite">
          {checks.map((c) => (
            <li key={c.label} className={c.ok ? 'ok' : undefined}>
              <span className="box">{c.ok ? <Icon.Check width={12} height={12} /> : ''}</span>
              <span>{c.label}</span>
            </li>
          ))}
        </motion.ul>
        <motion.div variants={item}>
          <TextField
            label="Confirm new password"
            type="password"
            autoComplete="new-password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            error={confirm && confirm !== next ? 'The passwords do not match' : undefined}
            required
          />
        </motion.div>
        <ErrorCallout error={error} />
        <motion.div variants={item} className="grid grid-2">
          <Button onClick={() => (me.mustChangePassword ? logout() : navigate(-1))}>{me.mustChangePassword ? 'Sign out' : 'Cancel'}</Button>
          <Button type="submit" variant="primary" loading={busy} disabled={!valid}>
            Change password
          </Button>
        </motion.div>
      </motion.form>
    </div>
  )
}
