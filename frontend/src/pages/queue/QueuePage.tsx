import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'motion/react'
import { useState } from 'react'
import { api } from '../../api/client'
import type { Claim, ClaimMeta, ClaimQueue, Nac, NacQueue } from '../../api/types'
import { useMe } from '../../auth/AuthContext'
import { ClaimAmounts, ClaimDetails, ClaimTable, ClaimTracker } from '../../components/claim/ClaimParts'
import { NacDetails, NacTable } from '../../components/nac/NacParts'
import { Button } from '../../components/ui/Button'
import { Callout, EmptyState, ErrorCallout, PageSkeleton } from '../../components/ui/Feedback'
import { Page, PageHeader, Panel, Stat, StatGrid } from '../../components/ui/Layout'
import { useToast } from '../../components/ui/Toast'
import { ageOf } from '../../lib/format'
import { Icon } from '../../lib/icons'
import { ease } from '../../lib/motion'
import { ClaimReviewPanel } from './ClaimReviewPanel'
import { NacReviewPanel } from './NacReviewPanel'

/**
 * The reviewer's work queue. Officials cannot pick a record: "Take next"
 * always hands over the oldest waiting one, which removes favouritism.
 */
export default function QueuePage() {
  const me = useMe()
  const dispensary = me.role === 'PHARMACIST' || me.role === 'MEDICAL_OFFICER'
  return dispensary ? <NacQueuePage /> : <ClaimQueuePage />
}

function QueueIntro({ waiting, overdue, slaDays, stage }: { waiting: number; overdue: number; slaDays: number; stage: string }) {
  return (
    <StatGrid columns={3}>
      <Stat label="Waiting in queue" value={waiting} foot={stage} />
      <Stat label="Past service level" value={overdue} alert={overdue > 0} foot={`Target ${slaDays} days per stage`} />
      <Stat label="Order" value={1} format={() => 'FIFO'} foot="Oldest first, no picking" />
    </StatGrid>
  )
}

// ======================================================================
// Claims: HoS, PAO auditor, PAO officer
// ======================================================================

function ClaimQueuePage() {
  const me = useMe()
  const toast = useToast()
  const queryClient = useQueryClient()
  const queue = useQuery({ queryKey: ['queue', 'claims'], queryFn: () => api.get<ClaimQueue>('/api/claims/queue') })
  const meta = useQuery({ queryKey: ['claim-meta'], queryFn: () => api.get<ClaimMeta>('/api/claims/meta'), staleTime: Infinity })
  const [current, setCurrent] = useState<Claim | null>(null)

  const take = useMutation({
    mutationFn: () => api.post<Claim>('/api/claims/queue/take-next'),
    onSuccess: (claim) => {
      setCurrent(claim)
      queryClient.invalidateQueries({ queryKey: ['queue'] })
      window.scrollTo({ top: 0, behavior: 'smooth' })
    },
    onError: (e) => toast.error(e instanceof Error ? e.message : 'Could not take the next claim'),
  })

  const active = current ?? queue.data?.current ?? null
  const done = (message: string) => {
    toast.ok(message)
    setCurrent(null)
    // Drop the finished claim at once so the stale copy never flashes back
    queryClient.setQueryData<ClaimQueue>(['queue', 'claims'], (q) => (q ? { ...q, current: null } : q))
    queryClient.invalidateQueries({ queryKey: ['queue'] })
    queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    queryClient.invalidateQueries({ queryKey: ['claims'] })
  }

  const title = me.role === 'HOS' ? 'Verification queue' : me.role === 'PAO_AUDITOR' ? 'Scrutiny queue' : 'Sanction queue'
  if (queue.isLoading || meta.isLoading) {
    return (
      <Page>
        <PageSkeleton />
      </Page>
    )
  }
  const q = queue.data
  if (!q || !meta.data) {
    return (
      <Page>
        <ErrorCallout error={queue.error ?? meta.error} />
      </Page>
    )
  }
  const untaken = q.waiting.filter((w) => !w.assignedTo).length

  return (
    <Page>
      <PageHeader
        eyebrow={q.stageLabel}
        title={title}
        description="Claims are handed out strictly in the order they were first submitted. A corrected claim returns to its original place."
        actions={
          !active && (
            <Button variant="primary" icon={<Icon.Arrow />} loading={take.isPending} disabled={untaken === 0} onClick={() => take.mutate()}>
              Take next claim
            </Button>
          )
        }
      />
      <AnimatePresence mode="wait">
        {active ? (
          <motion.div key={'claim-' + active.id} className="stack-lg" initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -16 }} transition={{ duration: 0.25, ease }}>
            <Callout title={`Working on ${active.claimNumber}`}>
              <p>
                Waiting at this stage for {ageOf(active.stageEnteredAt)}
                {active.returnCount > 0 ? ` · returned ${active.returnCount} time(s) before` : ''}. Finish, return or release it before taking another.
              </p>
            </Callout>
            <ClaimTracker claim={active} />
            <ClaimAmounts claim={active} />
            <ClaimReviewPanel claim={active} meta={meta.data} onDone={done} onRelease={() => done('Released back to the queue')} />
            <ClaimDetails claim={active} />
          </motion.div>
        ) : (
          <motion.div key="list" className="stack-lg" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
            <QueueIntro waiting={q.waiting.length} overdue={q.waiting.filter((w) => w.overdue).length} slaDays={q.slaDays} stage={q.stageLabel} />
            <Panel title="Waiting, oldest first" kicker="Read only preview" flush>
              {q.waiting.length > 0 ? (
                <ClaimTable rows={q.waiting} showEmployee showQueue />
              ) : (
                <div className="panel-body">
                  <EmptyState title="Queue is clear">Nothing is waiting at this stage. New claims appear here automatically.</EmptyState>
                </div>
              )}
            </Panel>
          </motion.div>
        )}
      </AnimatePresence>
    </Page>
  )
}

// ======================================================================
// e-NAC: pharmacist and medical officer
// ======================================================================

function NacQueuePage() {
  const me = useMe()
  const toast = useToast()
  const queryClient = useQueryClient()
  const queue = useQuery({ queryKey: ['queue', 'nac'], queryFn: () => api.get<NacQueue>('/api/nac/queue') })
  const [current, setCurrent] = useState<Nac | null>(null)

  const take = useMutation({
    mutationFn: () => api.post<Nac>('/api/nac/queue/take-next'),
    onSuccess: (nac) => {
      setCurrent(nac)
      queryClient.invalidateQueries({ queryKey: ['queue'] })
    },
    onError: (e) => toast.error(e instanceof Error ? e.message : 'Could not take the next prescription'),
  })

  const active = current ?? queue.data?.current ?? null
  const done = (message: string) => {
    toast.ok(message)
    setCurrent(null)
    queryClient.setQueryData<NacQueue>(['queue', 'nac'], (q) => (q ? { ...q, current: null } : q))
    queryClient.invalidateQueries({ queryKey: ['queue'] })
    queryClient.invalidateQueries({ queryKey: ['dashboard'] })
  }

  if (queue.isLoading) {
    return (
      <Page>
        <PageSkeleton />
      </Page>
    )
  }
  const q = queue.data
  if (!q) {
    return (
      <Page>
        <ErrorCallout error={queue.error} />
      </Page>
    )
  }
  const officer = me.role === 'MEDICAL_OFFICER'
  const untaken = q.waiting.filter((w) => !w.assignedTo).length

  return (
    <Page>
      <PageHeader
        eyebrow={officer ? 'Countersignature' : 'Item verification'}
        title="Prescription queue"
        description={
          officer
            ? 'Check the pharmacist\'s decisions, then countersign (password or Aadhaar eSign) or send the certificate back to the same pharmacist.'
            : 'Mark every prescribed item. Your name and the time are recorded against each decision.'
        }
        actions={
          !active && (
            <Button variant="primary" icon={<Icon.Arrow />} loading={take.isPending} disabled={untaken === 0} onClick={() => take.mutate()}>
              Take next prescription
            </Button>
          )
        }
      />
      <AnimatePresence mode="wait">
        {active ? (
          <motion.div key={'nac-' + active.id} className="stack-lg" initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -16 }} transition={{ duration: 0.25, ease }}>
            <NacReviewPanel nac={active} officer={officer} onDone={done} onRelease={() => done('Released back to the queue')} />
            <NacDetails nac={active} />
          </motion.div>
        ) : (
          <motion.div key="list" className="stack-lg" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
            <QueueIntro waiting={q.waiting.length} overdue={q.waiting.filter((w) => w.overdue).length} slaDays={q.slaDays} stage={officer ? 'With Medical Officer' : 'With pharmacist'} />
            <Panel title="Waiting, oldest first" kicker="Read only preview" flush>
              {q.waiting.length > 0 ? (
                <NacTable rows={q.waiting} showEmployee showQueue onOpen={() => toast.error('Use "Take next" to work on the oldest prescription first')} />
              ) : (
                <div className="panel-body">
                  <EmptyState title="Queue is clear">No prescriptions are waiting.</EmptyState>
                </div>
              )}
            </Panel>
          </motion.div>
        )}
      </AnimatePresence>
    </Page>
  )
}
