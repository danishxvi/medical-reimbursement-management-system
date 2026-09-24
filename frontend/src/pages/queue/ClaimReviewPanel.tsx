import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../../api/client'
import type { Claim, ClaimMeta } from '../../api/types'
import { useMe } from '../../auth/AuthContext'
import { Button } from '../../components/ui/Button'
import { Badge, Callout, ErrorCallout } from '../../components/ui/Feedback'
import { Checkbox, Segmented, TextAreaField } from '../../components/ui/Form'
import { Panel } from '../../components/ui/Layout'
import { Modal } from '../../components/ui/Modal'
import { PasswordConfirm } from '../../components/ui/PasswordConfirm'
import { formatMoney } from '../../lib/format'
import { Icon } from '../../lib/icons'

interface Props {
  claim: Claim
  meta: ClaimMeta
  onDone: (message: string) => void
  onRelease: () => void
}

/** Action area for the reviewer holding the claim. The server re-checks every rule. */
export function ClaimReviewPanel({ claim, meta, onDone, onRelease }: Props) {
  const me = useMe()
  const [returning, setReturning] = useState(false)

  const release = useMutation({
    mutationFn: () => api.post(`/api/claims/${claim.id}/release`),
    onSuccess: onRelease,
  })

  const common = (
    <>
      <Button icon={<Icon.Back />} loading={release.isPending} onClick={() => release.mutate()}>
        Release to queue
      </Button>
      {claim.allowedActions.includes('RETURN') && (
        <Button variant="danger" onClick={() => setReturning(true)}>
          Return for correction
        </Button>
      )}
    </>
  )

  return (
    <>
      {me.role === 'HOS' && <HosSheet claim={claim} meta={meta} onDone={onDone} extra={common} />}
      {me.role === 'PAO_AUDITOR' && <AuditSheet claim={claim} onDone={onDone} extra={common} />}
      {me.role === 'PAO_OFFICER' && <SanctionSheet claim={claim} onDone={onDone} extra={common} />}
      <ReturnModal open={returning} claim={claim} meta={meta} onClose={() => setReturning(false)} onDone={onDone} />
    </>
  )
}

// ----------------------------------------------------------------------
// Head of School: calculation sheet and certificate
// ----------------------------------------------------------------------

function HosSheet({ claim, meta, onDone, extra }: { claim: Claim; meta: ClaimMeta; onDone: (m: string) => void; extra: React.ReactNode }) {
  const [rows, setRows] = useState(() =>
    claim.items.map((i) => ({ itemId: i.id, dgehsRate: '', amountRestricted: String(i.amountClaimed), remarks: '' })),
  )
  const [accepted, setAccepted] = useState(false)
  const [signing, setSigning] = useState(false)
  const [remarks, setRemarks] = useState('')

  const forward = useMutation({
    mutationFn: (password: string) =>
      api.post<Claim>(`/api/claims/${claim.id}/forward`, {
        items: rows.map((r) => ({
          itemId: r.itemId,
          dgehsRate: r.dgehsRate ? Number(r.dgehsRate) : null,
          amountRestricted: Number(r.amountRestricted),
          remarks: r.remarks || null,
        })),
        certificateAccepted: accepted,
        password,
        remarks: remarks || null,
      }),
    onSuccess: (c) => {
      setSigning(false)
      onDone(`${c.claimNumber} verified and forwarded to the PAO`)
    },
    onError: () => setSigning(false),
  })

  const total = rows.reduce((a, r) => a + (Number(r.amountRestricted) || 0), 0)
  const invalid = rows.some((r, i) => {
    const v = Number(r.amountRestricted)
    return r.amountRestricted === '' || v < 0 || v > claim.items[i].amountClaimed || (v < claim.items[i].amountClaimed && !r.remarks.trim())
  })
  const update = (i: number, patch: Partial<(typeof rows)[number]>) => setRows((rs) => rs.map((r, j) => (j === i ? { ...r, ...patch } : r)))

  return (
    <Panel strong title="Calculation sheet and certificate" kicker="Restrict each bill to the DGEHS approved rate" footer={
      <>
        {extra}
        <Button variant="primary" icon={<Icon.Check />} disabled={invalid || !accepted} onClick={() => setSigning(true)}>
          Certify and forward to PAO
        </Button>
      </>
    }>
      <div className="stack-lg">
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Item</th>
                <th className="right">Claimed</th>
                <th>DGEHS rate</th>
                <th>Restricted claim</th>
                <th>Remarks</th>
              </tr>
            </thead>
            <tbody>
              {claim.items.map((it, i) => (
                <tr key={it.id}>
                  <td>
                    <strong>{it.description}</strong>
                    <div className="muted" style={{ fontSize: 'var(--text-xs)' }}>
                      {it.billNumber} · {it.vendorName}
                    </div>
                  </td>
                  <td className="right num">{formatMoney(it.amountClaimed)}</td>
                  <td style={{ width: 130 }}>
                    <input
                      className="control"
                      type="number"
                      step="0.01"
                      min="0"
                      aria-label={'DGEHS rate for ' + it.description}
                      value={rows[i].dgehsRate}
                      onChange={(e) => {
                        const rate = e.target.value
                        const restricted = rate ? String(Math.min(Number(rate), it.amountClaimed)) : String(it.amountClaimed)
                        update(i, { dgehsRate: rate, amountRestricted: restricted })
                      }}
                    />
                  </td>
                  <td style={{ width: 150 }}>
                    <input
                      className="control"
                      type="number"
                      step="0.01"
                      min="0"
                      max={it.amountClaimed}
                      aria-label={'Restricted amount for ' + it.description}
                      value={rows[i].amountRestricted}
                      onChange={(e) => update(i, { amountRestricted: e.target.value })}
                    />
                  </td>
                  <td>
                    <input
                      className="control"
                      maxLength={300}
                      aria-label={'Remarks for ' + it.description}
                      placeholder={Number(rows[i].amountRestricted) < it.amountClaimed ? 'Required when restricted' : ''}
                      value={rows[i].remarks}
                      onChange={(e) => update(i, { remarks: e.target.value })}
                    />
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr>
                <td>Total</td>
                <td className="right num">{formatMoney(claim.claimedAmount)}</td>
                <td />
                <td className="num">{formatMoney(total)}</td>
                <td />
              </tr>
            </tfoot>
          </table>
        </div>
        <div>
          <div className="caps muted" style={{ marginBottom: 8 }}>
            Certificate by HoS / HoO
          </div>
          <ol className="legal">
            {meta.hosCertificate.map((c) => (
              <li key={c}>{c}</li>
            ))}
          </ol>
          <div style={{ marginTop: 16 }}>
            <Checkbox checked={accepted} onChange={(e) => setAccepted(e.target.checked)} label={<strong>I have checked all the above facts and certify them.</strong>} />
          </div>
        </div>
        <TextAreaField label="Remarks for the PAO (optional)" value={remarks} maxLength={1000} onChange={(e) => setRemarks(e.target.value)} />
        {forward.error && <ErrorCallout error={forward.error} />}
      </div>
      <PasswordConfirm
        open={signing}
        title="Sign the certificate"
        confirmLabel="Certify and forward"
        busy={forward.isPending}
        onCancel={() => setSigning(false)}
        onConfirm={(p) => forward.mutate(p)}
      >
        <p>
          You are certifying claim <strong className="mono">{claim.claimNumber}</strong> for {formatMoney(total)}.
        </p>
      </PasswordConfirm>
    </Panel>
  )
}

// ----------------------------------------------------------------------
// PAO auditor: item level admission (maker)
// ----------------------------------------------------------------------

function AuditSheet({ claim, onDone, extra }: { claim: Claim; onDone: (m: string) => void; extra: React.ReactNode }) {
  const ceiling = (i: Claim['items'][number]) => i.amountRestricted ?? i.amountClaimed
  const [rows, setRows] = useState(() =>
    claim.items.map((i) => ({ itemId: i.id, amountAdmitted: String(i.amountAdmitted ?? ceiling(i)), disallowReason: i.disallowReason ?? '' })),
  )
  const [recommendation, setRecommendation] = useState<'SANCTION' | 'REJECT'>('SANCTION')
  const [remarks, setRemarks] = useState('')

  const submit = useMutation({
    mutationFn: () =>
      api.post<Claim>(`/api/claims/${claim.id}/audit`, {
        items: rows.map((r) => ({ itemId: r.itemId, amountAdmitted: Number(r.amountAdmitted), disallowReason: r.disallowReason || null })),
        recommendation,
        remarks: remarks || null,
      }),
    onSuccess: (c) => onDone(`${c.claimNumber} sent to the PAO officer for ${recommendation === 'SANCTION' ? 'sanction' : 'rejection'}`),
  })

  const update = (i: number, patch: Partial<(typeof rows)[number]>) => setRows((rs) => rs.map((r, j) => (j === i ? { ...r, ...patch } : r)))
  const total = rows.reduce((a, r) => a + (Number(r.amountAdmitted) || 0), 0)
  const invalid =
    rows.some((r, i) => {
      const v = Number(r.amountAdmitted)
      const max = ceiling(claim.items[i])
      return r.amountAdmitted === '' || v < 0 || v > max || (v < max && !r.disallowReason.trim())
    }) ||
    (recommendation === 'REJECT' && !remarks.trim()) ||
    (recommendation === 'SANCTION' && total <= 0)

  return (
    <Panel strong title="Scrutiny" kicker="Admit each item; give a reason for any amount disallowed" footer={
      <>
        {extra}
        <Button variant="primary" icon={<Icon.Arrow />} disabled={invalid} loading={submit.isPending} onClick={() => submit.mutate()}>
          Send to officer
        </Button>
      </>
    }>
      <div className="stack-lg">
        {claim.items.some((i) => i.legacyNac) && (
          <Callout variant="dashed" title="Paper NAC used">
            <p>Some medicines rely on a scanned paper certificate. Check it against the prescription before admitting.</p>
          </Callout>
        )}
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Item</th>
                <th>Evidence</th>
                <th className="right">School restricted</th>
                <th>Admitted</th>
                <th>Reason if disallowed</th>
              </tr>
            </thead>
            <tbody>
              {claim.items.map((it, i) => (
                <tr key={it.id}>
                  <td>
                    <strong>{it.description}</strong>
                    <div className="muted" style={{ fontSize: 'var(--text-xs)' }}>
                      {it.category} · {it.billNumber}
                    </div>
                  </td>
                  <td>
                    {it.nac ? <Badge tone="done">e-NAC {it.nac.decision === 'NOT_AVAILABLE' ? 'ok' : it.nac.decision}</Badge> : it.legacyNac ? <Badge tone="attention">Paper NAC</Badge> : <span className="muted">Bill only</span>}
                  </td>
                  <td className="right num">{formatMoney(ceiling(it))}</td>
                  <td style={{ width: 150 }}>
                    <input className="control" type="number" step="0.01" min="0" max={ceiling(it)} aria-label={'Admitted amount for ' + it.description} value={rows[i].amountAdmitted} onChange={(e) => update(i, { amountAdmitted: e.target.value })} />
                  </td>
                  <td>
                    <input className="control" maxLength={300} aria-label={'Reason for ' + it.description} value={rows[i].disallowReason} onChange={(e) => update(i, { disallowReason: e.target.value })} />
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr>
                <td colSpan={2}>Total</td>
                <td className="right num">{formatMoney(claim.restrictedAmount ?? claim.claimedAmount)}</td>
                <td className="num">{formatMoney(total)}</td>
                <td />
              </tr>
            </tfoot>
          </table>
        </div>
        <div className="grid grid-2">
          <Segmented
            name="recommendation"
            label="Recommendation"
            value={recommendation}
            options={[
              { value: 'SANCTION', label: 'Recommend sanction' },
              { value: 'REJECT', label: 'Recommend rejection' },
            ]}
            onChange={setRecommendation}
          />
          <TextAreaField label={recommendation === 'REJECT' ? 'Grounds for rejection (required)' : 'Remarks (optional)'} value={remarks} maxLength={1000} onChange={(e) => setRemarks(e.target.value)} />
        </div>
        <Callout title="Minor problems are not grounds for rejection">
          <p>If something can be fixed by the employee, use "Return for correction". The claim keeps its place in every queue.</p>
        </Callout>
        {submit.error && <ErrorCallout error={submit.error} />}
      </div>
    </Panel>
  )
}

// ----------------------------------------------------------------------
// PAO officer: sanction (checker)
// ----------------------------------------------------------------------

function SanctionSheet({ claim, onDone, extra }: { claim: Claim; onDone: (m: string) => void; extra: React.ReactNode }) {
  const [mode, setMode] = useState<'SANCTION' | 'REJECT' | 'SEND_BACK' | null>(null)
  const [text, setText] = useState('')

  const act = useMutation({
    mutationFn: ({ password }: { password?: string }) => {
      if (mode === 'SANCTION') return api.post<Claim>(`/api/claims/${claim.id}/sanction`, { password, remarks: text || null })
      if (mode === 'REJECT') return api.post<Claim>(`/api/claims/${claim.id}/reject`, { password, reason: text })
      return api.post<Claim>(`/api/claims/${claim.id}/send-back`, { remarks: text })
    },
    onSuccess: (c) => {
      const m = mode
      setMode(null)
      setText('')
      onDone(m === 'SANCTION' ? `${c.claimNumber} sanctioned` : m === 'REJECT' ? `${c.claimNumber} rejected` : `${c.claimNumber} sent back to the auditor`)
    },
  })

  return (
    <Panel strong title="Sanction" kicker={`Scrutinised by ${claim.auditedBy ?? '-'}; you cannot sanction your own scrutiny`} footer={
      <>
        {extra}
        <Button onClick={() => setMode('SEND_BACK')}>Send back to auditor</Button>
        <Button variant="danger" onClick={() => setMode('REJECT')}>
          Reject
        </Button>
        <Button variant="primary" icon={<Icon.Check />} disabled={!claim.admittedAmount} onClick={() => setMode('SANCTION')}>
          Sanction {formatMoney(claim.admittedAmount)}
        </Button>
      </>
    }>
      <div className="grid grid-3">
        <div>
          <div className="caps muted">Auditor recommends</div>
          <div style={{ marginTop: 8 }}>
            <Badge tone={claim.auditRecommendation === 'SANCTION' ? 'done' : 'attention'}>{claim.auditRecommendation ?? '-'}</Badge>
          </div>
        </div>
        <div>
          <div className="caps muted">Admitted amount</div>
          <div className="big-number" style={{ fontSize: 'var(--text-2xl)', marginTop: 8 }}>
            {formatMoney(claim.admittedAmount)}
          </div>
        </div>
        <div>
          <div className="caps muted">Latest remark</div>
          <p style={{ marginTop: 8 }}>{[...claim.timeline].reverse().find((t) => t.remarks)?.remarks ?? '-'}</p>
        </div>
      </div>

      <PasswordConfirm
        open={mode === 'SANCTION'}
        title="Sanction this claim"
        confirmLabel="Sanction"
        busy={act.isPending}
        onCancel={() => setMode(null)}
        onConfirm={(password) => act.mutate({ password })}
      >
        <p>
          Sanction <strong>{formatMoney(claim.admittedAmount)}</strong> for claim <span className="mono">{claim.claimNumber}</span>. It will be paid oldest first when funds are available.
        </p>
        {act.error && <ErrorCallout error={act.error} />}
      </PasswordConfirm>

      <PasswordConfirm
        open={mode === 'REJECT'}
        title="Reject this claim"
        confirmLabel="Reject claim"
        busy={act.isPending}
        onCancel={() => setMode(null)}
        onConfirm={(password) => (text.trim() ? act.mutate({ password }) : undefined)}
      >
        <TextAreaField label="Reason (shown to the employee)" required value={text} maxLength={1000} onChange={(e) => setText(e.target.value)} />
        {act.error && <ErrorCallout error={act.error} />}
      </PasswordConfirm>

      <Modal
        open={mode === 'SEND_BACK'}
        title="Send back to the auditor"
        onClose={() => setMode(null)}
        footer={
          <>
            <Button onClick={() => setMode(null)}>Cancel</Button>
            <Button variant="primary" disabled={!text.trim()} loading={act.isPending} onClick={() => act.mutate({})}>
              Send back
            </Button>
          </>
        }
      >
        <TextAreaField label="What should be looked at again" required value={text} maxLength={1000} onChange={(e) => setText(e.target.value)} />
        {act.error && <ErrorCallout error={act.error} />}
      </Modal>
    </Panel>
  )
}

// ----------------------------------------------------------------------
// Return for correction (HoS and PAO auditor)
// ----------------------------------------------------------------------

function ReturnModal({ open, claim, meta, onClose, onDone }: { open: boolean; claim: Claim; meta: ClaimMeta; onClose: () => void; onDone: (m: string) => void }) {
  const [reasons, setReasons] = useState<string[]>([])
  const [remarks, setRemarks] = useState('')
  const send = useMutation({
    mutationFn: () => api.post<Claim>(`/api/claims/${claim.id}/return`, { reasons, remarks }),
    onSuccess: (c) => {
      onClose()
      setReasons([])
      setRemarks('')
      onDone(`${c.claimNumber} returned to the employee for correction`)
    },
  })
  const toggle = (r: string) => setReasons((rs) => (rs.includes(r) ? rs.filter((x) => x !== r) : [...rs, r]))
  return (
    <Modal
      open={open}
      title="Return for correction"
      onClose={onClose}
      wide
      footer={
        <>
          <Button onClick={onClose}>Cancel</Button>
          <Button variant="primary" disabled={reasons.length === 0 || !remarks.trim()} loading={send.isPending} onClick={() => send.mutate()}>
            Return to employee
          </Button>
        </>
      }
    >
      <div className="stack">
        <p className="muted">The employee corrects only what you flag and resubmits. The claim keeps its original place in the queue.</p>
        <div className="grid grid-2" style={{ gap: 8 }}>
          {meta.returnReasons.map((r) => (
            <Checkbox key={r.value} checked={reasons.includes(r.value)} onChange={() => toggle(r.value)} label={r.label} />
          ))}
        </div>
        <TextAreaField label="Exactly what to correct" required value={remarks} maxLength={1000} onChange={(e) => setRemarks(e.target.value)} />
        {send.error && <ErrorCallout error={send.error} />}
      </div>
    </Modal>
  )
}
