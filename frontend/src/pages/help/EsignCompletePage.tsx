import { motion } from 'motion/react'
import { useEffect } from 'react'
import { useSearchParams } from 'react-router'
import { Icon } from '../../lib/icons'
import { pop } from '../../lib/motion'

/**
 * The eSign provider returns the signing window here. It tells the waiting
 * MRMS tab (same origin) and closes itself. No session is needed.
 */
export default function EsignCompletePage() {
  const [params] = useSearchParams()
  const ok = params.get('status') === 'ok'
  const txn = params.get('txn')

  useEffect(() => {
    if (typeof BroadcastChannel !== 'undefined') {
      const channel = new BroadcastChannel('mrms-esign')
      channel.postMessage({ txn, status: ok ? 'ok' : 'failed' })
      channel.close()
    }
    const t = window.setTimeout(() => window.close(), 1500)
    return () => window.clearTimeout(t)
  }, [ok, txn])

  return (
    <div className="auth-main" style={{ minHeight: '100vh' }}>
      <motion.div className="panel" style={{ maxWidth: 420, padding: 32, textAlign: 'center' }} variants={pop} initial="initial" animate="animate">
        <div className="guide-icon" style={{ margin: '0 auto 16px' }}>{ok ? <Icon.Check /> : <Icon.Shield />}</div>
        <h2 style={{ fontSize: 'var(--text-xl)' }}>{ok ? 'Signed' : 'Not signed'}</h2>
        <p className="muted" style={{ marginTop: 8 }}>
          {ok ? 'Your signature was received. This window closes by itself; you can also close it.' : 'Signing was not completed. Close this window and try again.'}
        </p>
      </motion.div>
    </div>
  )
}
