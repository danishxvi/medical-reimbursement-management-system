# 9. UI Design System

**Author:** Danish Husain

## 9.1 Principles

1. **Monochrome.** Black, white and greys only. Meaning never depends on colour: statuses differ by border style and label, so the interface works in greyscale print, for colour blind users and in dark mode.
2. **Boxy.** Square corners everywhere (`--radius: 0`), 1px rules, tiles that share borders.
3. **Symmetric.** Equal column grids (2, 3 or 4 across), stat blocks that always fill complete rows, identical padding on all sides of a panel.
4. **Everything moves, gently.** Every element has a short, purposeful animation, and all of it switches off when the user asks the operating system for reduced motion.
5. **Plain language.** Labels say what to do ("Correct and resubmit"), errors say how to fix it, and forms explain rules before they are broken.

## 9.2 Tokens (`frontend/src/styles/tokens.css`)

| Token | Light | Dark | Use |
|-------|-------|------|-----|
| `--bg` | `#f2f2ef` | `#0d0d0d` | Page background |
| `--surface` | `#ffffff` | `#141414` | Panels, inputs |
| `--ink` | `#0b0b0b` | `#f2f2ef` | Text, strong rules |
| `--muted` | `#666663` | `#9c9c98` | Secondary text |
| `--line` | `#d6d6d1` | `#2d2d2b` | Hairlines |
| `--invert` / `--invert-ink` | black / white | white / black | Active states, primary buttons |

Type: **IBM Plex Sans** for text, **IBM Plex Mono** for numbers, codes and small capitals labels. Both are bundled with the app (no font CDN).

Spacing follows an 8px grid (`--s-1` 4px to `--s-8` 64px).

## 9.3 Status language

| Family | Border | Used for |
|--------|--------|----------|
| Open | dashed, hollow marker | Draft |
| Waiting | dashed, pulsing marker | With HoS, PAO scrutiny, awaiting sanction, with pharmacist or MO |
| Attention | double border | Returned, overdue, not admissible |
| Done | solid inverted | Sanctioned, paid, issued, not available (claimable) |
| Closed | grey, struck through | Rejected, withdrawn, available (not claimable) |

## 9.4 Motion

| Element | Animation |
|---------|-----------|
| Page | Fades and rises 12px in, fades up out (`AnimatePresence`, 320ms) |
| Lists, tables, tiles | Children stagger in, 45ms apart |
| Numbers | Count up from zero when they scroll into view |
| Buttons | Ink fill sweeps from the left on hover; 1px press |
| Inputs | Underline grows from the left on focus; errors slide in |
| Navigation | Active background glides between links (shared layout) |
| Steps and tabs | Active block or underline glides between items |
| Tracker | Completed stages fill left to right in sequence |
| Modals | Backdrop fades, panel scales from 97% |
| Toasts | Slide in from the right, stack with layout animation |
| Theme toggle | Icon rotates in |
| Loading | Monochrome shimmer skeletons |

Motion honours `prefers-reduced-motion` in two places: `MotionConfig reducedMotion="user"` for animations driven by JavaScript and a global CSS rule for transitions.

## 9.5 Layout

- Desktop: a black brand square top left, header across, sidebar navigation, content up to 1280px wide.
- Phone: header, a single scrollable row of navigation, one column content; tables scroll sideways inside their panel, never the page.
- Every page starts with a page header: small capitals eyebrow, title, one sentence of purpose and the page's actions on the right.

## 9.6 Components (`frontend/src/components/ui`)

`Button`, `TextField`, `SelectField`, `TextAreaField`, `Checkbox`, `Segmented`, `Panel`, `PageHeader`, `Stat` and `StatGrid`, `Details`, `DataTable`, `Badge` and status badges, `Callout`, `EmptyState`, `Skeleton`, `Modal`, `PasswordConfirm`, `Toast`, `Tracker`, `Timeline`, `Bars`, `FileUpload`, `DocumentChip`.

## 9.7 Accessibility checklist

- All controls are real buttons, links and inputs, reachable with the keyboard, with visible focus outlines.
- Form fields have labels, hints and error messages linked with `aria-describedby`; errors use `role="alert"`.
- Modals trap focus, close on Escape and return focus to the trigger.
- Clickable table rows also respond to Enter and Space.
- A "Skip to content" link is the first focusable element.
- Contrast of body text on surfaces exceeds WCAG AA in both themes.
