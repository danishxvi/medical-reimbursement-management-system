import { useQuery } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router'
import { api } from '../api/client'
import type { ClaimSnapshot, ClaimStatus, ClaimSummary, Dashboard, NacSummary } from '../api/types'
import { useMe } from '../auth/AuthContext'
import { Bars } from '../components/ui/Bars'
import { Button } from '../components/ui/Button'
import { DataTable } from '../components/ui/DataTable'
import { Callout, ClaimStatusBadge, EmptyState, NacStatusBadge, PageSkeleton } from '../components/ui/Feedback'
import { Page, PageHeader, Panel, Stat, StatGrid, Stagger } from '../components/ui/Layout'
import { CLAIM_STATUS_LABELS, ROLE_LABELS, formatDate, formatMoney } from '../lib/format'
import { Icon } from '../lib/icons'

const money = (n: number) => formatMoney(n, true)

function sum(snapshot: ClaimSnapshot | undefined, statuses: ClaimStatus[]): number {
  return statuses.reduce((acc, s) => acc + (snapshot?.countsByStatus[s] ?? 0), 0)
}

function greeting(): string {
  const h = new Date().getHours()
  return h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening'
}

/** First name for the greeting, skipping honorifics such as Dr. or Smt. */
function firstName(fullName: string): string {
  const parts = fullName.split(/\s+/).filter(Boolean)
  const name = parts.find((p) => !/^(dr|mr|mrs|ms|shri|smt|sh|km|kumari)\.?$/i.test(p))
  return name ?? parts[0] ?? ''
}

export default function DashboardPage() {
  const me = useMe()
  const { data, isLoading } = useQuery({ queryKey: ['dashboard'], queryFn: () => api.get<Dashboard>('/api/dashboard') })

  if (isLoading || !data) {
    return (
      <Page>
        <PageSkeleton />
      </Page>
    )
  }

  return (
    <Page>
      <PageHeader
        eyebrow={`${ROLE_LABELS[me.role]} · Financial year ${data.financialYear}`}
        title={`${greeting()}, ${firstName(me.fullName)}`}
        description={DESCRIPTIONS[me.role]}
        actions={<DashboardActions role={me.role} />}
      />
      {me.role === 'EMPLOYEE' && <EmployeeDashboard data={data} />}
      {me.role === 'HOS' && <HosDashboard data={data} />}
      {(me.role === 'PHARMACIST' || me.role === 'MEDICAL_OFFICER') && <DispensaryDashboard data={data} officer={me.role === 'MEDICAL_OFFICER'} />}
      {(me.role === 'PAO_AUDITOR' || me.role === 'PAO_OFFICER') && <PaoDashboard data={data} officer={me.role === 'PAO_OFFICER'} />}
      {me.role === 'ADMIN' && <AdminDashboard data={data} />}
    </Page>
  )
}

const DESCRIPTIONS: Record<string, string> = {
  EMPLOYEE: 'File claims online, track every step and fix anything returned without losing your place in the queue.',
  HOS: 'Verify staff claims in the order they arrived and keep the school budget demand up to date.',
  PHARMACIST: 'Record item by item whether prescribed medicines are available. Every decision carries your name.',
  MEDICAL_OFFICER: "Review the pharmacist's decisions and countersign the electronic non availability certificate.",
  PAO_AUDITOR: 'Scrutinise forwarded claims item by item and recommend them for sanction.',
  PAO_OFFICER: 'Sanction scrutinised claims, record budget allocations and release payments oldest first.',
  ADMIN: 'Manage accounts and master data, and watch service levels across the system.',
}

function DashboardActions({ role }: { role: string }) {
  const navigate = useNavigate()
  if (role === 'EMPLOYEE') {
    return (
      <>
        <Button onClick={() => navigate('/nac/new')} icon={<Icon.Pill />}>
          Request e-NAC
        </Button>
        <Button variant="primary" onClick={() => navigate('/claims/new')} icon={<Icon.Plus />}>
          New claim
        </Button>
      </>
    )
  }
  if (role === 'ADMIN') {
    return (
      <Button variant="primary" onClick={() => navigate('/admin/employees')} icon={<Icon.Plus />}>
        Add employee
      </Button>
    )
  }
  return (
    <Button variant="primary" onClick={() => navigate('/queue')} icon={<Icon.Queue />}>
      Open my queue
    </Button>
  )
}

// ======================================================================
// Employee
// ======================================================================

function EmployeeDashboard({ data }: { data: Dashboard }) {
  const navigate = useNavigate()
  const c = data.claims
  const claims = useQuery({ queryKey: ['claims', 'mine'], queryFn: () => api.get<ClaimSummary[]>('/api/claims/mine') })
  const nacs = useQuery({ queryKey: ['nac', 'mine'], queryFn: () => api.get<NacSummary[]>('/api/nac/mine') })
  const returned = sum(c, ['RETURNED_BY_HOS', 'RETURNED_BY_PAO'])

  return (
    <div className="stack-lg">
      {returned > 0 && (
        <Callout variant="stripe" title={`${returned} claim${returned > 1 ? 's need' : ' needs'} your correction`}>
          <p>
            Returned claims keep their original place in every queue. Open the claim, fix only what was flagged and resubmit.
          </p>
        </Callout>
      )}
      <StatGrid>
        <Stat label="In progress" value={sum(c, ['PENDING_HOS', 'PENDING_PAO_AUDIT', 'PENDING_SANCTION'])} foot="With school or PAO" />
        <Stat label="Awaiting funds" value={sum(c, ['SANCTIONED'])} foot="Sanctioned, paid oldest first" />
        <Stat label="Claimed so far" value={c?.claimedTotal ?? 0} format={money} foot="All submitted claims" />
        <Stat label="Paid to you" value={c?.paidTotal ?? 0} format={money} foot="Credited with salary" />
      </StatGrid>

      <div className="grid grid-2">
        <Panel title="Recent claims" kicker="Latest five" actions={<Link to="/claims">View all</Link>} flush>
          {claims.data && claims.data.length > 0 ? (
            <DataTable
              rows={claims.data.slice(0, 5)}
              rowKey={(r) => r.id}
              onRowClick={(r) => navigate('/claims/' + r.id)}
              columns={[
                { key: 'no', header: 'Claim', render: (r) => <span className="mono">{r.claimNumber ?? 'Draft #' + r.id}</span> },
                { key: 'patient', header: 'Patient', render: (r) => r.patientName },
                { key: 'amt', header: 'Amount', align: 'right', render: (r) => <span className="num">{formatMoney(r.claimedAmount)}</span> },
                { key: 'st', header: 'Status', render: (r) => <ClaimStatusBadge status={r.status} /> },
              ]}
            />
          ) : (
            <div className="panel-body">
              <EmptyState title="No claims yet" action={<Button onClick={() => navigate('/claims/new')}>Start a claim</Button>}>
                Start with an e-NAC if you bought medicines, then file the claim with your bills.
              </EmptyState>
            </div>
          )}
        </Panel>
        <Panel title="e-NAC certificates" kicker="Dispensary" actions={<Link to="/nac">View all</Link>} flush>
          {nacs.data && nacs.data.length > 0 ? (
            <DataTable
              rows={nacs.data.slice(0, 5)}
              rowKey={(r) => r.id}
              onRowClick={(r) => navigate('/nac/' + r.id)}
              columns={[
                { key: 'no', header: 'Certificate', render: (r) => <span className="mono">{r.nacNumber ?? 'Request #' + r.id}</span> },
                { key: 'date', header: 'Prescribed', render: (r) => formatDate(r.prescriptionDate) },
                { key: 'st', header: 'Status', render: (r) => <NacStatusBadge status={r.status} /> },
              ]}
            />
          ) : (
            <div className="panel-body">
              <EmptyState title="No certificates yet">
                When a prescribed medicine is not available at the dispensary, request an e-NAC here instead of a paper stamp.
              </EmptyState>
            </div>
          )}
        </Panel>
      </div>

      <HowItWorks />
    </div>
  )
}

function HowItWorks() {
  const steps = [
    ['01', 'e-NAC', 'Upload the prescription. The pharmacist marks each item, the Medical Officer countersigns.'],
    ['02', 'Claim', 'Your details are pre filled. Add bills, attach documents and accept the undertaking.'],
    ['03', 'School', 'The Head of School verifies claims strictly in order of arrival and restricts rates.'],
    ['04', 'PAO', 'An auditor scrutinises, a different officer sanctions. Payment follows as funds arrive.'],
  ]
  return (
    <Panel title="How your claim moves" kicker="No files, no carriers, no waiting in the dark">
      <Stagger className="tiles even">
        {steps.map(([n, t, d]) => (
          <div key={n} className="stat" style={{ minHeight: 150 }}>
            <span className="caps stat-label">{n}</span>
            <strong style={{ fontSize: 'var(--text-lg)' }}>{t}</strong>
            <span className="stat-foot">{d}</span>
          </div>
        ))}
      </Stagger>
    </Panel>
  )
}

// ======================================================================
// Head of School
// ======================================================================

function HosDashboard({ data }: { data: Dashboard }) {
  const c = data.claims
  const b = data.budget
  const overdue = c?.overdueByStage?.PENDING_HOS ?? 0
  return (
    <div className="stack-lg">
      {overdue > 0 && (
        <Callout variant="stripe" title={`${overdue} claim${overdue > 1 ? 's are' : ' is'} past the service level`}>
          <p>Overdue claims are visible to the Directorate. Please take the next claim from your queue.</p>
        </Callout>
      )}
      <StatGrid>
        <Stat label="Waiting for you" value={c?.countsByStatus.PENDING_HOS ?? 0} alert={overdue > 0} foot={`${overdue} past service level`} />
        <Stat label="At the PAO" value={sum(c, ['PENDING_PAO_AUDIT', 'PENDING_SANCTION'])} foot="Forwarded by the school" />
        <Stat label="Awaiting funds" value={b?.awaitingFunds ?? 0} format={money} foot={`${b?.awaitingFundsCount ?? 0} sanctioned claims`} />
        <Stat label="Budget balance" value={b?.balance ?? 0} format={money} foot={`Suggested demand ${formatMoney(b?.suggestedDemand ?? 0, true)}`} />
      </StatGrid>
      <div className="grid grid-2">
        <PipelinePanel snapshot={c} />
        <Panel title="Average time to payment" kicker="Last 200 paid claims">
          <div className="big-number">{c?.averageDaysToPay ?? '-'}</div>
          <p className="muted" style={{ marginTop: 8 }}>
            Days from first submission to payment, for claims of this school.
          </p>
        </Panel>
      </div>
    </div>
  )
}

function PipelinePanel({ snapshot }: { snapshot?: ClaimSnapshot }) {
  const order: ClaimStatus[] = ['PENDING_HOS', 'RETURNED_BY_HOS', 'PENDING_PAO_AUDIT', 'RETURNED_BY_PAO', 'PENDING_SANCTION', 'SANCTIONED', 'PAID', 'REJECTED']
  return (
    <Panel title="Claims by stage" kicker="Pipeline">
      <Bars data={order.map((s) => ({ label: CLAIM_STATUS_LABELS[s], value: snapshot?.countsByStatus[s] ?? 0 }))} />
    </Panel>
  )
}

// ======================================================================
// Dispensary
// ======================================================================

function DispensaryDashboard({ data, officer }: { data: Dashboard; officer: boolean }) {
  const n = data.certificates ?? {}
  const mine = officer ? n.PENDING_MEDICAL_OFFICER ?? 0 : n.PENDING_PHARMACIST ?? 0
  const overdue = officer ? n.OVERDUE_MEDICAL_OFFICER ?? 0 : n.OVERDUE_PHARMACIST ?? 0
  return (
    <div className="stack-lg">
      <StatGrid>
        <Stat label="In your queue" value={mine} alert={overdue > 0} foot={`${overdue} past service level`} />
        <Stat label={officer ? 'With pharmacist' : 'With Medical Officer'} value={officer ? n.PENDING_PHARMACIST ?? 0 : n.PENDING_MEDICAL_OFFICER ?? 0} />
        <Stat label="Certificates issued" value={n.ISSUED ?? 0} />
        <Stat label="Returned to employees" value={n.RETURNED ?? 0} />
      </StatGrid>
      <Callout title="Accountability">
        <p>
          Each item decision is stored with your name and time and is visible to the employee, the school and the PAO.
          {officer ? ' Countersigning is a signature: you confirm it with your password or Aadhaar eSign.' : ' Items marked not admissible need a reason.'}
        </p>
      </Callout>
    </div>
  )
}

// ======================================================================
// PAO
// ======================================================================

function PaoDashboard({ data, officer }: { data: Dashboard; officer: boolean }) {
  const c = data.claims
  const stage: ClaimStatus = officer ? 'PENDING_SANCTION' : 'PENDING_PAO_AUDIT'
  const overdue = c?.overdueByStage?.[stage] ?? 0
  return (
    <div className="stack-lg">
      <StatGrid>
        <Stat label="In your queue" value={c?.countsByStatus[stage] ?? 0} alert={overdue > 0} foot={`${overdue} past service level`} />
        <Stat label="Awaiting funds" value={c?.countsByStatus.SANCTIONED ?? 0} foot="Sanctioned, not yet paid" />
        <Stat label="Paid this far" value={c?.paidTotal ?? 0} format={money} />
        <Stat label="Open demands" value={data.openDemands ?? 0} foot={`${data.schools ?? 0} schools served`} />
      </StatGrid>
      <div className="grid grid-2">
        <PipelinePanel snapshot={c} />
        <Panel title="Average time to payment" kicker="Across this PAO">
          <div className="big-number">{c?.averageDaysToPay ?? '-'}</div>
          <p className="muted" style={{ marginTop: 8 }}>
            Days from first submission to payment. The target is to keep this falling every quarter.
          </p>
        </Panel>
      </div>
    </div>
  )
}

// ======================================================================
// Administrator
// ======================================================================

function AdminDashboard({ data }: { data: Dashboard }) {
  const c = data.claims
  const u = data.users
  const totalOverdue = Object.values(c?.overdueByStage ?? {}).reduce((a, b) => a + (b ?? 0), 0)
  return (
    <div className="stack-lg">
      <StatGrid>
        <Stat label="Schools" value={data.schools ?? 0} />
        <Stat label="Employees" value={u?.EMPLOYEE ?? 0} />
        <Stat label="Claims past SLA" value={totalOverdue} alert={totalOverdue > 0} foot="All stages" />
        <Stat label="Paid overall" value={c?.paidTotal ?? 0} format={money} foot={`Average ${c?.averageDaysToPay ?? '-'} days`} />
      </StatGrid>
      <div className="grid grid-2">
        <PipelinePanel snapshot={c} />
        <Panel title="Service level breaches" kicker="By stage">
          <Bars
            data={[
              { label: 'Head of School', value: c?.overdueByStage?.PENDING_HOS ?? 0 },
              { label: 'PAO scrutiny', value: c?.overdueByStage?.PENDING_PAO_AUDIT ?? 0 },
              { label: 'PAO sanction', value: c?.overdueByStage?.PENDING_SANCTION ?? 0 },
            ]}
          />
          <p className="muted" style={{ marginTop: 16, fontSize: 'var(--text-sm)' }}>
            Administrators see aggregates only. Individual medical claims stay private to the employee and the officials handling them.
          </p>
        </Panel>
      </div>
      <Panel title="Active accounts by role" kicker="Identity">
        <Bars data={Object.entries(u ?? {}).map(([role, n]) => ({ label: ROLE_LABELS[role as keyof typeof ROLE_LABELS], value: n }))} />
      </Panel>
    </div>
  )
}
