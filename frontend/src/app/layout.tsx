import type { Metadata } from 'next';
import { SessionProvider } from '@/components/session-provider';
import { I18nProvider } from '@/components/i18n-provider';
import './globals.css';

export const metadata: Metadata = { title: 'AidBridge | Organization workspace', description: 'A shared workspace for aid cases and independent verification.' };
// Authenticated workspace shells are rendered per request, never as a cached page.
export const dynamic = 'force-dynamic';
export default function RootLayout({ children }: { children: React.ReactNode }) {
  const mode = process.env.NEXT_PUBLIC_API_MODE === 'mock' ? 'mock' : 'real';
  return <html lang="en"><body><I18nProvider><SessionProvider mode={mode}>{children}</SessionProvider></I18nProvider></body></html>;
}
