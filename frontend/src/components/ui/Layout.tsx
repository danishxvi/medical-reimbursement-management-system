import { animate, motion, useInView, useReducedMotion } from 'motion/react'
import { useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { item, list, page } from '../../lib/motion'

/** Wraps every routed page so it animates in and out. */
export function Page({ children }: { children: ReactNode }) {
  return (
    <motion.div className="content" variants={page} initial="initial" animate="animate" exit="exit">
      {children}
    </motion.div>
  )
}

interface PageHeaderProps {
  eyebrow?: string
  title: string
  description?: ReactNode
  actions?: ReactNode
}

export function PageHeader({ eyebrow, title, description, actions }: PageHeaderProps) {
  return (
    <motion.header className="page-header" variants={list} initial="initial" animate="animate">
      <div>
        {eyebrow && (
          <motion.div className="eyebrow caps" variants={item}>
            {eyebrow}
          </motion.div>
        )}
        <motion.h1 variants={item}>{title}</motion.h1>
        {description && <motion.p variants={item}>{description}</motion.p>}
      </div>
      {actions && (
        <motion.div className="page-actions" variants={item}>
          {actions}
        </motion.div>
      )}
    </motion.header>
  )
}

interface PanelProps {
  title?: ReactNode
  kicker?: string
  actions?: ReactNode
  footer?: ReactNode
  flush?: boolean
  strong?: boolean
  children: ReactNode
  className?: string
}

export function Panel({ title, kicker, actions, footer, flush, strong, children, className }: PanelProps) {
  return (
    <motion.section
      className={['panel', strong && 'panel-strong', className].filter(Boolean).join(' ')}
      variants={item}
      initial="initial"
      animate="animate"
    >
      {(title || actions) && (
        <div className="panel-head">
          <div>
            {kicker && <div className="caps">{kicker}</div>}
            {typeof title === 'string' ? <h2>{title}</h2> : title}
          </div>
          {actions && <div className="row">{actions}</div>}
        </div>
      )}
      <div className={flush ? 'panel-body flush' : 'panel-body'}>{children}</div>
      {footer && <div className="panel-foot">{footer}</div>}
    </motion.section>
  )
}

/** Container that staggers its children in. */
export function Stagger({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <motion.div className={className} variants={list} initial="initial" animate="animate">
      {children}
    </motion.div>
  )
}

export function StaggerItem({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <motion.div className={className} variants={item}>
      {children}
    </motion.div>
  )
}

/** Animates a number from 0 to its value when it scrolls into view. */
export function CountUp({ value, format }: { value: number; format?: (n: number) => string }) {
  const ref = useRef<HTMLSpanElement>(null)
  const inView = useInView(ref, { once: true })
  const reduce = useReducedMotion()
  const [shown, setShown] = useState(reduce ? value : 0)

  useEffect(() => {
    if (!inView) return
    if (reduce) {
      setShown(value)
      return
    }
    const controls = animate(0, value, {
      duration: 0.9,
      ease: [0.2, 0, 0, 1],
      onUpdate: (v) => setShown(v),
    })
    return () => controls.stop()
  }, [inView, value, reduce])

  const rounded = Number.isInteger(value) ? Math.round(shown) : Math.round(shown * 100) / 100
  return <span ref={ref}>{format ? format(rounded) : rounded.toLocaleString('en-IN')}</span>
}

interface StatProps {
  label: string
  value: number
  format?: (n: number) => string
  foot?: ReactNode
  alert?: boolean
}

export function Stat({ label, value, format, foot, alert }: StatProps) {
  return (
    <motion.div className={alert ? 'stat is-alert' : 'stat'} variants={item}>
      <span className="stat-label caps">{label}</span>
      <span className="stat-value">
        <CountUp value={value} format={format} />
      </span>
      {foot && <span className="stat-foot">{foot}</span>}
    </motion.div>
  )
}

/** A row of stat tiles sharing borders. */
export function StatGrid({ children, columns = 4 }: { children: ReactNode; columns?: number }) {
  return (
    <motion.div
      className="tiles"
      style={{ gridTemplateColumns: `repeat(auto-fit, minmax(min(100%, ${columns >= 4 ? 200 : 240}px), 1fr))` }}
      variants={list}
      initial="initial"
      animate="animate"
    >
      {children}
    </motion.div>
  )
}

/** Key value grid used for read only details. */
export function Details({ items, columns = 2 }: { items: [string, ReactNode][]; columns?: 2 | 3 }) {
  return (
    <dl className={columns === 3 ? 'dl dl-3' : 'dl'}>
      {items.map(([k, v]) => (
        <div key={k}>
          <dt>{k}</dt>
          <dd>{v === null || v === undefined || v === '' ? '-' : v}</dd>
        </div>
      ))}
    </dl>
  )
}
