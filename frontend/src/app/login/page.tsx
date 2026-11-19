'use client';

import { useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { activeMemberships, ApiError } from '@/lib/api-client';
import { useSession } from '@/components/session-provider';
import { disputeReturnPath } from '@/lib/login-return';
import { LanguageToggle, useI18n } from '@/components/i18n-provider';
import { ThemeToggle } from '@/components/theme-toggle';
import { BrandMark, Icon } from '@/components/icons';

/** The three steps every case goes through, drawn as a connected flow. */
function WorkflowDiagram() {
  const { t } = useI18n();
  const steps = [
    { icon: 'create', tone: 'green', title: t("Create a case"), detail: t("Needs, consent and evidence") },
    { icon: 'review', tone: 'mint', title: t("A second person verifies"), detail: t("Self-review is blocked") },
    { icon: 'audit', tone: 'amber', title: t("Recorded on Avalanche Fuji"), detail: t("Public audit trail without personal data") },
  ] as const;
  return <ol className="flow" aria-label={t("How a case moves")}>
    {steps.map((step, index) => <li key={step.icon} style={{ animationDelay: `${index * 60}ms` }}>
      <span className={`flow-tile tone-${step.tone}`}><Icon name={step.icon} size={18} /></span>
      <div><span className="flow-index">{String(index + 1).padStart(2, '0')}</span><strong>{step.title}</strong><small>{step.detail}</small></div>
    </li>)}
  </ol>;
}

export default function LoginPage() {
  const { user, login, notice, mode, loading, error: sessionError, refreshUser, goodsReview } = useSession();
  const router = useRouter();
  const { t } = useI18n();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const errorRef = useRef<HTMLParagraphElement>(null);
  useEffect(() => {
    if (user) {
      const returnTo = disputeReturnPath(new URLSearchParams(window.location.search).get('returnTo'));
      router.replace(goodsReview?.route ?? returnTo ??
        (user.donorEnabled && activeMemberships(user).length === 0 ? '/campaigns' : '/cases'));
    }
  }, [user, router, goodsReview]);
  useEffect(() => { if (error) errorRef.current?.focus(); }, [error]);
  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError(''); setBusy(true);
    try { await login({ email: email.trim(), password }); }
    catch (cause) {
      setError(cause instanceof ApiError && cause.code === 'INVALID_CREDENTIALS' ? 'Email or password is incorrect.' : cause instanceof ApiError && cause.code === 'CSRF_INVALID' ? 'The security token expired. Please try signing in again.' : cause instanceof ApiError && cause.code === 'FORBIDDEN' ? 'This account is not allowed to sign in.' : 'Sign in could not be completed. Check the API connection and retry.');
    } finally { setPassword(''); setBusy(false); }
  }
  return <main className="login-page">
    <section className="login-story">
      <div className="brand"><BrandMark /><span>AidBridge</span></div>
      <div className="story-content">
        <div><h1>{t("Aid cases, checked by a second person")}</h1><p>{t("Bring people, essential supplies and independent verification together in one shared workspace.")}</p></div>
        <WorkflowDiagram />
      </div>
      <p className="story-footnote">{t("Goods only · no payments")}</p>
    </section>
    <section className="login-form-area"><div className="login-card"><div className="toolbar"><span className={`environment ${mode}`}>{mode === 'mock' ? t('Mock demo · fictional data') : t('Real API')}</span><div className="actions"><LanguageToggle /><ThemeToggle /></div></div><h2>{t("Sign in")}</h2><p className="muted">{t("Sign in to your organization workspace.")}</p>
      {notice && <p className="notice" role="status">{t(notice)}</p>}
      {sessionError && <div className="notice error" role="alert">{t(sessionError)} <button className="text-button" onClick={() => void refreshUser()}>{t("Retry connection")}</button></div>}
      <form onSubmit={submit} className="stack">
        <label className="field">{t("Email address")}<input autoComplete="username" type="email" maxLength={254} required value={email} onChange={event => setEmail(event.target.value)} placeholder="you@organization.org" /></label>
        <label className="field">{t("Password")}<input autoComplete="current-password" type="password" required value={password} onChange={event => setPassword(event.target.value)} placeholder={t("Enter your password")} /></label>
        {error && <p className="notice error" id="login-error" ref={errorRef} tabIndex={-1} role="alert">{t(error)}</p>}
        <button className="button login-submit" disabled={busy || loading}>{busy ? t("Signing in…") : loading ? t("Checking session…") : t("Sign in")}</button>
      </form>
      <p className="login-help">{t("Use the individual account provided by your organization.")}</p>
      {mode === 'mock' && <details className="mock-accounts" open><summary>{t("Try the mock workspace")}</summary><p>{t('Fictional accounts share password')} <code>Mock-only-123!</code></p><button className="account-choice" onClick={() => { setEmail('minh.a@example.test'); setPassword('Mock-only-123!'); }}><strong>A · Alex Nguyen</strong><span>{t('Creator + verifier · self-review blocked')}</span></button><button className="account-choice" onClick={() => { setEmail('minh.b@example.test'); setPassword('Mock-only-123!'); }}><strong>B · Blair Tran</strong><span>{t("Independent verifier")}</span></button><button className="account-choice" onClick={() => { setEmail('minh.admin@example.test'); setPassword('Mock-only-123!'); }}><strong>Admin · Morgan Lin</strong><span>{t("Draft and publish campaigns")}</span></button><p className="muted">{t("Demo records are in memory and reset when the server restarts.")}</p></details>}
    </div></section>
  </main>;
}
