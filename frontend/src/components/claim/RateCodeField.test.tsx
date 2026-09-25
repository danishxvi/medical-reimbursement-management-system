import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { api } from '../../api/client'
import { renderWithProviders } from '../../test/render'
import { RateCodeField } from './RateCodeField'

vi.mock('../../api/client', async (original) => {
  const actual = await original<typeof import('../../api/client')>()
  return { ...actual, api: { ...actual.api, get: vi.fn() } }
})

function Harness({ onPick }: { onPick: (name: string) => void }) {
  const [code, setCode] = useState('')
  return (
    <>
      <RateCodeField value={code} billDate="2026-01-15" onChange={setCode} onPick={(h) => onPick(h.name)} />
      <output data-testid="code">{code}</output>
    </>
  )
}

describe('RateCodeField', () => {
  it('searches the rate list by name for the bill date and fills the chosen code', async () => {
    vi.mocked(api.get).mockResolvedValue([
      { code: 'LB012', name: 'Complete Haemogram/CBC', speciality: 'Laboratory Investigation', nonNabh: 255, nabh: 300, superSpeciality: 300, listCode: 'CGHS-2025-T1' },
    ])
    const onPick = vi.fn()
    renderWithProviders(<Harness onPick={onPick} />)

    await userEvent.type(screen.getByRole('combobox'), 'haemo')
    const option = await screen.findByRole('option', { name: /LB012/ })
    expect(api.get).toHaveBeenCalledWith('/api/rates/search?q=haemo&date=2026-01-15')

    await userEvent.click(option)
    expect(screen.getByTestId('code')).toHaveTextContent('LB012')
    expect(onPick).toHaveBeenCalledWith('Complete Haemogram/CBC')
  })

  it('says so when nothing matches', async () => {
    vi.mocked(api.get).mockResolvedValue([])
    renderWithProviders(<Harness onPick={vi.fn()} />)
    await userEvent.type(screen.getByRole('combobox'), 'zz')
    expect(await screen.findByText(/No matching code/)).toBeInTheDocument()
  })
})
