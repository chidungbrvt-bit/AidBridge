'use client';

import { useEffect, useState } from 'react';
import { useI18n } from './i18n-provider';

type Theme = 'light' | 'dark';

const STORAGE_KEY = 'aidbridge-theme';

function resolveTheme(): Theme {
  if (typeof document !== 'undefined') {
    const attr = document.documentElement.dataset.theme;
    if (attr === 'dark' || attr === 'light') return attr;
  }
  if (typeof window !== 'undefined') {
    try {
      const stored = window.localStorage.getItem(STORAGE_KEY);
      if (stored === 'dark' || stored === 'light') return stored;
    } catch {
      // Ignore storage access errors in restricted environments.
    }
    if (window.matchMedia?.('(prefers-color-scheme: dark)').matches) {
      return 'dark';
    }
  }
  return 'light';
}

export function ThemeToggle() {
  const { t } = useI18n();
  const [theme, setTheme] = useState<Theme>('light');

  useEffect(() => {
    const initial = resolveTheme();
    document.documentElement.dataset.theme = initial;
    setTheme(initial);

    const media = window.matchMedia('(prefers-color-scheme: dark)');
    const handleMediaChange = (event: MediaQueryListEvent) => {
      try {
        const stored = window.localStorage.getItem(STORAGE_KEY);
        if (stored === 'dark' || stored === 'light') return;
      } catch {
        // Ignore storage errors.
      }
      const next: Theme = event.matches ? 'dark' : 'light';
      document.documentElement.dataset.theme = next;
      setTheme(next);
    };

    const handleStorage = (event: StorageEvent) => {
      if (event.key !== STORAGE_KEY) return;
      const next: Theme =
        event.newValue === 'dark' || event.newValue === 'light'
          ? event.newValue
          : media.matches
            ? 'dark'
            : 'light';
      document.documentElement.dataset.theme = next;
      setTheme(next);
    };

    media.addEventListener('change', handleMediaChange);
    window.addEventListener('storage', handleStorage);
    return () => {
      media.removeEventListener('change', handleMediaChange);
      window.removeEventListener('storage', handleStorage);
    };
  }, []);

  function toggleTheme() {
    const current =
      document.documentElement.dataset.theme === 'dark' ? 'dark' : theme;
    const next: Theme = current === 'dark' ? 'light' : 'dark';
    document.documentElement.dataset.theme = next;
    setTheme(next);
    try {
      window.localStorage.setItem(STORAGE_KEY, next);
    } catch {
      // Ensure theme applies to the document even if storage is unavailable.
    }
  }

  const isDark = theme === 'dark';

  const label = isDark ? t('Switch to light mode') : t('Switch to dark mode');

  return (
    <button
      type="button"
      className="button secondary theme-toggle"
      onClick={toggleTheme}
      aria-label={label}
      aria-pressed={isDark}
      title={label}
    >
      <svg
        width="14"
        height="14"
        viewBox="0 0 16 16"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
      >
        {isDark ? (
          <>
            <circle cx="8" cy="8" r="3.25" />
            <path d="M8 1.5v1.75M8 12.75v1.75M1.5 8h1.75M12.75 8h1.75M3.4 3.4l1.25 1.25M11.35 11.35l1.25 1.25M12.6 3.4l-1.25 1.25M4.65 11.35 3.4 12.6" />
          </>
        ) : (
          <path d="M13.5 9.8A5.75 5.75 0 0 1 6.2 2.5a5.75 5.75 0 1 0 7.3 7.3Z" />
        )}
      </svg>
      <span>{isDark ? t('Light') : t('Dark')}</span>
    </button>
  );
}
