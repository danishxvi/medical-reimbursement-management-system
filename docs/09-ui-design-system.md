# 9. UI Design System

**Author:** Danish Husain

## 9.1 Principles

1. **Saffron and white, one theme.** White surfaces on a warm white page, deep saffron for anything active or primary, bright saffron for accents. There is a single theme (no dark mode), so every office sees the same interface. Meaning never depends on colour alone: statuses also differ by border style and label, so the interface still works in greyscale print and for colour blind users.
2. **Boxy, with rounded edges.** Everything sits in a box: panels, stat tiles, form fields, buttons, tracker stages and wizard steps are separate boxes with rounded corners (14px for cards, 10px for controls, pills for status badges) and soft shadows.
3. **Symmetric.** Equal column grids (2, 3 or 4 across), stat blocks that always fill complete rows, identical padding on all sides of a panel.
4. **Everything moves, gently.** Every element has a short, purposeful animation, and all of it switches off when the user asks the operating system for reduced motion.
5. **Plain language.** Labels say what to do ("Correct and resubmit"), errors say how to fix it, and forms explain rules before they are broken.

## 9.2 Tokens (`frontend/src/styles/tokens.css`)

| Token | Value | Use |
|-------|-------|-----|
| `--bg` | `#fff7ee` | Page background (warm white) |
| `--surface` | `#ffffff` | Panels, inputs, tiles |
| `--surface-2` | `#fff4e8` | Table headers, hover rows, footers |
| `--ink` | `#2b1d12` | Text (16:1 on white) |
| `--muted` | `#735a45` | Secondary text (6.4:1 on white) |
| `--line` / `--line-strong` | `#f1dcc6` / `#e07b00` | Hairlines / emphasised borders |
| `--invert` / `--invert-ink` | `#c25400` / white | Deep saffron: active states, primary buttons, done badges (4.6:1, WCAG AA) |
| `--invert-hover` | `#a94700` | Pressed and hover state of deep saffron |
| `--accent` | `#ff9933` | Bright saffron: focus rings, progress fills, highlights; never small text |
| `--radius` / `--radius-md` / `--radius-sm` | 14px / 10px / 6px | Cards / controls / chips |

Type: **IBM Plex Sans** for text, **IBM Plex Mono** for numbers, codes and small capitals labels. Both are bundled with the app (no font CDN).

Spacing follows an 8px grid (`--s-1` 4px to `--s-8` 64px).

## 9.3 Status language

| Family | Border | Used for |
|--------|--------|----------|
| Open | dashed, hollow marker | Draft |
| Waiting | dashed, pulsing marker | With HoS, PAO scrutiny, awaiting sanction, with pharmacist or MO |
| Attention | double border | Returned, overdue, not admissible |
| Done | solid deep saffron | Sanctioned, paid, issued, not available (claimable) |
| Closed | grey, struck through | Rejected, withdrawn, available (not claimable) |

## 9.4 Motion

| Element | Animation |
|---------|-----------|
| Page | Fades and rises 12px in, fades up out (`AnimatePresence`, 320ms) |
| Lists, tables, tiles | Children stagger in, 45ms apart |
| Numbers | Count up from zero when they scroll into view |
| Buttons | Saffron fill sweeps from the left on hover, the button lifts 1px; 1px press |
| Inputs | A soft saffron ring grows around the field on focus; errors slide in |
| Navigation | Active background glides between links (shared layout) |
| Steps and tabs | Active block or underline glides between items |
| Tracker | Completed stages fill left to right in sequence |
| Modals | Backdrop fades, panel scales from 97% |
| Toasts | Slide in from the right, stack with layout animation |
| Cards and tiles | Stat tiles lift and fill with saffron on hover; page headings draw a saffron accent line |
| Loading | Warm shimmer skeletons |

Motion honours `prefers-reduced-motion` in two places: `MotionConfig reducedMotion="user"` for animations driven by JavaScript and a global CSS rule for transitions.

## 9.5 Layout

- Desktop: a saffron brand block top left, header across, sidebar navigation with rounded pill links, content up to 1280px wide.
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
- Contrast of body text on surfaces exceeds WCAG AA; white text only sits on deep saffron, never on bright saffron.
