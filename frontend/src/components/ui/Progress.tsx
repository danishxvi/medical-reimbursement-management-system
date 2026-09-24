import { motion } from 'motion/react'
import type { TimelineEntry } from '../../api/types'
import { formatDateTime } from '../../lib/format'
import { ease, item, list } from '../../lib/motion'

export interface TrackerStep {
  label: string
  detail?: string
  state: 'done' | 'current' | 'todo'
}

/** Horizontal stage tracker: completed stages fill in one after another. */
export function Tracker({ steps }: { steps: TrackerStep[] }) {
  return (
    <div className="tracker" style={{ ['--steps' as string]: steps.length }}>
      {steps.map((s, i) => (
        <div key={s.label} className={'tracker-step ' + s.state}>
          {s.state !== 'todo' && (
            <motion.span
              className="fill"
              initial={{ scaleX: 0 }}
              animate={{ scaleX: 1 }}
              transition={{ duration: 0.4, delay: 0.12 * i, ease }}
              style={s.state === 'current' ? { opacity: 0.85 } : undefined}
            />
          )}
          <span className="idx">{String(i + 1).padStart(2, '0')}</span>
          <strong>{s.label}</strong>
          {s.detail && <span>{s.detail}</span>}
        </div>
      ))}
    </div>
  )
}

const ACTION_LABELS: Record<string, string> = {
  SUBMITTED: 'Submitted',
  RESUBMITTED: 'Corrected and resubmitted',
  RETURNED_BY_HOS: 'Returned for correction by HoS',
  FORWARDED_BY_HOS: 'Verified and forwarded to PAO',
  RETURNED_BY_PAO: 'Returned for correction by PAO',
  RECOMMENDED_FOR_SANCTION: 'Scrutinised, recommended for sanction',
  RECOMMENDED_FOR_REJECTION: 'Scrutinised, recommended for rejection',
  SENT_BACK_TO_AUDIT: 'Sent back to auditor',
  SANCTIONED: 'Sanctioned',
  REJECTED: 'Rejected',
  WITHDRAWN: 'Withdrawn by employee',
  PAID: 'Paid',
}

/** Vertical history of who did what, when, and why. */
export function Timeline({ entries }: { entries: TimelineEntry[] }) {
  if (entries.length === 0) {
    return <p className="muted">Nothing has happened yet. The history appears here once the claim is submitted.</p>
  }
  return (
    <motion.ol className="timeline" variants={list} initial="initial" animate="animate">
      {entries.map((e, i) => (
        <motion.li key={e.occurredAt + e.action} variants={item} className={i === entries.length - 1 ? 'current' : undefined}>
          <span className="marker">{String(i + 1).padStart(2, '0')}</span>
          <div>
            <strong>{ACTION_LABELS[e.action] ?? e.action}</strong>
            <div className="when">
              {formatDateTime(e.occurredAt)} by {e.actorName} ({e.actorRole})
            </div>
            {e.reasons.length > 0 && (
              <div className="row" style={{ marginTop: 6 }}>
                {e.reasons.map((r) => (
                  <span key={r} className="tag">
                    {r}
                  </span>
                ))}
              </div>
            )}
            {e.remarks && <div className="remark">{e.remarks}</div>}
          </div>
        </motion.li>
      ))}
    </motion.ol>
  )
}
