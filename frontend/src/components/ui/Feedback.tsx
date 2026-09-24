import { motion } from 'motion/react'
import type { ReactNode } from 'react'
import type { ClaimStatus, NacStatus } from '../../api/types'
import { CLAIM_STATUS_LABELS, NAC_STATUS_LABELS, claimTone, nacTone } from '../../lib/format'
import type { Tone } from '../../lib/format'
import { Icon } from '../../lib/icons'
import { item } from '../../lib/motion'

export function Badge({ tone = 'open', children }: { tone?: Tone; children: ReactNode }) {
  return <span className={'badge badge-' + tone}>{children}</span>
}

export function ClaimStatusBadge({ status }: { status: ClaimStatus }) {
  return <Badge tone={claimTone(status)}>{CLAIM_STATUS_LABELS[status]}</Badge>
}

export function NacStatusBadge({ status }: { status: NacStatus }) {
  return <Badge tone={nacTone(status)}>{NAC_STATUS_LABELS[status]}</Badge>
}

interface EmptyProps {
  title: string
  children?: ReactNode
  action?: ReactNode
}

export function EmptyState({ title, children, action }: EmptyProps) {
  return (
    <motion.div className="empty" variants={item} initial="initial" animate="animate">
      <div className="glyph">
        <Icon.Grid width={20} height={20} />
      </div>
      <strong style={{ color: 'var(--ink)' }}>{title}</strong>
      {children && <p style={{ maxWidth: '48ch' }}>{children}</p>}
      {action}
    </motion.div>
  )
}

export function Skeleton({ height = 16, width = '100%' }: { height?: number; width?: number | string }) {
  return <div className="skeleton" style={{ height, width }} aria-hidden="true" />
}

/** Placeholder block shown while a page loads. */
export function PageSkeleton() {
  return (
    <div className="stack" aria-busy="true" aria-label="Loading">
      <Skeleton height={36} width="40%" />
      <Skeleton height={16} width="60%" />
      <div className="grid grid-4" style={{ marginTop: 24 }}>
        {[0, 1, 2, 3].map((i) => (
          <Skeleton key={i} height={120} />
        ))}
      </div>
      <Skeleton height={240} />
    </div>
  )
}

interface CalloutProps {
  title?: ReactNode
  children?: ReactNode
  variant?: 'plain' | 'dashed' | 'stripe'
}

export function Callout({ title, children, variant = 'plain' }: CalloutProps) {
  const cls = ['callout', variant === 'dashed' && 'callout-dashed', variant === 'stripe' && 'callout-stripe']
    .filter(Boolean)
    .join(' ')
  return (
    <motion.div className={cls} variants={item} initial="initial" animate="animate" role="note">
      <div>
        {title && <strong>{title}</strong>}
        {children}
      </div>
    </motion.div>
  )
}

/** Renders an API error (message plus the problem list if the server sent one). */
export function ErrorCallout({ error }: { error: unknown }) {
  if (!error) return null
  const message = error instanceof Error ? error.message : 'Something went wrong'
  const parts = message.split('. ').filter(Boolean)
  return (
    <Callout variant="stripe" title="Please check the following">
      {parts.length > 1 ? (
        <ul>
          {parts.map((p) => (
            <li key={p}>{p.replace(/\.$/, '')}</li>
          ))}
        </ul>
      ) : (
        <p>{message}</p>
      )}
    </Callout>
  )
}
