import { useQuery } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'motion/react'
import { useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { api } from '../../api/client'
import { Icon } from '../../lib/icons'
import { fade } from '../../lib/motion'
import { Button } from './Button'
import { Callout } from './Feedback'
import { TextField } from './Form'
import { Modal } from './Modal'

/** What the signer supplies with the action: a password, or a completed eSign transaction. */
export interface StepUp {
  password?: string
  esignTxn?: string
}

interface EsignConfig {
  mode: 'PASSWORD' | 'ESIGN'
  providerName: string
  simulator: boolean
}

interface Started {
  txn: string
  espUrl: string
  fields: Record<string, string>
  documentInfo: string
  providerName: string | null
}

interface Props {
  open: boolean
  title: string
  children?: ReactNode
  confirmLabel: string
  busy?: boolean
  onCancel: () => void
  onConfirm: (stepUp: StepUp) => void
  /** Signing purpose known to the server, for example CLAIM_HOS_CERTIFY */
  purpose: string
  subjectId: string | number
  /** Exactly the body the action will send (without password or transaction): the signature covers it. */
  payload: Record<string, unknown>
  /** False while something the action needs is missing (for example a rejection reason). */
  ready?: boolean
}

export function useSigningMode() {
  return useQuery({ queryKey: ['esign-config'], queryFn: () => api.get<EsignConfig>('/api/esign/config'), staleTime: Infinity })
}

const POPUP = 'mrms-esign'
const launchUrl = (txn: string) => `/esign/launch?txn=${encodeURIComponent(txn)}`

/**
 * Signature before a legally significant action (certifying, countersigning,
 * sanctioning, rejecting, paying). Depending on the portal's configuration
 * the signer re-enters their password or signs with Aadhaar eSign at the
 * eSign provider, in a separate window. The Aadhaar number is typed only on
 * the provider's page.
 */
export function SignConfirm(props: Props) {
  const mode = useSigningMode()
  // A fresh dialog every time it opens, so an earlier signature is never reused by accident
  if (mode.data?.mode === 'ESIGN') return <EsignDialog key={props.open ? 'open' : 'closed'} {...props} config={mode.data} />
  return <PasswordDialog {...props} />
}

function PasswordDialog({ open, title, children, confirmLabel, busy, onCancel, onConfirm, ready = true }: Props) {
  const [password, setPassword] = useState('')
  const close = () => {
    setPassword('')
    onCancel()
  }
  const submit = () => {
    if (!password || !ready) return
    onConfirm({ password })
    setPassword('')
  }
  return (
    <Modal
      open={open}
      title={title}
      onClose={close}
      footer={
        <>
          <Button onClick={close}>Cancel</Button>
          <Button variant="primary" loading={busy} disabled={!password || !ready} onClick={submit}>
            {confirmLabel}
          </Button>
        </>
      }
    >
      <form
        className="stack"
        onSubmit={(e) => {
          e.preventDefault()
          submit()
        }}
      >
        {children}
        <TextField
          label="Your password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          hint="Confirms that you, personally, are taking this action."
        />
      </form>
    </Modal>
  )
}

type Phase = 'idle' | 'starting' | 'blocked' | 'waiting' | 'signed' | 'failed'

function EsignDialog({ open, title, children, confirmLabel, busy, onCancel, onConfirm, purpose, subjectId, payload, ready = true, config }: Props & { config: EsignConfig }) {
  const [phase, setPhase] = useState<Phase>('idle')
  const [started, setStarted] = useState<Started | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const confirmed = useRef(false)

  const reset = () => {
    setPhase('idle')
    setStarted(null)
    setMessage(null)
    confirmed.current = false
  }
  const close = () => {
    reset()
    onCancel()
  }

  // Wait for the provider's result: a message from the completion page, or polling as a fallback
  useEffect(() => {
    if (phase !== 'waiting' || !started) return
    let stopped = false
    const finish = (status: string, text?: string | null) => {
      if (stopped) return
      if (status === 'SIGNED' || status === 'ok') {
        stopped = true
        setPhase('signed')
        if (!confirmed.current) {
          confirmed.current = true
          onConfirm({ esignTxn: started.txn })
        }
      } else if (status === 'FAILED' || status === 'failed') {
        stopped = true
        setPhase('failed')
        setMessage(text || 'Signing was not completed')
      }
    }
    const check = () =>
      api
        .get<{ status: string; message: string | null }>(`/api/esign/${started.txn}`)
        .then((s) => finish(s.status, s.message))
        .catch(() => undefined)
    let channel: BroadcastChannel | null = null
    if (typeof BroadcastChannel !== 'undefined') {
      channel = new BroadcastChannel(POPUP)
      channel.onmessage = (e) => {
        if (e.data?.txn === started.txn) check()
      }
    }
    const timer = window.setInterval(check, 2500)
    return () => {
      stopped = true
      window.clearInterval(timer)
      channel?.close()
    }
  }, [phase, started, onConfirm])

  async function sign() {
    // Open the window inside the click, so the browser does not block it. It shows our own launch
    // page, which posts the signed request to the provider once the transaction exists.
    const popup = window.open('/esign/launch', POPUP, 'popup,width=520,height=760')
    setPhase('starting')
    setMessage(null)
    try {
      const s = await api.post<Started>('/api/esign/start', { purpose, subjectId: String(subjectId), payload })
      setStarted(s)
      if (popup) {
        popup.location.replace(launchUrl(s.txn))
        setPhase('waiting')
      } else {
        // Pop ups blocked: a plain link to the launch page is always allowed
        setPhase('blocked')
      }
    } catch (e) {
      popup?.close()
      setPhase('failed')
      setMessage(e instanceof Error ? e.message : 'Could not start signing')
    }
  }

  const provider = started?.providerName || config.providerName || 'the eSign provider'
  return (
    <Modal
      open={open}
      title={title}
      onClose={close}
      footer={
        <>
          <Button onClick={close}>Cancel</Button>
          {phase === 'idle' || phase === 'failed' ? (
            <Button variant="primary" icon={<Icon.Shield />} disabled={!ready} onClick={sign}>
              {phase === 'failed' ? 'Try again' : 'Sign with Aadhaar eSign'}
            </Button>
          ) : phase === 'blocked' && started ? (
            <a className="btn btn-primary" href={launchUrl(started.txn)} target="_blank" rel="opener" onClick={() => setPhase('waiting')}>
              Open the eSign page <Icon.Arrow />
            </a>
          ) : phase === 'signed' && !busy ? (
            // The action did not go through (see the message above): sign the current content again
            <Button variant="primary" icon={<Icon.Shield />} disabled={!ready} onClick={reset}>
              Sign again
            </Button>
          ) : (
            <Button variant="primary" loading={phase !== 'signed' || busy} disabled>
              {phase === 'signed' ? confirmLabel : 'Waiting for signature'}
            </Button>
          )}
        </>
      }
    >
      <div className="stack">
        {children}
        <AnimatePresence mode="wait">
          <motion.div key={phase} variants={fade} initial="initial" animate="animate" exit="exit">
            {phase === 'idle' && (
              <Callout title="Aadhaar eSign">
                <p>
                  A window from {provider} opens. Enter your Aadhaar number or Virtual ID there and the OTP sent to your registered mobile. MRMS never sees your Aadhaar
                  number.
                  {config.simulator && ' (Development: simulated provider, OTP 123456.)'}
                </p>
              </Callout>
            )}
            {phase === 'blocked' && (
              <Callout variant="dashed" title="Open the eSign page">
                <p>Your browser blocked the signing window. Use the button below to open the eSign page in a new tab; this dialog continues by itself once you have signed.</p>
              </Callout>
            )}
            {(phase === 'starting' || phase === 'waiting') && (
              <Callout variant="dashed" title={phase === 'starting' ? 'Preparing the document' : 'Complete signing in the provider window'}>
                {started && (
                  <p>
                    Document: <strong>{started.documentInfo}</strong>
                  </p>
                )}
                <p className="muted">This dialog continues by itself once you have signed.</p>
              </Callout>
            )}
            {phase === 'signed' && (
              <Callout title="Signed">
                <p>Your signature was verified. {busy ? 'Completing the action...' : ''}</p>
              </Callout>
            )}
            {phase === 'failed' && message && (
              <Callout variant="stripe" title="Not signed">
                <p>{message}</p>
              </Callout>
            )}
          </motion.div>
        </AnimatePresence>
      </div>
    </Modal>
  )
}
