import { motion } from 'motion/react'
import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router'
import { api } from '../../api/client'
import { Icon } from '../../lib/icons'
import { pop } from '../../lib/motion'

interface SignedRequest {
  txn: string
  espUrl: string
  fields: Record<string, string>
  documentInfo: string
  providerName: string | null
}

/**
 * Opens in the signing window. Fetches the signed request for the
 * transaction and posts it to the eSign provider, which is how providers
 * expect to be called. Being a page of this site, it can be opened by a
 * plain link when the browser blocks pop ups.
 */
export default function EsignLaunchPage() {
  const [params] = useSearchParams()
  const txn = params.get('txn')
  const [error, setError] = useState<string | null>(null)
  const [info, setInfo] = useState<string | null>(null)
  const form = useRef<HTMLFormElement>(null)
  const [request, setRequest] = useState<SignedRequest | null>(null)

  useEffect(() => {
    if (!txn) return
    api
      .get<SignedRequest>(`/api/esign/${encodeURIComponent(txn)}/request`)
      .then((r) => {
        setInfo(r.documentInfo)
        setRequest(r)
      })
      .catch((e) => setError(e instanceof Error ? e.message : 'The signing request could not be opened'))
  }, [txn])

  // Submit once the hidden form for the provider is on the page
  useEffect(() => {
    if (request) form.current?.submit()
  }, [request])

  return (
    <div className="auth-main" style={{ minHeight: '100vh' }}>
      <motion.div className="panel" style={{ maxWidth: 440, padding: 32, textAlign: 'center' }} variants={pop} initial="initial" animate="animate">
        <div className="guide-icon" style={{ margin: '0 auto 16px' }}>
          <Icon.Shield />
        </div>
        <h2 style={{ fontSize: 'var(--text-xl)' }}>{error ? 'Cannot open signing' : 'Aadhaar eSign'}</h2>
        <p className="muted" style={{ marginTop: 8 }}>
          {error ?? (txn ? 'Taking you to the eSign provider...' : 'Preparing the document...')}
        </p>
        {info && !error && (
          <p style={{ marginTop: 12, fontSize: 'var(--text-sm)' }}>
            <strong>{info}</strong>
          </p>
        )}
        {request && (
          <form ref={form} method="post" action={request.espUrl}>
            {Object.entries(request.fields).map(([name, value]) => (
              <input key={name} type="hidden" name={name} value={value} />
            ))}
          </form>
        )}
      </motion.div>
    </div>
  )
}
