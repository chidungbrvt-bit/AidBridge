'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { activeMemberships, canCreateCases, canReadCases, canReviewCases } from '@/lib/api-client';
import { useSession } from './session-provider';

export function WorkspaceShell({ children }: { children: React.ReactNode }) {
  const session = useSession();
  const { user, loading, organizationId, mode, generation, navigationLocked } = session;
  const router = useRouter();
  const pathname = usePathname();
  const donorPath = pathname === '/campaigns' || pathname.startsWith('/campaigns/') || pathname === '/me/commitments';
  const [actionError, setActionError] = useState('');
  const [busy, setBusy] = useState(false);
  useEffect(() => { if (!loading && !user) router.replace('/login'); }, [loading, user, router]);
  if (loading || !user) return <main className="center-state" aria-live="polite">Opening your workspace…</main>;
  const memberships = activeMemberships(user);
  const membership = memberships.find(item => item.organizationId === organizationId);
  const contextKey = `${generation}:${user.userId}:${organizationId}:${JSON.stringify(membership)}`;
  async function signOut() {
    setBusy(true); setActionError('');
    try { await session.logout(); router.replace('/login'); }
    catch { setActionError('Sign out could not be confirmed. Please retry.'); }
    finally { setBusy(false); }
  }
  return <div className="app-shell" onClickCapture={event => {
    if (navigationLocked && event.target instanceof Element && event.target.closest('a')) event.preventDefault();
  }}>
    <aside className="sidebar">
      <Link href={donorPath ? "/campaigns" : "/cases"} className="brand"><span className="brand-icon" aria-hidden="true">a<span>↗</span></span><span>AidBridge<small>CARE, WITH ACCOUNTABILITY</small></span></Link>
      <div className="workspace-label">{donorPath ? "DONOR WORKSPACE" : "ORGANIZATION WORKSPACE"}</div>
      {memberships.length > 0 && <><label className="org-label" htmlFor="organization">Active organization</label>
      <select id="organization" value={organizationId} disabled={navigationLocked} onChange={event => session.setOrganizationId(event.target.value)}>
        {memberships.map(item => <option key={item.organizationId} value={item.organizationId}>
          {mode === 'mock' && item.organizationId.endsWith('0001') ? 'Riverlight Aid' : mode === 'mock' && item.organizationId.endsWith('0002') ? 'Harbor Relief' : item.organizationId}
        </option>)}
      </select></>}
      <nav aria-label="Workspace navigation">
        <Link className={pathname.startsWith('/campaigns') ? 'nav-active' : ''} href="/campaigns">Campaigns</Link>
        {user.donorEnabled && <Link className={pathname === '/me/commitments' ? 'nav-active' : ''} href="/me/commitments">My pledges</Link>}
        {canReadCases(user, organizationId) && <Link className={pathname === '/cases' ? 'nav-active' : ''} href="/cases"><span aria-hidden="true">▦</span> Cases</Link>}
        {canCreateCases(user, organizationId) && <Link className={pathname === '/cases/new' ? 'nav-active' : ''} href="/cases/new"><span aria-hidden="true">＋</span> Create case</Link>}
        {canReviewCases(user, organizationId) && <Link href="/cases?status=SUBMITTED"><span aria-hidden="true">✓</span> Review queue</Link>}
      </nav>
      <div className="sidebar-note"><span className="eyebrow">INDEPENDENT REVIEW</span><p>Every case starts with a need.<br/>Every approval needs a second person.</p></div>
      <div className="profile"><span className="avatar">{user.fullName.slice(0, 1)}</span><div><strong>{user.fullName}</strong><small>{membership?.roles.join(' · ') || (user.donorEnabled ? 'Donor account' : 'No active staff role')}</small></div></div>
      <button className="button secondary signout" onClick={signOut} disabled={busy || navigationLocked}>{busy ? 'Signing out…' : 'Sign out'}</button>
    </aside>
    <div className="workspace-main">
      <header className="topbar"><span>{donorPath ? 'Donor / Goods commitments' : 'Organization / Case management'}</span><span className={`environment ${mode}`}><span aria-hidden="true">●</span> {mode === 'mock' ? 'MOCK DEMO · FICTIONAL DATA' : 'REAL API'}</span></header>
      <main className="main-content" key={contextKey}>
        {navigationLocked && <p className="notice" role="status">Resolve the current submission before leaving this page or switching organization.</p>}
        {actionError && <p className="notice error" role="alert">{actionError}</p>}
        {memberships.length || donorPath ? children : <section className="card"><h1>No active organization membership</h1><p>Your account has no active staff membership. Ask the organization administrator to check access.</p></section>}
      </main>
      {mode === 'mock' && <div className="demo-tools"><details><summary>Mock test controls</summary><p>Local simulation only. These controls do not exist in real mode.</p><div className="actions"><button className="button secondary" onClick={async () => { try { await session.api.mockControl('expire-session'); await session.refreshUser(); } catch { setActionError('Mock control failed.'); } }}>Expire session</button><button className="button secondary" onClick={async () => { try { await session.api.mockControl('next-error'); setActionError('Next case read will return 503. Open Cases or refresh the case.'); } catch { setActionError('Mock control failed.'); } }}>Fail next read</button><button className="button secondary" onClick={async () => { try { await session.api.mockControl('lose-next-response'); setActionError('Next write will commit but its response will be lost. Retry with the same key.'); } catch { setActionError('Mock control failed.'); } }}>Lose next write response</button></div></details></div>}
    </div>
  </div>;
}
