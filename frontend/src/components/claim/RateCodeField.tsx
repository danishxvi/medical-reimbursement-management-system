import { useQuery } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'motion/react'
import { useEffect, useId, useState } from 'react'
import { api } from '../../api/client'
import type { RateSearchHit } from '../../api/types'
import { formatMoney } from '../../lib/format'
import { fade } from '../../lib/motion'

interface Props {
  value: string
  billDate?: string
  onChange: (code: string) => void
  /** Called when a list entry is chosen, for example to fill an empty description. */
  onPick?: (hit: RateSearchHit) => void
}

/**
 * DGEHS / CGHS code with search: type a code or part of the name ("CBC",
 * "haemogram", "LB012") and pick from the rate list in force on the bill date.
 */
export function RateCodeField({ value, billDate, onChange, onPick }: Props) {
  const id = useId()
  const [open, setOpen] = useState(false)
  const [term, setTerm] = useState(value)
  const [active, setActive] = useState(0)

  // Search after a short pause in typing
  useEffect(() => {
    const t = setTimeout(() => setTerm(value), 250)
    return () => clearTimeout(t)
  }, [value])

  const results = useQuery({
    queryKey: ['rate-search', term, billDate ?? ''],
    queryFn: () => api.get<RateSearchHit[]>(`/api/rates/search?q=${encodeURIComponent(term)}${billDate ? '&date=' + billDate : ''}`),
    enabled: open && term.trim().length >= 2,
    staleTime: 300_000,
  })
  const hits = results.data ?? []
  const show = open && term.trim().length >= 2 && (hits.length > 0 || results.isFetched)

  function pick(hit: RateSearchHit) {
    onChange(hit.code)
    onPick?.(hit)
    setOpen(false)
  }

  return (
    <div className="field combo">
      <label className="field-label" htmlFor={id}>
        <span>DGEHS / CGHS code</span>
      </label>
      <div className="control-wrap">
        <input
          id={id}
          className="control"
          value={value}
          maxLength={30}
          autoComplete="off"
          role="combobox"
          aria-expanded={show}
          aria-controls={id + '-list'}
          aria-autocomplete="list"
          placeholder="Code or test name"
          onChange={(e) => {
            onChange(e.target.value)
            setOpen(true)
            setActive(0)
          }}
          onFocus={() => setOpen(true)}
          onBlur={() => setTimeout(() => setOpen(false), 150)}
          onKeyDown={(e) => {
            if (!show || hits.length === 0) return
            if (e.key === 'ArrowDown') {
              e.preventDefault()
              setActive((a) => Math.min(a + 1, hits.length - 1))
            } else if (e.key === 'ArrowUp') {
              e.preventDefault()
              setActive((a) => Math.max(a - 1, 0))
            } else if (e.key === 'Enter') {
              e.preventDefault()
              pick(hits[active])
            } else if (e.key === 'Escape') {
              setOpen(false)
            }
          }}
        />
        <AnimatePresence>
          {show && (
            <motion.ul className="combo-list" id={id + '-list'} role="listbox" variants={fade} initial="initial" animate="animate" exit="exit">
              {hits.length === 0 && <li className="combo-empty">No matching code in the rate list for this date</li>}
              {hits.map((h, i) => (
                <li
                  key={h.code}
                  role="option"
                  aria-selected={i === active}
                  className={i === active ? 'active' : undefined}
                  onMouseDown={(e) => {
                    e.preventDefault()
                    pick(h)
                  }}
                  onMouseEnter={() => setActive(i)}
                >
                  <span className="mono">{h.code}</span>
                  <span className="combo-name">{h.name}</span>
                  <span className="num muted">{formatMoney(h.nabh)}</span>
                </li>
              ))}
            </motion.ul>
          )}
        </AnimatePresence>
      </div>
      <span className="field-hint">Optional. Helps the school apply the approved rate.</span>
    </div>
  )
}
