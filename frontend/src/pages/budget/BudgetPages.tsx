import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { api } from '../../api/client'
import type { Allocation, Demand, PayableClaim, PaymentBatch, SchoolPosition, SchoolRef } from '../../api/types'
import { useMe } from '../../auth/AuthContext'
import { Button } from '../../components/ui/Button'
import { DataTable } from '../../components/ui/DataTable'
import { Badge, Callout, EmptyState, ErrorCallout, PageSkeleton } from '../../components/ui/Feedback'
import { TextField } from '../../components/ui/Form'
import { Page, PageHeader, Panel, Stat, StatGrid } from '../../components/ui/Layout'
import { Modal } from '../../components/ui/Modal'
import { PasswordConfirm } from '../../components/ui/PasswordConfirm'
import { useToast } from '../../components/ui/Toast'
import { formatDate, formatDateTime, formatMoney } from '../../lib/format'
import { Icon } from '../../lib/icons'

const money = (n: number) => formatMoney(n, true)

export default function BudgetPage() {
  const me = useMe()
  return me.role === 'HOS' ? <SchoolBudget /> : <PaoBudget />
}

function PositionTiles({ p }: { p: SchoolPosition }) {
  return (
    <StatGrid>
      <Stat label="Allocated" value={p.allocated} format={money} foot={'Financial year ' + p.financialYear} />
      <Stat label="Paid" value={p.paid} format={money} />
      <Stat label="Balance" value={p.balance} format={money} />
      <Stat label="Awaiting funds" value={p.awaitingFunds} format={money} foot={`${p.awaitingFundsCount} sanctioned claims`} alert={p.awaitingFunds > p.balance} />
      <Stat label="Under scrutiny" value={p.pipelinePending} format={money} foot={`${p.pendingCount} claims, best estimate`} />
      <Stat label="Suggested demand" value={p.suggestedDemand} format={money} foot="Pipeline minus balance" />
    </StatGrid>
  )
}

function DemandTable({ rows, officer, onAck }: { rows: Demand[]; officer?: boolean; onAck?: (d: Demand) => void }) {
  return (
    <DataTable
      rows={rows}
      rowKey={(d) => d.id}
      columns={[
        { key: 's', header: 'School', render: (d) => d.schoolName ?? d.schoolId },
        { key: 'fy', header: 'Year', render: (d) => d.financialYear },
        { key: 'a', header: 'Amount', align: 'right', render: (d) => <span className="num">{formatMoney(d.amount)}</span> },
        { key: 'n', header: 'Claims', align: 'right', render: (d) => <span className="num">{d.claimCount}</span> },
        { key: 'by', header: 'Raised', render: (d) => `${d.raisedBy ?? '-'}, ${formatDateTime(d.raisedAt)}` },
        {
          key: 'st',
          header: 'Status',
          render: (d) => <Badge tone={d.status === 'RAISED' ? 'wait' : d.status === 'ACKNOWLEDGED' ? 'done' : 'closed'}>{d.status}</Badge>,
        },
        ...(officer
          ? [
              {
                key: 'act',
                header: '',
                render: (d: Demand) =>
                  d.status === 'RAISED' && (
                    <Button size="sm" onClick={() => onAck?.(d)}>
                      Acknowledge
                    </Button>
                  ),
              },
            ]
          : []),
      ]}
    />
  )
}

function AllocationTable({ rows }: { rows: Allocation[] }) {
  return (
    <DataTable
      rows={rows}
      rowKey={(a) => a.id}
      columns={[
        { key: 'o', header: 'Sanction order', render: (a) => <span className="mono">{a.sanctionOrderNo}</span> },
        { key: 'a', header: 'Amount', align: 'right', render: (a) => <span className="num">{formatMoney(a.amount)}</span> },
        { key: 'by', header: 'Recorded by', render: (a) => `${a.allocatedBy ?? '-'}, ${formatDate(a.allocatedAt)}` },
        { key: 'r', header: 'Remarks', render: (a) => a.remarks ?? '-' },
      ]}
    />
  )
}

// ======================================================================
// Head of School
// ======================================================================

function SchoolBudget() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const { data, isLoading, error } = useQuery({
    queryKey: ['budget', 'school'],
    queryFn: () => api.get<{ position: SchoolPosition; demands: Demand[]; allocations: Allocation[] }>('/api/budget/school'),
  })
  const raise = useMutation({
    mutationFn: () => api.post<Demand>('/api/budget/school/demand'),
    onSuccess: (d) => {
      toast.ok(`Demand of ${formatMoney(d.amount)} sent to the PAO`)
      queryClient.invalidateQueries({ queryKey: ['budget'] })
    },
    onError: (e) => toast.error(e instanceof Error ? e.message : 'Could not raise the demand'),
  })

  if (isLoading) return <Page><PageSkeleton /></Page>
  if (!data) return <Page><ErrorCallout error={error} /></Page>

  return (
    <Page>
      <PageHeader
        eyebrow="School"
        title="Budget and demand"
        description="The demand is calculated from claims actually filed, so there is no need to collect estimates from staff."
        actions={
          <Button variant="primary" icon={<Icon.Arrow />} loading={raise.isPending} disabled={data.position.suggestedDemand <= 0} onClick={() => raise.mutate()}>
            Send demand of {money(data.position.suggestedDemand)}
          </Button>
        }
      />
      <div className="stack-lg">
        <PositionTiles p={data.position} />
        <Callout title="Why claims no longer wait for the next budget">
          <p>
            Claims are verified and sanctioned whenever they arrive. Only the payment waits for money, and sanctioned claims are paid
            strictly oldest first as soon as the PAO records an allocation.
          </p>
        </Callout>
        <div className="grid grid-2">
          <Panel title="Demands sent" flush>
            {data.demands.length ? <DemandTable rows={data.demands} /> : <div className="panel-body"><EmptyState title="No demands yet" /></div>}
          </Panel>
          <Panel title="Allocations received" kicker="This financial year" flush>
            {data.allocations.length ? <AllocationTable rows={data.allocations} /> : <div className="panel-body"><EmptyState title="No allocations yet" /></div>}
          </Panel>
        </div>
      </div>
    </Page>
  )
}

// ======================================================================
// PAO overview
// ======================================================================

function PaoBudget() {
  const me = useMe()
  const navigate = useNavigate()
  const toast = useToast()
  const queryClient = useQueryClient()
  const officer = me.role === 'PAO_OFFICER'
  const positions = useQuery({ queryKey: ['budget', 'pao', 'schools'], queryFn: () => api.get<SchoolPosition[]>('/api/budget/pao/schools') })
  const schools = useQuery({ queryKey: ['pao-schools'], queryFn: () => api.get<SchoolRef[]>('/api/org/pao/schools') })
  const demands = useQuery({ queryKey: ['budget', 'pao', 'demands'], queryFn: () => api.get<Demand[]>('/api/budget/pao/demands') })
  const ack = useMutation({
    mutationFn: (d: Demand) => api.post(`/api/budget/pao/demands/${d.id}/acknowledge`),
    onSuccess: () => {
      toast.ok('Demand acknowledged')
      queryClient.invalidateQueries({ queryKey: ['budget'] })
    },
  })

  if (positions.isLoading || schools.isLoading) return <Page><PageSkeleton /></Page>
  const names = new Map((schools.data ?? []).map((s) => [s.id, s]))
  const rows = positions.data ?? []
  const totals = rows.reduce(
    (a, p) => ({ balance: a.balance + p.balance, awaiting: a.awaiting + p.awaitingFunds, demand: a.demand + p.suggestedDemand }),
    { balance: 0, awaiting: 0, demand: 0 },
  )

  return (
    <Page>
      <PageHeader
        eyebrow="Pay and Accounts Office"
        title={officer ? 'Budgets and payments' : 'School budgets'}
        description="Record allocations against sanction orders and release payments. Payments always go to the oldest sanctioned claim first."
      />
      <div className="stack-lg">
        <StatGrid columns={3}>
          <Stat label="Schools" value={rows.length} />
          <Stat label="Total balance" value={totals.balance} format={money} />
          <Stat label="Sanctioned, awaiting funds" value={totals.awaiting} format={money} alert={totals.awaiting > totals.balance} />
        </StatGrid>
        <Panel title="Schools" kicker="Open a school to allocate funds or run payments" flush>
          <DataTable
            rows={rows}
            rowKey={(p) => p.schoolId}
            onRowClick={(p) => navigate('/budget/schools/' + p.schoolId)}
            columns={[
              { key: 's', header: 'School', render: (p) => <span>{names.get(p.schoolId)?.name ?? p.schoolId} <span className="muted mono">{names.get(p.schoolId)?.code}</span></span> },
              { key: 'al', header: 'Allocated', align: 'right', render: (p) => <span className="num">{formatMoney(p.allocated)}</span> },
              { key: 'pd', header: 'Paid', align: 'right', render: (p) => <span className="num">{formatMoney(p.paid)}</span> },
              { key: 'b', header: 'Balance', align: 'right', render: (p) => <span className="num">{formatMoney(p.balance)}</span> },
              { key: 'aw', header: 'Awaiting funds', align: 'right', render: (p) => <span className="num">{formatMoney(p.awaitingFunds)} ({p.awaitingFundsCount})</span> },
              { key: 'sd', header: 'Suggested demand', align: 'right', render: (p) => <span className="num">{formatMoney(p.suggestedDemand)}</span> },
            ]}
          />
        </Panel>
        <Panel title="Demands from schools" flush>
          {demands.data && demands.data.length > 0 ? (
            <DemandTable rows={demands.data} officer={officer} onAck={(d) => ack.mutate(d)} />
          ) : (
            <div className="panel-body"><EmptyState title="No demands yet" /></div>
          )}
        </Panel>
      </div>
    </Page>
  )
}

// ======================================================================
// PAO: one school
// ======================================================================

interface SchoolDetail {
  school: SchoolRef
  position: SchoolPosition
  allocations: Allocation[]
  payable: PayableClaim[]
  batches: PaymentBatch[]
}

export function PaoSchoolBudgetPage() {
  const { schoolId } = useParams()
  const me = useMe()
  const navigate = useNavigate()
  const toast = useToast()
  const queryClient = useQueryClient()
  const officer = me.role === 'PAO_OFFICER'
  const [allocating, setAllocating] = useState(false)
  const [paying, setPaying] = useState(false)
  const [amount, setAmount] = useState('')
  const [order, setOrder] = useState('')
  const [remarks, setRemarks] = useState('')

  const { data, isLoading, error } = useQuery({
    queryKey: ['budget', 'school-detail', schoolId],
    queryFn: () => api.get<SchoolDetail>('/api/budget/pao/schools/' + schoolId),
  })
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['budget'] })

  const allocate = useMutation({
    mutationFn: () => api.post(`/api/budget/pao/schools/${schoolId}/allocations`, { amount: Number(amount), sanctionOrderNo: order, remarks: remarks || null }),
    onSuccess: () => {
      toast.ok('Allocation recorded')
      setAllocating(false)
      setAmount('')
      setOrder('')
      setRemarks('')
      refresh()
    },
  })
  const pay = useMutation({
    mutationFn: (password: string) => api.post<PaymentBatch>(`/api/budget/pao/schools/${schoolId}/pay`, { password }),
    onSuccess: (b) => {
      toast.ok(`Batch ${b.batchRef}: ${b.claimCount} claims paid, ${formatMoney(b.totalAmount)}`)
      setPaying(false)
      refresh()
      queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    },
  })

  if (isLoading) return <Page><PageSkeleton /></Page>
  if (!data) return <Page><ErrorCallout error={error} /></Page>

  // Preview of what a payment run would pay: oldest first, stop at the first that does not fit
  let running = 0
  const preview = new Set<number>()
  for (const c of data.payable) {
    if (running + c.admittedAmount > data.position.balance) break
    running += c.admittedAmount
    preview.add(c.claimId)
  }

  return (
    <Page>
      <PageHeader
        eyebrow={'School ' + data.school.code}
        title={data.school.name}
        description={data.school.address ?? undefined}
        actions={
          <>
            <Button icon={<Icon.Back />} onClick={() => navigate('/budget')}>
              All schools
            </Button>
            {officer && (
              <>
                <Button onClick={() => setAllocating(true)} icon={<Icon.Plus />}>
                  Record allocation
                </Button>
                <Button variant="primary" disabled={preview.size === 0} onClick={() => setPaying(true)} icon={<Icon.Rupee />}>
                  Pay {preview.size} claim{preview.size === 1 ? '' : 's'}
                </Button>
              </>
            )}
          </>
        }
      />
      <div className="stack-lg">
        <PositionTiles p={data.position} />
        <Panel title="Sanctioned claims awaiting funds" kicker="Paid in this exact order" flush>
          {data.payable.length > 0 ? (
            <DataTable
              rows={data.payable}
              rowKey={(c) => c.claimId}
              columns={[
                { key: 'i', header: '#', render: (_, i) => <span className="num">{String(i + 1).padStart(2, '0')}</span> },
                { key: 'n', header: 'Claim', render: (c) => <span className="mono">{c.claimNumber}</span> },
                { key: 'e', header: 'Employee', render: (c) => c.employeeName },
                { key: 'f', header: 'First submitted', render: (c) => formatDate(c.firstSubmittedAt) },
                { key: 'a', header: 'Admitted', align: 'right', render: (c) => <span className="num">{formatMoney(c.admittedAmount)}</span> },
                { key: 'p', header: 'Next run', render: (c) => (preview.has(c.claimId) ? <Badge tone="done">Will be paid</Badge> : <Badge tone="wait">Needs funds</Badge>) },
              ]}
            />
          ) : (
            <div className="panel-body"><EmptyState title="Nothing awaiting payment" /></div>
          )}
        </Panel>
        <div className="grid grid-2">
          <Panel title="Allocations" kicker="This financial year" flush>
            {data.allocations.length ? <AllocationTable rows={data.allocations} /> : <div className="panel-body"><EmptyState title="No allocations yet" /></div>}
          </Panel>
          <Panel title="Payment batches" flush>
            {data.batches.length ? (
              <DataTable
                rows={data.batches}
                rowKey={(b) => b.batchRef}
                columns={[
                  { key: 'r', header: 'Batch', render: (b) => <span className="mono">{b.batchRef}</span> },
                  { key: 'n', header: 'Claims', align: 'right', render: (b) => <span className="num">{b.claimCount}</span> },
                  { key: 't', header: 'Total', align: 'right', render: (b) => <span className="num">{formatMoney(b.totalAmount)}</span> },
                  { key: 'd', header: 'Date', render: (b) => formatDateTime(b.createdAt) },
                ]}
              />
            ) : (
              <div className="panel-body"><EmptyState title="No payments yet" /></div>
            )}
          </Panel>
        </div>
      </div>

      <Modal
        open={allocating}
        title="Record a budget allocation"
        onClose={() => setAllocating(false)}
        footer={
          <>
            <Button onClick={() => setAllocating(false)}>Cancel</Button>
            <Button variant="primary" loading={allocate.isPending} disabled={!(Number(amount) > 0) || !order.trim()} onClick={() => allocate.mutate()}>
              Record
            </Button>
          </>
        }
      >
        <div className="stack">
          <TextField label="Amount (Rs)" type="number" min="1" step="0.01" required value={amount} onChange={(e) => setAmount(e.target.value)} />
          <TextField label="Sanction order number" required maxLength={60} value={order} onChange={(e) => setOrder(e.target.value)} />
          <TextField label="Remarks" maxLength={300} value={remarks} onChange={(e) => setRemarks(e.target.value)} />
          {allocate.error && <ErrorCallout error={allocate.error} />}
        </div>
      </Modal>

      <PasswordConfirm open={paying} title="Release payment" confirmLabel="Pay now" busy={pay.isPending} onCancel={() => setPaying(false)} onConfirm={(p) => pay.mutate(p)}>
        <p>
          {preview.size} claim{preview.size === 1 ? '' : 's'} totalling <strong>{formatMoney(running)}</strong> will be marked paid under one batch, oldest first.
        </p>
        {pay.error && <ErrorCallout error={pay.error} />}
      </PasswordConfirm>
    </Page>
  )
}
