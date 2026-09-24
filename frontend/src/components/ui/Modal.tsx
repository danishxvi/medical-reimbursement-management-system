import { AnimatePresence, motion } from 'motion/react'
import { useEffect, useRef } from 'react'
import type { ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { fade, pop } from '../../lib/motion'
import { Icon } from '../../lib/icons'

interface Props {
  open: boolean
  title: string
  onClose: () => void
  children: ReactNode
  footer?: ReactNode
  wide?: boolean
}

/**
 * Accessible dialog: focus moves inside on open, Escape closes, focus is
 * trapped with Tab and returned to the trigger on close.
 */
export function Modal({ open, title, onClose, children, footer, wide }: Props) {
  const panel = useRef<HTMLDivElement>(null)
  // Latest onClose without re-running the focus effect on every render
  const closeRef = useRef(onClose)
  useEffect(() => {
    closeRef.current = onClose
  }, [onClose])

  useEffect(() => {
    if (!open) return
    const previous = document.activeElement as HTMLElement | null
    const node = panel.current
    const focusable = () =>
      Array.from(
        node?.querySelectorAll<HTMLElement>('button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])') ?? [],
      ).filter((el) => !el.hasAttribute('disabled'))
    setTimeout(() => (focusable()[1] ?? focusable()[0])?.focus(), 30)

    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') closeRef.current()
      if (e.key === 'Tab') {
        const els = focusable()
        if (els.length === 0) return
        const first = els[0]
        const last = els[els.length - 1]
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault()
          last.focus()
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault()
          first.focus()
        }
      }
    }
    document.addEventListener('keydown', onKey)
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = ''
      previous?.focus()
    }
  }, [open])

  return createPortal(
    <AnimatePresence>
      {open && (
        <motion.div
          className="modal-backdrop"
          variants={fade}
          initial="initial"
          animate="animate"
          exit="exit"
          onMouseDown={(e) => e.target === e.currentTarget && onClose()}
        >
          <motion.div
            ref={panel}
            className={wide ? 'modal modal-wide' : 'modal'}
            role="dialog"
            aria-modal="true"
            aria-label={title}
            variants={pop}
          >
            <div className="panel-head">
              <h2>{title}</h2>
              <button type="button" className="btn btn-ghost btn-sm" onClick={onClose} aria-label="Close">
                <Icon.X />
              </button>
            </div>
            <div className="panel-body">{children}</div>
            {footer && <div className="panel-foot">{footer}</div>}
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>,
    document.body,
  )
}
