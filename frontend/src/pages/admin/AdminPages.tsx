import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { motion } from 'motion/react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { api, qs } from '../../api/client'
import type { AccountSummary, AuditEntry, CreatedAccount, EmployeeProfile, OfficeRef, Page as PageOf, Role, SchoolRef } from '../../api/types'
import { Button } from '../../components/ui/Button'
import { DataTable } from '../../components/ui/DataTable'
import { Badge, Callout, EmptyState, ErrorCallout, PageSkeleton } from '../../components/ui/Feedback'
import { SelectField, TextField } from '../../components/ui/Form'
import { Page, PageHeader, Panel } from '../../components/ui/Layout'
import { Modal } from '../../components/ui/Modal'
import { useToast } from '../../components/ui/Toast'
import { ROLE_LABELS, formatDate, formatDateTime } from '../../lib/format'
import { Icon } from '../../lib/icons'

// ----------------------------------------------------------------------
// Shared bits
// ----------------------------------------------------------------------

/** Shows a one time temporary password so the administrator can hand it over. */
function TemporaryPassword({ account, onClose }: { account: CreatedAccount | null; onClose: () => void }) {
  const toast = useToast()
  return (
    <Modal open={!!account} title="Temporary password" onClose={onClose} footer={<Button variant="primary" onClick={onClose}>Done</Button>}>
      {account && (
        <div className="stack">
          <p>
            Give this to <strong className="mono">{account.username}</strong> in person or through an official channel. It is shown only once and must be
            changed at first sign in.
          </p>
          <div className="row-between panel" style={{ padding: 16 }}>
            <span className="big-number" style={{ fontSize: 'var(--text-xl)' }}>{account.temporaryPassword}</span>
            <Button size="sm" onClick={() => navigator.clipboard?.writeText(account.temporaryPassword ?? '').then(() => toast.ok('Copied'))}>
              Copy
            </Button>
          </div>
        </div>
      )}
    </Modal>
  )
}

function Pager({ page, totalPages, onPage }: { page: number; totalPages: number; onPage: (p: number) => void }) {
  if (totalPages <= 1) return null
  return (
    <div className="row-between" style={{ padding: '12px 24px', borderTop: 'var(--border)' }}>
      <Button size="sm" disabled={page === 0} onClick={() => onPage(page - 1)} icon={<Icon.Back />}>Previous</Button>
      <span className="caps muted">Page {page + 1} of {totalPages}</span>
      <Button size="sm" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)} icon={<Icon.Arrow />}>Next</Button>
    </div>
  )
}

function useOffices() {
  const paos = useQuery({ queryKey: ['admin', 'paos'], queryFn: () => api.get<OfficeRef[]>('/api/admin/paos') })
  const schools = useQuery({ queryKey: ['admin', 'schools', 'all'], queryFn: () => api.get<PageOf<SchoolRef>>('/api/admin/schools?size=100') })
  const dispensaries = useQuery({ queryKey: ['admin', 'dispensaries'], queryFn: () => api.get<OfficeRef[]>('/api/admin/dispensaries') })
  return { paos: paos.data ?? [], schools: schools.data?.items ?? [], dispensaries: dispensaries.data ?? [] }
}

// ======================================================================
// Official accounts
// ======================================================================

export function UsersPage() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const offices = useOffices()
  const [role, setRole] = useState<Role | ''>('')
  const [q, setQ] = useState('')
  const [page, setPage] = useState(0)
  const [creating, setCreating] = useState(false)
  const [temp, setTemp] = useState<CreatedAccount | null>(null)

  const users = useQuery({
    queryKey: ['admin', 'users', role, q, page],
    queryFn: () => api.get<PageOf<AccountSummary>>('/api/admin/users' + qs({ role, q, page })),
  })
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })

  const action = useMutation({
    mutationFn: ({ id, act }: { id: number; act: 'reset-password' | 'unlock' | 'disable' | 'enable' }) =>
      api.post<CreatedAccount | undefined>(`/api/admin/users/${id}/${act}`),
    onSuccess: (result, { act }) => {
      refresh()
      if (act === 'reset-password' && result) setTemp(result)
      else toast.ok('Done')
    },
    onError: (e) => toast.error(e instanceof Error ? e.message : 'Action failed'),
  })

  const officeName = (u: AccountSummary) =>
    u.schoolId ? offices.schools.find((s) => s.id === u.schoolId)?.name
      : u.dispensaryId ? offices.dispensaries.find((d) => d.id === u.dispensaryId)?.name
        : u.paoId ? offices.paos.find((p) => p.id === u.paoId)?.name
          : u.zone ? u.zone
          : 'Directorate'

  return (
    <Page>
      <PageHeader
        eyebrow="Administration"
        title="Official accounts"
        description="Heads of School, dispensary staff, PAO officials and administrators. Employees are added from the Employees screen."
        actions={<Button variant="primary" icon={<Icon.Plus />} onClick={() => setCreating(true)}>New official</Button>}
      />
      <Panel
        flush
        title={
          <div className="row">
            <input className="control" style={{ width: 240 }} placeholder="Search ID or name" value={q} onChange={(e) => { setQ(e.target.value); setPage(0) }} aria-label="Search" />
            <select className="control" style={{ width: 200 }} value={role} onChange={(e) => { setRole(e.target.value as Role | ''); setPage(0) }} aria-label="Role">
              <option value="">All roles</option>
              {(Object.keys(ROLE_LABELS) as Role[]).map((r) => <option key={r} value={r}>{ROLE_LABELS[r]}</option>)}
            </select>
          </div>
        }
      >
        {users.isLoading ? <div className="panel-body"><PageSkeleton /></div> : (
          <>
            <DataTable
              rows={users.data?.items ?? []}
              rowKey={(u) => u.id}
              columns={[
                { key: 'u', header: 'Login ID', render: (u) => <span className="mono">{u.username}</span> },
                { key: 'n', header: 'Name', render: (u) => u.fullName },
                { key: 'r', header: 'Role', render: (u) => ROLE_LABELS[u.role] },
                { key: 'o', header: 'Office', render: (u) => officeName(u) ?? '-' },
                { key: 'l', header: 'Last sign in', render: (u) => formatDateTime(u.lastLoginAt) },
                {
                  key: 's',
                  header: 'State',
                  render: (u) => (
                    <span className="row" style={{ gap: 6 }}>
                      {u.enabled ? <Badge tone="done">Active</Badge> : <Badge tone="closed">Disabled</Badge>}
                      {u.locked && <Badge tone="attention">Locked</Badge>}
                    </span>
                  ),
                },
                {
                  key: 'a',
                  header: '',
                  render: (u) => (
                    <span className="row" style={{ gap: 4, flexWrap: 'nowrap' }}>
                      <Button size="sm" onClick={() => action.mutate({ id: u.id, act: 'reset-password' })}>Reset password</Button>
                      {u.locked && <Button size="sm" onClick={() => action.mutate({ id: u.id, act: 'unlock' })}>Unlock</Button>}
                      <Button size="sm" variant="ghost" onClick={() => action.mutate({ id: u.id, act: u.enabled ? 'disable' : 'enable' })}>
                        {u.enabled ? 'Disable' : 'Enable'}
                      </Button>
                    </span>
                  ),
                },
              ]}
            />
            <Pager page={page} totalPages={users.data?.totalPages ?? 0} onPage={setPage} />
          </>
        )}
      </Panel>
      <CreateOfficial open={creating} onClose={() => setCreating(false)} offices={offices} onCreated={(a) => { setCreating(false); setTemp(a); refresh() }} />
      <TemporaryPassword account={temp} onClose={() => setTemp(null)} />
    </Page>
  )
}

function CreateOfficial({ open, onClose, offices, onCreated }: { open: boolean; onClose: () => void; offices: ReturnType<typeof useOffices>; onCreated: (a: CreatedAccount) => void }) {
  const { register, watch, handleSubmit, reset } = useForm({ defaultValues: { username: '', fullName: '', role: 'HOS' as Role, email: '', mobile: '', officeId: '' } })
  const role = watch('role')
  const scope =
    role === 'HOS' ? 'school' : role === 'PHARMACIST' || role === 'MEDICAL_OFFICER' ? 'dispensary' : role === 'ADMIN' ? null : role === 'OVERSIGHT' ? 'zone' : 'pao'
  const options = scope === 'school' ? offices.schools : scope === 'dispensary' ? offices.dispensaries : scope === 'pao' ? offices.paos : []
  // Zones are those used by the schools on record
  const zones = [...new Set(offices.schools.map((s) => s.zone).filter((z): z is string => !!z))].sort()
  const create = useMutation({
    mutationFn: (v: { username: string; fullName: string; role: Role; email: string; mobile: string; officeId: string }) =>
      api.post<CreatedAccount>('/api/admin/users', {
        username: v.username, fullName: v.fullName, role: v.role, email: v.email || null, mobile: v.mobile || null,
        schoolId: scope === 'school' ? Number(v.officeId) : null,
        dispensaryId: scope === 'dispensary' ? Number(v.officeId) : null,
        paoId: scope === 'pao' ? Number(v.officeId) : null,
        zone: scope === 'zone' ? v.officeId : null,
      }),
    onSuccess: (a) => { reset(); onCreated(a) },
  })
  return (
    <Modal open={open} title="New official account" onClose={onClose} footer={<><Button onClick={onClose}>Cancel</Button><Button variant="primary" loading={create.isPending} onClick={handleSubmit((v) => create.mutate(v))}>Create</Button></>}>
      <form className="grid grid-2" onSubmit={(e) => e.preventDefault()}>
        <SelectField label="Role" {...register('role')} options={(Object.keys(ROLE_LABELS) as Role[]).filter((r) => r !== 'EMPLOYEE').map((r) => ({ value: r, label: ROLE_LABELS[r] }))} />
        {scope === 'zone' && <SelectField label="Zone" {...register('officeId', { required: true })} placeholder="Select" options={zones.map((z) => ({ value: z, label: z }))} />}
        {scope && scope !== 'zone' && <SelectField label={scope === 'school' ? 'School' : scope === 'dispensary' ? 'Dispensary' : 'PAO'} {...register('officeId', { required: true })} placeholder="Select" options={options.map((o) => ({ value: String(o.id), label: `${o.name} (${o.code})` }))} />}
        <TextField label="Login ID" required maxLength={40} {...register('username', { required: true })} />
        <TextField label="Full name" required maxLength={120} {...register('fullName', { required: true })} />
        <TextField label="E-mail" type="email" maxLength={150} {...register('email')} />
        <TextField label="Mobile" inputMode="numeric" maxLength={10} {...register('mobile')} />
        {create.error && <div className="span-all"><ErrorCallout error={create.error} /></div>}
      </form>
    </Modal>
  )
}

// ======================================================================
// Employees
// ======================================================================

export function EmployeesPage() {
  const queryClient = useQueryClient()
  const offices = useOffices()
  const [q, setQ] = useState('')
  const [page, setPage] = useState(0)
  const [creating, setCreating] = useState(false)
  const [temp, setTemp] = useState<CreatedAccount | null>(null)
  const list = useQuery({ queryKey: ['admin', 'employees', q, page], queryFn: () => api.get<PageOf<EmployeeProfile>>('/api/admin/employees' + qs({ q, page })) })

  return (
    <Page>
      <PageHeader
        eyebrow="Administration"
        title="Employees"
        description="Service and DGEHS details entered here pre fill every claim, so employees never retype them."
        actions={<Button variant="primary" icon={<Icon.Plus />} onClick={() => setCreating(true)}>Add employee</Button>}
      />
      <Panel flush title={<input className="control" style={{ width: 280 }} placeholder="Search Employee ID or designation" value={q} onChange={(e) => { setQ(e.target.value); setPage(0) }} aria-label="Search" />}>
        {list.isLoading ? <div className="panel-body"><PageSkeleton /></div> : list.data && list.data.items.length > 0 ? (
          <>
            <DataTable
              rows={list.data.items}
              rowKey={(e) => e.userId}
              columns={[
                { key: 'c', header: 'Employee ID', render: (e) => <span className="mono">{e.employeeCode}</span> },
                { key: 'n', header: 'Name', render: (e) => e.fullName },
                { key: 'd', header: 'Designation', render: (e) => e.designation },
                { key: 's', header: 'School', render: (e) => e.school?.name ?? '-' },
                { key: 'g', header: 'DGEHS card', render: (e) => (e.dgehsCardNo ? <span className="mono">{e.dgehsCardNo}</span> : <Badge tone="attention">Missing</Badge>) },
                { key: 'v', header: 'Valid to', render: (e) => formatDate(e.dgehsValidTo) },
                { key: 'f', header: 'Family', align: 'right', render: (e) => <span className="num">{e.dependents.length}</span> },
              ]}
            />
            <Pager page={page} totalPages={list.data.totalPages} onPage={setPage} />
          </>
        ) : <div className="panel-body"><EmptyState title="No employees found" /></div>}
      </Panel>
      <OnboardEmployee open={creating} onClose={() => setCreating(false)} schools={offices.schools} onCreated={(a) => { setCreating(false); setTemp(a); queryClient.invalidateQueries({ queryKey: ['admin', 'employees'] }) }} />
      <TemporaryPassword account={temp} onClose={() => setTemp(null)} />
    </Page>
  )
}

type EmployeeForm = Record<
  'employeeCode' | 'fullName' | 'email' | 'mobile' | 'schoolId' | 'designation' | 'payScale' | 'payLevel' | 'basicPay' | 'dgehsCardNo' | 'dgehsCardPlace' |
  'dgehsValidFrom' | 'dgehsValidTo' | 'wardEntitlement' | 'dateOfBirth' | 'dateOfJoining' | 'gender' | 'residentialAddress' | 'phoneOffice' |
  'phoneResidence' | 'bankName' | 'bankBranch' | 'bankAccountNumber' | 'ifsc' | 'micr', string
>

function OnboardEmployee({ open, onClose, schools, onCreated }: { open: boolean; onClose: () => void; schools: SchoolRef[]; onCreated: (a: CreatedAccount) => void }) {
  const { register, handleSubmit, reset } = useForm<EmployeeForm>()
  const create = useMutation({
    mutationFn: (v: EmployeeForm) => {
      const body: Record<string, unknown> = {}
      Object.entries(v).forEach(([k, val]) => (body[k] = val === '' ? null : val))
      body.schoolId = Number(v.schoolId)
      body.basicPay = v.basicPay ? Number(v.basicPay) : null
      body.ifsc = v.ifsc ? v.ifsc.toUpperCase() : null
      body.mustChangePassword = true
      return api.post<CreatedAccount>('/api/admin/employees', body)
    },
    onSuccess: (a) => { reset(); onCreated(a) },
  })
  const t = (name: keyof EmployeeForm, label: string, extra: Record<string, unknown> = {}) => <TextField label={label} {...register(name)} {...extra} />
  return (
    <Modal open={open} title="Add employee" wide onClose={onClose} footer={<><Button onClick={onClose}>Cancel</Button><Button variant="primary" loading={create.isPending} onClick={handleSubmit((v) => create.mutate(v))}>Add employee</Button></>}>
      <form className="stack" onSubmit={(e) => e.preventDefault()}>
        <div className="caps muted">Service</div>
        <div className="grid grid-3">
          {t('employeeCode', 'Employee ID (login)', { required: true, maxLength: 40 })}
          {t('fullName', 'Full name', { required: true, maxLength: 120 })}
          <SelectField label="School" required {...register('schoolId', { required: true })} placeholder="Select" options={schools.map((s) => ({ value: String(s.id), label: `${s.name} (${s.code})` }))} />
          {t('designation', 'Designation', { required: true, maxLength: 80 })}
          {t('payScale', 'Pay scale')}
          {t('payLevel', 'Pay level', { maxLength: 10 })}
          {t('basicPay', 'Basic pay (Rs)', { type: 'number', min: 0 })}
          {t('dateOfJoining', 'Date of joining', { type: 'date' })}
          {t('dateOfBirth', 'Date of birth', { type: 'date' })}
        </div>
        <div className="caps muted">DGEHS</div>
        <div className="grid grid-3">
          {t('dgehsCardNo', 'DGEHS card number')}
          {t('dgehsCardPlace', 'Place of issue')}
          <SelectField label="Ward entitlement" {...register('wardEntitlement')} placeholder="Select" options={[{ value: 'PRIVATE', label: 'Private' }, { value: 'SEMI_PRIVATE', label: 'Semi private' }, { value: 'GENERAL', label: 'General' }]} />
          {t('dgehsValidFrom', 'Valid from', { type: 'date' })}
          {t('dgehsValidTo', 'Valid to', { type: 'date' })}
          <SelectField label="Gender" {...register('gender')} placeholder="Select" options={[{ value: 'FEMALE', label: 'Female' }, { value: 'MALE', label: 'Male' }, { value: 'OTHER', label: 'Other' }]} />
        </div>
        <div className="caps muted">Contact and bank</div>
        <div className="grid grid-3">
          {t('email', 'E-mail', { type: 'email' })}
          {t('mobile', 'Mobile', { inputMode: 'numeric', maxLength: 10 })}
          {t('phoneOffice', 'Office phone')}
          {t('residentialAddress', 'Residential address', { className: 'span-2' })}
          {t('phoneResidence', 'Residence phone')}
          {t('bankName', 'Bank')}
          {t('bankBranch', 'Branch')}
          {t('bankAccountNumber', 'Salary account number', { inputMode: 'numeric', hint: 'Only the last 4 digits are stored' })}
          {t('ifsc', 'IFSC', { maxLength: 11 })}
          {t('micr', 'MICR', { inputMode: 'numeric', maxLength: 9 })}
        </div>
        {create.error && <ErrorCallout error={create.error} />}
      </form>
    </Modal>
  )
}

// ======================================================================
// Offices and schools
// ======================================================================

export function OrganisationPage() {
  const queryClient = useQueryClient()
  const toast = useToast()
  const offices = useOffices()
  const [tab, setTab] = useState<'schools' | 'paos' | 'dispensaries'>('schools')
  const { register, handleSubmit, reset } = useForm({ defaultValues: { code: '', name: '', address: '', district: '', zone: '', paoId: '' } })
  const create = useMutation({
    mutationFn: (v: Record<string, string>) => {
      const path = tab === 'schools' ? 'schools' : tab === 'paos' ? 'paos' : 'dispensaries'
      const body = tab === 'schools' ? { ...v, paoId: Number(v.paoId) } : { code: v.code, name: v.name, address: v.address }
      return api.post(`/api/admin/${path}`, body)
    },
    onSuccess: () => { toast.ok('Saved'); reset(); queryClient.invalidateQueries({ queryKey: ['admin'] }) },
  })
  const rows: (OfficeRef | SchoolRef)[] = tab === 'schools' ? offices.schools : tab === 'paos' ? offices.paos : offices.dispensaries
  const tabs = [['schools', 'Schools'], ['paos', 'Pay and Accounts Offices'], ['dispensaries', 'Dispensaries']] as const

  return (
    <Page>
      <PageHeader eyebrow="Administration" title="Offices and schools" description="Each school is linked to the PAO that pays its staff. Claims route automatically." />
      <div className="tabs" role="tablist">
        {tabs.map(([k, label]) => (
          <button key={k} type="button" role="tab" aria-selected={tab === k} className={'tab' + (tab === k ? ' active' : '')} onClick={() => setTab(k)}>
            {label}
            {tab === k && <motion.span layoutId="org-tab" className="tab-line" />}
          </button>
        ))}
      </div>
      <div className="grid" style={{ gridTemplateColumns: 'minmax(0, 2fr) minmax(0, 1fr)' }}>
        <Panel flush title={`${rows.length} ${tabs.find((t) => t[0] === tab)?.[1].toLowerCase()}`}>
          <DataTable
            rows={rows}
            rowKey={(r) => r.id}
            columns={[
              { key: 'c', header: 'Code', render: (r) => <span className="mono">{r.code}</span> },
              { key: 'n', header: 'Name', render: (r) => r.name },
              ...(tab === 'schools' ? [{ key: 'p', header: 'PAO', render: (r: OfficeRef | SchoolRef) => ('paoName' in r ? r.paoName : '-') }] : []),
              { key: 'a', header: 'Address', render: (r) => r.address ?? '-' },
            ]}
          />
        </Panel>
        <Panel title="Add new" kicker={tabs.find((t) => t[0] === tab)?.[1]} footer={<Button variant="primary" loading={create.isPending} onClick={handleSubmit((v) => create.mutate(v))}>Save</Button>}>
          <form className="stack" onSubmit={(e) => e.preventDefault()}>
            <TextField label={tab === 'schools' ? 'School ID' : 'Code'} required maxLength={20} {...register('code', { required: true })} />
            <TextField label="Name" required maxLength={200} {...register('name', { required: true })} />
            {tab === 'schools' && (
              <>
                <SelectField label="Pay and Accounts Office" required {...register('paoId', { required: true })} placeholder="Select" options={offices.paos.map((p) => ({ value: String(p.id), label: p.name }))} />
                <div className="grid grid-2">
                  <TextField label="District" maxLength={80} {...register('district')} />
                  <TextField label="Zone" maxLength={80} {...register('zone')} />
                </div>
              </>
            )}
            <TextField label="Address" maxLength={300} {...register('address')} />
            {create.error && <ErrorCallout error={create.error} />}
          </form>
        </Panel>
      </div>
    </Page>
  )
}

// ======================================================================
// Audit trail
// ======================================================================

export function AuditPage() {
  const [filters, setFilters] = useState({ entityType: '', entityId: '', actor: '', action: '' })
  const [page, setPage] = useState(0)
  const list = useQuery({ queryKey: ['admin', 'audit', filters, page], queryFn: () => api.get<PageOf<AuditEntry>>('/api/admin/audit' + qs({ ...filters, page })) })
  const verify = useMutation({ mutationFn: () => api.get<{ valid: boolean; entriesChecked: number; firstBrokenEntryId: number | null }>('/api/admin/audit/verify') })
  const set = (k: keyof typeof filters, v: string) => { setFilters((f) => ({ ...f, [k]: v })); setPage(0) }

  return (
    <Page>
      <PageHeader
        eyebrow="Administration"
        title="Audit trail"
        description="Append only. Each entry carries a SHA-256 hash of the previous one, so any edit or deletion breaks the chain."
        actions={<Button variant="primary" icon={<Icon.Shield />} loading={verify.isPending} onClick={() => verify.mutate()}>Verify chain</Button>}
      />
      <div className="stack-lg">
        {verify.data && (
          <Callout variant={verify.data.valid ? 'plain' : 'stripe'} title={verify.data.valid ? 'Chain intact' : 'Chain broken'}>
            <p>
              {verify.data.entriesChecked} entries checked.
              {!verify.data.valid && ` The first inconsistent entry is #${verify.data.firstBrokenEntryId}. Treat this as a security incident.`}
            </p>
          </Callout>
        )}
        {verify.error && <ErrorCallout error={verify.error} />}
        <Panel flush title={
          <div className="row">
            <input className="control" style={{ width: 150 }} placeholder="Entity type" value={filters.entityType} onChange={(e) => set('entityType', e.target.value.toUpperCase())} aria-label="Entity type" />
            <input className="control" style={{ width: 120 }} placeholder="Entity id" value={filters.entityId} onChange={(e) => set('entityId', e.target.value)} aria-label="Entity id" />
            <input className="control" style={{ width: 150 }} placeholder="Actor login" value={filters.actor} onChange={(e) => set('actor', e.target.value)} aria-label="Actor" />
            <input className="control" style={{ width: 200 }} placeholder="Action" value={filters.action} onChange={(e) => set('action', e.target.value.toUpperCase())} aria-label="Action" />
          </div>
        }>
          {list.isLoading ? <div className="panel-body"><PageSkeleton /></div> : (
            <>
              <DataTable
                rows={list.data?.items ?? []}
                rowKey={(e) => e.id}
                columns={[
                  { key: 'i', header: '#', render: (e) => <span className="num">{e.id}</span> },
                  { key: 't', header: 'When', render: (e) => formatDateTime(e.occurredAt) },
                  { key: 'a', header: 'Actor', render: (e) => e.actorUsername ? <span className="mono">{e.actorUsername}</span> : <span className="muted">system</span> },
                  { key: 'ac', header: 'Action', render: (e) => <span className="tag">{e.action}</span> },
                  { key: 'en', header: 'Record', render: (e) => `${e.entityType}${e.entityId ? ' ' + e.entityId : ''}` },
                  { key: 'd', header: 'Details', render: (e) => <span style={{ fontSize: 'var(--text-xs)' }}>{e.details ?? '-'}</span> },
                  { key: 'ip', header: 'IP', render: (e) => <span className="mono" style={{ fontSize: 'var(--text-xs)' }}>{e.ipAddress ?? '-'}</span> },
                  { key: 'h', header: 'Hash', render: (e) => <span className="mono muted" title={e.hash} style={{ fontSize: 'var(--text-xs)' }}>{e.hash.substring(0, 10)}</span> },
                ]}
              />
              <Pager page={page} totalPages={list.data?.totalPages ?? 0} onPage={setPage} />
            </>
          )}
        </Panel>
      </div>
    </Page>
  )
}
