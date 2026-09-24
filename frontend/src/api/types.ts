/* Types mirroring the backend DTOs. Money arrives as JSON numbers. */

export type Role =
  | 'EMPLOYEE'
  | 'HOS'
  | 'PHARMACIST'
  | 'MEDICAL_OFFICER'
  | 'PAO_AUDITOR'
  | 'PAO_OFFICER'
  | 'ADMIN'

export interface Me {
  id: number
  username: string
  fullName: string
  role: Role
  roleLabel: string
  schoolId: number | null
  dispensaryId: number | null
  paoId: number | null
  mustChangePassword: boolean
}

export interface Office {
  type: 'SCHOOL' | 'DISPENSARY' | 'PAO' | 'DIRECTORATE'
  id?: number
  code?: string
  name: string
}

export interface Option {
  value: string
  label: string
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

// ---------------- Organisation ----------------

export interface OfficeRef {
  id: number
  code: string
  name: string
  address: string | null
}

export interface SchoolRef extends OfficeRef {
  district: string | null
  zone: string | null
  paoId: number
  paoName: string | null
}

export type Relation =
  | 'SELF'
  | 'SPOUSE'
  | 'SON'
  | 'DAUGHTER'
  | 'FATHER'
  | 'MOTHER'
  | 'FATHER_IN_LAW'
  | 'MOTHER_IN_LAW'
  | 'OTHER_DEPENDENT'

export interface Dependent {
  id: number
  fullName: string
  relation: Relation
  dateOfBirth: string | null
  active: boolean
}

export interface EmployeeProfile {
  userId: number
  employeeCode: string
  fullName: string
  email: string | null
  mobile: string | null
  designation: string
  payScale: string | null
  payLevel: string | null
  basicPay: number | null
  dgehsCardNo: string | null
  dgehsCardPlace: string | null
  dgehsValidFrom: string | null
  dgehsValidTo: string | null
  wardEntitlement: string | null
  dateOfBirth: string | null
  dateOfJoining: string | null
  gender: string | null
  residentialAddress: string | null
  phoneOffice: string | null
  phoneResidence: string | null
  bankName: string | null
  bankBranch: string | null
  bankAccountMasked: string | null
  ifsc: string | null
  micr: string | null
  school: SchoolRef | null
  dependents: Dependent[]
}

// ---------------- Documents ----------------

export type DocumentCategory =
  | 'PRESCRIPTION'
  | 'BILL'
  | 'DGEHS_CARD'
  | 'REFERRAL'
  | 'DISCHARGE_SUMMARY'
  | 'LAB_BREAKUP'
  | 'DRUG_BREAKUP'
  | 'INVESTIGATION_REPORT'
  | 'EMERGENCY_CERTIFICATE'
  | 'EMERGENCY_LETTER'
  | 'NAC_SCAN'
  | 'CANCELLED_CHEQUE'
  | 'AFFIDAVIT'
  | 'DEATH_CERTIFICATE'
  | 'OTHER'

export interface DocumentMeta {
  id: string
  ownerUserId: number
  category: DocumentCategory
  originalName: string
  contentType: string
  sizeBytes: number
  sha256: string
  uploadedAt: string
}

// ---------------- e-NAC ----------------

export type NacStatus = 'PENDING_PHARMACIST' | 'PENDING_MEDICAL_OFFICER' | 'ISSUED' | 'RETURNED'
export type NacItemType = 'MEDICINE' | 'INVESTIGATION' | 'OTHER'
export type NacDecision = 'AVAILABLE' | 'NOT_AVAILABLE' | 'NOT_ADMISSIBLE'

export interface NacItem {
  id: number
  lineNo: number
  itemName: string
  itemType: NacItemType
  quantity: string
  decision: NacDecision | null
  decisionReason: string | null
  decidedBy: string | null
  decidedAt: string | null
}

export interface Nac {
  id: number
  nacNumber: string | null
  status: NacStatus
  employeeUserId: number
  employeeName: string | null
  dispensaryId: number
  dispensaryName: string | null
  patientName: string
  patientRelation: Relation
  prescriptionDate: string
  prescribedBy: string
  prescription: DocumentMeta | null
  items: NacItem[]
  createdAt: string
  queueSince: string
  stageEnteredAt: string
  queuePosition: number | null
  overdue: boolean
  assignedTo: string | null
  assignedToMe: boolean
  pharmacistName: string | null
  pharmacistAt: string | null
  pharmacistRemarks: string | null
  medicalOfficerName: string | null
  medicalOfficerAt: string | null
  medicalOfficerRemarks: string | null
  returnRemarks: string | null
  issuedAt: string | null
}

export interface NacSummary {
  id: number
  nacNumber: string | null
  status: NacStatus
  employeeName: string | null
  patientName: string
  patientRelation: Relation
  dispensaryName: string | null
  prescriptionDate: string
  itemCount: number
  createdAt: string
  queueSince: string
  stageEnteredAt: string
  overdue: boolean
  assignedTo: string | null
}

export interface NacQueue {
  stage: NacStatus
  slaDays: number
  current: Nac | null
  waiting: NacSummary[]
}

// ---------------- Claims ----------------

export type ClaimStatus =
  | 'DRAFT'
  | 'PENDING_HOS'
  | 'RETURNED_BY_HOS'
  | 'PENDING_PAO_AUDIT'
  | 'RETURNED_BY_PAO'
  | 'PENDING_SANCTION'
  | 'SANCTIONED'
  | 'PAID'
  | 'REJECTED'
  | 'WITHDRAWN'

export type TreatmentType = 'OPD' | 'INDOOR'
export type HospitalType = 'GOVERNMENT' | 'EMPANELLED' | 'PRIVATE'
export type ItemCategory = 'CONSULTATION' | 'INVESTIGATION' | 'MEDICINE' | 'OTHER'

export interface NacLink {
  nacRequestId: number
  nacNumber: string | null
  itemName: string
  decision: NacDecision | null
  dispensaryName: string | null
  prescriptionDocumentId: string
  prescriptionDate: string
}

export interface ClaimItem {
  id: number
  lineNo: number
  category: ItemCategory
  description: string
  billNumber: string
  billDate: string
  vendorName: string
  dgehsCode: string | null
  amountClaimed: number
  dgehsRate: number | null
  amountRestricted: number | null
  hosRemarks: string | null
  amountAdmitted: number | null
  disallowReason: string | null
  nacItemId: number | null
  legacyNac: boolean
  nac: NacLink | null
  billDocument: DocumentMeta | null
}

export interface ChecklistEntry {
  code: string
  label: string
  submitted: boolean
  required: boolean
}

export interface TimelineEntry {
  action: string
  fromStatus: ClaimStatus | null
  toStatus: ClaimStatus
  actorName: string
  actorRole: string
  remarks: string | null
  reasons: string[]
  occurredAt: string
}

export type ClaimAction =
  | 'EDIT'
  | 'SUBMIT'
  | 'DELETE'
  | 'WITHDRAW'
  | 'FORWARD'
  | 'RETURN'
  | 'RELEASE'
  | 'RECOMMEND'
  | 'SANCTION'
  | 'SEND_BACK'
  | 'REJECT'

export interface Claim {
  id: number
  claimNumber: string | null
  status: ClaimStatus
  statusLabel: string
  employee: EmployeeProfile | null
  patientName: string
  patientRelation: Relation
  dependentId: number | null
  illnessDescription: string
  treatmentType: TreatmentType
  treatmentFrom: string
  treatmentTo: string
  admissionDate: string | null
  dischargeDate: string | null
  hospitalName: string
  hospitalAddress: string | null
  hospitalType: HospitalType
  emergency: boolean
  referralDetails: string | null
  medicalAdvanceDetails: string | null
  claimedAmount: number
  restrictedAmount: number | null
  admittedAmount: number | null
  totalsByCategory: Record<ItemCategory, { OPD: number; INDOOR: number }>
  items: ClaimItem[]
  attachments: { category: DocumentCategory; document: DocumentMeta | null }[]
  checklist: ChecklistEntry[]
  timeline: TimelineEntry[]
  financialYear: string | null
  createdAt: string
  firstSubmittedAt: string | null
  lastSubmittedAt: string | null
  stageEnteredAt: string | null
  queuePosition: number | null
  overdue: boolean
  slaDays: number
  assignedTo: string | null
  assignedToMe: boolean
  returnCount: number
  hosCertifiedBy: string | null
  hosCertifiedAt: string | null
  auditedBy: string | null
  auditedAt: string | null
  auditRecommendation: 'SANCTION' | 'REJECT' | null
  sanctionedBy: string | null
  sanctionedAt: string | null
  paidAt: string | null
  paymentBatchRef: string | null
  rejectionReason: string | null
  allowedActions: ClaimAction[]
}

export interface ClaimSummary {
  id: number
  claimNumber: string | null
  status: ClaimStatus
  statusLabel: string
  employeeName: string | null
  patientName: string
  patientRelation: Relation
  treatmentType: TreatmentType
  claimedAmount: number
  restrictedAmount: number | null
  admittedAmount: number | null
  createdAt: string
  firstSubmittedAt: string | null
  stageEnteredAt: string | null
  overdue: boolean
  assignedTo: string | null
  returnCount: number
}

export interface ClaimQueue {
  stage: ClaimStatus
  stageLabel: string
  slaDays: number
  current: Claim | null
  waiting: ClaimSummary[]
}

export interface ClaimableNacItem {
  nacItemId: number
  nacRequestId: number
  nacNumber: string
  itemName: string
  quantity: string
  patientName: string
  patientRelation: Relation
  dependentId: number | null
  prescriptionDate: string
  issuedAt: string
}

export interface ClaimMeta {
  treatmentTypes: Option[]
  hospitalTypes: Option[]
  itemCategories: Option[]
  returnReasons: Option[]
  documentCategories: Option[]
  undertaking: string[]
  hosCertificate: string[]
  submissionWindowDays: number
  maxItems: number
}

export interface ItemInput {
  category: ItemCategory
  description: string
  billNumber: string
  billDate: string
  vendorName: string
  dgehsCode?: string | null
  amountClaimed: number
  nacItemId?: number | null
  legacyNac: boolean
  billDocumentId: string
}

export interface ClaimInput {
  dependentId: number | null
  illnessDescription: string
  treatmentType: TreatmentType
  treatmentFrom: string
  treatmentTo: string
  admissionDate?: string | null
  dischargeDate?: string | null
  hospitalName: string
  hospitalAddress?: string | null
  hospitalType: HospitalType
  emergency: boolean
  referralDetails?: string | null
  medicalAdvanceDetails?: string | null
  items: ItemInput[]
  attachments: { documentId: string; category: DocumentCategory }[]
}

// ---------------- Budget ----------------

export interface SchoolPosition {
  schoolId: number
  financialYear: string
  allocated: number
  paid: number
  balance: number
  pendingCount: number
  pipelinePending: number
  awaitingFundsCount: number
  awaitingFunds: number
  suggestedDemand: number
}

export interface Demand {
  id: number
  schoolId: number
  schoolName: string | null
  financialYear: string
  amount: number
  claimCount: number
  status: 'RAISED' | 'ACKNOWLEDGED' | 'SUPERSEDED'
  raisedBy: string | null
  raisedAt: string
  acknowledgedBy: string | null
  acknowledgedAt: string | null
}

export interface Allocation {
  id: number
  financialYear: string
  amount: number
  sanctionOrderNo: string
  remarks: string | null
  allocatedBy: string | null
  allocatedAt: string
}

export interface PaymentBatch {
  batchRef: string
  financialYear: string
  totalAmount: number
  claimCount: number
  createdAt: string
}

export interface PayableClaim {
  claimId: number
  claimNumber: string
  employeeName: string | null
  admittedAmount: number
  firstSubmittedAt: string
  sanctionedAt: string
}

// ---------------- Dashboard ----------------

export interface ClaimSnapshot {
  countsByStatus: Record<ClaimStatus, number>
  claimedTotal: number
  paidTotal: number
  overdueByStage: Partial<Record<ClaimStatus, number>>
  averageDaysToPay: number | null
}

export interface Dashboard {
  role: Role
  financialYear: string
  claims?: ClaimSnapshot
  certificates?: Record<string, number>
  budget?: SchoolPosition
  schools?: number
  openDemands?: number
  users?: Record<Role, number>
}

// ---------------- Notifications ----------------

export interface Notification {
  id: number
  title: string
  message: string
  link: string | null
  read: boolean
  createdAt: string
}

// ---------------- Admin ----------------

export interface AccountSummary {
  id: number
  username: string
  fullName: string
  role: Role
  email: string | null
  mobile: string | null
  schoolId: number | null
  dispensaryId: number | null
  paoId: number | null
  enabled: boolean
  locked: boolean
  lastLoginAt: string | null
}

export interface CreatedAccount {
  id: number
  username: string
  temporaryPassword: string | null
}

export interface AuditEntry {
  id: number
  occurredAt: string
  actorUsername: string | null
  actorRole: string | null
  action: string
  entityType: string
  entityId: string | null
  details: string | null
  ipAddress: string | null
  hash: string
}
