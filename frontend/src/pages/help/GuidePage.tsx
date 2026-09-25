import { useQueryClient } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'motion/react'
import { useState } from 'react'
import type { ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { api } from '../../api/client'
import { useMe } from '../../auth/AuthContext'
import { Button } from '../../components/ui/Button'
import { Page, PageHeader } from '../../components/ui/Layout'
import { useToast } from '../../components/ui/Toast'
import { ROLE_LABELS } from '../../lib/format'
import { Icon } from '../../lib/icons'
import { ease, item, list } from '../../lib/motion'
import { GUIDES } from './guides'
import type { GuideIcon } from './guides'

const ICONS: Record<GuideIcon, ReactNode> = {
  grid: <Icon.Grid />,
  pill: <Icon.Pill />,
  file: <Icon.File />,
  upload: <Icon.Upload />,
  queue: <Icon.Queue />,
  shield: <Icon.Shield />,
  rupee: <Icon.Rupee />,
  clock: <Icon.Clock />,
  bell: <Icon.Bell />,
  users: <Icon.Users />,
  audit: <Icon.Audit />,
  download: <Icon.Download />,
}

/**
 * Role specific guide. Opens by itself after the first sign in and stays
 * available from the Help menu afterwards.
 */
export default function GuidePage() {
  const me = useMe()
  const navigate = useNavigate()
  const toast = useToast()
  const queryClient = useQueryClient()
  const guide = GUIDES[me.role]
  const [index, setIndex] = useState(0)
  const [direction, setDirection] = useState(1)
  const [busy, setBusy] = useState(false)
  const step = guide.steps[index]
  const last = index === guide.steps.length - 1
  const firstTime = !me.guideSeen

  function go(next: number) {
    setDirection(next > index ? 1 : -1)
    setIndex(next)
  }

  async function finish() {
    if (!firstTime) {
      navigate('/')
      return
    }
    setBusy(true)
    try {
      await api.post('/api/auth/guide/seen')
      await queryClient.invalidateQueries({ queryKey: ['me'] })
      toast.ok('You are all set. The guide stays under Help in the menu.')
      navigate('/', { replace: true })
    } finally {
      setBusy(false)
    }
  }

  return (
    <Page>
      <PageHeader
        eyebrow={firstTime ? 'Welcome, ' + me.fullName : 'Help'}
        title={'User guide: ' + ROLE_LABELS[me.role]}
        description={guide.intro}
        actions={
          firstTime ? (
            <Button variant="ghost" onClick={finish} disabled={busy}>
              Skip the guide
            </Button>
          ) : undefined
        }
      />

      <div className="guide">
        <motion.ol className="guide-steps" variants={list} initial="initial" animate="animate" aria-label="Guide steps">
          {guide.steps.map((s, i) => (
            <motion.li key={s.title} variants={item}>
              <button
                type="button"
                className={'guide-step' + (i === index ? ' active' : '') + (i < index ? ' done' : '')}
                onClick={() => go(i)}
                aria-current={i === index ? 'step' : undefined}
              >
                {i === index && <motion.span layoutId="guide-active" className="guide-step-bg" transition={{ type: 'spring', stiffness: 500, damping: 40 }} />}
                <span className="n">{i < index ? <Icon.Check width={12} height={12} /> : i + 1}</span>
                <span>{s.title}</span>
              </button>
            </motion.li>
          ))}
        </motion.ol>

        <section className="panel guide-card" aria-live="polite">
          <div className="guide-progress" aria-hidden="true">
            <motion.span animate={{ width: `${((index + 1) / guide.steps.length) * 100}%` }} transition={{ duration: 0.4, ease }} />
          </div>
          <AnimatePresence mode="wait" custom={direction}>
            <motion.div
              key={index}
              className="guide-body"
              custom={direction}
              initial={{ opacity: 0, x: 28 * direction }}
              animate={{ opacity: 1, x: 0, transition: { duration: 0.28, ease } }}
              exit={{ opacity: 0, x: -20 * direction, transition: { duration: 0.16, ease } }}
            >
              <div className="guide-icon">{ICONS[step.icon]}</div>
              <div className="caps muted">
                Step {index + 1} of {guide.steps.length}
              </div>
              <h2>{step.title}</h2>
              {step.body.map((p) => (
                <p key={p}>{p}</p>
              ))}
              {step.where && (
                <div className="guide-where">
                  <Icon.Arrow width={14} height={14} /> {step.where}
                </div>
              )}
            </motion.div>
          </AnimatePresence>
          <div className="panel-foot">
            <Button icon={<Icon.Back />} onClick={() => go(index - 1)} disabled={index === 0}>
              Previous
            </Button>
            {last ? (
              <Button variant="primary" icon={<Icon.Check />} onClick={finish} loading={busy}>
                {firstTime ? 'Start using MRMS' : 'Back to dashboard'}
              </Button>
            ) : (
              <Button variant="primary" onClick={() => go(index + 1)}>
                Next <Icon.Arrow />
              </Button>
            )}
          </div>
        </section>
      </div>
    </Page>
  )
}
