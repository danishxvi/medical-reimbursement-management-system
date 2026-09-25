import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { DocumentMeta } from '../../api/types'
import { renderWithProviders } from '../../test/render'
import { DocumentChip } from './Files'

const doc: DocumentMeta = {
  id: 'a1',
  ownerUserId: 1,
  category: 'BILL',
  standardName: 'EMP1001_BILL_20260925_01.pdf',
  originalName: 'scan (final).pdf',
  contentType: 'application/pdf',
  sizeBytes: 2048,
  sha256: 'ab',
  uploadedAt: '2026-09-25T10:00:00Z',
  scanStatus: 'CLEAN',
}

describe('DocumentChip', () => {
  it('shows the standard name, keeps the uploaded name as a hint, and marks a clean scan', () => {
    renderWithProviders(<DocumentChip doc={doc} />)
    expect(screen.getByText('EMP1001_BILL_20260925_01.pdf')).toHaveAttribute('title', 'Uploaded as scan (final).pdf')
    expect(screen.getByLabelText('Virus scan clean')).toBeInTheDocument()
  })

  it('uses the name inside a claim when given', () => {
    renderWithProviders(<DocumentChip doc={{ ...doc, scanStatus: 'NOT_SCANNED' }} name="MR-9900001-2026-27-000001_BILL_01.pdf" />)
    expect(screen.getByText('MR-9900001-2026-27-000001_BILL_01.pdf')).toBeInTheDocument()
    expect(screen.queryByLabelText('Virus scan clean')).not.toBeInTheDocument()
  })
})
