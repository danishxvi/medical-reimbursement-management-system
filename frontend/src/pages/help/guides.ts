/* First login guides, one per role. Each step is short and ends with where to find the feature. */
import type { Role } from '../../api/types'

export type GuideIcon = 'grid' | 'pill' | 'file' | 'upload' | 'queue' | 'shield' | 'rupee' | 'clock' | 'bell' | 'users' | 'audit' | 'download'

export interface GuideStep {
  title: string
  icon: GuideIcon
  body: string[]
  where?: string
}

export interface Guide {
  intro: string
  steps: GuideStep[]
}

const help: GuideStep = {
  title: 'Help is always here',
  icon: 'bell',
  body: [
    'Notifications (the bell at the top) tell you when something needs you. Important ones also arrive by SMS and e-mail if your mobile number and e-mail are on record.',
    'This guide stays in the menu under Help, next to the privacy notice. Your session ends after 15 minutes without activity, so save drafts as you go.',
  ],
  where: 'Menu: Help, User guide',
}

const EMPLOYEE: Guide = {
  intro: 'File medical reimbursement claims online, track every step and correct anything returned without losing your place in the queue.',
  steps: [
    {
      title: 'Check your profile first',
      icon: 'grid',
      body: [
        'Your service details, DGEHS card and bank details are entered once by your office and fill every claim automatically.',
        'Check them, add your family members who are covered, and keep your mobile number and e-mail current so you receive updates.',
      ],
      where: 'Menu: Account, Profile and family',
    },
    {
      title: 'Get an e-NAC for medicines',
      icon: 'pill',
      body: [
        'Medicines bought outside are payable only if the dispensary certifies they were not available. Upload the prescription and list the medicines.',
        'The pharmacist marks each item, the Medical Officer countersigns, and you are notified. Only items marked "not available" can be claimed.',
      ],
      where: 'Menu: Reimbursement, e-NAC certificates',
    },
    {
      title: 'File a claim in four steps',
      icon: 'file',
      body: [
        'Patient and treatment, bills, documents, review. Each bill needs its PDF or photo. For tests and procedures you can pick the DGEHS code so the school can apply the approved rate.',
        'Save a draft at any time. File within 90 days of the end of treatment.',
      ],
      where: 'Menu: Reimbursement, New claim',
    },
    {
      title: 'Uploads are checked and renamed',
      icon: 'upload',
      body: [
        'PDF, JPEG and PNG up to 5 MB. Every file is scanned for viruses and stored encrypted. Files are renamed to a standard name (for example your ID, the document type and the date) so every office sees them the same way.',
      ],
    },
    {
      title: 'Track it, fix it, keep your place',
      icon: 'queue',
      body: [
        'Your claim shows each stage, who has it, its place in the queue and the time limit for that stage. Late stages are escalated automatically.',
        'If a claim is returned, only the flagged points need correcting. Resubmit and it goes back to the front of the queue, not the end.',
      ],
      where: 'Menu: Reimbursement, My claims',
    },
    {
      title: 'Download the complete claim',
      icon: 'download',
      body: ['Every claim can be downloaded as one PDF with all forms, the calculation sheet, certificates and a list of attached documents, for your records.'],
      where: 'Claim page: Download PDF',
    },
    help,
  ],
}

const HOS: Guide = {
  intro: 'Verify claims of your school in strict order, restrict amounts to DGEHS rates, sign the certificate and forward them to the PAO.',
  steps: [
    {
      title: 'Your queue is first come, first served',
      icon: 'queue',
      body: [
        'Press "Take next" to open the oldest waiting claim. You cannot pick claims out of order; this protects you and your staff from any charge of favouritism.',
        'Each stage has a time limit (3 days for the school). Reminders come before it runs out; after that the claim is flagged to the zonal office.',
      ],
      where: 'Menu: School, Verification queue',
    },
    {
      title: 'Calculation sheet with DGEHS rates',
      icon: 'rupee',
      body: [
        'For every bill the approved rate is suggested from the rate list when a DGEHS code is given, adjusted for hospital type and ward. Restrict each amount; a remark is required when you pay less than claimed.',
      ],
    },
    {
      title: 'Return only what needs fixing',
      icon: 'file',
      body: ['Use "Return for correction" with the standard reasons and a clear remark. The employee corrects only those points and the claim comes back to the front of your queue.'],
    },
    {
      title: 'Sign the certificate',
      icon: 'shield',
      body: [
        'Forwarding signs the Head of School certificate. Depending on how the portal is set up you confirm with your password or with Aadhaar eSign; either way your name and the time are recorded permanently.',
      ],
    },
    {
      title: 'Budget demand from real claims',
      icon: 'rupee',
      body: ['The demand for funds is calculated from claims actually in the pipeline. Forward it to the PAO in one click.'],
      where: 'Menu: School, Budget and demand',
    },
    help,
  ],
}

const PHARMACIST: Guide = {
  intro: 'Verify prescriptions item by item. Your decision on each medicine decides what the employee can claim.',
  steps: [
    {
      title: 'Take the next prescription',
      icon: 'queue',
      body: ['Prescriptions are handed out oldest first. Open the uploaded prescription and compare it with the items listed.'],
      where: 'Menu: Dispensary, Prescription queue',
    },
    {
      title: 'Decide every item',
      icon: 'pill',
      body: [
        'Available: given from stock, not claimable. Not available: may be bought and claimed. Not admissible: not allowed under DGEHS, a reason is required.',
        'Every decision carries your name and the time. Return the request if the prescription is unreadable or incomplete.',
      ],
    },
    {
      title: 'Time limits',
      icon: 'clock',
      body: ['Each prescription should be decided within 2 days. Late items are reminded and then escalated to the Medical Officer.'],
    },
    help,
  ],
}

const MEDICAL_OFFICER: Guide = {
  intro: 'Countersign the pharmacist\'s decisions. Your countersignature turns them into an electronic non availability certificate.',
  steps: [
    {
      title: 'Review the pharmacist\'s decisions',
      icon: 'queue',
      body: ['Take the next certificate, check each decision against the prescription and stock, and send it back to the same pharmacist if something is wrong.'],
      where: 'Menu: Dispensary, Prescription queue',
    },
    {
      title: 'Countersign',
      icon: 'shield',
      body: ['Countersigning issues the certificate with a number. You confirm with your password or Aadhaar eSign, and the record cannot be changed afterwards.'],
    },
    {
      title: 'Time limits',
      icon: 'clock',
      body: ['Countersign within 2 days. Overdue certificates are reminded and escalated.'],
    },
    help,
  ],
}

const PAO_AUDITOR: Guide = {
  intro: 'Scrutinise certified claims item by item and recommend sanction or rejection. A different officer takes the final decision.',
  steps: [
    {
      title: 'Scrutiny queue',
      icon: 'queue',
      body: ['Take the next claim certified by a school of your PAO. The documents, e-NAC links and the school\'s calculation sheet are all on one page.'],
      where: 'Menu: Pay and Accounts Office, Scrutiny queue',
    },
    {
      title: 'Admit amounts item by item',
      icon: 'rupee',
      body: ['Admit up to the school\'s restricted amount. Any amount disallowed needs a reason. The approved rate from the rate list is shown for reference.'],
    },
    {
      title: 'Return instead of reject',
      icon: 'file',
      body: ['Small mistakes are returned for correction with a reason; the claim keeps its seniority. Rejection is only for genuinely inadmissible claims.'],
    },
    {
      title: 'Time limits',
      icon: 'clock',
      body: ['Scrutiny should be finished within 7 days. Delays are reminded and then escalated, and they appear on the performance record of the office.'],
    },
    help,
  ],
}

const PAO_OFFICER: Guide = {
  intro: 'Take the final decision on scrutinised claims and release payments, oldest first, as funds arrive.',
  steps: [
    {
      title: 'Sanction queue',
      icon: 'queue',
      body: ['Claims scrutinised by an auditor wait here. You cannot sanction a claim you scrutinised yourself.'],
      where: 'Menu: Pay and Accounts Office, Sanction queue',
    },
    {
      title: 'Sanction, send back or reject',
      icon: 'shield',
      body: ['Sanctioning and rejecting are signed with your password or Aadhaar eSign. "Send back" returns the claim to the same auditor with your remarks.'],
    },
    {
      title: 'Allocations and payment runs',
      icon: 'rupee',
      body: ['Record funds received against the sanction order, then run payments. Claims are paid oldest first and the run stops at the first claim the balance cannot cover.'],
      where: 'Menu: Pay and Accounts Office, Budgets and payments',
    },
    help,
  ],
}

const ADMIN: Guide = {
  intro: 'Manage accounts and office data, watch time limits across offices, and verify the audit trail. You never see individual medical claims.',
  steps: [
    {
      title: 'Accounts',
      icon: 'users',
      body: ['Create official accounts and employees. A temporary password is shown once and must be changed at first sign in. Unlock or disable accounts when needed.'],
      where: 'Menu: Administration, Official accounts and Employees',
    },
    {
      title: 'Offices and rates',
      icon: 'grid',
      body: ['Maintain PAOs, schools and dispensaries, and load new DGEHS rate lists when an order revises them. A new list is imported inactive and takes effect only when you activate it.'],
      where: 'Menu: Administration, Offices and schools and DGEHS rate lists',
    },
    {
      title: 'Time limits',
      icon: 'clock',
      body: ['See every record past its time limit in every zone, who holds it, and each office\'s record of delays. Create Zonal Oversight accounts for each zone so delays are followed up locally.'],
      where: 'Menu: Administration, Time limits',
    },
    {
      title: 'Audit trail',
      icon: 'audit',
      body: ['Every action is recorded in a hash chained trail. "Verify chain" proves nothing was edited or deleted.'],
      where: 'Menu: Administration, Audit trail',
    },
    help,
  ],
}

const OVERSIGHT: Guide = {
  intro: 'Follow up delays in your zone. Claims and certificates that miss their time limit are escalated to you automatically.',
  steps: [
    {
      title: 'Escalations',
      icon: 'clock',
      body: ['See every overdue item in your zone with who holds it and for how long. Send a reminder in one click; each reminder is recorded, at most one an hour per record.'],
      where: 'Menu: Oversight, Time limits',
    },
    {
      title: 'Performance',
      icon: 'grid',
      body: ['Delays are kept on record per office and per stage, so recurring bottlenecks are visible. You see status and dates only, never medical documents.'],
    },
    help,
  ],
}

export const GUIDES: Record<Role, Guide> = {
  EMPLOYEE,
  HOS,
  PHARMACIST,
  MEDICAL_OFFICER,
  PAO_AUDITOR,
  PAO_OFFICER,
  ADMIN,
  OVERSIGHT,
}
