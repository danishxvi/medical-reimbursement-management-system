import type { Transition, Variants } from 'motion/react'

/* Shared motion language: short, eased, and always vertical or horizontal
   (never diagonal) to match the boxy layout. */

export const ease: Transition['ease'] = [0.2, 0, 0, 1]

export const page: Variants = {
  initial: { opacity: 0, y: 12 },
  animate: { opacity: 1, y: 0, transition: { duration: 0.32, ease } },
  exit: { opacity: 0, y: -8, transition: { duration: 0.18, ease } },
}

export const list: Variants = {
  initial: {},
  animate: { transition: { staggerChildren: 0.045, delayChildren: 0.05 } },
}

export const item: Variants = {
  initial: { opacity: 0, y: 10 },
  animate: { opacity: 1, y: 0, transition: { duration: 0.28, ease } },
}

export const fade: Variants = {
  initial: { opacity: 0 },
  animate: { opacity: 1, transition: { duration: 0.24, ease } },
  exit: { opacity: 0, transition: { duration: 0.16, ease } },
}

export const slideIn: Variants = {
  initial: { opacity: 0, x: 24 },
  animate: { opacity: 1, x: 0, transition: { duration: 0.28, ease } },
  exit: { opacity: 0, x: 24, transition: { duration: 0.18, ease } },
}

export const pop: Variants = {
  initial: { opacity: 0, scale: 0.97, y: 8 },
  animate: { opacity: 1, scale: 1, y: 0, transition: { duration: 0.24, ease } },
  exit: { opacity: 0, scale: 0.98, y: 4, transition: { duration: 0.14, ease } },
}
