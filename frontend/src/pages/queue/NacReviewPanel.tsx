import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../../api/client'
import type { Nac, NacDecision } from '../../api/types'
import { Button } from '../../components/ui/Button'
import { Callout, ErrorCallout } from '../../components/ui/Feedback'
import { TextAreaField } from '../../components/ui/Form'
import { Panel } from '../../components/ui/Layout'
import { Modal } from '../../components/ui/Modal'
import { PasswordConfirm } from '../../components/ui/PasswordConfirm'
import { DECISION_LABELS, formatDate } from '../../lib/format'
import { Icon } from '../../lib/icons'

interface Props {
  nac: Nac
  officer: boolean
  onDone: (message: string) => void
  onRelease: () => void
}

export function NacReviewPanel({ nac, officer, onDone, onRelease }: Props) {
  const release = useMutation({ mutationFn: () => api.post(`/api/nac/${nac.id}/release`), onSuccess: onRelease })
  const releaseButton = (
    <Button icon={<Icon.Back />} loading={release.isPending} onClick={() => release.mutate()}>
      Release to queue
    </Button>
  )
  return officer ? (
    <OfficerPanel nac={nac} onDone={onDone} extra={releaseButton} />
  ) : (
    <PharmacistPanel nac={nac} onDone={onDone} extra={releaseButton} />
  )
}

const DECISIONS: NacDecision[] = ['AVAILABLE', 'NOT_AVAILABLE', 'NOT_ADMISSIBLE']

function PharmacistPanel({ nac, onDone, extra }: { nac: Nac; onDone: (m: string) => void; extra: React.ReactNode }) {
  const [rows, setRows] = useState(() => nac.items.map((i) => ({ itemId: i.id, decision: i.decision, reason: i.decisionReason ?? '' })))
  const [remarks, setRemarks] = useState('')
  const [returning, setReturning] = useState(false)
  const [returnText, setReturnText] = useState('')

  const review = useMutation({
    mutationFn: () =>
      api.post<Nac>(`/api/nac/${nac.id}/pharmacist-review`, {
        decisions: rows.map((r) => ({ itemId: r.itemId, decision: r.decision, reason: r.reason || null })),
        remarks: remarks || null,
      }),
    onSuccess: () => onDone('Sent to the Medical Officer for countersignature'),
  })
  const giveBack = useMutation({
    mutationFn: () => api.post<Nac>(`/api/nac/${nac.id}/return`, { remarks: returnText }),
    onSuccess: () => {
      setReturning(false)
      onDone('Returned to the employee')
    },
  })

  const setRow = (i: number, patch: Partial<(typeof rows)[number]>) => setRows((rs) => rs.map((r, j) => (j === i ? { ...r, ...patch } : r)))
  const invalid = rows.some((r) => !r.decision || (r.decision === 'NOT_ADMISSIBLE' && !r.reason.trim()))

  return (
    <Panel
      strong
      title="Mark every item"
      kicker={`Prescription of ${formatDate(nac.prescriptionDate)} by ${nac.prescribedBy}`}
      footer={
        <>
          {extra}
          <Button variant="danger" onClick={() => setReturning(true)}>
            Return to employee
          </Button>
          <Button variant="primary" icon={<Icon.Arrow />} disabled={invalid} loading={review.isPending} onClick={() => review.mutate()}>
            Send to Medical Officer
          </Button>
        </>
      }
    >
      <div className="stack">
        {nac.medicalOfficerRemarks && (
          <Callout variant="stripe" title="Sent back by the Medical Officer">
            <p>{nac.medicalOfficerRemarks}</p>
          </Callout>
        )}
        {nac.items.map((it, i) => (
          <div key={it.id} className="panel" style={{ padding: 16 }}>
            <div className="row-between">
              <div>
                <strong>
                  {it.lineNo}. {it.itemName}
                </strong>
                <span className="muted"> · {it.quantity}</span>
              </div>
              <span className="tag">{it.itemType}</span>
            </div>
            <div className="segmented" role="radiogroup" aria-label={'Decision for ' + it.itemName} style={{ marginTop: 12 }}>
              {DECISIONS.map((d) => (
                <label key={d}>
                  <input type="radio" name={'d-' + it.id} checked={rows[i].decision === d} onChange={() => setRow(i, { decision: d })} />
                  {DECISION_LABELS[d]}
                </label>
              ))}
            </div>
            {rows[i].decision === 'NOT_ADMISSIBLE' && (
              <input
                className="control"
                style={{ marginTop: 8 }}
                placeholder="Reason it is not admissible (required)"
                aria-label={'Reason for ' + it.itemName}
                maxLength={300}
                value={rows[i].reason}
                onChange={(e) => setRow(i, { reason: e.target.value })}
              />
            )}
          </div>
        ))}
        <TextAreaField label="Remarks (optional)" value={remarks} maxLength={500} onChange={(e) => setRemarks(e.target.value)} />
        {review.error && <ErrorCallout error={review.error} />}
      </div>
      <Modal
        open={returning}
        title="Return to the employee"
        onClose={() => setReturning(false)}
        footer={
          <>
            <Button onClick={() => setReturning(false)}>Cancel</Button>
            <Button variant="primary" disabled={!returnText.trim()} loading={giveBack.isPending} onClick={() => giveBack.mutate()}>
              Return
            </Button>
          </>
        }
      >
        <TextAreaField label="What the employee must correct" required value={returnText} maxLength={500} onChange={(e) => setReturnText(e.target.value)} hint="For example: prescription is not legible, or the doctor's stamp is missing." />
        {giveBack.error && <ErrorCallout error={giveBack.error} />}
      </Modal>
    </Panel>
  )
}

function OfficerPanel({ nac, onDone, extra }: { nac: Nac; onDone: (m: string) => void; extra: React.ReactNode }) {
  const [signing, setSigning] = useState(false)
  const [sendingBack, setSendingBack] = useState(false)
  const [text, setText] = useState('')
  const counts = DECISIONS.map((d) => [d, nac.items.filter((i) => i.decision === d).length] as const)

  const sign = useMutation({
    mutationFn: (password: string) => api.post<Nac>(`/api/nac/${nac.id}/countersign`, { password, remarks: text || null }),
    onSuccess: (n) => {
      setSigning(false)
      onDone(`Certificate ${n.nacNumber} issued`)
    },
  })
  const back = useMutation({
    mutationFn: () => api.post<Nac>(`/api/nac/${nac.id}/send-back`, { remarks: text }),
    onSuccess: () => {
      setSendingBack(false)
      onDone('Sent back to the pharmacist')
    },
  })

  return (
    <Panel
      strong
      title="Countersign"
      kicker={`Decisions by ${nac.pharmacistName ?? 'the pharmacist'}`}
      footer={
        <>
          {extra}
          <Button variant="danger" onClick={() => setSendingBack(true)}>
            Send back to pharmacist
          </Button>
          <Button variant="primary" icon={<Icon.Check />} onClick={() => setSigning(true)}>
            Countersign and issue
          </Button>
        </>
      }
    >
      <div className="tiles" style={{ ['--cols-base' as string]: 3 }}>
        {counts.map(([d, n]) => (
          <div key={d} className="stat" style={{ minHeight: 96 }}>
            <span className="caps stat-label">{DECISION_LABELS[d]}</span>
            <span className="stat-value">{n}</span>
          </div>
        ))}
      </div>
      {nac.pharmacistRemarks && (
        <p style={{ marginTop: 16 }}>
          <span className="caps muted">Pharmacist remarks: </span>
          {nac.pharmacistRemarks}
        </p>
      )}
      <PasswordConfirm open={signing} title="Countersign the e-NAC" confirmLabel="Countersign" busy={sign.isPending} onCancel={() => setSigning(false)} onConfirm={(p) => sign.mutate(p)}>
        <p>You confirm the pharmacist's decisions for all {nac.items.length} items. Your name will appear on the certificate.</p>
        <TextAreaField label="Remarks (optional)" value={text} maxLength={500} onChange={(e) => setText(e.target.value)} />
        {sign.error && <ErrorCallout error={sign.error} />}
      </PasswordConfirm>
      <Modal
        open={sendingBack}
        title="Send back to the pharmacist"
        onClose={() => setSendingBack(false)}
        footer={
          <>
            <Button onClick={() => setSendingBack(false)}>Cancel</Button>
            <Button variant="primary" disabled={!text.trim()} loading={back.isPending} onClick={() => back.mutate()}>
              Send back
            </Button>
          </>
        }
      >
        <TextAreaField label="Which decisions need another look" required value={text} maxLength={500} onChange={(e) => setText(e.target.value)} />
        {back.error && <ErrorCallout error={back.error} />}
      </Modal>
    </Panel>
  )
}
