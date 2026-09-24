import { useState } from 'react'
import type { ReactNode } from 'react'
import { Button } from './Button'
import { TextField } from './Form'
import { Modal } from './Modal'

interface Props {
  open: boolean
  title: string
  children?: ReactNode
  confirmLabel: string
  busy?: boolean
  onCancel: () => void
  onConfirm: (password: string) => void
}

/**
 * Re-enter the password before a legally significant action (certifying,
 * countersigning, sanctioning). It works like a signature and is recorded.
 */
export function PasswordConfirm({ open, title, children, confirmLabel, busy, onCancel, onConfirm }: Props) {
  const [password, setPassword] = useState('')
  const close = () => {
    setPassword('')
    onCancel()
  }
  return (
    <Modal
      open={open}
      title={title}
      onClose={close}
      footer={
        <>
          <Button onClick={close}>Cancel</Button>
          <Button
            variant="primary"
            loading={busy}
            disabled={!password}
            onClick={() => {
              onConfirm(password)
              setPassword('')
            }}
          >
            {confirmLabel}
          </Button>
        </>
      }
    >
      <form
        className="stack"
        onSubmit={(e) => {
          e.preventDefault()
          if (password) {
            onConfirm(password)
            setPassword('')
          }
        }}
      >
        {children}
        <TextField label="Your password" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} hint="Confirms that you, personally, are taking this action." />
      </form>
    </Modal>
  )
}
