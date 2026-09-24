import { useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router'
import { api } from '../../api/client'
import type { ClaimStatus, ClaimSummary } from '../../api/types'
import { useMe } from '../../auth/AuthContext'
import { ClaimTable } from '../../components/claim/ClaimParts'
import { Button } from '../../components/ui/Button'
import { EmptyState, PageSkeleton } from '../../components/ui/Feedback'
import { Page, PageHeader, Panel } from '../../components/ui/Layout'
import { Icon } from '../../lib/icons'
import { motion } from 'motion/react'

type Filter = 'ALL' | 'ACTION' | 'PROGRESS' | 'DONE'

const GROUPS: Record<Filter, ClaimStatus[] | null> = {
  ALL: null,
  ACTION: ['DRAFT', 'RETURNED_BY_HOS', 'RETURNED_BY_PAO'],
  PROGRESS: ['PENDING_HOS', 'PENDING_PAO_AUDIT', 'PENDING_SANCTION', 'SANCTIONED'],
  DONE: ['PAID', 'REJECTED', 'WITHDRAWN'],
}

const LABELS: Record<Filter, string> = {
  ALL: 'All',
  ACTION: 'Needs my action',
  PROGRESS: 'In progress',
  DONE: 'Closed',
}

function Tabs({ value, onChange, counts }: { value: Filter; onChange: (f: Filter) => void; counts: Record<Filter, number> }) {
  return (
    <div className="tabs" role="tablist">
      {(Object.keys(LABELS) as Filter[]).map((f) => (
        <button key={f} type="button" role="tab" aria-selected={value === f} className={'tab' + (value === f ? ' active' : '')} onClick={() => onChange(f)}>
          {LABELS[f]} <span className="num muted">({counts[f]})</span>
          {value === f && <motion.span layoutId="claims-tab" className="tab-line" />}
        </button>
      ))}
    </div>
  )
}

/** The employee's own claims. */
export default function ClaimsListPage() {
  const navigate = useNavigate()
  const { data, isLoading } = useQuery({ queryKey: ['claims', 'mine'], queryFn: () => api.get<ClaimSummary[]>('/api/claims/mine') })
  const [filter, setFilter] = useState<Filter>('ALL')

  const counts = useMemo(() => {
    const all = data ?? []
    return {
      ALL: all.length,
      ACTION: all.filter((c) => GROUPS.ACTION!.includes(c.status)).length,
      PROGRESS: all.filter((c) => GROUPS.PROGRESS!.includes(c.status)).length,
      DONE: all.filter((c) => GROUPS.DONE!.includes(c.status)).length,
    }
  }, [data])
  const rows = (data ?? []).filter((c) => !GROUPS[filter] || GROUPS[filter]!.includes(c.status))

  return (
    <Page>
      <PageHeader
        eyebrow="Reimbursement"
        title="My claims"
        description="Every claim you have started, with its current stage. Open a claim to see who has it and for how long."
        actions={
          <Button variant="primary" icon={<Icon.Plus />} onClick={() => navigate('/claims/new')}>
            New claim
          </Button>
        }
      />
      {isLoading ? (
        <PageSkeleton />
      ) : (
        <>
          <Tabs value={filter} onChange={setFilter} counts={counts} />
          <Panel flush>
            {rows.length > 0 ? (
              <ClaimTable rows={rows} />
            ) : (
              <div className="panel-body">
                <EmptyState title="Nothing here">No claims match this filter.</EmptyState>
              </div>
            )}
          </Panel>
        </>
      )}
    </Page>
  )
}

/** Claims of the reviewer's school or PAO (history and search). */
export function OfficeClaimsPage() {
  const me = useMe()
  const [status, setStatus] = useState<ClaimStatus | ''>('')
  const { data, isLoading } = useQuery({
    queryKey: ['claims', 'office', status],
    queryFn: () => api.get<ClaimSummary[]>('/api/claims/office' + (status ? '?status=' + status : '')),
  })
  const [q, setQ] = useState('')
  const rows = (data ?? []).filter((c) =>
    !q ? true : [c.claimNumber, c.employeeName, c.patientName].some((v) => v?.toLowerCase().includes(q.toLowerCase())),
  )
  const statuses: ClaimStatus[] = ['PENDING_HOS', 'RETURNED_BY_HOS', 'PENDING_PAO_AUDIT', 'RETURNED_BY_PAO', 'PENDING_SANCTION', 'SANCTIONED', 'PAID', 'REJECTED', 'WITHDRAWN']

  return (
    <Page>
      <PageHeader
        eyebrow={me.role === 'HOS' ? 'School' : 'Pay and Accounts Office'}
        title={me.role === 'HOS' ? 'School claims' : 'Claims at this PAO'}
        description="Read only history. Work on claims only through your queue, in order of arrival."
      />
      <Panel
        flush
        title={
          <div className="row">
            <input className="control" style={{ width: 260 }} placeholder="Search claim, employee or patient" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search" />
            <select className="control" style={{ width: 220 }} value={status} onChange={(e) => setStatus(e.target.value as ClaimStatus | '')} aria-label="Filter by status">
              <option value="">All statuses</option>
              {statuses.map((s) => (
                <option key={s} value={s}>
                  {s.replace(/_/g, ' ').toLowerCase()}
                </option>
              ))}
            </select>
          </div>
        }
      >
        {isLoading ? (
          <div className="panel-body">
            <PageSkeleton />
          </div>
        ) : rows.length > 0 ? (
          <ClaimTable rows={rows} showEmployee />
        ) : (
          <div className="panel-body">
            <EmptyState title="No claims found" />
          </div>
        )}
      </Panel>
    </Page>
  )
}
