import { useQuery, useQueryClient } from '@tanstack/react-query'
import { motion } from 'motion/react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { api } from '../../api/client'
import type { PrivacyNotice } from '../../api/types'
import { useAuth, useMe } from '../../auth/AuthContext'
import { Button } from '../../components/ui/Button'
import { Callout, ErrorCallout, PageSkeleton } from '../../components/ui/Feedback'
import { Checkbox } from '../../components/ui/Form'
import { Page, PageHeader } from '../../components/ui/Layout'
import { formatDate } from '../../lib/format'
import { Icon } from '../../lib/icons'
import { item, list } from '../../lib/motion'

function usePrivacyNotice() {
  return useQuery({ queryKey: ['privacy-notice'], queryFn: () => api.get<PrivacyNotice>('/api/legal/privacy'), staleTime: 3_600_000 })
}

/** The notice itself: numbered sections that stagger in. */
function NoticeBody({ notice }: { notice: PrivacyNotice }) {
  return (
    <motion.div className="stack" variants={list} initial="initial" animate="animate">
      {notice.draft && (
        <motion.div variants={item}>
          <Callout title="Draft for approval" variant="dashed">
            This notice is a draft prepared with the prototype. The Directorate must approve the final text, retention periods and grievance contact before real use.
          </Callout>
        </motion.div>
      )}
      {notice.sections.map((s) => (
        <motion.section key={s.heading} className="panel notice-section" variants={item}>
          <div className="panel-body">
            <h2>{s.heading}</h2>
            {s.paragraphs.length > 1 ? (
              <ul>
                {s.paragraphs.map((p) => (
                  <li key={p}>{p}</li>
                ))}
              </ul>
            ) : (
              <p>{s.paragraphs[0]}</p>
            )}
          </div>
        </motion.section>
      ))}
    </motion.div>
  )
}

function meta(notice: PrivacyNotice) {
  return `Version ${notice.version}, effective ${formatDate(notice.effectiveFrom)}. Written for the Digital Personal Data Protection Act, 2023.`
}

/** Inside the app, from the Help menu. */
export function PrivacyNoticePage() {
  const { data, isLoading, error } = usePrivacyNotice()
  return (
    <Page>
      <PageHeader eyebrow="Help" title="Privacy notice" description={data ? meta(data) : undefined} />
      {isLoading ? <PageSkeleton /> : error ? <ErrorCallout error={error} /> : data && <NoticeBody notice={data} />}
    </Page>
  )
}

/** Public copy linked from the sign in page. */
export function PublicPrivacyPage() {
  const { data, isLoading, error } = usePrivacyNotice()
  return (
    <div className="standalone">
      <div className="content">
        <PageHeader
          eyebrow="Medical Reimbursement Management System"
          title="Privacy notice"
          description={data ? meta(data) : undefined}
          actions={
            <Link className="btn" to="/login">
              <Icon.Back /> Sign in
            </Link>
          }
        />
        {isLoading ? <PageSkeleton /> : error ? <ErrorCallout error={error} /> : data && <NoticeBody notice={data} />}
      </div>
    </div>
  )
}

/** Shown once after the first sign in, and again whenever the notice changes. */
export function AcceptPrivacyPage() {
  const me = useMe()
  const { logout } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { data, isLoading, error } = usePrivacyNotice()
  const [read, setRead] = useState(false)
  const [busy, setBusy] = useState(false)
  const [failure, setFailure] = useState<unknown>(null)

  async function accept() {
    if (!data) return
    setBusy(true)
    setFailure(null)
    try {
      await api.post('/api/auth/privacy/accept', { version: data.version })
      await queryClient.invalidateQueries({ queryKey: ['me'] })
      navigate(me.guideSeen ? '/' : '/guide', { replace: true })
    } catch (e) {
      setFailure(e)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="standalone">
      <div className="content" style={{ maxWidth: 880 }}>
        <PageHeader
          eyebrow={me.privacyNoticeVersion && me.guideSeen ? 'The notice has changed' : 'Before you begin'}
          title="How your data is used"
          description="Please read how MRMS handles your personal and health data. You can read it again at any time from Help in the menu."
        />
        {isLoading ? <PageSkeleton /> : error ? <ErrorCallout error={error} /> : data && <NoticeBody notice={data} />}
        {data && (
          <motion.div className="panel accept-bar" variants={item} initial="initial" animate="animate">
            <div className="panel-body stack">
              <Checkbox
                label={`I have read and understood the privacy notice (version ${data.version}).`}
                checked={read}
                onChange={(e) => setRead(e.target.checked)}
              />
              <ErrorCallout error={failure} />
              <div className="row-between">
                <Button variant="ghost" onClick={() => logout()}>
                  Sign out
                </Button>
                <Button variant="primary" icon={<Icon.Check />} disabled={!read} loading={busy} onClick={accept}>
                  Continue
                </Button>
              </div>
            </div>
          </motion.div>
        )}
      </div>
    </div>
  )
}
