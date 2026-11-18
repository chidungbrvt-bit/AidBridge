'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { activeMemberships, canCreateCases, canReadCases, canReviewCases } from '@/lib/api-client';
import { useSession } from './session-provider';
import { disputeReturnPath } from '@/lib/login-return';
import { LanguageToggle, useI18n } from './i18n-provider';

export function WorkspaceShell({ children }: { children: React.ReactNode }) {
  const session = useSession();
  const { t } = useI18n();
  const { user, loading, organizationId, mode, generation, navigationLocked, goodsReview } = session;
  const router = useRouter();
  const pathname = usePathname();
  const donorPath = pathname === '/campaigns' || pathname.startsWith('/campaigns/') || pathname === '/me/commitments';
  const platformDispute = mode === "real" && user?.platformRole === "PLATFORM_ADMIN" && pathname.startsWith("/disputes/");
  const [actionError, setActionError] = useState('');
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (!loading && !user && !session.checkingFailed) {
      const returnTo = disputeReturnPath(pathname);
      router.replace(returnTo ? `/login?returnTo=${encodeURIComponent(returnTo)}` : '/login');
    }
  }, [loading, user, session.checkingFailed, pathname, router]);
  useEffect(() => {
    if (goodsReview && pathname !== goodsReview.route) router.replace(goodsReview.route);
  }, [goodsReview, pathname, router]);
  if (session.checkingFailed && !user) return <main className="center-state"><div className="card stack"><p role="alert">{t(session.error)}</p><button className="button" onClick={() => void session.refreshUser()}>{t("Check session again")}</button></div></main>;
  if (loading || !user) return <main className="center-state" aria-live="polite">{t('Opening your workspace…')}</main>;
  const memberships = activeMemberships(user);
  const membership = memberships.find(item => item.organizationId === organizationId);
  const contextKey = `${generation}:${user.userId}:${organizationId}:${JSON.stringify(membership)}`;
  async function signOut() {
    setBusy(true); setActionError('');
    try { await session.logout(); router.replace('/login'); }
    catch { setActionError(t("Sign out could not be confirmed. Please retry.")); }
    finally { setBusy(false); }
  }
  return <div className="app-shell" onClickCapture={event => {
    if (navigationLocked && event.target instanceof Element && event.target.closest('a')) event.preventDefault();
  }}>
    <aside className="sidebar">
      <Link href={donorPath ? "/campaigns" : "/cases"} className="brand"><span className="brand-icon" aria-hidden="true">a<span>↗</span></span><span>AidBridge<small>{t('CARE, WITH ACCOUNTABILITY')}</small></span></Link>
      <div className="workspace-label">{donorPath ? t("DONOR WORKSPACE") : t("ORGANIZATION WORKSPACE")}</div>
      {memberships.length > 0 && <><label className="org-label" htmlFor="organization">{t("Active organization")}</label>
      <select id="organization" value={organizationId} disabled={navigationLocked} onChange={event => session.setOrganizationId(event.target.value)}>
        {memberships.map((item, index) => <option key={item.organizationId} value={item.organizationId}>
          {item.organizationName || (mode === 'mock' && item.organizationId.endsWith('0001') ? 'Riverlight Aid' : mode === 'mock' && item.organizationId.endsWith('0002') ? 'Harbor Relief' : t('Organization {n}', { n: index + 1 }))}
        </option>)}
      </select></>}
      <nav aria-label="Workspace navigation">
        {mode === 'mock' && <Link href="/goods-demo">Goods demo · MOCK</Link>}
        <Link className={pathname.startsWith('/campaigns') ? 'nav-active' : ''} href="/campaigns">{t("Campaigns")}</Link>
        {user.donorEnabled && <Link className={pathname === '/me/commitments' ? 'nav-active' : ''} href="/me/commitments">{t("My pledges")}</Link>}
        {canReadCases(user, organizationId) && <Link className={pathname === '/cases' ? 'nav-active' : ''} href="/cases"><span aria-hidden="true">▦</span> {t('Cases')}</Link>}
        {canCreateCases(user, organizationId) && <Link className={pathname === '/cases/new' ? 'nav-active' : ''} href="/cases/new"><span aria-hidden="true">＋</span> {t('Create case')}</Link>}
        {canReadCases(user, organizationId) && <Link className={pathname === '/audit-preview' ? 'nav-active' : ''} href="/audit-preview">{t("Audit preview")}</Link>}
        {canReviewCases(user, organizationId) && <Link href="/cases?status=SUBMITTED"><span aria-hidden="true">✓</span> {t('Review queue')}</Link>}
      </nav>
      <div className="sidebar-note"><span className="eyebrow">{t("INDEPENDENT REVIEW")}</span><p>{t("Every case starts with a need.")}<br/>{t("Every approval needs a second person.")}</p></div>
      <div className="profile"><span className="avatar">{user.fullName.slice(0, 1)}</span><div><strong>{user.fullName}</strong><small>{membership?.roles.join(' · ') || (user.donorEnabled ? t("Donor account") : t("No active staff role"))}</small></div></div>
      <button className="button secondary signout" onClick={signOut} disabled={busy || navigationLocked}>{busy ? t("Signing out…") : t("Sign out")}</button>
    </aside>
    <div className="workspace-main">
      <header className="topbar"><span>{donorPath ? t("Donor / Goods commitments") : t("Organization / Case management")}</span><div className="topbar-actions"><span className={`environment ${mode}`}><span aria-hidden="true">●</span> {mode === 'mock' ? t('MOCK DEMO · FICTIONAL DATA') : t('REAL API')}</span><LanguageToggle /></div></header>
      <main className="main-content" key={contextKey}>
        {navigationLocked && <p className="notice" role="status">{t("Resolve the current submission before leaving this page or switching organization.")}</p>}
        {actionError && <p className="notice error" role="alert">{t(actionError)}</p>}
        {memberships.length || donorPath || platformDispute ? children : <section className="card"><h1>{t('No active organization membership')}</h1><p>{t('Your account has no active staff membership. Ask the organization administrator to check access.')}</p></section>}
      </main>
      {mode === 'mock' && <div className="demo-tools"><details><summary>{t("Mock test controls")}</summary><p>{t("Local simulation only. These controls do not exist in real mode.")}</p><div className="actions"><button className="button secondary" onClick={async () => { try { await session.api.mockControl('expire-session'); await session.refreshUser(); } catch { setActionError('Mock control failed.'); } }}>{t("Expire session")}</button><button className="button secondary" onClick={async () => { try { await session.api.mockControl('next-error'); setActionError('Next case read will return 503. Open Cases or refresh the case.'); } catch { setActionError('Mock control failed.'); } }}>{t("Fail next read")}</button><button className="button secondary" onClick={async () => { try { await session.api.mockControl('lose-next-response'); setActionError('Next write will commit but its response will be lost. Retry with the same key.'); } catch { setActionError('Mock control failed.'); } }}>{t("Lose next write response")}</button></div></details></div>}
    </div>
  </div>;
}
