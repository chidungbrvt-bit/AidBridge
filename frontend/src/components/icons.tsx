// Line icons drawn from lucide (ISC licence), inlined so the app needs no icon package.
// One size and stroke everywhere: 16px, 1.5 stroke, colour from the surrounding text.

export type IconName ='cases' | 'create' | 'review' | 'campaigns' | 'pledges' | 'audit' | 'goods' | 'signout';

const PATHS: Record<IconName, readonly string[]> = {
  cases: ['M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z'],
  create: ['M5 12h14', 'M12 5v14'],
  // Two ticks: the product's mark for "checked by a second person".
  review: ['M18 6 7 17l-5-5', 'm22 10-7.5 7.5L13 16'],
  campaigns: ['M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z', 'M4 22v-7'],
  pledges: ['M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z'],
  audit: ['M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8', 'M3 3v5h5', 'M12 7v5l4 2'],
  goods: ['M11 21.73a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73z', 'M12 22V12', 'm3.3 7 8.7 5 8.7-5'],
  signout: ['M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4', 'm16 17 5-5-5-5', 'M21 12H9'],
};

export function Icon({ name, size = 16 }: { name: IconName; size?: number }) {
  return <svg className="icon" width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5 * 24 / size} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    {PATHS[name].map(d => <path key={d} d={d} />)}
  </svg>;
}

export type Tone = 'green' | 'mint' | 'amber' | 'clay' | 'sky';

/** A small tinted tile that gives each workspace area its own colour. */
export function NavTile({ name, tone }: { name: IconName; tone: Tone }) {
  return <span className={`nav-tile tone-${tone}`}><Icon name={name} /></span>;
}

/** Flat AidBridge mark: one arch over a deck line, in the accent colour. */
export function BrandMark() {
  return <svg className="brand-mark" width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" aria-hidden="true">
    <path d="M2 14.5h16" />
    <path d="M4 14.5c0-5 2.7-8 6-8s6 3 6 8" />
    <path d="M7.5 14.5V9.6M12.5 14.5V9.6" />
  </svg>;
}
