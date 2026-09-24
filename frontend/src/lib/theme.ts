/* Light / dark preference. Stored per browser; the page works without it. */

export type Theme = 'light' | 'dark'
const KEY = 'mrms-theme'

export function storedTheme(): Theme | null {
  try {
    const value = localStorage.getItem(KEY)
    return value === 'light' || value === 'dark' ? value : null
  } catch {
    return null
  }
}

export function effectiveTheme(): Theme {
  return storedTheme() ?? (window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')
}

export function applyTheme(theme: Theme | null): void {
  if (theme) document.documentElement.dataset.theme = theme
  else delete document.documentElement.dataset.theme
}

export function saveTheme(theme: Theme): void {
  try {
    localStorage.setItem(KEY, theme)
  } catch {
    // Storage may be blocked; the choice then lasts for this page view only
  }
  applyTheme(theme)
}
