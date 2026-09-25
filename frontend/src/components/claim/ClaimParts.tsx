import { useNavigate } from 'react-router'
import { openDocument } from '../../api/client'
import type { Claim, ClaimStatus, ClaimSummary } from '../../api/types'
import { RELATION_LABELS, ageOf, formatDate, formatDateTime, formatMoney } from '../../lib/format'
import { Icon } from '../../lib/icons'
import { DataTable } from '../ui/DataTable'
import { Badge, ClaimStatusBadge } from '../ui/Feedback'
import { DocumentChip } from '../ui/Files'
import { Details, Panel } from '../ui/Layout'
import { Timeline, Tracker } from '../ui/Progress'
import type { TrackerStep } from '../ui/Progress'

const CATEGORY_LABELS: Record<string, string> = {
  CONSULTATION: 'Consultation',
  INVESTIGATION: 'Investigation',
  MEDICINE: 'Medicine',
  OTHER: 'Other',
}

/** Table of claims used by the employee list and the office lists. */
export function ClaimTable({ rows, showEmployee, showQueue }: { rows: ClaimSummary[]; showEmployee?: boolean; showQueue?: boolean }) {
  const navigate = useNavigate()
  return (
    <DataTable
      rows={rows}
      rowKey={(r) => r.id}
      onRowClick={(r) => navigate('/claims/' + r.id)}
      caption="Claims"
      columns={[
        ...(showQueue ? [{ key: 'pos', header: '#', render: (_: ClaimSummary, i: number) => <span className="num">{String(i + 1).padStart(2, '0')}</span> }] : []),
        { key: 'no', header: 'Claim', render: (r) => <span className="mono">{r.claimNumber ?? 'Draft #' + r.id}</span> },
        ...(showEmployee ? [{ key: 'emp', header: 'Employee', render: (r: ClaimSummary) => r.employeeName ?? '-' }] : []),
        {
          key: 'patient',
          header: 'Patient',
          render: (r) => (
            <span>
              {r.patientName} <span className="muted">({RELATION_LABELS[r.patientRelation]})</span>
            </span>
          ),
        },
        { key: 'type', header: 'Type', render: (r) => <span className="tag">{r.treatmentType}</span> },
        { key: 'amt', header: 'Claimed', align: 'right', render: (r) => <span className="num">{formatMoney(r.claimedAmount)}</span> },
        {
          key: 'when',
          header: showQueue ? 'Waiting' : 'Submitted',
          render: (r) => (showQueue ? ageOf(r.stageEnteredAt) : formatDate(r.firstSubmittedAt ?? r.createdAt)),
        },
        {
          key: 'st',
          header: 'Status',
          render: (r) => (
            <span className="row" style={{ gap: 6 }}>
              <ClaimStatusBadge status={r.status} />
              {r.overdue && <Badge tone="attention">Overdue</Badge>}
            </span>
          ),
        },
        ...(showQueue ? [{ key: 'with', header: 'Taken by', render: (r: ClaimSummary) => r.assignedTo ?? <span className="muted">Not taken</span> }] : []),
      ]}
    />
  )
}

/** Where the claim is, as a row of stage boxes. */
export function ClaimTracker({ claim }: { claim: Claim }) {
  const order: { label: string; statuses: ClaimStatus[] }[] = [
    { label: 'Filed by employee', statuses: ['DRAFT', 'RETURNED_BY_HOS', 'RETURNED_BY_PAO'] },
    { label: 'Head of School', statuses: ['PENDING_HOS'] },
    { label: 'PAO scrutiny', statuses: ['PENDING_PAO_AUDIT'] },
    { label: 'PAO sanction', statuses: ['PENDING_SANCTION'] },
    { label: 'Payment', statuses: ['SANCTIONED', 'PAID'] },
  ]
  const index = order.findIndex((o) => o.statuses.includes(claim.status))
  const closed = claim.status === 'REJECTED' || claim.status === 'WITHDRAWN'
  const steps: TrackerStep[] = order.map((o, i) => {
    let state: TrackerStep['state'] = 'todo'
    if (claim.status === 'PAID' || i < index) state = 'done'
    else if (i === index && !closed) state = 'current'
    let detail: string | undefined
    if (state === 'current') {
      if (claim.status === 'SANCTIONED') detail = 'Sanctioned, waiting for funds'
      else if (claim.status.startsWith('RETURNED')) detail = 'Returned: correct and resubmit'
      else if (claim.queuePosition) detail = `Position ${claim.queuePosition} in queue`
      else if (claim.status === 'DRAFT') detail = 'Draft, not yet submitted'
    }
    if (claim.status === 'PAID' && i === order.length - 1) detail = 'Paid ' + formatDate(claim.paidAt)
    return { label: o.label, state, detail }
  })
  return <Tracker steps={steps} />
}

/** Read only rendering of a whole claim, shared by employee and reviewers. */
export function ClaimDetails({ claim, documentBase }: { claim: Claim; documentBase?: string }) {
  const base = documentBase ?? `/api/claims/${claim.id}/documents/`
  const e = claim.employee
  const showRestricted = claim.items.some((i) => i.amountRestricted !== null)
  const showAdmitted = claim.items.some((i) => i.amountAdmitted !== null)
  const prescriptions = new Map<string, { nacNumber: string | null; date: string }>()
  claim.items.forEach((i) => i.nac && prescriptions.set(i.nac.prescriptionDocumentId, { nacNumber: i.nac.nacNumber, date: i.nac.prescriptionDate }))

  return (
    <div className="stack-lg">
      <div className="grid grid-2">
        <Panel title="Patient and treatment" kicker="Annexure II, items 16 to 21">
          <Details
            items={[
              ['Patient', `${claim.patientName} (${RELATION_LABELS[claim.patientRelation]})`],
              ['Illness / diagnosis', claim.illnessDescription],
              ['Treatment', claim.treatmentType === 'OPD' ? 'Out patient (OPD)' : 'Indoor (admitted)'],
              ['Period', `${formatDate(claim.treatmentFrom)} to ${formatDate(claim.treatmentTo)}`],
              ...(claim.treatmentType === 'INDOOR'
                ? ([['Admission / discharge', `${formatDate(claim.admissionDate)} / ${formatDate(claim.dischargeDate)}`]] as [string, string][])
                : []),
              ['Hospital', claim.hospitalName + (claim.hospitalAddress ? ', ' + claim.hospitalAddress : '')],
              ['Hospital type', claim.hospitalType === 'GOVERNMENT' ? 'Government' : claim.hospitalType === 'EMPANELLED' ? 'Panel' : 'Private'],
              ['Emergency', claim.emergency ? 'Yes' : 'No'],
              ['Referral', claim.referralDetails],
              ['Medical advance', claim.medicalAdvanceDetails],
            ]}
          />
        </Panel>
        <Panel title="Employee" kicker="Pre filled from service records">
          {e ? (
            <Details
              items={[
                ['Name', e.fullName],
                ['Employee ID', <span className="mono">{e.employeeCode}</span>],
                ['Designation', e.designation],
                ['School', e.school ? `${e.school.name} (${e.school.code})` : '-'],
                ['Pay scale / basic pay', `${e.payScale ?? '-'} / ${formatMoney(e.basicPay)}`],
                ['DGEHS card', `${e.dgehsCardNo ?? '-'}${e.dgehsCardPlace ? ', ' + e.dgehsCardPlace : ''}`],
                ['Card validity', `${formatDate(e.dgehsValidFrom)} to ${formatDate(e.dgehsValidTo)}`],
                ['Ward entitlement', e.wardEntitlement?.replace('_', ' ').toLowerCase() ?? '-'],
                ['Bank', `${e.bankName ?? '-'}, ${e.bankBranch ?? '-'}`],
                ['Account / IFSC', <span className="mono">{`${e.bankAccountMasked ?? '-'} / ${e.ifsc ?? '-'}`}</span>],
              ]}
            />
          ) : (
            <p className="muted">Profile not available.</p>
          )}
        </Panel>
      </div>

      <Panel title="Bills" kicker="Calculation sheet" flush>
        <DataTable
          rows={claim.items}
          rowKey={(i) => i.id}
          columns={[
            { key: 'n', header: 'S.No', render: (i) => <span className="num">{i.lineNo}</span> },
            {
              key: 'd',
              header: 'Item',
              render: (i) => (
                <div>
                  <strong>{i.description}</strong>
                  <div className="muted" style={{ fontSize: 'var(--text-xs)' }}>
                    {CATEGORY_LABELS[i.category]} · Bill {i.billNumber} of {formatDate(i.billDate)} · {i.vendorName}
                    {i.dgehsCode ? ' · DGEHS code ' + i.dgehsCode : ''}
                  </div>
                  {i.nac && (
                    <div style={{ marginTop: 4 }}>
                      <span className="tag">e-NAC {i.nac.nacNumber}</span>
                    </div>
                  )}
                  {i.legacyNac && (
                    <div style={{ marginTop: 4 }}>
                      <span className="tag">Paper NAC attached</span>
                    </div>
                  )}
                  {i.hosRemarks && <div className="muted" style={{ fontSize: 'var(--text-xs)', marginTop: 4 }}>School: {i.hosRemarks}</div>}
                  {i.disallowReason && <div style={{ fontSize: 'var(--text-xs)', marginTop: 4 }}>PAO: {i.disallowReason}</div>}
                </div>
              ),
            },
            { key: 'c', header: 'Claimed', align: 'right', render: (i) => <span className="num">{formatMoney(i.amountClaimed)}</span> },
            ...(showRestricted
              ? [
                  { key: 'r', header: 'DGEHS rate', align: 'right' as const, render: (i: Claim['items'][number]) => <span className="num">{formatMoney(i.dgehsRate)}</span> },
                  { key: 'rs', header: 'Restricted', align: 'right' as const, render: (i: Claim['items'][number]) => <span className="num">{formatMoney(i.amountRestricted)}</span> },
                ]
              : []),
            ...(showAdmitted
              ? [{ key: 'a', header: 'Admitted', align: 'right' as const, render: (i: Claim['items'][number]) => <span className="num">{formatMoney(i.amountAdmitted)}</span> }]
              : []),
            {
              key: 'doc',
              header: 'Bill',
              render: (i) => (i.billDocument ? <DocumentChip doc={i.billDocument} href={base + i.billDocument.id} name={claim.documentNames[i.billDocument.id]} /> : '-'),
            },
          ]}
          footer={
            <tr>
              <td colSpan={2}>Total</td>
              <td className="right num">{formatMoney(claim.claimedAmount)}</td>
              {showRestricted && <td />}
              {showRestricted && <td className="right num">{formatMoney(claim.restrictedAmount)}</td>}
              {showAdmitted && <td className="right num">{formatMoney(claim.admittedAmount)}</td>}
              <td />
            </tr>
          }
        />
      </Panel>

      <div className="grid grid-2">
        <Panel title="Supporting documents" kicker="Attachments and e-NAC prescriptions">
          <div className="stack" style={{ gap: 8 }}>
            {claim.attachments.map(
              (a) =>
                a.document && (
                  <div key={a.document.id}>
                    <div className="caps muted" style={{ marginBottom: 4 }}>
                      {a.category.replace(/_/g, ' ')}
                    </div>
                    <DocumentChip doc={a.document} href={base + a.document.id} name={claim.documentNames[a.document.id]} />
                  </div>
                ),
            )}
            {[...prescriptions.entries()].map(([docId, p]) => (
              <div key={docId}>
                <div className="caps muted" style={{ marginBottom: 4 }}>
                  Prescription for e-NAC {p.nacNumber}
                </div>
                <button type="button" className="file-chip btn-ghost" style={{ width: '100%', cursor: 'pointer' }} onClick={() => openDocument(base + docId, claim.documentNames[docId] ?? 'prescription.pdf').catch(() => undefined)}>
                  <span className="row">
                    <Icon.File width={16} height={16} /> Prescription dated {formatDate(p.date)}
                  </span>
                  <Icon.Eye width={16} height={16} />
                </button>
              </div>
            ))}
            {claim.attachments.length === 0 && prescriptions.size === 0 && <p className="muted">No supporting documents.</p>}
          </div>
        </Panel>
        <Panel title="Document check list" kicker="Annexure I, derived automatically">
          <ul className="checklist" style={{ gridTemplateColumns: '1fr' }}>
            {claim.checklist.map((c) => (
              <li key={c.code} className={c.submitted ? 'ok' : c.required ? 'missing' : undefined}>
                <span className="box">{c.submitted ? <Icon.Check width={12} height={12} /> : c.code}</span>
                <span>
                  {c.label}
                  {c.required && !c.submitted && <strong> (required)</strong>}
                </span>
              </li>
            ))}
          </ul>
        </Panel>
      </div>

      <div className="grid grid-2">
        <Panel title="History" kicker="Every action with name and time">
          <Timeline entries={claim.timeline} />
        </Panel>
        <Panel title="Record" kicker="Accountability">
          <Details
            items={[
              ['Claim number', claim.claimNumber ? <span className="mono">{claim.claimNumber}</span> : 'Assigned on submission'],
              ['Financial year', claim.financialYear],
              ['First submitted', formatDateTime(claim.firstSubmittedAt)],
              ['Last submitted', formatDateTime(claim.lastSubmittedAt)],
              ['Returned', claim.returnCount + (claim.returnCount === 1 ? ' time' : ' times')],
              ['Certified by HoS', claim.hosCertifiedBy ? `${claim.hosCertifiedBy}, ${formatDateTime(claim.hosCertifiedAt)}` : '-'],
              ['Scrutinised by', claim.auditedBy ? `${claim.auditedBy}, ${formatDateTime(claim.auditedAt)}` : '-'],
              ['Sanctioned by', claim.sanctionedBy ? `${claim.sanctionedBy}, ${formatDateTime(claim.sanctionedAt)}` : '-'],
              ['Payment batch', claim.paymentBatchRef ? <span className="mono">{claim.paymentBatchRef}</span> : '-'],
              ['Rejection reason', claim.rejectionReason],
            ]}
          />
        </Panel>
      </div>
    </div>
  )
}

/** Header strip with the three amounts of a claim. */
export function ClaimAmounts({ claim }: { claim: Claim }) {
  return (
    <div className="tiles even">
      {[
        ['Claimed', claim.claimedAmount],
        ['Restricted by school', claim.restrictedAmount],
        ['Admitted by PAO', claim.admittedAmount],
        ['Status', null],
      ].map(([label, value]) => (
        <div key={label as string} className="stat" style={{ minHeight: 96 }}>
          <span className="caps stat-label">{label}</span>
          {label === 'Status' ? (
            <span>
              <ClaimStatusBadge status={claim.status} />
            </span>
          ) : (
            <span className="stat-value" style={{ fontSize: 'var(--text-xl)' }}>
              {formatMoney(value as number | null)}
            </span>
          )}
        </div>
      ))}
    </div>
  )
}
