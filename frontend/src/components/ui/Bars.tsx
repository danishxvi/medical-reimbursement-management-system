import { motion } from 'motion/react'
import { ease } from '../../lib/motion'

interface Bar {
  label: string
  value: number
}

/**
 * Horizontal bars for a small categorical breakdown. Every bar is one
 * saffron and labelled directly with its value, so no legend or colour
 * coding is needed.
 */
export function Bars({ data, format }: { data: Bar[]; format?: (n: number) => string }) {
  const max = Math.max(1, ...data.map((d) => d.value))
  return (
    <div className="stack" style={{ gap: 10 }} role="list">
      {data.map((d, i) => (
        <div key={d.label} role="listitem" aria-label={`${d.label}: ${d.value}`} style={{ display: 'grid', gridTemplateColumns: 'minmax(120px, 34%) 1fr auto', gap: 12, alignItems: 'center' }}>
          <span style={{ fontSize: 'var(--text-sm)' }}>{d.label}</span>
          <span style={{ height: 14, background: 'var(--surface-2)', border: 'var(--border)', borderRadius: 'var(--radius-pill)', position: 'relative', overflow: 'hidden' }}>
            <motion.span
              style={{ position: 'absolute', inset: 0, background: 'linear-gradient(90deg, var(--accent), var(--invert))', borderRadius: 'var(--radius-pill)', transformOrigin: 'left' }}
              initial={{ scaleX: 0 }}
              animate={{ scaleX: d.value / max }}
              transition={{ duration: 0.6, delay: 0.05 * i, ease }}
            />
          </span>
          <span className="num" style={{ minWidth: 40, textAlign: 'right', fontSize: 'var(--text-sm)' }}>
            {format ? format(d.value) : d.value}
          </span>
        </div>
      ))}
    </div>
  )
}
