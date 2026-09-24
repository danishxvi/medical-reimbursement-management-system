import { useQuery, useQueryClient } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'motion/react'
import { useEffect, useMemo, useState } from 'react'
import { useFieldArray, useForm } from 'react-hook-form'
import { useNavigate, useParams } from 'react-router'
import { api } from '../../api/client'
import type {
  Claim,
  ClaimInput,
  ClaimMeta,
  ClaimableNacItem,
  DocumentCategory,
  DocumentMeta,
  EmployeeProfile,
  HospitalType,
  ItemCategory,
  TreatmentType,
} from '../../api/types'
import { Button } from '../../components/ui/Button'
import { DataTable } from '../../components/ui/DataTable'
import { Callout, ErrorCallout, PageSkeleton } from '../../components/ui/Feedback'
import { DocumentChip, FileUpload } from '../../components/ui/Files'
import { Checkbox, Segmented, SelectField, TextAreaField, TextField } from '../../components/ui/Form'
import { Details, Page, PageHeader, Panel } from '../../components/ui/Layout'
import { Modal } from '../../components/ui/Modal'
import { useToast } from '../../components/ui/Toast'
import { RELATION_LABELS, daysAgoIso, formatDate, formatMoney, todayIso } from '../../lib/format'
import { Icon } from '../../lib/icons'
import { ease } from '../../lib/motion'

// ----------------------------------------------------------------------
// Form model
// ----------------------------------------------------------------------

interface ItemForm {
  category: ItemCategory
  description: string
  billNumber: string
  billDate: string
  vendorName: string
  dgehsCode: string
  amountClaimed: string
  nacItemId: string
  legacyNac: boolean
  billDocumentId: string
}

interface FormValues {
  dependentId: string
  illnessDescription: string
  treatmentType: TreatmentType
  treatmentFrom: string
  treatmentTo: string
  admissionDate: string
  dischargeDate: string
  hospitalName: string
  hospitalAddress: string
  hospitalType: HospitalType
  emergency: boolean
  referralDetails: string
  medicalAdvanceDetails: string
  items: ItemForm[]
}

const EMPTY_ITEM: ItemForm = {
  category: 'MEDICINE',
  description: '',
  billNumber: '',
  billDate: '',
  vendorName: '',
  dgehsCode: '',
  amountClaimed: '',
  nacItemId: '',
  legacyNac: false,
  billDocumentId: '',
}

function fromClaim(c: Claim): FormValues {
  return {
    dependentId: c.dependentId ? String(c.dependentId) : '',
    illnessDescription: c.illnessDescription,
    treatmentType: c.treatmentType,
    treatmentFrom: c.treatmentFrom,
    treatmentTo: c.treatmentTo,
    admissionDate: c.admissionDate ?? '',
    dischargeDate: c.dischargeDate ?? '',
    hospitalName: c.hospitalName,
    hospitalAddress: c.hospitalAddress ?? '',
    hospitalType: c.hospitalType,
    emergency: c.emergency,
    referralDetails: c.referralDetails ?? '',
    medicalAdvanceDetails: c.medicalAdvanceDetails ?? '',
    items: c.items.map((i) => ({
      category: i.category,
      description: i.description,
      billNumber: i.billNumber,
      billDate: i.billDate,
      vendorName: i.vendorName,
      dgehsCode: i.dgehsCode ?? '',
      amountClaimed: String(i.amountClaimed),
      nacItemId: i.nacItemId ? String(i.nacItemId) : '',
      legacyNac: i.legacyNac,
      billDocumentId: i.billDocument?.id ?? '',
    })),
  }
}

function toInput(v: FormValues, attachments: { documentId: string; category: DocumentCategory }[]): ClaimInput {
  const blank = (s: string) => (s.trim() === '' ? null : s.trim())
  return {
    dependentId: v.dependentId ? Number(v.dependentId) : null,
    illnessDescription: v.illnessDescription.trim(),
    treatmentType: v.treatmentType,
    treatmentFrom: v.treatmentFrom,
    treatmentTo: v.treatmentTo,
    admissionDate: v.treatmentType === 'INDOOR' ? blank(v.admissionDate) : null,
    dischargeDate: v.treatmentType === 'INDOOR' ? blank(v.dischargeDate) : null,
    hospitalName: v.hospitalName.trim(),
    hospitalAddress: blank(v.hospitalAddress),
    hospitalType: v.hospitalType,
    emergency: v.emergency,
    referralDetails: blank(v.referralDetails),
    medicalAdvanceDetails: blank(v.medicalAdvanceDetails),
    // Only complete rows are saved; half filled rows stay in the form until finished
    items: v.items
      .filter(
        (i) =>
          i.billDocumentId &&
          i.description.trim() &&
          i.billNumber.trim() &&
          i.billDate &&
          i.vendorName.trim() &&
          Number(i.amountClaimed) > 0,
      )
      .map((i) => ({
        category: i.category,
        description: i.description.trim(),
        billNumber: i.billNumber.trim(),
        billDate: i.billDate,
        vendorName: i.vendorName.trim(),
        dgehsCode: blank(i.dgehsCode),
        amountClaimed: Number(i.amountClaimed),
        nacItemId: i.category === 'MEDICINE' && i.nacItemId ? Number(i.nacItemId) : null,
        legacyNac: i.category === 'MEDICINE' && !i.nacItemId && i.legacyNac,
        billDocumentId: i.billDocumentId,
      })),
    attachments,
  }
}

const STEPS = ['Patient and treatment', 'Bills', 'Documents', 'Review and submit'] as const

// ----------------------------------------------------------------------
// Page
// ----------------------------------------------------------------------

export default function ClaimEditorPage() {
  const { id } = useParams()
  const editing = !!id
  const existing = useQuery({ queryKey: ['claim', id], queryFn: () => api.get<Claim>('/api/claims/' + id), enabled: editing })
  const profile = useQuery({ queryKey: ['profile'], queryFn: () => api.get<EmployeeProfile>('/api/profile') })
  const meta = useQuery({ queryKey: ['claim-meta'], queryFn: () => api.get<ClaimMeta>('/api/claims/meta'), staleTime: Infinity })

  if ((editing && existing.isLoading) || profile.isLoading || meta.isLoading) {
    return (
      <Page>
        <PageSkeleton />
      </Page>
    )
  }
  if (!profile.data || !meta.data || (editing && !existing.data)) {
    return (
      <Page>
        <PageHeader title="Cannot open the claim form" />
        <ErrorCallout error={existing.error ?? profile.error ?? meta.error} />
      </Page>
    )
  }
  if (existing.data && !existing.data.allowedActions.includes('EDIT')) {
    return (
      <Page>
        <PageHeader title="This claim can no longer be edited" description="It has been submitted and is with a reviewer." />
      </Page>
    )
  }
  return <Editor claim={existing.data ?? null} profile={profile.data} meta={meta.data} />
}

function Editor({ claim, profile, meta }: { claim: Claim | null; profile: EmployeeProfile; meta: ClaimMeta }) {
  const navigate = useNavigate()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [step, setStep] = useState(0)
  const [claimId, setClaimId] = useState<number | null>(claim?.id ?? null)
  const [saved, setSaved] = useState<Claim | null>(claim)
  const [busy, setBusy] = useState<'save' | 'submit' | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [accepted, setAccepted] = useState(false)
  const [nacPicker, setNacPicker] = useState(false)

  // Documents already known (to show names) and the non bill attachments
  const [docs, setDocs] = useState<Record<string, DocumentMeta>>(() => {
    const map: Record<string, DocumentMeta> = {}
    claim?.items.forEach((i) => i.billDocument && (map[i.billDocument.id] = i.billDocument))
    claim?.attachments.forEach((a) => a.document && (map[a.document.id] = a.document))
    return map
  })
  const [attachments, setAttachments] = useState<{ documentId: string; category: DocumentCategory }[]>(
    () => claim?.attachments.filter((a) => a.document).map((a) => ({ documentId: a.document!.id, category: a.category })) ?? [],
  )

  const form = useForm<FormValues>({
    mode: 'onTouched',
    defaultValues: claim
      ? fromClaim(claim)
      : {
          dependentId: '',
          illnessDescription: '',
          treatmentType: 'OPD',
          treatmentFrom: daysAgoIso(7),
          treatmentTo: todayIso(),
          admissionDate: '',
          dischargeDate: '',
          hospitalName: '',
          hospitalAddress: '',
          hospitalType: 'GOVERNMENT',
          emergency: false,
          referralDetails: '',
          medicalAdvanceDetails: '',
          items: [],
        },
  })
  const { register, control, watch, setValue, trigger, getValues, formState } = form
  const items = useFieldArray({ control, name: 'items' })
  const values = watch()

  // e-NAC items this employee can still claim, plus those already linked here
  const claimable = useQuery({ queryKey: ['claimable-nac'], queryFn: () => api.get<ClaimableNacItem[]>('/api/claims/claimable-nac-items') })
  const nacOptions = useMemo(() => {
    const map = new Map<number, { id: number; label: string; dependentId: number | null }>()
    claim?.items.forEach(
      (i) =>
        i.nac &&
        i.nacItemId &&
        map.set(i.nacItemId, { id: i.nacItemId, label: `${i.nac.itemName} (${i.nac.nacNumber})`, dependentId: claim.dependentId }),
    )
    claimable.data?.forEach((c) => map.set(c.nacItemId, { id: c.nacItemId, label: `${c.itemName} (${c.nacNumber})`, dependentId: c.dependentId }))
    return [...map.values()]
  }, [claim, claimable.data])
  const patientDependent = values.dependentId ? Number(values.dependentId) : null
  const nacForPatient = nacOptions.filter((o) => o.dependentId === patientDependent)

  const total = values.items.reduce((acc, i) => acc + (Number(i.amountClaimed) || 0), 0)
  const hasEnac = values.items.some((i) => i.category === 'MEDICINE' && i.nacItemId)
  const hasLegacy = values.items.some((i) => i.category === 'MEDICINE' && !i.nacItemId && i.legacyNac)

  // Leaving with unsaved changes asks for confirmation
  useEffect(() => {
    const handler = (e: BeforeUnloadEvent) => {
      if (formState.isDirty && busy === null) e.preventDefault()
    }
    window.addEventListener('beforeunload', handler)
    return () => window.removeEventListener('beforeunload', handler)
  }, [formState.isDirty, busy])

  // ------------------------------------------------------------------
  // Actions
  // ------------------------------------------------------------------

  async function save(quiet = false): Promise<Claim | null> {
    setError(null)
    setBusy('save')
    try {
      const body = toInput(getValues(), attachments)
      const result = claimId ? await api.put<Claim>('/api/claims/' + claimId, body) : await api.post<Claim>('/api/claims', body)
      setClaimId(result.id)
      setSaved(result)
      form.reset(getValues())
      queryClient.setQueryData(['claim', String(result.id)], result)
      queryClient.invalidateQueries({ queryKey: ['claims'] })
      // Point the address bar at the draft (so a reload reopens it) without a
      // route change, which would remount the form and lose the current step
      if (!claimId) window.history.replaceState(window.history.state, '', `/claims/${result.id}/edit`)
      if (!quiet) toast.ok('Draft saved')
      return result
    } catch (e) {
      setError(e)
      return null
    } finally {
      setBusy(null)
    }
  }

  async function submit() {
    const draft = await save(true)
    if (!draft) return
    setBusy('submit')
    try {
      const result = await api.post<Claim>(`/api/claims/${draft.id}/submit`, { undertakingAccepted: accepted })
      queryClient.invalidateQueries({ queryKey: ['claims'] })
      queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      queryClient.setQueryData(['claim', String(result.id)], result)
      toast.ok(`Claim ${result.claimNumber} submitted to your Head of School`)
      navigate('/claims/' + result.id, { replace: true })
    } catch (e) {
      setError(e)
    } finally {
      setBusy(null)
    }
  }

  const STEP_FIELDS: (keyof FormValues | `items.${number}.${keyof ItemForm}`)[][] = [
    ['illnessDescription', 'treatmentFrom', 'treatmentTo', 'hospitalName', 'admissionDate', 'dischargeDate'],
    values.items.flatMap((_, i) =>
      (['description', 'billNumber', 'billDate', 'vendorName', 'amountClaimed', 'billDocumentId'] as const).map((f) => `items.${i}.${f}` as const),
    ),
    [],
    [],
  ]

  async function go(next: number) {
    if (next > step) {
      const ok = await trigger(STEP_FIELDS[step] as Parameters<typeof trigger>[0])
      if (!ok) {
        toast.error('Some fields need attention before you continue')
        return
      }
      if (step === 1 && values.items.length === 0) {
        toast.error('Add at least one bill')
        return
      }
    }
    if (next === 3) {
      const draft = await save(true)
      if (!draft) return
    }
    setStep(next)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  function addFromNac(selected: ClaimableNacItem[]) {
    selected.forEach((s) =>
      items.append({ ...EMPTY_ITEM, category: 'MEDICINE', description: s.itemName, nacItemId: String(s.nacItemId), billDate: '' }),
    )
    setNacPicker(false)
  }

  function addAttachment(doc: DocumentMeta) {
    setDocs((d) => ({ ...d, [doc.id]: doc }))
    setAttachments((a) => [...a, { documentId: doc.id, category: doc.category }])
  }

  // ------------------------------------------------------------------
  // Render
  // ------------------------------------------------------------------

  const isReturned = claim?.status === 'RETURNED_BY_HOS' || claim?.status === 'RETURNED_BY_PAO'
  const lastReturn = claim ? [...claim.timeline].reverse().find((t) => t.action.startsWith('RETURNED')) : undefined
  const dependents = profile.dependents.filter((d) => d.active)

  return (
    <Page>
      <PageHeader
        eyebrow={claim?.claimNumber ? 'Claim ' + claim.claimNumber : claimId ? 'Draft #' + claimId : 'New claim'}
        title={isReturned ? 'Correct and resubmit' : claimId ? 'Continue your claim' : 'File a medical reimbursement claim'}
        description="Your service and DGEHS details are filled in from your records. Save the draft at any time and come back later."
        actions={
          <>
            <Button icon={<Icon.Back />} onClick={() => navigate(claimId ? '/claims/' + claimId : '/claims')}>
              Close
            </Button>
            <Button loading={busy === 'save'} onClick={() => save()} disabled={!values.illnessDescription || !values.hospitalName}>
              Save draft
            </Button>
          </>
        }
      />

      {isReturned && lastReturn && (
        <div style={{ marginBottom: 24 }}>
          <Callout variant="stripe" title={`What to correct (returned by ${lastReturn.actorName})`}>
            {lastReturn.reasons.length > 0 && (
              <ul>
                {lastReturn.reasons.map((r) => (
                  <li key={r}>{r}</li>
                ))}
              </ul>
            )}
            {lastReturn.remarks && <p style={{ marginTop: 6 }}>{lastReturn.remarks}</p>}
          </Callout>
        </div>
      )}

      <div className="steps" style={{ ['--steps' as string]: STEPS.length }} role="tablist" aria-label="Claim steps">
        {STEPS.map((label, i) => (
          <button key={label} type="button" role="tab" aria-selected={step === i} className={'step' + (step === i ? ' active' : '')} onClick={() => i < step && go(i)}>
            {step === i && <motion.span layoutId="step-bg" className="step-bg" transition={{ duration: 0.3, ease }} />}
            <span className="n">{i < step ? <Icon.Check width={12} height={12} /> : i + 1}</span>
            <span>{label}</span>
          </button>
        ))}
      </div>

      <AnimatePresence mode="wait">
        <motion.div key={step} initial={{ opacity: 0, x: 16 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -16 }} transition={{ duration: 0.22, ease }}>
          {/* ---------------- Step 1 ---------------- */}
          {step === 0 && (
            <div className="stack-lg">
              <Panel title="Your details" kicker="Pre filled, read only">
                <Details
                  columns={3}
                  items={[
                    ['Name', profile.fullName],
                    ['Employee ID', profile.employeeCode],
                    ['Designation', profile.designation],
                    ['School', profile.school?.name ?? '-'],
                    ['DGEHS card', profile.dgehsCardNo ?? 'Missing: ask your office'],
                    ['Card valid until', formatDate(profile.dgehsValidTo)],
                  ]}
                />
              </Panel>
              <Panel title="Patient and treatment">
                <div className="grid grid-2">
                  <SelectField
                    label="Patient"
                    {...register('dependentId')}
                    options={[
                      { value: '', label: `${profile.fullName} (Self)` },
                      ...dependents.map((d) => ({ value: String(d.id), label: `${d.fullName} (${RELATION_LABELS[d.relation]})` })),
                    ]}
                    hint={dependents.length === 0 ? 'Add family members under Profile to claim for them.' : undefined}
                  />
                  <Segmented
                    name="treatmentType"
                    label="Treatment"
                    value={values.treatmentType}
                    options={meta.treatmentTypes.map((t) => ({ value: t.value as TreatmentType, label: t.label }))}
                    onChange={(v) => setValue('treatmentType', v, { shouldDirty: true })}
                  />
                  <TextAreaField
                    className="span-all"
                    label="Illness or diagnosis"
                    required
                    maxLength={500}
                    {...register('illnessDescription', { required: 'Describe the illness or diagnosis' })}
                    error={formState.errors.illnessDescription?.message}
                  />
                  <TextField
                    label="Treatment from"
                    type="date"
                    required
                    max={todayIso()}
                    {...register('treatmentFrom', { required: 'Required' })}
                    error={formState.errors.treatmentFrom?.message}
                  />
                  <TextField
                    label="Treatment to"
                    type="date"
                    required
                    max={todayIso()}
                    {...register('treatmentTo', {
                      required: 'Required',
                      validate: (v) => v >= getValues('treatmentFrom') || 'Cannot be before the start date',
                    })}
                    error={formState.errors.treatmentTo?.message}
                    hint={`Claims must be filed within ${meta.submissionWindowDays} days of the end of treatment.`}
                  />
                  {values.treatmentType === 'INDOOR' && (
                    <>
                      <TextField label="Date of admission" type="date" required {...register('admissionDate', { required: values.treatmentType === 'INDOOR' ? 'Required for indoor treatment' : false })} error={formState.errors.admissionDate?.message} />
                      <TextField label="Date of discharge" type="date" required {...register('dischargeDate', { required: values.treatmentType === 'INDOOR' ? 'Required for indoor treatment' : false })} error={formState.errors.dischargeDate?.message} />
                    </>
                  )}
                  <TextField
                    label="Hospital or dispensary"
                    required
                    maxLength={200}
                    {...register('hospitalName', { required: 'Name the treating hospital' })}
                    error={formState.errors.hospitalName?.message}
                  />
                  <SelectField label="Type of hospital" {...register('hospitalType')} options={meta.hospitalTypes} />
                  <TextField className="span-all" label="Hospital address" maxLength={300} {...register('hospitalAddress')} />
                  <TextField label="Referral details" hint="Referral or authorisation from the AMA, if any" maxLength={300} {...register('referralDetails')} />
                  <TextField label="Medical advance taken" hint="Amount and date, if any" maxLength={300} {...register('medicalAdvanceDetails')} />
                  <div className="span-all">
                    <Checkbox label="This was an emergency (an emergency certificate from the hospital will be needed)" {...register('emergency')} />
                  </div>
                </div>
              </Panel>
            </div>
          )}

          {/* ---------------- Step 2 ---------------- */}
          {step === 1 && (
            <div className="stack">
              <Callout title="Medicines need an e-NAC">
                <p>
                  For OPD medicines, link each bill to an item the dispensary marked <strong>not available</strong> on your e-NAC.
                  If you only have an old paper certificate, tick the paper NAC box and attach its scan in the next step.
                </p>
              </Callout>
              <div className="row">
                <Button icon={<Icon.Plus />} onClick={() => items.append({ ...EMPTY_ITEM, category: 'CONSULTATION' })}>
                  Add a bill
                </Button>
                <Button icon={<Icon.Pill />} onClick={() => setNacPicker(true)} disabled={nacForPatient.length === 0}>
                  Add medicines from e-NAC ({claimable.data?.filter((c) => c.dependentId === patientDependent).length ?? 0} available)
                </Button>
              </div>

              <AnimatePresence initial={false}>
                {items.fields.map((field, index) => {
                  const it = values.items[index]
                  const err = formState.errors.items?.[index]
                  return (
                    <motion.div key={field.id} layout initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, x: -24 }} transition={{ duration: 0.22, ease }}>
                      <Panel
                        strong
                        title={`Bill ${index + 1}`}
                        kicker={it?.amountClaimed ? formatMoney(Number(it.amountClaimed)) : undefined}
                        actions={
                          <Button size="sm" variant="ghost" icon={<Icon.Trash />} onClick={() => items.remove(index)}>
                            Remove
                          </Button>
                        }
                      >
                        <div className="grid grid-3">
                          <SelectField label="Category" {...register(`items.${index}.category`)} options={meta.itemCategories} />
                          <TextField
                            className="span-2"
                            label="Description"
                            required
                            maxLength={200}
                            placeholder="Medicine name, test or consultation"
                            {...register(`items.${index}.description`, { required: 'Required' })}
                            error={err?.description?.message}
                          />
                          <TextField label="Bill / receipt number" required maxLength={50} {...register(`items.${index}.billNumber`, { required: 'Required' })} error={err?.billNumber?.message} />
                          <TextField
                            label="Bill date"
                            type="date"
                            required
                            min={values.treatmentFrom}
                            max={values.treatmentTo}
                            {...register(`items.${index}.billDate`, {
                              required: 'Required',
                              validate: (v) => (v >= values.treatmentFrom && v <= values.treatmentTo) || 'Must fall within the treatment period',
                            })}
                            error={err?.billDate?.message}
                          />
                          <TextField
                            label="Amount (Rs)"
                            type="number"
                            inputMode="decimal"
                            step="0.01"
                            min="0.01"
                            required
                            {...register(`items.${index}.amountClaimed`, {
                              required: 'Required',
                              validate: (v) => (Number(v) > 0 && /^\d+(\.\d{1,2})?$/.test(v)) || 'Enter an amount like 450 or 450.50',
                            })}
                            error={err?.amountClaimed?.message}
                          />
                          <TextField label="Chemist / lab / hospital" required maxLength={150} {...register(`items.${index}.vendorName`, { required: 'Required' })} error={err?.vendorName?.message} />
                          <TextField label="DGEHS code" hint="If printed on the bill" maxLength={30} {...register(`items.${index}.dgehsCode`)} />
                          {it?.category === 'MEDICINE' && (
                            <SelectField
                              label="Linked e-NAC item"
                              {...register(`items.${index}.nacItemId`)}
                              placeholder={nacForPatient.length ? 'Select the certificate item' : 'No e-NAC items for this patient'}
                              options={nacForPatient.map((o) => ({ value: String(o.id), label: o.label }))}
                            />
                          )}
                          {it?.category === 'MEDICINE' && !it.nacItemId && (
                            <div style={{ alignSelf: 'end' }}>
                              <Checkbox label="I have a paper NAC instead" {...register(`items.${index}.legacyNac`)} />
                            </div>
                          )}
                        </div>
                        <input type="hidden" {...register(`items.${index}.billDocumentId`, { required: 'Upload the bill' })} />
                        <div style={{ marginTop: 16 }}>
                          {it?.billDocumentId && docs[it.billDocumentId] ? (
                            <DocumentChip doc={docs[it.billDocumentId]} onRemove={() => setValue(`items.${index}.billDocumentId`, '', { shouldDirty: true })} />
                          ) : (
                            <FileUpload
                              category="BILL"
                              compact
                              label="Upload the bill for this item"
                              onUploaded={(doc) => {
                                setDocs((d) => ({ ...d, [doc.id]: doc }))
                                setValue(`items.${index}.billDocumentId`, doc.id, { shouldDirty: true, shouldValidate: true })
                              }}
                            />
                          )}
                          {err?.billDocumentId && <span className="field-error" style={{ display: 'inline-block', marginTop: 8 }}>Upload the bill for this item</span>}
                        </div>
                      </Panel>
                    </motion.div>
                  )
                })}
              </AnimatePresence>

              {items.fields.length === 0 && (
                <div className="empty">
                  <strong style={{ color: 'var(--ink)' }}>No bills added yet</strong>
                  <p>Add one entry per bill or receipt. A single bill covering several medicines can be attached to each of them.</p>
                </div>
              )}
              <div className="row-between panel" style={{ padding: '16px 24px' }}>
                <span className="caps">Total claimed</span>
                <span className="big-number" style={{ fontSize: 'var(--text-2xl)' }}>
                  {formatMoney(total)}
                </span>
              </div>
            </div>
          )}

          {/* ---------------- Step 3 ---------------- */}
          {step === 2 && (
            <DocumentsStep
              meta={meta}
              attachments={attachments}
              docs={docs}
              onAdd={addAttachment}
              onRemove={(docId) => setAttachments((a) => a.filter((x) => x.documentId !== docId))}
              needs={{
                prescription: values.treatmentType === 'OPD' && !hasEnac,
                discharge: values.treatmentType === 'INDOOR',
                emergency: values.emergency,
                legacyNac: hasLegacy,
              }}
            />
          )}

          {/* ---------------- Step 4 ---------------- */}
          {step === 3 && saved && (
            <div className="stack-lg">
              <div className="grid grid-2">
                <Panel title="Total amount claimed" kicker="Annexure II, item 19" flush>
                  <DataTable
                    rows={Object.entries(saved.totalsByCategory)}
                    rowKey={([k]) => k}
                    columns={[
                      { key: 'k', header: 'Charges', render: ([k]) => meta.itemCategories.find((c) => c.value === k)?.label ?? k },
                      { key: 'o', header: 'OPD', align: 'right', render: ([, v]) => <span className="num">{formatMoney(v.OPD)}</span> },
                      { key: 'i', header: 'Indoor', align: 'right', render: ([, v]) => <span className="num">{formatMoney(v.INDOOR)}</span> },
                    ]}
                    footer={
                      <tr>
                        <td>Total</td>
                        <td className="right num" colSpan={2}>
                          {formatMoney(saved.claimedAmount)}
                        </td>
                      </tr>
                    }
                  />
                </Panel>
                <Panel title="Document check list" kicker="Annexure I">
                  <ul className="checklist" style={{ gridTemplateColumns: '1fr' }}>
                    {saved.checklist
                      .filter((c) => c.required || c.submitted)
                      .map((c) => (
                        <li key={c.code} className={c.submitted ? 'ok' : 'missing'}>
                          <span className="box">{c.submitted ? <Icon.Check width={12} height={12} /> : c.code}</span>
                          <span>
                            {c.label}
                            {!c.submitted && <strong> (missing)</strong>}
                          </span>
                        </li>
                      ))}
                  </ul>
                </Panel>
              </div>
              <Panel title="Undertaking" kicker="Read carefully. This replaces your signature." strong>
                <ol className="legal">
                  {meta.undertaking.map((u) => (
                    <li key={u}>{u}</li>
                  ))}
                </ol>
                <hr className="divider" />
                <Checkbox
                  checked={accepted}
                  onChange={(e) => setAccepted(e.target.checked)}
                  label={<strong>I, {profile.fullName}, have read and accept every statement above.</strong>}
                />
              </Panel>
            </div>
          )}
        </motion.div>
      </AnimatePresence>

      {error != null && (
        <div style={{ marginTop: 24 }}>
          <ErrorCallout error={error} />
        </div>
      )}

      <div className="row-between" style={{ marginTop: 32, paddingTop: 24, borderTop: 'var(--border-strong)' }}>
        <Button icon={<Icon.Back />} disabled={step === 0} onClick={() => go(step - 1)}>
          Previous
        </Button>
        <span className="caps muted">
          Step {step + 1} of {STEPS.length}
        </span>
        {step < 3 ? (
          <Button variant="primary" icon={<Icon.Arrow />} loading={busy === 'save'} onClick={() => go(step + 1)}>
            {step === 2 ? 'Review' : 'Next'}
          </Button>
        ) : (
          <Button variant="primary" icon={<Icon.Check />} loading={busy === 'submit'} disabled={!accepted} onClick={submit}>
            {isReturned ? 'Resubmit claim' : 'Submit claim'}
          </Button>
        )}
      </div>

      <NacPicker
        open={nacPicker}
        onClose={() => setNacPicker(false)}
        options={(claimable.data ?? []).filter(
          (c) => c.dependentId === patientDependent && !values.items.some((i) => i.nacItemId === String(c.nacItemId)),
        )}
        onPick={addFromNac}
      />
    </Page>
  )
}

// ----------------------------------------------------------------------
// Documents step
// ----------------------------------------------------------------------

interface DocumentsStepProps {
  meta: ClaimMeta
  attachments: { documentId: string; category: DocumentCategory }[]
  docs: Record<string, DocumentMeta>
  onAdd: (doc: DocumentMeta) => void
  onRemove: (docId: string) => void
  needs: { prescription: boolean; discharge: boolean; emergency: boolean; legacyNac: boolean }
}

function DocumentsStep({ meta, attachments, docs, onAdd, onRemove, needs }: DocumentsStepProps) {
  const required: { category: DocumentCategory; why: string }[] = [
    { category: 'DGEHS_CARD', why: 'Always required' },
    ...(needs.prescription ? [{ category: 'PRESCRIPTION' as DocumentCategory, why: 'No e-NAC is linked, so attach the prescription' }] : []),
    ...(needs.discharge ? [{ category: 'DISCHARGE_SUMMARY' as DocumentCategory, why: 'Required for indoor treatment' }] : []),
    ...(needs.emergency ? [{ category: 'EMERGENCY_CERTIFICATE' as DocumentCategory, why: 'You marked the treatment as an emergency' }] : []),
    ...(needs.legacyNac ? [{ category: 'NAC_SCAN' as DocumentCategory, why: 'Some medicines use a paper NAC' }] : []),
  ]
  const [extra, setExtra] = useState<DocumentCategory>('REFERRAL')
  const labelOf = (c: DocumentCategory) => meta.documentCategories.find((d) => d.value === c)?.label ?? c
  const requiredSet = new Set(required.map((r) => r.category))
  const others = attachments.filter((a) => !requiredSet.has(a.category))

  return (
    <div className="stack-lg">
      <div className="grid grid-2">
        {required.map((r) => {
          const mine = attachments.filter((a) => a.category === r.category)
          return (
            <Panel key={r.category} title={labelOf(r.category)} kicker={r.why} strong={mine.length === 0}>
              <div className="stack" style={{ gap: 8 }}>
                {mine.map((a) => docs[a.documentId] && <DocumentChip key={a.documentId} doc={docs[a.documentId]} onRemove={() => onRemove(a.documentId)} />)}
                <FileUpload category={r.category} compact={mine.length > 0} label={mine.length ? 'Add another page' : undefined} onUploaded={onAdd} />
              </div>
            </Panel>
          )
        })}
      </div>
      <Panel title="Other supporting documents" kicker="Optional: referral, reports, breakups, cancelled cheque">
        <div className="grid grid-2">
          <SelectField
            label="Document type"
            value={extra}
            onChange={(e) => setExtra(e.target.value as DocumentCategory)}
            options={meta.documentCategories.filter((c) => !requiredSet.has(c.value as DocumentCategory))}
          />
          <FileUpload category={extra} compact onUploaded={onAdd} />
        </div>
        {others.length > 0 && (
          <div className="stack" style={{ gap: 8, marginTop: 16 }}>
            {others.map(
              (a) =>
                docs[a.documentId] && (
                  <div key={a.documentId}>
                    <div className="caps muted" style={{ marginBottom: 4 }}>
                      {labelOf(a.category)}
                    </div>
                    <DocumentChip doc={docs[a.documentId]} onRemove={() => onRemove(a.documentId)} />
                  </div>
                ),
            )}
          </div>
        )}
      </Panel>
    </div>
  )
}

// ----------------------------------------------------------------------
// e-NAC item picker
// ----------------------------------------------------------------------

function NacPicker({ open, onClose, options, onPick }: { open: boolean; onClose: () => void; options: ClaimableNacItem[]; onPick: (s: ClaimableNacItem[]) => void }) {
  const [picked, setPicked] = useState<number[]>([])
  const toggle = (id: number) => setPicked((p) => (p.includes(id) ? p.filter((x) => x !== id) : [...p, id]))
  return (
    <Modal
      open={open}
      title="Add medicines from your e-NAC"
      onClose={onClose}
      wide
      footer={
        <>
          <Button onClick={onClose}>Cancel</Button>
          <Button
            variant="primary"
            disabled={picked.length === 0}
            onClick={() => {
              onPick(options.filter((o) => picked.includes(o.nacItemId)))
              setPicked([])
            }}
          >
            Add {picked.length || ''} item{picked.length === 1 ? '' : 's'}
          </Button>
        </>
      }
    >
      {options.length === 0 ? (
        <p className="muted">There are no unclaimed e-NAC items for this patient.</p>
      ) : (
        <div className="stack" style={{ gap: 8 }}>
          {options.map((o) => (
            <label key={o.nacItemId} className="file-chip" style={{ cursor: 'pointer' }}>
              <span className="check">
                <input type="checkbox" checked={picked.includes(o.nacItemId)} onChange={() => toggle(o.nacItemId)} />
                <span>
                  <strong>{o.itemName}</strong> <span className="muted">({o.quantity})</span>
                  <br />
                  <span className="caps muted">
                    {o.nacNumber} · prescribed {formatDate(o.prescriptionDate)}
                  </span>
                </span>
              </span>
            </label>
          ))}
        </div>
      )}
    </Modal>
  )
}
