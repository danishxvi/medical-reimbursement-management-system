import type { ClaimStatus, NacDecision, NacStatus, Relation, Role } from '../api/types'

const money = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 })
const moneyShort = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 })
const date = new Intl.DateTimeFormat('en-IN', { day: '2-digit', month: 'short', year: 'numeric', timeZone: 'Asia/Kolkata' })
const dateTime = new Intl.DateTimeFormat('en-IN', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  timeZone: 'Asia/Kolkata',
})

export function formatMoney(value: number | null | undefined, short = false): string {
  if (value === null || value === undefined) return '-'
  return (short ? moneyShort : money).format(value)
}

/** Dates from the API are ISO strings: plain dates (YYYY-MM-DD) or instants. */
export function formatDate(value: string | null | undefined): string {
  if (!value) return '-'
  const d = value.length === 10 ? new Date(value + 'T00:00:00+05:30') : new Date(value)
  return date.format(d)
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '-'
  return dateTime.format(new Date(value))
}

/** "3 days" style age, used for queue waiting times. */
export function ageOf(value: string | null | undefined): string {
  if (!value) return '-'
  const ms = Date.now() - new Date(value).getTime()
  const hours = Math.floor(ms / 3_600_000)
  if (hours < 1) return 'under an hour'
  if (hours < 24) return hours + (hours === 1 ? ' hour' : ' hours')
  const days = Math.floor(hours / 24)
  return days + (days === 1 ? ' day' : ' days')
}

export function todayIso(): string {
  const now = new Date()
  const offset = now.getTimezoneOffset() * 60_000
  return new Date(now.getTime() - offset).toISOString().substring(0, 10)
}

export function daysAgoIso(days: number): string {
  const d = new Date(Date.now() - days * 86_400_000)
  const offset = d.getTimezoneOffset() * 60_000
  return new Date(d.getTime() - offset).toISOString().substring(0, 10)
}

export function fileSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(0) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

export function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter((p) => /^[A-Za-z]/.test(p) && !/^(dr|mr|mrs|ms|shri|smt)\.?$/i.test(p))
    .slice(0, 2)
    .map((p) => p[0].toUpperCase())
    .join('')
}

export const ROLE_LABELS: Record<Role, string> = {
  EMPLOYEE: 'Employee',
  HOS: 'Head of School',
  PHARMACIST: 'Pharmacist',
  MEDICAL_OFFICER: 'Medical Officer',
  PAO_AUDITOR: 'PAO Auditor',
  PAO_OFFICER: 'PAO Officer',
  ADMIN: 'Administrator',
  OVERSIGHT: 'Zonal Oversight Officer',
}

export const RELATION_LABELS: Record<Relation, string> = {
  SELF: 'Self',
  SPOUSE: 'Spouse',
  SON: 'Son',
  DAUGHTER: 'Daughter',
  FATHER: 'Father',
  MOTHER: 'Mother',
  FATHER_IN_LAW: 'Father in law',
  MOTHER_IN_LAW: 'Mother in law',
  OTHER_DEPENDENT: 'Other dependent',
}

export const CLAIM_STATUS_LABELS: Record<ClaimStatus, string> = {
  DRAFT: 'Draft',
  PENDING_HOS: 'With HoS',
  RETURNED_BY_HOS: 'Returned by HoS',
  PENDING_PAO_AUDIT: 'PAO scrutiny',
  RETURNED_BY_PAO: 'Returned by PAO',
  PENDING_SANCTION: 'Awaiting sanction',
  SANCTIONED: 'Sanctioned',
  PAID: 'Paid',
  REJECTED: 'Rejected',
  WITHDRAWN: 'Withdrawn',
}

export const NAC_STATUS_LABELS: Record<NacStatus, string> = {
  PENDING_PHARMACIST: 'With pharmacist',
  PENDING_MEDICAL_OFFICER: 'With Medical Officer',
  ISSUED: 'Issued',
  RETURNED: 'Returned',
}

export const DECISION_LABELS: Record<NacDecision, string> = {
  AVAILABLE: 'Available (given)',
  NOT_AVAILABLE: 'Not available',
  NOT_ADMISSIBLE: 'Not admissible',
}

/** Visual family of a status: drives the badge's border style. */
export type Tone = 'open' | 'wait' | 'attention' | 'done' | 'closed'

export function claimTone(status: ClaimStatus): Tone {
  switch (status) {
    case 'DRAFT':
      return 'open'
    case 'RETURNED_BY_HOS':
    case 'RETURNED_BY_PAO':
      return 'attention'
    case 'PAID':
    case 'SANCTIONED':
      return 'done'
    case 'REJECTED':
    case 'WITHDRAWN':
      return 'closed'
    default:
      return 'wait'
  }
}

export function nacTone(status: NacStatus): Tone {
  switch (status) {
    case 'ISSUED':
      return 'done'
    case 'RETURNED':
      return 'attention'
    default:
      return 'wait'
  }
}
