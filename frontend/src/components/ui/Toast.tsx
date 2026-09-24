import { AnimatePresence, motion } from 'motion/react'
import { createContext, useCallback, useContext, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { slideIn } from '../../lib/motion'

interface Toast {
  id: number
  message: string
  kind: 'ok' | 'error'
}

interface ToastApi {
  ok: (message: string) => void
  error: (message: string) => void
}

const ToastContext = createContext<ToastApi | null>(null)

let nextId = 1

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])

  const dismiss = useCallback((id: number) => setToasts((t) => t.filter((x) => x.id !== id)), [])

  const push = useCallback(
    (message: string, kind: Toast['kind']) => {
      const id = nextId++
      setToasts((t) => [...t.slice(-3), { id, message, kind }])
      setTimeout(() => dismiss(id), kind === 'error' ? 7000 : 4000)
    },
    [dismiss],
  )

  const value = useMemo<ToastApi>(
    () => ({ ok: (m) => push(m, 'ok'), error: (m) => push(m, 'error') }),
    [push],
  )

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="toasts" role="status" aria-live="polite">
        <AnimatePresence initial={false}>
          {toasts.map((t) => (
            <motion.div
              key={t.id}
              layout
              className={t.kind === 'error' ? 'toast error' : 'toast'}
              variants={slideIn}
              initial="initial"
              animate="animate"
              exit="exit"
            >
              <span>{t.message}</span>
              <button type="button" onClick={() => dismiss(t.id)} aria-label="Dismiss">
                x
              </button>
            </motion.div>
          ))}
        </AnimatePresence>
      </div>
    </ToastContext.Provider>
  )
}

export function useToast(): ToastApi {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('useToast must be used inside ToastProvider')
  return ctx
}
