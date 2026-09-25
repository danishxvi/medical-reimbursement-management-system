import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import type { FormEvent } from 'react'
import { api, qs } from '../../api/client'
import type { Page as PageOf, RateItemRow, RateList } from '../../api/types'
import { Button } from '../../components/ui/Button'
import { DataTable } from '../../components/ui/DataTable'
import { Badge, Callout, ErrorCallout, PageSkeleton } from '../../components/ui/Feedback'
import { SelectField, TextAreaField, TextField } from '../../components/ui/Form'
import { Page, PageHeader, Panel } from '../../components/ui/Layout'
import { Modal } from '../../components/ui/Modal'
import { useToast } from '../../components/ui/Toast'
import { formatDate, formatMoney } from '../../lib/format'
import { Icon } from '../../lib/icons'

/**
 * Rate lists used for the school's calculation sheet. A revised list is
 * imported as inactive, checked, then activated; the previous list then
 * ends the day before the new one starts, so older bills keep their rates.
 */
export default function RatesPage() {
  const queryClient = useQueryClient()
  const toast = useToast()
  const lists = useQuery({ queryKey: ['admin', 'rate-lists'], queryFn: () => api.get<RateList[]>('/api/admin/rates/lists') })
  const [selected, setSelected] = useState<number | null>(null)
  const [importing, setImporting] = useState(false)

  const toggle = useMutation({
    mutationFn: (l: RateList) => api.post<RateList>(`/api/admin/rates/lists/${l.id}/${l.active ? 'deactivate' : 'activate'}`),
    onSuccess: (l) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'rate-lists'] })
      toast.ok(`${l.code} ${l.active ? 'activated' : 'deactivated'}`)
    },
    onError: (e) => toast.error(e instanceof Error ? e.message : 'Could not change the list'),
  })

  const current = lists.data?.find((l) => l.id === selected) ?? lists.data?.[0]

  return (
    <Page>
      <PageHeader
        eyebrow="Administration"
        title="DGEHS rate lists"
        description="Approved rates used to suggest the restricted amount on the school's calculation sheet. Official orders always prevail."
        actions={
          <Button variant="primary" icon={<Icon.Upload />} onClick={() => setImporting(true)}>
            Import a revised list
          </Button>
        }
      />
      {lists.isLoading ? (
        <PageSkeleton />
      ) : (
        <div className="stack-lg">
          <Panel title="Lists" kicker="Newest first" flush>
            <DataTable
              rows={lists.data ?? []}
              rowKey={(l) => l.id}
              onRowClick={(l) => setSelected(l.id)}
              columns={[
                {
                  key: 'code',
                  header: 'List',
                  render: (l) => (
                    <div>
                      <strong className="mono">{l.code}</strong>
                      <div className="muted" style={{ fontSize: 'var(--text-xs)' }}>
                        {l.title}
                      </div>
                    </div>
                  ),
                },
                { key: 'order', header: 'Order', render: (l) => <span style={{ fontSize: 'var(--text-sm)' }}>{l.orderReference}</span> },
                {
                  key: 'period',
                  header: 'In force',
                  render: (l) => (
                    <span className="num">
                      {formatDate(l.effectiveFrom)} to {l.effectiveTo ? formatDate(l.effectiveTo) : 'further orders'}
                    </span>
                  ),
                },
                { key: 'count', header: 'Rates', align: 'right', render: (l) => <span className="num">{l.itemCount}</span> },
                { key: 'status', header: 'Status', render: (l) => <Badge tone={l.active ? 'done' : 'closed'}>{l.active ? 'Active' : 'Inactive'}</Badge> },
                {
                  key: 'act',
                  header: '',
                  render: (l) => (
                    <Button
                      size="sm"
                      loading={toggle.isPending && toggle.variables?.id === l.id}
                      onClick={(e) => {
                        e.stopPropagation()
                        toggle.mutate(l)
                      }}
                    >
                      {l.active ? 'Deactivate' : 'Activate'}
                    </Button>
                  ),
                },
              ]}
            />
          </Panel>
          {current && <ListItems list={current} />}
        </div>
      )}
      <ImportModal open={importing} onClose={() => setImporting(false)} />
    </Page>
  )
}

function ListItems({ list }: { list: RateList }) {
  const [q, setQ] = useState('')
  const [page, setPage] = useState(0)
  const items = useQuery({
    queryKey: ['admin', 'rate-items', list.id, q, page],
    queryFn: () => api.get<PageOf<RateItemRow>>(`/api/admin/rates/lists/${list.id}/items${qs({ q: q || undefined, page })}`),
  })
  return (
    <Panel
      title={list.code}
      kicker={list.sourceUrl ? 'Source recorded with its SHA-256 fingerprint' : 'Imported list'}
      actions={
        list.sourceUrl ? (
          <a className="btn btn-sm" href={list.sourceUrl} target="_blank" rel="noopener noreferrer">
            Source <Icon.Arrow />
          </a>
        ) : undefined
      }
      flush
    >
      <div className="panel-body stack">
        {list.notes && <Callout variant="dashed">{list.notes}</Callout>}
        <TextField label="Search this list" placeholder="Code or name, for example LB012 or haemogram" value={q} onChange={(e) => {
          setQ(e.target.value)
          setPage(0)
        }} />
      </div>
      {items.isLoading ? (
        <div className="panel-body">
          <PageSkeleton />
        </div>
      ) : (
        <>
          <DataTable
            rows={items.data?.items ?? []}
            rowKey={(r) => r.code}
            columns={[
              { key: 'sr', header: 'Sr', render: (r) => <span className="num muted">{r.serialNo ?? ''}</span> },
              { key: 'code', header: 'Code', render: (r) => <span className="mono">{r.code}</span> },
              { key: 'name', header: 'Procedure / investigation', render: (r) => <span style={{ fontSize: 'var(--text-sm)' }}>{r.name}</span> },
              { key: 'sp', header: 'Classification', render: (r) => <span className="muted" style={{ fontSize: 'var(--text-xs)' }}>{r.speciality}</span> },
              { key: 'nn', header: 'Non-NABH', align: 'right', render: (r) => <span className="num">{formatMoney(r.nonNabh)}</span> },
              { key: 'n', header: 'NABH', align: 'right', render: (r) => <span className="num">{formatMoney(r.nabh)}</span> },
              { key: 'ss', header: 'Super spec.', align: 'right', render: (r) => <span className="num">{formatMoney(r.superSpeciality)}</span> },
            ]}
          />
          {items.data && items.data.totalPages > 1 && (
            <div className="row-between" style={{ padding: '12px 24px', borderTop: 'var(--border)' }}>
              <Button size="sm" disabled={page === 0} onClick={() => setPage(page - 1)} icon={<Icon.Back />}>
                Previous
              </Button>
              <span className="caps muted">
                Page {page + 1} of {items.data.totalPages}
              </span>
              <Button size="sm" disabled={page + 1 >= items.data.totalPages} onClick={() => setPage(page + 1)}>
                Next <Icon.Arrow />
              </Button>
            </div>
          )}
        </>
      )}
    </Panel>
  )
}

function ImportModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient()
  const toast = useToast()
  const [file, setFile] = useState<File | null>(null)
  const [fields, setFields] = useState({ code: '', title: '', orderReference: '', sourceUrl: '', cityTier: 'X', effectiveFrom: '', notes: '' })
  const set = (k: keyof typeof fields, v: string) => setFields((f) => ({ ...f, [k]: v }))

  const run = useMutation({
    mutationFn: () => {
      const form = new FormData()
      Object.entries(fields).forEach(([k, v]) => form.append(k, v))
      if (file) form.append('file', file)
      return api.upload<RateList>('/api/admin/rates/lists', form)
    },
    onSuccess: (l) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'rate-lists'] })
      toast.ok(`${l.code} imported with ${l.itemCount} rates. Check it, then activate it.`)
      onClose()
    },
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    run.mutate()
  }

  const ready = file && fields.code && fields.title && fields.orderReference && fields.effectiveFrom
  return (
    <Modal
      open={open}
      title="Import a revised rate list"
      onClose={onClose}
      wide
      footer={
        <>
          <Button onClick={onClose}>Cancel</Button>
          <Button variant="primary" loading={run.isPending} disabled={!ready} onClick={() => run.mutate()}>
            Import as inactive
          </Button>
        </>
      }
    >
      <form className="stack" onSubmit={submit}>
        <Callout variant="dashed" title="CSV format">
          UTF-8, one row per rate, with the columns <span className="mono">code, name, speciality, non_nabh, nabh, super_speciality</span> (optional{' '}
          <span className="mono">sr</span>). Lines starting with # are ignored. Every row is checked; nothing is imported if any row is wrong.
        </Callout>
        <div className="grid grid-2">
          <TextField label="List code" required placeholder="CGHS-2026-T1" value={fields.code} onChange={(e) => set('code', e.target.value)} />
          <TextField label="Effective from" type="date" required value={fields.effectiveFrom} onChange={(e) => set('effectiveFrom', e.target.value)} />
          <TextField className="span-2" label="Title" required value={fields.title} onChange={(e) => set('title', e.target.value)} />
          <TextField className="span-2" label="Order reference" required placeholder="O.M. number and date" value={fields.orderReference} onChange={(e) => set('orderReference', e.target.value)} />
          <TextField label="Source address" placeholder="https://" value={fields.sourceUrl} onChange={(e) => set('sourceUrl', e.target.value)} />
          <SelectField
            label="City tier"
            value={fields.cityTier}
            onChange={(e) => set('cityTier', e.target.value)}
            options={[
              { value: 'X', label: 'X (Tier I, includes Delhi)' },
              { value: 'Y', label: 'Y (Tier II)' },
              { value: 'Z', label: 'Z (Tier III)' },
            ]}
          />
        </div>
        <TextAreaField label="Notes" value={fields.notes} maxLength={1000} onChange={(e) => set('notes', e.target.value)} />
        <label className="upload" style={{ minHeight: 88 }}>
          <input type="file" accept=".csv,text/csv" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
          <Icon.Upload />
          <span>{file ? file.name : 'Choose the CSV file'}</span>
        </label>
        <ErrorCallout error={run.error} />
      </form>
    </Modal>
  )
}
