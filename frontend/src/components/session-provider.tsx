'use client';

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { activeMemberships, ApiClient, ApiError, mockTransport, realTransport } from '@/lib/api-client';
import { createSessionWork } from '@/lib/session-work';
import type { LoginRequest, MeResponse } from '@/lib/types';

type SessionContextValue = {
  user: MeResponse | null; api: ApiClient; mode: 'mock' | 'real'; loading: boolean;
  organizationId: string; setOrganizationId: (id: string) => void;
  login: (request: LoginRequest) => Promise<void>; logout: () => Promise<void>;
  refreshUser: () => Promise<void>; notice: string; error: string; generation: number;
  work: ReturnType<typeof createSessionWork>; checkingFailed: boolean;
  navigationLocked: boolean; setNavigationLocked: (locked: boolean) => void;
};
const SessionContext = createContext<SessionContextValue | null>(null);

export function SessionProvider({ children, mode }: { children: React.ReactNode; mode: 'mock' | 'real' }) {
  const [user, setUser] = useState<MeResponse | null>(null);
  const [organizationId, setOrg] = useState('');
  const [loading, setLoading] = useState(true);
  const [notice, setNotice] = useState('');
  const [error, setError] = useState('');
  const [generation, setGeneration] = useState(0);
  const [navigationLocked, setNavigationLocked] = useState(false);
  const epoch = useRef(0);
  const activeUserId = useRef<string | null>(null);
  const work = useMemo(createSessionWork, []);
  const [checkingFailed, setCheckingFailed] = useState(false);
  const clear = useCallback(() => {
    epoch.current += 1;
    if (activeUserId.current) setNotice('Your session ended. Sign in again to continue.');
    activeUserId.current = null;
    work.clear();
    setCheckingFailed(false);
    setUser(null); setOrg(''); setLoading(false); setNavigationLocked(false); setGeneration(value => value + 1);
  }, [work]);
  const api = useMemo(() => new ApiClient(mode === 'mock' ? mockTransport : realTransport, clear), [mode, clear]);
  const apply = useCallback((next: MeResponse) => {
    if (next.status !== 'ACTIVE') { clear(); return; }
    if (activeUserId.current && activeUserId.current !== next.userId) work.clear();
    activeUserId.current = next.userId;
    setCheckingFailed(false);
    setUser(next); setError('');
    const memberships = activeMemberships(next);
    setOrg(current => memberships.some(item => item.organizationId === current) ? current : memberships[0]?.organizationId ?? '');
  }, [clear, work]);
  const refreshUser = useCallback(async () => {
    const started = epoch.current;
    try {
      const next = await api.me();
      if (started === epoch.current) apply(next);
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 401) return;
      if (started === epoch.current) {
        // Hide private state when current permissions cannot be established.
        setUser(null);
        setCheckingFailed(true);
        setError('Session could not be checked. Retry when the API is available.');
      }
    } finally { setLoading(false); }
  }, [api, apply]);
  useEffect(() => {
    let live = true;
    void api.csrf().then(() => { if (live) return refreshUser(); }).catch(() => {
      if (live) { setError('Cannot reach the session service. Check the API connection and retry.'); setLoading(false); }
    });
    return () => { live = false; };
  }, [api, refreshUser]);
  useEffect(() => {
    const focus = () => { if (activeUserId.current) void refreshUser(); };
    window.addEventListener('focus', focus);
    return () => window.removeEventListener('focus', focus);
  }, [refreshUser]);
  const login = async (request: LoginRequest) => {
    const started = ++epoch.current;
    setError(''); setNotice('');
    const next = await api.login(request);
    if (started === epoch.current) { apply(next); setGeneration(value => value + 1); }
  };
  const logout = async () => {
    // Keep the UI mounted on an unknown logout outcome so the user can retry.
    await api.logout();
    clear(); setNotice('Signed out. Private workspace data has been cleared.');
  };
  return <SessionContext.Provider value={{ user, api, mode, loading, organizationId,
    setOrganizationId: id => {
      if (activeMemberships(user).some(item => item.organizationId === id)) setOrg(id);
    }, login, logout, refreshUser, notice, error, generation, work, checkingFailed, navigationLocked, setNavigationLocked }}>{children}</SessionContext.Provider>;
}
export function useSession() {
  const context = useContext(SessionContext);
  if (!context) throw new Error('SessionProvider is required');
  return context;
}
