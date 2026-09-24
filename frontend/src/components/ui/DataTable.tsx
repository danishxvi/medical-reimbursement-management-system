import { motion } from 'motion/react'
import type { ReactNode } from 'react'
import { item, list } from '../../lib/motion'

export interface Column<T> {
  key: string
  header: ReactNode
  render: (row: T, index: number) => ReactNode
  align?: 'left' | 'right'
  width?: string
}

interface Props<T> {
  columns: Column<T>[]
  rows: T[]
  rowKey: (row: T) => string | number
  onRowClick?: (row: T) => void
  footer?: ReactNode
  caption?: string
}

/** Table whose rows stagger in; rows become keyboard accessible when clickable. */
export function DataTable<T>({ columns, rows, rowKey, onRowClick, footer, caption }: Props<T>) {
  return (
    <div className="table-wrap">
      <table className="table">
        {caption && <caption className="sr-only">{caption}</caption>}
        <thead>
          <tr>
            {columns.map((c) => (
              <th key={c.key} className={c.align === 'right' ? 'right' : undefined} style={{ width: c.width }}>
                {c.header}
              </th>
            ))}
          </tr>
        </thead>
        <motion.tbody variants={list} initial="initial" animate="animate">
          {rows.map((row, i) => (
            <motion.tr
              key={rowKey(row)}
              variants={item}
              className={onRowClick ? 'clickable' : undefined}
              onClick={onRowClick ? () => onRowClick(row) : undefined}
              onKeyDown={
                onRowClick
                  ? (e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault()
                        onRowClick(row)
                      }
                    }
                  : undefined
              }
              tabIndex={onRowClick ? 0 : undefined}
            >
              {columns.map((c) => (
                <td key={c.key} className={c.align === 'right' ? 'right' : undefined}>
                  {c.render(row, i)}
                </td>
              ))}
            </motion.tr>
          ))}
        </motion.tbody>
        {footer && <tfoot>{footer}</tfoot>}
      </table>
    </div>
  )
}
