import type { Metadata } from 'next';
import { IBM_Plex_Mono, IBM_Plex_Sans } from 'next/font/google';
import { SessionProvider } from '@/components/session-provider';
import { I18nProvider } from '@/components/i18n-provider';
import './globals.css';

export const metadata: Metadata = { title: 'AidBridge | Organization workspace', description: 'A shared workspace for aid cases and independent verification.' };
// Authenticated workspace shells are rendered per request, never as a cached page.
export const dynamic = 'force-dynamic';

// Self-hosted at build time by next/font; the browser never calls Google.
const plexSans = IBM_Plex_Sans({ subsets: ['latin', 'latin-ext', 'vietnamese'], weight: ['400', '500', '600'], variable: '--font-plex-sans', display: 'swap' });
const plexMono = IBM_Plex_Mono({ subsets: ['latin'], weight: ['400', '500'], variable: '--font-plex-mono', display: 'swap' });

const THEME_INIT_SCRIPT = `try{var t=localStorage.getItem('aidbridge-theme');if(t==='dark'||t==='light'){document.documentElement.dataset.theme=t;}else{document.documentElement.dataset.theme=window.matchMedia('(prefers-color-scheme: dark)').matches?'dark':'light';}}catch(e){}`;

export default function RootLayout({ children }: { children: React.ReactNode }) {
  const mode = process.env.NEXT_PUBLIC_API_MODE === 'mock' ? 'mock' : 'real';
  return (
    <html lang="en" className={`${plexSans.variable} ${plexMono.variable}`} suppressHydrationWarning>
      <head>
        <meta name="color-scheme" content="light dark" />
        <script dangerouslySetInnerHTML={{ __html: THEME_INIT_SCRIPT }} />
      </head>
      <body>
        <I18nProvider>
          <SessionProvider mode={mode}>{children}</SessionProvider>
        </I18nProvider>
      </body>
    </html>
  );
}
