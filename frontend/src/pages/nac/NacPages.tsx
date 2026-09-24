import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'motion/react'
import { useState } from 'react'
import { useFieldArray, useForm } from 'react-hook-form'
import { useNavigate, useParams } from 'react-router'
import { api } from '../../api/client'
import type { DocumentMeta, EmployeeProfile, Nac, NacItemType, NacSummary, OfficeRef } from '../../api/types'
import { NacDetails, NacTable, NacTracker } from '../../components/nac/NacParts'
import { Button } from '../../components/ui/Button'
import { Callout, EmptyState, ErrorCallout, PageSkeleton } from '../../components/ui/Feedback'
import { DocumentChip, FileUpload } from '../../components/ui/Files'
import { SelectField, TextField } from '../../components/ui/Form'
import { Page, PageHeader, Panel } from '../../components/ui/Layout'
import { useToast } from '../../components/ui/Toast'
import { RELATION_LABELS, todayIso } from '../../lib/format'
import { Icon } from '../../lib/icons'
import { ease } from '../../lib/motion'

// ======================================================================
// List
// ======================================================================

export function NacListPage() {
  const navigate = useNavigate()
  const { data, isLoading } = useQuery({ queryKey: ['nac', 'mine'], queryFn: () => api.get<NacSummary[]>('/api/nac/mine') })
  return (
    <Page>
      <PageHeader
        eyebrow="Dispensary"
        title="e-NAC certificates"
        description="The electronic non availability certificate replaces the paper stamp. Items marked not available can be bought and claimed."
        actions={
          <Button variant="primary" icon={<Icon.Plus />} onClick={() => navigate('/nac/new')}>
            Request e-NAC
          </Button>
        }
      />
      {isLoading ? (
        <PageSkeleton />
      ) : data && data.length > 0 ? (
        <Panel flush>
          <NacTable rows={data} />
        </Panel>
      ) : (
        <EmptyState title="No certificates yet" action={<Button onClick={() => navigate('/nac/new')}>Request your first e-NAC</Button>}>
          Upload the prescription from the government dispensary or hospital and list the medicines and tests.
        </EmptyState>
      )}
    </Page>
  )
}

// ======================================================================
// Detail
// ======================================================================

export function NacDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { data: nac, isLoading, error } = useQuery({ queryKey: ['nac', id], queryFn: () => api.get<Nac>('/api/nac/' + id) })
  if (isLoading) {
    return (
      <Page>
        <PageSkeleton />
      </Page>
    )
  }
  if (!nac) {
    return (
      <Page>
        <PageHeader title="Certificate not found" />
        <ErrorCallout error={error} />
      </Page>
    )
  }
  return (
    <Page>
      <PageHeader
        eyebrow={nac.nacNumber ? 'e-NAC ' + nac.nacNumber : 'e-NAC request #' + nac.id}
        title={`Prescription for ${nac.patientName}`}
        description={`${nac.items.length} items · ${nac.dispensaryName}`}
        actions={
          <>
            <Button icon={<Icon.Back />} onClick={() => navigate(-1)}>
              Back
            </Button>
            {nac.status === 'RETURNED' && (
              <Button variant="primary" onClick={() => navigate(`/nac/${nac.id}/resubmit`)}>
                Correct and resubmit
              </Button>
            )}
            {nac.status === 'ISSUED' && (
              <Button variant="primary" icon={<Icon.Plus />} onClick={() => navigate('/claims/new')}>
                Claim these items
              </Button>
            )}
          </>
        }
      />
      <div className="stack-lg">
        {nac.status === 'RETURNED' && (
          <Callout variant="stripe" title="Returned by the dispensary">
            <p>{nac.returnRemarks}</p>
          </Callout>
        )}
        <NacTracker nac={nac} />
        <NacDetails nac={nac} />
      </div>
    </Page>
  )
}

// ======================================================================
// New request / resubmission
// ======================================================================

interface NacForm {
  dispensaryId: string
  dependentId: string
  prescriptionDate: string
  prescribedBy: string
  items: { itemName: string; itemType: NacItemType; quantity: string }[]
}

export function NacFormPage() {
  const { id } = useParams()
  const existing = useQuery({ queryKey: ['nac', id], queryFn: () => api.get<Nac>('/api/nac/' + id), enabled: !!id })
  const profile = useQuery({ queryKey: ['profile'], queryFn: () => api.get<EmployeeProfile>('/api/profile') })
  const dispensaries = useQuery({ queryKey: ['dispensaries'], queryFn: () => api.get<OfficeRef[]>('/api/org/dispensaries'), staleTime: 300_000 })
  if ((id && existing.isLoading) || profile.isLoading || dispensaries.isLoading) {
    return (
      <Page>
        <PageSkeleton />
      </Page>
    )
  }
  if (!profile.data || !dispensaries.data) {
    return (
      <Page>
        <ErrorCallout error={profile.error ?? dispensaries.error} />
      </Page>
    )
  }
  return <NacFormInner existing={existing.data ?? null} profile={profile.data} dispensaries={dispensaries.data} />
}

function NacFormInner({ existing, profile, dispensaries }: { existing: Nac | null; profile: EmployeeProfile; dispensaries: OfficeRef[] }) {
  const navigate = useNavigate()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [prescription, setPrescription] = useState<DocumentMeta | null>(existing?.prescription ?? null)
  const { register, control, handleSubmit, formState } = useForm<NacForm>({
    defaultValues: existing
      ? {
          dispensaryId: String(existing.dispensaryId),
          dependentId: '',
          prescriptionDate: existing.prescriptionDate,
          prescribedBy: existing.prescribedBy,
          items: existing.items.map((i) => ({ itemName: i.itemName, itemType: i.itemType, quantity: i.quantity })),
        }
      : { dispensaryId: dispensaries[0] ? String(dispensaries[0].id) : '', dependentId: '', prescriptionDate: todayIso(), prescribedBy: '', items: [{ itemName: '', itemType: 'MEDICINE', quantity: '' }] },
  })
  const items = useFieldArray({ control, name: 'items' })

  const mutation = useMutation({
    mutationFn: (v: NacForm) => {
      const body = {
        prescriptionDate: v.prescriptionDate,
        prescribedBy: v.prescribedBy.trim(),
        prescriptionDocumentId: prescription?.id,
        items: v.items.map((i) => ({ itemName: i.itemName.trim(), itemType: i.itemType, quantity: i.quantity.trim() })),
      }
      return existing
        ? api.post<Nac>(`/api/nac/${existing.id}/resubmit`, body)
        : api.post<Nac>('/api/nac', { ...body, dispensaryId: Number(v.dispensaryId), dependentId: v.dependentId ? Number(v.dependentId) : null })
    },
    onSuccess: (nac) => {
      queryClient.invalidateQueries({ queryKey: ['nac'] })
      toast.ok('Sent to the dispensary. You will be notified when it is issued.')
      navigate('/nac/' + nac.id, { replace: true })
    },
  })

  const errs = formState.errors
  return (
    <Page>
      <PageHeader
        eyebrow="Dispensary"
        title={existing ? 'Correct and resubmit prescription' : 'Request an e-NAC'}
        description="List every prescribed medicine or test. The pharmacist marks each one; you can claim only those marked not available."
        actions={
          <Button icon={<Icon.Back />} onClick={() => navigate(-1)}>
            Cancel
          </Button>
        }
      />
      <form onSubmit={handleSubmit((v) => (prescription ? mutation.mutate(v) : toast.error('Upload the prescription first')))} className="stack-lg" noValidate>
        {existing?.returnRemarks && (
          <Callout variant="stripe" title="Why it was returned">
            <p>{existing.returnRemarks}</p>
          </Callout>
        )}
        <div className="grid grid-2">
          <Panel title="Prescription details">
            <div className="stack">
              <SelectField label="Dispensary (AMA)" disabled={!!existing} {...register('dispensaryId', { required: true })} options={dispensaries.map((d) => ({ value: String(d.id), label: d.name }))} />
              {!existing && (
                <SelectField
                  label="Patient"
                  {...register('dependentId')}
                  options={[
                    { value: '', label: `${profile.fullName} (Self)` },
                    ...profile.dependents.filter((d) => d.active).map((d) => ({ value: String(d.id), label: `${d.fullName} (${RELATION_LABELS[d.relation]})` })),
                  ]}
                />
              )}
              <div className="grid grid-2">
                <TextField label="Prescription date" type="date" required max={todayIso()} {...register('prescriptionDate', { required: 'Required' })} error={errs.prescriptionDate?.message} />
                <TextField label="Prescribed by (doctor)" required maxLength={120} {...register('prescribedBy', { required: 'Required' })} error={errs.prescribedBy?.message} />
              </div>
            </div>
          </Panel>
          <Panel title="Prescription copy" kicker="Clear scan or photo of the whole slip">
            {prescription ? (
              <DocumentChip doc={prescription} onRemove={() => setPrescription(null)} />
            ) : (
              <FileUpload category="PRESCRIPTION" onUploaded={setPrescription} />
            )}
          </Panel>
        </div>

        <Panel
          title="Prescribed items"
          kicker={`${items.fields.length} item${items.fields.length === 1 ? '' : 's'}`}
          actions={
            <Button size="sm" icon={<Icon.Plus />} onClick={() => items.append({ itemName: '', itemType: 'MEDICINE', quantity: '' })} disabled={items.fields.length >= 40}>
              Add item
            </Button>
          }
        >
          <div className="stack" style={{ gap: 8 }}>
            <AnimatePresence initial={false}>
              {items.fields.map((f, i) => (
                <motion.div
                  key={f.id}
                  layout
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, x: -16 }}
                  transition={{ duration: 0.2, ease }}
                  className="item-row"
                >
                  <span className="num muted line-no">
                    {String(i + 1).padStart(2, '0')}
                  </span>
                  <TextField label="Medicine or test" required maxLength={200} {...register(`items.${i}.itemName`, { required: 'Required' })} error={errs.items?.[i]?.itemName?.message} />
                  <SelectField
                    label="Type"
                    {...register(`items.${i}.itemType`)}
                    options={[
                      { value: 'MEDICINE', label: 'Medicine' },
                      { value: 'INVESTIGATION', label: 'Investigation' },
                      { value: 'OTHER', label: 'Other' },
                    ]}
                  />
                  <TextField label="Quantity" required maxLength={40} placeholder="30 tablets" {...register(`items.${i}.quantity`, { required: 'Required' })} error={errs.items?.[i]?.quantity?.message} />
                  <Button variant="ghost" aria-label={'Remove item ' + (i + 1)} onClick={() => items.remove(i)} disabled={items.fields.length === 1}>
                    <Icon.Trash />
                  </Button>
                </motion.div>
              ))}
            </AnimatePresence>
          </div>
        </Panel>

        {mutation.error && <ErrorCallout error={mutation.error} />}
        <div className="row" style={{ justifyContent: 'flex-end' }}>
          <Button type="submit" variant="primary" icon={<Icon.Arrow />} loading={mutation.isPending}>
            {existing ? 'Resubmit to dispensary' : 'Send to dispensary'}
          </Button>
        </div>
      </form>
    </Page>
  )
}
