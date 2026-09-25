import { describe, expect, it } from 'vitest'
import { fileSize, formatDate, formatMoney, initials } from './format'

describe('format', () => {
  it('shows money in rupees with Indian digit grouping', () => {
    expect(formatMoney(123456.5)).toContain('1,23,456.5')
    expect(formatMoney(123456.5)).toContain('₹')
    expect(formatMoney(null)).toBe('-')
  })

  it('reads plain dates as Indian dates, whatever the machine time zone', () => {
    expect(formatDate('2026-09-25')).toMatch(/25 Sept? 2026/)
    expect(formatDate(null)).toBe('-')
  })

  it('makes initials without honorifics', () => {
    expect(initials('Dr. Neha Singh')).toBe('NS')
    expect(initials('Asha Verma')).toBe('AV')
  })

  it('sizes files in readable units', () => {
    expect(fileSize(512)).toBe('512 B')
    expect(fileSize(2048)).toBe('2 KB')
    expect(fileSize(3 * 1024 * 1024)).toBe('3.0 MB')
  })
})
