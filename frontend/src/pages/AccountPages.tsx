import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'motion/react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router'
import { api } from '../api/client'
import type { EmployeeProfile, Notification, Relation } from '../api/types'
import { Button } from '../components/ui/Button'
import { EmptyState, ErrorCallout, PageSkeleton } from '../components/ui/Feedback'
import { SelectField, TextField } from '../components/ui/Form'
import { Details, Page, PageHeader, Panel } from '../components/ui/Layout'
import { Modal } from '../components/ui/Modal'
import { useToast } from '../components/ui/Toast'
import { RELATION_LABELS, formatDate, formatDateTime, formatMoney } from '../lib/format'
import { Icon } from '../lib/icons'
import { item, list } from '../lib/motion'

// ======================================================================
// Profile (employee)
// ======================================================================

export function ProfilePage() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const { data: p, isLoading, error } = useQuery({ queryKey: ['profile'], queryFn: () => api.get<EmployeeProfile>('/api/profile') })
  const [adding, setAdding] = useState(false)
  const [removing, setRemoving] = useState<number | null>(null)
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['profile'] })

  const contact = useForm({ values: { residentialAddress: p?.residentialAddress ?? '', phoneOffice: p?.phoneOffice ?? '', phoneResidence: p?.phoneResidence ?? '' } })
  const saveContact = useMutation({
    mutationFn: (v: { residentialAddress: string; phoneOffice: string; phoneResidence: string }) => api.put('/api/profile/contact', v),
    onSuccess: () => { toast.ok('Contact details saved'); refresh() },
  })

  const dep = useForm<{ fullName: string; relation: Relation; dateOfBirth: string }>({ defaultValues: { fullName: '', relation: 'SPOUSE', dateOfBirth: '' } })
  const addDependent = useMutation({
    mutationFn: (v: { fullName: string; relation: Relation; dateOfBirth: string }) => api.post('/api/profile/dependents', { ...v, dateOfBirth: v.dateOfBirth || null }),
    onSuccess: () => { toast.ok('Family member added'); setAdding(false); dep.reset(); refresh() },
  })
  const removeDependent = useMutation({
    mutationFn: (id: number) => api.del('/api/profile/dependents/' + id),
    onSuccess: () => { toast.ok('Removed'); setRemoving(null); refresh() },
  })

  if (isLoading) return <Page><PageSkeleton /></Page>
  if (!p) return <Page><ErrorCallout error={error} /></Page>

  return (
    <Page>
      <PageHeader
        eyebrow={'Employee ' + p.employeeCode}
        title={p.fullName}
        description="Service and DGEHS details come from your office records. If anything is wrong, ask your office to correct it."
        actions={<Link className="btn" to="/change-password"><Icon.Lock /> Change password</Link>}
      />
      <div className="stack-lg">
        <div className="grid grid-2">
          <Panel title="Service" kicker="Read only">
            <Details items={[
              ['Designation', p.designation],
              ['School', p.school ? `${p.school.name} (${p.school.code})` : '-'],
              ['PAO', p.school?.paoName],
              ['Pay scale / level', `${p.payScale ?? '-'} / ${p.payLevel ?? '-'}`],
              ['Basic pay', formatMoney(p.basicPay)],
              ['Date of joining', formatDate(p.dateOfJoining)],
            ]} />
          </Panel>
          <Panel title="DGEHS and bank" kicker="Read only">
            <Details items={[
              ['DGEHS card', p.dgehsCardNo],
              ['Place of issue', p.dgehsCardPlace],
              ['Valid', `${formatDate(p.dgehsValidFrom)} to ${formatDate(p.dgehsValidTo)}`],
              ['Ward entitlement', p.wardEntitlement?.replace('_', ' ').toLowerCase()],
              ['Bank / branch', `${p.bankName ?? '-'} / ${p.bankBranch ?? '-'}`],
              ['Account / IFSC', `${p.bankAccountMasked ?? '-'} / ${p.ifsc ?? '-'}`],
            ]} />
          </Panel>
        </div>
        <div className="grid grid-2">
          <Panel title="Contact details" kicker="You can edit these" footer={<Button variant="primary" loading={saveContact.isPending} onClick={contact.handleSubmit((v) => saveContact.mutate(v))}>Save</Button>}>
            <form className="stack" onSubmit={(e) => e.preventDefault()}>
              <TextField label="Residential address" maxLength={300} {...contact.register('residentialAddress')} />
              <div className="grid grid-2">
                <TextField label="Office phone" maxLength={15} {...contact.register('phoneOffice')} />
                <TextField label="Residence phone" maxLength={15} {...contact.register('phoneResidence')} />
              </div>
              {saveContact.error && <ErrorCallout error={saveContact.error} />}
            </form>
          </Panel>
          <Panel title="Family members" kicker="Covered under your DGEHS card" actions={<Button size="sm" icon={<Icon.Plus />} onClick={() => setAdding(true)}>Add</Button>} flush>
            {p.dependents.length === 0 ? (
              <div className="panel-body"><EmptyState title="No family members added">Add dependents to file claims for their treatment.</EmptyState></div>
            ) : (
              <motion.ul className="stack" style={{ listStyle: 'none', margin: 0, padding: 16, gap: 8 }} variants={list} initial="initial" animate="animate">
                <AnimatePresence>
                  {p.dependents.map((d) => (
                    <motion.li key={d.id} variants={item} exit={{ opacity: 0, x: -16 }} className="file-chip">
                      <span>
                        <strong>{d.fullName}</strong> <span className="muted">· {RELATION_LABELS[d.relation]} · born {formatDate(d.dateOfBirth)}</span>
                      </span>
                      <Button size="sm" variant="ghost" onClick={() => setRemoving(d.id)} aria-label={'Remove ' + d.fullName}><Icon.Trash /></Button>
                    </motion.li>
                  ))}
                </AnimatePresence>
              </motion.ul>
            )}
          </Panel>
        </div>
      </div>

      <Modal open={adding} title="Add a family member" onClose={() => setAdding(false)} footer={<><Button onClick={() => setAdding(false)}>Cancel</Button><Button variant="primary" loading={addDependent.isPending} onClick={dep.handleSubmit((v) => addDependent.mutate(v))}>Add</Button></>}>
        <form className="stack" onSubmit={(e) => e.preventDefault()}>
          <TextField label="Full name" required maxLength={120} {...dep.register('fullName', { required: true })} />
          <div className="grid grid-2">
            <SelectField label="Relation" {...dep.register('relation')} options={(Object.keys(RELATION_LABELS) as Relation[]).filter((r) => r !== 'SELF').map((r) => ({ value: r, label: RELATION_LABELS[r] }))} />
            <TextField label="Date of birth" type="date" {...dep.register('dateOfBirth')} />
          </div>
          <p className="muted" style={{ fontSize: 'var(--text-xs)' }}>The Head of School certifies dependency when verifying each claim.</p>
          {addDependent.error && <ErrorCallout error={addDependent.error} />}
        </form>
      </Modal>
      <Modal open={removing !== null} title="Remove this family member?" onClose={() => setRemoving(null)} footer={<><Button onClick={() => setRemoving(null)}>Keep</Button><Button variant="primary" loading={removeDependent.isPending} onClick={() => removing && removeDependent.mutate(removing)}>Remove</Button></>}>
        <p>Existing claims are not affected. New claims can no longer be filed for this person.</p>
      </Modal>
    </Page>
  )
}

// ======================================================================
// Notifications
// ======================================================================

export function NotificationsPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { data, isLoading } = useQuery({ queryKey: ['notifications', 'list'], queryFn: () => api.get<Notification[]>('/api/notifications') })
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['notifications'] })
  const readAll = useMutation({ mutationFn: () => api.post('/api/notifications/read-all'), onSuccess: refresh })
  const open = async (n: Notification) => {
    if (!n.read) await api.post(`/api/notifications/${n.id}/read`).catch(() => undefined)
    refresh()
    if (n.link) navigate(n.link)
  }
  return (
    <Page>
      <PageHeader
        eyebrow="Account"
        title="Notifications"
        description="Updates on your claims and your queues."
        actions={<Button onClick={() => readAll.mutate()} loading={readAll.isPending} disabled={!data?.some((n) => !n.read)}>Mark all as read</Button>}
      />
      {isLoading ? <PageSkeleton /> : !data || data.length === 0 ? <EmptyState title="You are all caught up" /> : (
        <motion.div className="tiles" style={{ ['--cols-base' as string]: 1 }} variants={list} initial="initial" animate="animate">
          {data.map((n) => (
            <motion.button
              key={n.id}
              type="button"
              variants={item}
              onClick={() => open(n)}
              className="stat"
              style={{ minHeight: 0, textAlign: 'left', cursor: 'pointer', display: 'grid', gridTemplateColumns: '12px 1fr auto', gap: 16, alignItems: 'start', font: 'inherit' }}
            >
              <span style={{ width: 10, height: 10, marginTop: 6, borderRadius: '50%', background: n.read ? 'transparent' : 'currentColor', border: '1px solid currentColor' }} aria-label={n.read ? 'Read' : 'Unread'} />
              <span>
                <strong style={{ display: 'block' }}>{n.title}</strong>
                <span className="stat-foot" style={{ fontSize: 'var(--text-sm)' }}>{n.message}</span>
              </span>
              <span className="caps stat-label">{formatDateTime(n.createdAt)}</span>
            </motion.button>
          ))}
        </motion.div>
      )}
    </Page>
  )
}

// ======================================================================
// Not found
// ======================================================================

export function NotFoundPage() {
  const navigate = useNavigate()
  return (
    <Page>
      <div className="empty" style={{ minHeight: '50vh' }}>
        <div className="big-number">404</div>
        <strong style={{ color: 'var(--ink)' }}>This page does not exist</strong>
        <Button variant="primary" onClick={() => navigate('/')}>Go to dashboard</Button>
      </div>
    </Page>
  )
}
