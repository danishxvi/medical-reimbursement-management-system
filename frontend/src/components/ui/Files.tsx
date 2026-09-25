import { AnimatePresence, motion } from 'motion/react'
import { useState } from 'react'
import { api, openDocument } from '../../api/client'
import type { DocumentCategory, DocumentMeta } from '../../api/types'
import { fileSize } from '../../lib/format'
import { Icon } from '../../lib/icons'
import { pop } from '../../lib/motion'
import { useToast } from './Toast'

const MAX_BYTES = 5 * 1024 * 1024
const ACCEPT = '.pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png'

interface UploadProps {
  category: DocumentCategory
  label?: string
  onUploaded: (doc: DocumentMeta) => void
  compact?: boolean
}

/**
 * Drag and drop or click to upload. Checks type and size in the browser
 * for fast feedback; the server re-checks the real file content.
 */
export function FileUpload({ category, label = 'Drop a PDF or photo here, or click to choose', onUploaded, compact }: UploadProps) {
  const toast = useToast()
  const [busy, setBusy] = useState(false)
  const [dragging, setDragging] = useState(false)

  async function send(file: File | undefined) {
    if (!file) return
    if (file.size > MAX_BYTES) {
      toast.error('The file is larger than 5 MB. Please scan at a lower resolution.')
      return
    }
    if (!/\.(pdf|jpe?g|png)$/i.test(file.name)) {
      toast.error('Only PDF, JPEG and PNG files are accepted.')
      return
    }
    const form = new FormData()
    form.append('file', file)
    form.append('category', category)
    setBusy(true)
    try {
      const doc = await api.upload<DocumentMeta>('/api/documents', form)
      onUploaded(doc)
      toast.ok('Uploaded ' + doc.originalName)
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Upload failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <label
      className={'upload' + (dragging ? ' dragging' : '')}
      style={compact ? { minHeight: 64 } : undefined}
      onDragOver={(e) => {
        e.preventDefault()
        setDragging(true)
      }}
      onDragLeave={() => setDragging(false)}
      onDrop={(e) => {
        e.preventDefault()
        setDragging(false)
        send(e.dataTransfer.files?.[0])
      }}
    >
      <input
        type="file"
        accept={ACCEPT}
        disabled={busy}
        onChange={(e) => {
          send(e.target.files?.[0])
          e.target.value = ''
        }}
      />
      {busy ? (
        <span className="row">
          <span className="spinner" aria-hidden="true" />
          Uploading and checking the file
        </span>
      ) : (
        <span className="row" style={{ justifyContent: 'center' }}>
          <Icon.Upload width={18} height={18} />
          <span>{label}</span>
        </span>
      )}
      {!compact && <span className="caps muted">PDF, JPEG or PNG, up to 5 MB</span>}
    </label>
  )
}

interface ChipProps {
  doc: DocumentMeta
  /** Download URL; defaults to the owner endpoint. */
  href?: string
  /** Name to show and save as (for example the name inside a claim); defaults to the standard name. */
  name?: string
  onRemove?: () => void
}

/** A stored document with view and download actions. */
export function DocumentChip({ doc, href, name, onRemove }: ChipProps) {
  const toast = useToast()
  const url = href ?? `/api/documents/${doc.id}/content`
  const label = name ?? doc.standardName
  const open = (download: boolean) =>
    openDocument(url, label, download).catch((e) => toast.error(e instanceof Error ? e.message : 'Could not open'))
  return (
    <AnimatePresence>
      <motion.div className="file-chip" variants={pop} initial="initial" animate="animate" exit="exit">
        <span className="row" style={{ minWidth: 0, flexWrap: 'nowrap' }}>
          <Icon.File width={16} height={16} />
          <span className="name" title={'Uploaded as ' + doc.originalName}>
            {label}
          </span>
          {doc.scanStatus === 'CLEAN' && (
            <span className="scan-ok" title="Scanned for viruses: clean" aria-label="Virus scan clean">
              <Icon.Shield width={12} height={12} />
            </span>
          )}
          <span className="caps muted">{fileSize(doc.sizeBytes)}</span>
        </span>
        <span className="row" style={{ flexWrap: 'nowrap', gap: 4 }}>
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => open(false)} aria-label={'View ' + label}>
            <Icon.Eye />
          </button>
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => open(true)} aria-label={'Download ' + label}>
            <Icon.Download />
          </button>
          {onRemove && (
            <button type="button" className="btn btn-ghost btn-sm" onClick={onRemove} aria-label={'Remove ' + label}>
              <Icon.X />
            </button>
          )}
        </span>
      </motion.div>
    </AnimatePresence>
  )
}
