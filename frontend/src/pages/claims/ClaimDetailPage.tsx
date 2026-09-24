import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { api } from '../../api/client'
import type { Claim } from '../../api/types'
import { ClaimAmounts, ClaimDetails, ClaimTracker } from '../../components/claim/ClaimParts'
import { Button } from '../../components/ui/Button'
import { Callout, ErrorCallout, PageSkeleton } from '../../components/ui/Feedback'
import { Page, PageHeader } from '../../components/ui/Layout'
import { Modal } from '../../components/ui/Modal'
import { useToast } from '../../components/ui/Toast'
import { ageOf, formatDate } from '../../lib/format'
import { Icon } from '../../lib/icons'

export default function ClaimDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [confirm, setConfirm] = useState<'WITHDRAW' | 'DELETE' | null>(null)

  const { data: claim, isLoading, error } = useQuery({
    queryKey: ['claim', id],
    queryFn: () => api.get<Claim>('/api/claims/' + id),
  })

  const act = useMutation({
    mutationFn: (action: 'WITHDRAW' | 'DELETE') =>
      action === 'DELETE' ? api.del('/api/claims/' + id) : api.post<Claim>(`/api/claims/${id}/withdraw`),
    onSuccess: (_, action) => {
      queryClient.invalidateQueries({ queryKey: ['claims'] })
      queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      setConfirm(null)
      if (action === 'DELETE') {
        toast.ok('Draft deleted')
        navigate('/claims', { replace: true })
      } else {
        toast.ok('Claim withdrawn')
        queryClient.invalidateQueries({ queryKey: ['claim', id] })
      }
    },
    onError: (e) => toast.error(e instanceof Error ? e.message : 'Action failed'),
  })

  if (isLoading) {
    return (
      <Page>
        <PageSkeleton />
      </Page>
    )
  }
  if (error || !claim) {
    return (
      <Page>
        <PageHeader title="Claim not found" description="It may not exist, or it is not visible to your account." />
        <ErrorCallout error={error} />
      </Page>
    )
  }

  const can = (a: string) => claim.allowedActions.includes(a as Claim['allowedActions'][number])
  const lastReturn = [...claim.timeline].reverse().find((t) => t.action.startsWith('RETURNED'))
  const isReturned = claim.status === 'RETURNED_BY_HOS' || claim.status === 'RETURNED_BY_PAO'

  return (
    <Page>
      <PageHeader
        eyebrow={claim.claimNumber ? 'Claim ' + claim.claimNumber : 'Draft claim'}
        title={`${claim.patientName}: ${claim.illnessDescription}`}
        description={`Treatment ${formatDate(claim.treatmentFrom)} to ${formatDate(claim.treatmentTo)} at ${claim.hospitalName}`}
        actions={
          <>
            <Button icon={<Icon.Back />} onClick={() => navigate(-1)}>
              Back
            </Button>
            {can('DELETE') && (
              <Button variant="danger" icon={<Icon.Trash />} onClick={() => setConfirm('DELETE')}>
                Delete draft
              </Button>
            )}
            {can('WITHDRAW') && (
              <Button variant="danger" onClick={() => setConfirm('WITHDRAW')}>
                Withdraw
              </Button>
            )}
            {can('EDIT') && (
              <Button variant="primary" icon={<Icon.Arrow />} onClick={() => navigate(`/claims/${claim.id}/edit`)}>
                {isReturned ? 'Correct and resubmit' : 'Continue editing'}
              </Button>
            )}
          </>
        }
      />

      <div className="stack-lg">
        {isReturned && lastReturn && (
          <Callout variant="stripe" title={`Returned by ${lastReturn.actorName} (${lastReturn.actorRole})`}>
            {lastReturn.reasons.length > 0 && (
              <ul>
                {lastReturn.reasons.map((r) => (
                  <li key={r}>{r}</li>
                ))}
              </ul>
            )}
            {lastReturn.remarks && <p style={{ marginTop: 8 }}>{lastReturn.remarks}</p>}
            <p className="muted" style={{ marginTop: 8 }}>
              Correct only what is flagged and resubmit. Your claim keeps its original place in the queue.
            </p>
          </Callout>
        )}
        {claim.status === 'SANCTIONED' && (
          <Callout title="Sanctioned">
            <p>Your claim is approved. It will be paid, strictly oldest first, as soon as the school's budget has funds.</p>
          </Callout>
        )}
        {claim.overdue && (
          <Callout variant="dashed" title="Past the service level">
            <p>This stage has taken longer than {claim.slaDays} days ({ageOf(claim.stageEnteredAt)}). The delay is visible to the Directorate.</p>
          </Callout>
        )}
        <ClaimTracker claim={claim} />
        <ClaimAmounts claim={claim} />
        <ClaimDetails claim={claim} />
      </div>

      <Modal
        open={confirm !== null}
        title={confirm === 'DELETE' ? 'Delete this draft?' : 'Withdraw this claim?'}
        onClose={() => setConfirm(null)}
        footer={
          <>
            <Button onClick={() => setConfirm(null)}>Keep it</Button>
            <Button variant="primary" loading={act.isPending} onClick={() => confirm && act.mutate(confirm)}>
              {confirm === 'DELETE' ? 'Delete' : 'Withdraw'}
            </Button>
          </>
        }
      >
        <p>
          {confirm === 'DELETE'
            ? 'The draft and its entries will be removed. Uploaded files stay in your account.'
            : 'The claim will be closed and removed from every queue. Its bills can then be claimed in a new claim.'}
        </p>
      </Modal>
    </Page>
  )
}
