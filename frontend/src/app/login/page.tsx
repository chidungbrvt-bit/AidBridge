'use client';

import { useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { activeMemberships, ApiError } from '@/lib/api-client';
import { useSession } from '@/components/session-provider';
import { disputeReturnPath } from '@/lib/login-return';
import { LanguageToggle, useI18n } from '@/components/i18n-provider';

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
    <section className="login-story"><div className="brand"><span className="brand-icon">a<span>↗</span></span><span>AidBridge</span></div><div className="story-content"><span className="eyebrow">{t("FROM NEED TO VERIFIED ACTION")}</span><h1>{t("A little care.")}<br/>{t("A clear record.")}<br/><em>{t("A stronger bridge.")}</em></h1><p>{t("Bring people, essential supplies and independent verification together in one shared workspace.")}</p><div className="journey"><span>01 &nbsp; {t("Create a case")}</span><span>02 &nbsp; {t("Review independently")}</span><span>03 &nbsp; {t("Track the outcome")}</span></div></div><p className="story-footnote">{t("Essential goods. Human accountability.")}</p></section>
    <section className="login-form-area"><div className="login-card"><div className="toolbar"><span className="environment">{mode === 'mock' ? t('MOCK DEMO · FICTIONAL DATA') : t('REAL API')}</span><LanguageToggle /></div><h2>{t("Welcome back")}</h2><p className="muted">{t("Sign in to your organization workspace.")}</p>
      {notice && <p className="notice" role="status">{t(notice)}</p>}
      {sessionError && <div className="notice error" role="alert">{t(sessionError)} <button className="text-button" onClick={() => void refreshUser()}>{t("Retry connection")}</button></div>}
      <form onSubmit={submit} className="stack">
        <label className="field">{t("Email address")}<input autoComplete="username" type="email" maxLength={254} required value={email} onChange={event => setEmail(event.target.value)} placeholder="you@organization.org" /></label>
        <label className="field">{t("Password")}<input autoComplete="current-password" type="password" required value={password} onChange={event => setPassword(event.target.value)} placeholder={t("Enter your password")} /></label>
        {error && <p className="notice error" id="login-error" ref={errorRef} tabIndex={-1} role="alert">{t(error)}</p>}
        <button className="button login-submit" disabled={busy || loading}>{busy ? t("Signing in…") : loading ? t("Checking session…") : t("Sign in →")}</button>
      </form>
      <p className="login-help">{t("Use the individual account provided by your organization.")}</p>
      {mode === 'mock' && <details className="mock-accounts" open><summary>{t("Try the mock workspace")}</summary><p>{t('Fictional accounts share password')} <code>Mock-only-123!</code></p><button className="account-choice" onClick={() => { setEmail('minh.a@example.test'); setPassword('Mock-only-123!'); }}><strong>A · Alex Nguyen</strong><span>{t('Creator + verifier · self-review blocked')}</span></button><button className="account-choice" onClick={() => { setEmail('minh.b@example.test'); setPassword('Mock-only-123!'); }}><strong>B · Blair Tran</strong><span>{t("Independent verifier")}</span></button><button className="account-choice" onClick={() => { setEmail('minh.admin@example.test'); setPassword('Mock-only-123!'); }}><strong>Admin · Morgan Lin</strong><span>{t("Draft and publish campaigns")}</span></button><p className="muted">{t("Demo records are in memory and reset when the server restarts.")}</p></details>}
    </div></section>
  </main>;
}
