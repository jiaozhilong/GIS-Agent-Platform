import type { SVGProps } from 'react';

type IconProps = SVGProps<SVGSVGElement>;

const base = (props: IconProps) => ({
  width: 18,
  height: 18,
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 2,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
  ...props,
});

export const IconDashboard = (p: IconProps) => (
  <svg {...base(p)}><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/></svg>
);
export const IconProject = (p: IconProps) => (
  <svg {...base(p)}><path d="M4 5h6l2 2h8v12H4V5Z"/></svg>
);
export const IconBrain = (p: IconProps) => (
  <svg {...base(p)}><rect x="4" y="7" width="16" height="12" rx="3"/><path d="M9 3h6M12 3v4M8 12h.01M16 12h.01M9 16h6"/></svg>
);
export const IconBook = (p: IconProps) => (
  <svg {...base(p)}><path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H11v16H6.5A2.5 2.5 0 0 0 4 21.5v-16ZM20 5.5A2.5 2.5 0 0 0 17.5 3H13v16h4.5a2.5 2.5 0 0 1 2.5 2.5v-16Z"/></svg>
);
export const IconFlow = (p: IconProps) => (
  <svg {...base(p)}><circle cx="5" cy="6" r="2"/><circle cx="19" cy="6" r="2"/><circle cx="12" cy="18" r="2"/><path d="M7 6h10M6 8l5 8M18 8l-5 8"/></svg>
);
export const IconTemplate = (p: IconProps) => (
  <svg {...base(p)}><path d="M6 3h9l4 4v14H6zM14 3v5h5M9 13h6M9 17h4"/></svg>
);
export const IconUser = (p: IconProps) => (
  <svg {...base(p)}><circle cx="12" cy="8" r="4"/><path d="M4 20c0-4 4-7 8-7s8 3 8 7"/></svg>
);
export const IconTeam = (p: IconProps) => (
  <svg {...base(p)}><circle cx="9" cy="8" r="3.2"/><path d="M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6"/><path d="M16 5.5a3 3 0 0 1 0 5.8M21 20c0-2.8-1.8-5-4.5-5.6"/></svg>
);
export const IconStats = (p: IconProps) => (
  <svg {...base(p)}><path d="M4 20V10M10 20V4M16 20v-7M21 20H3"/></svg>
);
export const IconWand = (p: IconProps) => (
  <svg {...base(p)}><path d="M15 4V2M15 6V4M13 4h2M11 8l-1 1 4 4 1-1z"/><path d="M4 20l9-9M14 10l3 3"/><path d="M3 21l2-2"/></svg>
);
export const IconLock = (p: IconProps) => (
  <svg {...base(p)}><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
);
export const IconShield = (p: IconProps) => (
  <svg {...base(p)}><path d="M12 2l8 3v6c0 5-3.5 8.5-8 11-4.5-2.5-8-6-8-11V5z"/><path d="M9 12l2 2 4-4"/></svg>
);
export const IconBell = (p: IconProps) => (
  <svg {...base(p)}><path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.7 21a2 2 0 0 1-3.4 0"/></svg>
);
export const IconPlus = (p: IconProps) => (
  <svg {...base(p)}><path d="M12 5v14M5 12h14"/></svg>
);
export const IconCheck = (p: IconProps) => (
  <svg {...base(p)}><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><path d="m9 11 3 3L22 4"/></svg>
);
export const IconSearch = (p: IconProps) => (
  <svg {...base(p)}><circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/></svg>
);
export const IconDownload = (p: IconProps) => (
  <svg {...base(p)}><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3"/></svg>
);
export const IconPlay = (p: IconProps) => (
  <svg {...base(p)}><path d="m8 5 11 7-11 7V5Z"/></svg>
);
export const IconClose = (p: IconProps) => (
  <svg {...base(p)}><path d="M18 6 6 18M6 6l12 12"/></svg>
);
export const IconLink = (p: IconProps) => (
  <svg {...base(p)}><path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71" /><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71" /></svg>
);
export const IconClock = (p: IconProps) => (
  <svg {...base(p)}><circle cx="12" cy="12" r="10" /><path d="M12 6v6l4 2" /></svg>
);
export const IconSync = (p: IconProps) => (
  <svg {...base(p)}><path d="M21 12a9 9 0 0 1-9 9 9 9 0 0 1-6-2.7L3 21"/><path d="M3 12a9 9 0 0 1 9-9 9 9 0 0 1 6 2.7L21 3"/></svg>
);
export const IconEdit = (p: IconProps) => (
  <svg {...base(p)}><path d="M11 5H6a2 2 0 0 0-2 2v11a2 2 0 0 0 2 2h11a2 2 0 0 0 2-2v-5M18.4 2.6a2 2 0 0 1 3 3L12 15l-4 1 1-4Z"/></svg>
);
export const IconTrash = (p: IconProps) => (
  <svg {...base(p)}><path d="M3 6h18M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/></svg>
);
export const IconDoc = (p: IconProps) => (
  <svg {...base(p)}><path d="M6 3h9l4 4v14H6zM14 3v5h5M9 13h6M9 17h4"/></svg>
);
export const IconChevronRight = (p: IconProps) => (
  <svg {...base(p)}><path d="m9 18 6-6-6-6"/></svg>
);
export const IconBrand = (p: IconProps) => (
  <svg {...base(p)}><path d="M4 17.5 12 21l8-3.5M4 12l8 3.5 8-3.5M12 3 4 6.5l8 3.5 8-3.5L12 3Z"/></svg>
);
// 用量计费：计量表（仪表盘指针 + 刻度）
export const IconUsage = (p: IconProps) => (
  <svg {...base(p)}><path d="M12 13l4-4"/><circle cx="12" cy="13" r="8"/><path d="M12 13h.01"/><path d="M4.9 19.1 3 21M19.1 19.1 21 21"/></svg>
);
