/* Minimal line icons with rounded ends, drawn inline (no icon font, no external requests). */
import type { ReactNode, SVGProps } from 'react'

type IconProps = SVGProps<SVGSVGElement>

function Svg(props: IconProps & { children: ReactNode }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.6}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      {...props}
    />
  )
}

export const Icon = {
  Cross: (p: IconProps) => (
    <Svg {...p}>
      <path d="M12 4v16M4 12h16" />
    </Svg>
  ),
  Grid: (p: IconProps) => (
    <Svg {...p}>
      <rect x="3" y="3" width="7" height="7" />
      <rect x="14" y="3" width="7" height="7" />
      <rect x="3" y="14" width="7" height="7" />
      <rect x="14" y="14" width="7" height="7" />
    </Svg>
  ),
  File: (p: IconProps) => (
    <Svg {...p}>
      <path d="M6 3h8l4 4v14H6z" />
      <path d="M14 3v4h4M9 12h6M9 16h6" />
    </Svg>
  ),
  Plus: (p: IconProps) => (
    <Svg {...p}>
      <path d="M12 5v14M5 12h14" />
    </Svg>
  ),
  Pill: (p: IconProps) => (
    <Svg {...p}>
      <rect x="3" y="9" width="18" height="6" />
      <path d="M12 9v6" />
    </Svg>
  ),
  Queue: (p: IconProps) => (
    <Svg {...p}>
      <path d="M4 6h16M4 12h16M4 18h10" />
    </Svg>
  ),
  Bell: (p: IconProps) => (
    <Svg {...p}>
      <path d="M6 17V11a6 6 0 0 1 12 0v6l2 2H4z" />
      <path d="M10 21h4" />
    </Svg>
  ),
  User: (p: IconProps) => (
    <Svg {...p}>
      <rect x="8" y="3" width="8" height="8" />
      <path d="M4 21v-4h16v4" />
    </Svg>
  ),
  Users: (p: IconProps) => (
    <Svg {...p}>
      <rect x="4" y="4" width="6" height="6" />
      <rect x="14" y="4" width="6" height="6" />
      <path d="M2 20v-4h10v4M12 20v-4h10v4" />
    </Svg>
  ),
  Building: (p: IconProps) => (
    <Svg {...p}>
      <path d="M4 21V5h10v16M14 9h6v12M2 21h20" />
      <path d="M8 9h2M8 13h2M8 17h2" />
    </Svg>
  ),
  Rupee: (p: IconProps) => (
    <Svg {...p}>
      <path d="M6 4h12M6 9h12M9 4c5 0 5 10 0 10H6l8 7" />
    </Svg>
  ),
  Shield: (p: IconProps) => (
    <Svg {...p}>
      <path d="M12 3l8 3v6c0 5-4 8-8 9-4-1-8-4-8-9V6z" />
      <path d="M9 12l2 2 4-4" />
    </Svg>
  ),
  Logout: (p: IconProps) => (
    <Svg {...p}>
      <path d="M14 4H4v16h10M10 12h10M17 8l4 4-4 4" />
    </Svg>
  ),
  Arrow: (p: IconProps) => (
    <Svg {...p}>
      <path d="M4 12h15M14 7l5 5-5 5" />
    </Svg>
  ),
  Back: (p: IconProps) => (
    <Svg {...p}>
      <path d="M20 12H5M10 7l-5 5 5 5" />
    </Svg>
  ),
  Check: (p: IconProps) => (
    <Svg {...p}>
      <path d="M4 12l5 5L20 6" />
    </Svg>
  ),
  X: (p: IconProps) => (
    <Svg {...p}>
      <path d="M5 5l14 14M19 5L5 19" />
    </Svg>
  ),
  Upload: (p: IconProps) => (
    <Svg {...p}>
      <path d="M12 16V4M7 9l5-5 5 5M4 20h16" />
    </Svg>
  ),
  Eye: (p: IconProps) => (
    <Svg {...p}>
      <path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z" />
      <rect x="10" y="10" width="4" height="4" />
    </Svg>
  ),
  Download: (p: IconProps) => (
    <Svg {...p}>
      <path d="M12 4v12M7 11l5 5 5-5M4 20h16" />
    </Svg>
  ),
  Lock: (p: IconProps) => (
    <Svg {...p}>
      <rect x="5" y="10" width="14" height="11" />
      <path d="M8 10V7a4 4 0 0 1 8 0v3" />
    </Svg>
  ),
  Clock: (p: IconProps) => (
    <Svg {...p}>
      <rect x="3" y="3" width="18" height="18" />
      <path d="M12 7v5l3 3" />
    </Svg>
  ),
  Trash: (p: IconProps) => (
    <Svg {...p}>
      <path d="M4 7h16M9 7V4h6v3M6 7l1 14h10l1-14" />
    </Svg>
  ),
  Audit: (p: IconProps) => (
    <Svg {...p}>
      <path d="M6 3h12v18H6z" />
      <path d="M9 8h6M9 12h6M9 16h3" />
    </Svg>
  ),
}
