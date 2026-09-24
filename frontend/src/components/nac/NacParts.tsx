import { useNavigate } from 'react-router'
import type { Nac, NacSummary } from '../../api/types'
import { DECISION_LABELS, RELATION_LABELS, ageOf, formatDate, formatDateTime } from '../../lib/format'
import { DataTable } from '../ui/DataTable'
import { Badge, NacStatusBadge } from '../ui/Feedback'
import { DocumentChip } from '../ui/Files'
import { Details, Panel } from '../ui/Layout'
import { Tracker } from '../ui/Progress'

export function NacTable({ rows, showEmployee, showQueue, onOpen }: { rows: NacSummary[]; showEmployee?: boolean; showQueue?: boolean; onOpen?: (r: NacSummary) => void }) {
  const navigate = useNavigate()
  return (
    <DataTable
      rows={rows}
      rowKey={(r) => r.id}
      onRowClick={onOpen ?? ((r) => navigate('/nac/' + r.id))}
      caption="Certificates"
      columns={[
        ...(showQueue ? [{ key: 'pos', header: '#', render: (_: NacSummary, i: number) => <span className="num">{String(i + 1).padStart(2, '0')}</span> }] : []),
        { key: 'no', header: 'Certificate', render: (r) => <span className="mono">{r.nacNumber ?? 'Request #' + r.id}</span> },
        ...(showEmployee ? [{ key: 'emp', header: 'Employee', render: (r: NacSummary) => r.employeeName ?? '-' }] : []),
        { key: 'pt', header: 'Patient', render: (r) => `${r.patientName} (${RELATION_LABELS[r.patientRelation]})` },
        { key: 'items', header: 'Items', align: 'right', render: (r) => <span className="num">{r.itemCount}</span> },
        { key: 'date', header: showQueue ? 'Waiting' : 'Prescribed', render: (r) => (showQueue ? ageOf(r.stageEnteredAt) : formatDate(r.prescriptionDate)) },
        {
          key: 'st',
          header: 'Status',
          render: (r) => (
            <span className="row" style={{ gap: 6 }}>
              <NacStatusBadge status={r.status} />
              {r.overdue && <Badge tone="attention">Overdue</Badge>}
            </span>
          ),
        },
        ...(showQueue ? [{ key: 'with', header: 'Taken by', render: (r: NacSummary) => r.assignedTo ?? <span className="muted">Not taken</span> }] : []),
      ]}
    />
  )
}

export function NacTracker({ nac }: { nac: Nac }) {
  const stages = ['Submitted', 'Pharmacist', 'Medical Officer', 'Issued']
  const index = { PENDING_PHARMACIST: 1, PENDING_MEDICAL_OFFICER: 2, ISSUED: 3, RETURNED: 0 }[nac.status]
  return (
    <Tracker
      steps={stages.map((label, i) => ({
        label,
        state: nac.status === 'ISSUED' || i < index ? 'done' : i === index ? 'current' : 'todo',
        detail:
          i === index && nac.status === 'RETURNED'
            ? 'Returned: correct and resubmit'
            : i === index && nac.queuePosition
              ? `Position ${nac.queuePosition} in queue`
              : i === 3 && nac.issuedAt
                ? formatDate(nac.issuedAt)
                : undefined,
      }))}
    />
  )
}

/** Certificate details with the item decisions and who made them. */
export function NacDetails({ nac }: { nac: Nac }) {
  return (
    <div className="stack-lg">
      <div className="grid grid-2">
        <Panel title="Prescription" kicker="Submitted by the employee">
          <Details
            items={[
              ['Employee', nac.employeeName],
              ['Patient', `${nac.patientName} (${RELATION_LABELS[nac.patientRelation]})`],
              ['Dispensary', nac.dispensaryName],
              ['Prescribed by', nac.prescribedBy],
              ['Prescription date', formatDate(nac.prescriptionDate)],
              ['Submitted', formatDateTime(nac.createdAt)],
            ]}
          />
          {nac.prescription && (
            <div style={{ marginTop: 16 }}>
              <DocumentChip doc={nac.prescription} href={`/api/nac/${nac.id}/prescription`} />
            </div>
          )}
        </Panel>
        <Panel title="Certificate" kicker="Accountability">
          <Details
            items={[
              ['Certificate number', nac.nacNumber ? <span className="mono">{nac.nacNumber}</span> : 'Assigned when issued'],
              ['Status', <NacStatusBadge status={nac.status} />],
              ['Pharmacist', nac.pharmacistName ? `${nac.pharmacistName}, ${formatDateTime(nac.pharmacistAt)}` : '-'],
              ['Pharmacist remarks', nac.pharmacistRemarks],
              ['Medical Officer', nac.medicalOfficerName ? `${nac.medicalOfficerName}, ${formatDateTime(nac.medicalOfficerAt)}` : '-'],
              ['Medical Officer remarks', nac.medicalOfficerRemarks],
            ]}
          />
        </Panel>
      </div>
      <Panel title="Prescribed items" kicker="Decision per item" flush>
        <DataTable
          rows={nac.items}
          rowKey={(i) => i.id}
          columns={[
            { key: 'n', header: 'S.No', render: (i) => <span className="num">{i.lineNo}</span> },
            { key: 'name', header: 'Item', render: (i) => <strong>{i.itemName}</strong> },
            { key: 't', header: 'Type', render: (i) => <span className="tag">{i.itemType}</span> },
            { key: 'q', header: 'Quantity', render: (i) => i.quantity },
            {
              key: 'd',
              header: 'Decision',
              render: (i) =>
                i.decision ? (
                  <Badge tone={i.decision === 'NOT_AVAILABLE' ? 'done' : i.decision === 'NOT_ADMISSIBLE' ? 'attention' : 'closed'}>
                    {DECISION_LABELS[i.decision]}
                  </Badge>
                ) : (
                  <span className="muted">Pending</span>
                ),
            },
            {
              key: 'by',
              header: 'Decided by',
              render: (i) =>
                i.decidedBy ? (
                  <span>
                    {i.decidedBy}
                    <br />
                    <span className="muted" style={{ fontSize: 'var(--text-xs)' }}>
                      {formatDateTime(i.decidedAt)}
                    </span>
                  </span>
                ) : (
                  '-'
                ),
            },
            { key: 'r', header: 'Reason', render: (i) => i.decisionReason ?? '-' },
          ]}
        />
      </Panel>
    </div>
  )
}
