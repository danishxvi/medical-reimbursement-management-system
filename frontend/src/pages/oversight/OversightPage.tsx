import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import { useMe } from '../../auth/AuthContext'
import { Button } from '../../components/ui/Button'
import { DataTable } from '../../components/ui/DataTable'
import { Badge, Callout, EmptyState, ErrorCallout, PageSkeleton } from '../../components/ui/Feedback'
import { Page, PageHeader, Panel, Stat, StatGrid } from '../../components/ui/Layout'
import { useToast } from '../../components/ui/Toast'
import { formatDateTime } from '../../lib/format'
import { Icon } from '../../lib/icons'

interface OverdueItem {
  subjectType: 'CLAIM' | 'NAC'
  subjectId: number
  reference: string
  stageLabel: string
  officeType: string
  officeName: string
  heldBy: string | null
  stageEnteredAt: string
  slaDays: number
  daysOverdue: number
  level: 'BREACH' | 'ESCALATED'
  lastReminderAt: string | null
}

interface OfficeRecord {
  officeType: string
  officeId: number
  officeName: string
  stage: string
  breaches: number
  open: number
}

interface Overview {
  zone: string | null
  overdue: number
  escalated: number
  dueSoon: number
  breaches90d: number
  items: OverdueItem[]
  offices: OfficeRecord[]
}

const OFFICE_LABELS: Record<string, string> = { SCHOOL: 'School', PAO: 'PAO', DISPENSARY: 'Dispensary' }

/**
 * Time limits across the zone (or every zone for an administrator): what is
 * late right now, who holds it, and each office's record of delays. Status
 * and dates only, never medical documents.
 */
export default function OversightPage() {
  const me = useMe()
  const toast = useToast()
  const queryClient = useQueryClient()
  const { data, isLoading, error } = useQuery({ queryKey: ['oversight'], queryFn: () => api.get<Overview>('/api/oversight'), refetchInterval: 60_000 })

  const remind = useMutation({
    mutationFn: (i: OverdueItem) => api.post('/api/oversight/remind', { subjectType: i.subjectType, subjectId: i.subjectId }),
    onSuccess: (_, i) => {
      toast.ok(`Reminder sent for ${i.reference}`)
      queryClient.invalidateQueries({ queryKey: ['oversight'] })
    },
    onError: (e) => toast.error(e instanceof Error ? e.message : 'Could not send the reminder'),
  })

  return (
    <Page>
      <PageHeader
        eyebrow={me.role === 'ADMIN' ? 'All zones' : data?.zone ?? 'Oversight'}
        title="Time limits"
        description="Claims and certificates past their time limit, who holds them, and each office's record of delays. Late records are escalated here automatically."
      />
      {isLoading ? (
        <PageSkeleton />
      ) : error ? (
        <ErrorCallout error={error} />
      ) : (
        data && (
          <div className="stack-lg">
            <StatGrid>
              <Stat label="Past the limit now" value={data.overdue} alert={data.overdue > 0} foot="Across every queue" />
              <Stat label="Escalated" value={data.escalated} alert={data.escalated > 0} foot="Waiting twice the limit or more" />
              <Stat label="Due soon" value={data.dueSoon} foot="Reminders already sent" />
              <Stat label="Breaches, 90 days" value={data.breaches90d} foot="Kept on record per office" />
            </StatGrid>

            <Panel title="Past the limit now" kicker="Longest delay first" flush>
              {data.items.length === 0 ? (
                <div className="panel-body">
                  <EmptyState title="Nothing is late">Every claim and certificate is within its time limit.</EmptyState>
                </div>
              ) : (
                <DataTable
                  rows={data.items}
                  rowKey={(i) => i.subjectType + i.subjectId}
                  columns={[
                    {
                      key: 'r',
                      header: 'Record',
                      render: (i) => (
                        <div>
                          <strong className="mono">{i.reference}</strong>
                          <div className="muted" style={{ fontSize: 'var(--text-xs)' }}>
                            {i.stageLabel}
                          </div>
                        </div>
                      ),
                    },
                    {
                      key: 'o',
                      header: 'Office',
                      render: (i) => (
                        <div>
                          {i.officeName}
                          <div className="caps muted">{OFFICE_LABELS[i.officeType] ?? i.officeType}</div>
                        </div>
                      ),
                    },
                    { key: 'h', header: 'Held by', render: (i) => i.heldBy ?? <span className="muted">In the queue</span> },
                    {
                      key: 'd',
                      header: 'Late by',
                      align: 'right',
                      render: (i) => (
                        <span className="num">
                          {i.daysOverdue} d <span className="muted">/ {i.slaDays} d limit</span>
                        </span>
                      ),
                    },
                    { key: 'l', header: 'Status', render: (i) => <Badge tone={i.level === 'ESCALATED' ? 'attention' : 'wait'}>{i.level === 'ESCALATED' ? 'Escalated' : 'Overdue'}</Badge> },
                    {
                      key: 'a',
                      header: '',
                      render: (i) => (
                        <div style={{ textAlign: 'right' }}>
                          <Button size="sm" icon={<Icon.Bell />} loading={remind.isPending && remind.variables?.subjectId === i.subjectId} onClick={() => remind.mutate(i)}>
                            Remind
                          </Button>
                          {i.lastReminderAt && <div className="muted" style={{ fontSize: 'var(--text-xs)', marginTop: 4 }}>Last {formatDateTime(i.lastReminderAt)}</div>}
                        </div>
                      ),
                    },
                  ]}
                />
              )}
            </Panel>

            <Panel title="Record of delays" kicker="Breaches in the last 90 days, by office and stage" flush>
              {data.offices.length === 0 ? (
                <div className="panel-body">
                  <EmptyState title="No breaches recorded">No office has missed a time limit in the last 90 days.</EmptyState>
                </div>
              ) : (
                <DataTable
                  rows={data.offices}
                  rowKey={(o) => o.officeType + o.officeId + o.stage}
                  columns={[
                    { key: 'o', header: 'Office', render: (o) => o.officeName },
                    { key: 't', header: 'Type', render: (o) => <span className="caps muted">{OFFICE_LABELS[o.officeType] ?? o.officeType}</span> },
                    { key: 's', header: 'Stage', render: (o) => o.stage },
                    { key: 'b', header: 'Breaches', align: 'right', render: (o) => <span className="num">{o.breaches}</span> },
                    { key: 'p', header: 'Still open', align: 'right', render: (o) => <span className="num">{o.open}</span> },
                  ]}
                />
              )}
            </Panel>

            <Callout title="How escalation works">
              <p>
                Officials are reminded at 80% of a time limit. When the limit passes, the office and its supervisor are told, the employee is informed, and a record held by one
                official goes back to the queue if a colleague can take it. At twice the limit it is escalated here. Reminders you send are recorded, at most one an hour per record.
              </p>
            </Callout>
          </div>
        )
      )}
    </Page>
  )
}
