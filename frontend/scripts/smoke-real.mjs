/**
 * Real HTTP smoke. Creates one fictional case in the explicitly configured test API.
 * Node >=22.13; no dependencies. Values belong in process environment, never source.
 * Required: API_BASE_URL (e.g. http://127.0.0.1:3000/api), REAL_A_EMAIL,
 * REAL_A_PASSWORD, REAL_B_EMAIL, REAL_B_PASSWORD, REAL_ORGANIZATION_ID,
 * REAL_EVIDENCE_FIXTURE_KEYS (comma-separated allowlisted keys).
 * A needs VOLUNTEER (also VERIFIER to exercise SELF_APPROVAL); B needs VERIFIER.
 * Never reports response bodies, cookies, credentials, user IDs or private fields.
 */
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';

const required = ['API_BASE_URL', 'REAL_A_EMAIL', 'REAL_A_PASSWORD', 'REAL_B_EMAIL',
  'REAL_B_PASSWORD', 'REAL_ORGANIZATION_ID', 'REAL_EVIDENCE_FIXTURE_KEYS'];
const missing = required.filter(name => !process.env[name]?.trim());
if (missing.length) {
  console.log(`Not Run: real HTTP smoke requires ${missing.join(', ')}.`);
  process.exit(0);
}

let stage = 'configuration';
let failures = 0;
let lastObservation = '';
const authenticated = [];
let base;
const organizationId = process.env.REAL_ORGANIZATION_ID.trim();
const fixtureKeys = process.env.REAL_EVIDENCE_FIXTURE_KEYS.split(',').map(key => key.trim()).filter(Boolean);
const mockEmails = new Set(['minh.a@example.test', 'minh.b@example.test',
  'minh.reader@example.test', 'minh.outsider@example.test']);
const report = (name, status, code = '') => console.log(`PASS ${name}: HTTP ${status}${code ? ` ${code}` : ''}`);

class Browser {
  cookies = new Map();
  csrf;
  me;

  async request(path, { method = 'GET', body, key, csrf = true } = {}) {
    lastObservation = '';
    const url = new URL(path.replace(/^\//, ''), base);
    assert.equal(url.origin, base.origin);
    const headers = { Accept: 'application/json' };
    const cookies = [...this.cookies.entries()]
      .filter(([, value]) => (!value.secure || url.protocol === 'https:') && url.pathname.startsWith(value.path))
      .map(([name, value]) => `${name}=${value.value}`).join('; ');
    if (cookies) headers.Cookie = cookies;
    if (method !== 'GET' && csrf && this.csrf) headers[this.csrf.headerName] = this.csrf.token;
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (key) headers['Idempotency-Key'] = key;
    const response = await fetch(url, {
      method, headers, body: body === undefined ? undefined : JSON.stringify(body),
      cache: 'no-store', redirect: 'error', signal: AbortSignal.timeout(12000),
    });
    if (response.headers.has('x-aidbridge-mock')) throw new Error('MOCK_TARGET_REJECTED');
    for (const cookie of response.headers.getSetCookie()) {
      const [pair, ...attributes] = cookie.split(';').map(part => part.trim());
      const split = pair.indexOf('=');
      const name = pair.slice(0, split);
      const value = pair.slice(split + 1);
      const lower = attributes.map(part => part.toLowerCase());
      if (!value || lower.includes('max-age=0')) this.cookies.delete(name);
      else this.cookies.set(name, {
        value, secure: lower.includes('secure'),
        path: attributes.find(part => /^path=/i.test(part))?.slice(5) || '/',
      });
    }
    const raw = await response.text();
    let data;
    if (raw) {
      try { data = JSON.parse(raw); } catch { throw new Error('NON_JSON_RESPONSE'); }
    }
    const code = typeof data?.code === 'string' && /^[A-Z][A-Z0-9_]{0,63}$/.test(data.code) ? ` ${data.code}` : '';
    lastObservation = `HTTP ${response.status}${code}`;
    return { status: response.status, headers: response.headers, data, empty: !raw };
  }

  async token() {
    const result = await this.request('auth/csrf');
    assert.equal(result.status, 200);
    assert.ok(result.headers.get('cache-control')?.includes('no-store'));
    assert.equal(typeof result.data?.token, 'string');
    assert.equal(result.data?.headerName, 'X-CSRF-TOKEN');
    this.csrf = result.data;
    return result;
  }

  async mutation(path, body, key = randomUUID()) {
    // An unknown network outcome retries the exact payload and key once.
    try { return await this.request(path, { method: 'POST', body, key }); }
    catch (error) {
      if (!(error instanceof TypeError) && error?.name !== 'TimeoutError') throw error;
      console.log(`RETRY ${stage}: same Idempotency-Key after unknown network outcome.`);
      return this.request(path, { method: 'POST', body, key });
    }
  }
}

function problem(result, status, code) {
  assert.equal(result.status, status);
  assert.equal(result.data?.code, code);
  assert.ok(result.headers.get('content-type')?.includes('application/problem+json'));
  report(stage, status, code);
}

async function login(browser, prefix) {
  stage = `${prefix} CSRF bootstrap`;
  report(stage, (await browser.token()).status);
  stage = `${prefix} valid login`;
  const result = await browser.request('auth/login', {
    method: 'POST', body: { email: process.env[`REAL_${prefix}_EMAIL`], password: process.env[`REAL_${prefix}_PASSWORD`] },
  });
  assert.equal(result.status, 200);
  authenticated.push(browser);
  assert.equal(typeof result.data?.user?.id, 'string');
  report(stage, result.status);
  stage = `${prefix} CSRF refresh after login`;
  await browser.token();
  stage = `${prefix} current memberships`;
  const current = await browser.request('auth/me');
  assert.equal(current.status, 200);
  assert.equal(current.data?.userId, result.data.user.id);
  assert.ok(Array.isArray(current.data?.memberships));
  browser.me = current.data;
  report(stage, current.status);
}

try {
  base = new URL(process.env.API_BASE_URL.replace(/\/$/, '') + '/');
  assert.ok(['http:', 'https:'].includes(base.protocol));
  assert.ok(!base.username && !base.password && !base.search && !base.hash);
  assert.equal(base.pathname, '/api/');
  assert.ok(base.protocol === 'https:' || ['localhost', '127.0.0.1', '[::1]'].includes(base.hostname));
  assert.ok(fixtureKeys.length >= 1 && fixtureKeys.length <= 10);
  assert.equal(new Set(fixtureKeys).size, fixtureKeys.length);
  for (const actor of ['A', 'B']) {
    assert.ok(!mockEmails.has(process.env[`REAL_${actor}_EMAIL`].trim().toLowerCase()));
    assert.notEqual(process.env[`REAL_${actor}_PASSWORD`], 'Mock-only-123!');
  }
  assert.notEqual(process.env.REAL_A_EMAIL.trim().toLowerCase(), process.env.REAL_B_EMAIL.trim().toLowerCase());

  const a = new Browser();
  const b = new Browser();
  // No supplied credential is transmitted until a non-mock CSRF endpoint is observed.
  stage = 'real target preflight';
  await a.token();
  stage = 'anonymous session';
  problem(await a.request('auth/me'), 401, 'UNAUTHENTICATED');
  stage = 'missing CSRF';
  problem(await a.request('auth/login', { method: 'POST', csrf: false,
    body: { email: process.env.REAL_A_EMAIL, password: randomUUID() } }), 403, 'CSRF_INVALID');
  stage = 'wrong password';
  problem(await a.request('auth/login', { method: 'POST',
    body: { email: process.env.REAL_A_EMAIL, password: randomUUID() } }), 401, 'INVALID_CREDENTIALS');
  await login(a, 'A');
  await login(b, 'B');
  assert.notEqual(a.me.userId, b.me.userId);
  const membership = browser => browser.me.memberships.find(item => item.organizationId === organizationId && item.status === 'ACTIVE');
  stage = 'required active roles';
  assert.ok(membership(a)?.roles.includes('VOLUNTEER'));
  assert.ok(membership(b)?.roles.includes('VERIFIER'));

  stage = 'organization evidence allowlist';
  const fixtures = await a.request(`evidence-fixtures?organizationId=${encodeURIComponent(organizationId)}`);
  assert.equal(fixtures.status, 200);
  // E01 list projection is not frozen. Validate keys when the server uses the agreed frontend projection.
  const available = Array.isArray(fixtures.data) ? fixtures.data : fixtures.data?.items;
  assert.ok(Array.isArray(available));
  assert.ok(fixtureKeys.every(key => available.some(item => item.key === key)));
  report(stage, fixtures.status);

  stage = 'organization list';
  const list = await a.request(`cases?organizationId=${encodeURIComponent(organizationId)}&page=0&size=20`);
  assert.equal(list.status, 200);
  assert.ok(Array.isArray(list.data?.items));
  assert.ok(list.data.items.every(item => item.organizationId === organizationId));
  report(stage, list.status);

  const body = {
    organizationId, beneficiaryAlias: `Fictional smoke household ${randomUUID().slice(0, 8)}`,
    approximateArea: 'Fictional test district', internalTitle: 'F03 F04 real HTTP smoke',
    priority: 'NORMAL', consent: { basis: 'CONSENT', scope: 'Fictional smoke test only',
      capturedAt: new Date().toISOString(), publicSummaryAllowed: false },
    needs: [{ name: 'Rice', unit: 'bag', quantity: 2 }, { name: 'Hygiene kit', unit: 'kit', quantity: 2 }],
    evidenceFixtureKeys: fixtureKeys,
  };
  stage = 'invalid second need';
  const invalid = structuredClone(body);
  invalid.needs[1].quantity = 0;
  const validation = await a.mutation('cases', invalid);
  problem(validation, 400, 'VALIDATION_ERROR');
  assert.ok(validation.data?.fieldErrors?.some(item => item.field === 'needs[1].quantity'));

  stage = 'two-item case creation';
  const creationKey = randomUUID();
  const created = await a.mutation('cases', body, creationKey);
  assert.equal(created.status, 201);
  assert.equal(created.data?.status, 'SUBMITTED');
  assert.equal(created.data?.needs?.length, 2);
  assert.ok(created.headers.get('location'));
  report(stage, created.status);
  stage = 'idempotent creation replay';
  const replay = await a.mutation('cases', body, creationKey);
  assert.equal(replay.status, 201);
  assert.equal(replay.data?.id, created.data.id);
  assert.equal(replay.headers.get('idempotent-replayed'), 'true');
  report(stage, replay.status);

  const casePath = `cases/${encodeURIComponent(created.data.id)}`;
  stage = 'B detail';
  const detail = await b.request(casePath);
  assert.equal(detail.status, 200);
  assert.equal(detail.data?.creator?.id, a.me.userId);
  assert.ok(Number.isInteger(detail.data?.version));
  assert.ok(detail.data?.evidenceVersions?.length > 0);
  report(stage, detail.status);
  const decision = { expectedVersion: detail.data.version,
    reviewedEvidenceVersionIds: detail.data.evidenceVersions.map(item => item.id) };
  stage = 'A denied review';
  const denied = await a.mutation(`${casePath}/verify`, decision);
  if (membership(a).roles.includes('VERIFIER')) problem(denied, 403, 'SELF_APPROVAL');
  else {
    problem(denied, 403, 'FORBIDDEN');
    console.log('Not Run: dual-role SELF_APPROVAL requires A to also have VERIFIER.');
  }
  for (const action of ['reject', 'request-changes']) {
    stage = `${action} missing reason`;
    problem(await b.mutation(`${casePath}/${action}`, decision), 400, 'VALIDATION_ERROR');
  }
  stage = 'stale case version';
  problem(await b.mutation(`${casePath}/verify`, { ...decision, expectedVersion: decision.expectedVersion + 1 }), 409, 'VERSION_CONFLICT');
  stage = 'refresh after conflict';
  const refreshed = await b.request(casePath);
  assert.equal(refreshed.status, 200);
  decision.expectedVersion = refreshed.data.version;
  decision.reviewedEvidenceVersionIds = refreshed.data.evidenceVersions.map(item => item.id);
  stage = 'B independent approval';
  const verified = await b.mutation(`${casePath}/verify`, decision);
  assert.equal(verified.status, 200);
  assert.equal(verified.data?.caseStatus, 'VERIFIED');
  assert.equal(verified.data?.blockchainStatus, 'PENDING_ON_CHAIN');
  assert.equal(verified.data?.reviewerId, b.me.userId);
  report(stage, verified.status);
  console.log('PASS business VERIFIED and chain PENDING_ON_CHAIN asserted independently.');
  console.log('Not Run: real idle-session expiry, cross-organization 404, successful reject/request-changes need separate backend fixtures.');
} catch (error) {
  failures++;
  const safeCode = ['MOCK_TARGET_REJECTED', 'NON_JSON_RESPONSE'].includes(error?.message)
    ? error.message : error?.name === 'AssertionError' ? 'CONTRACT_ASSERTION_FAILED' : 'REQUEST_FAILED';
  console.error(`FAIL ${stage}: ${safeCode}${lastObservation ? ` (${lastObservation})` : ''}. Response body and credentials are withheld.`);
} finally {
  for (let index = 0; index < authenticated.length; index++) {
    const browser = authenticated[index];
    stage = `session ${index + 1} logout`;
    try {
      await browser.token();
      const result = await browser.request('auth/logout', { method: 'POST' });
      assert.equal(result.status, 204);
      assert.ok(result.empty);
      report(stage, result.status);
      stage = `session ${index + 1} revoked`;
      problem(await browser.request('auth/me'), 401, 'UNAUTHENTICATED');
      await browser.token();
      problem(await browser.request('auth/me'), 401, 'UNAUTHENTICATED');
    } catch {
      failures++;
      console.error(`FAIL ${stage}: LOGOUT_CHECK_FAILED.`);
    }
  }
}
process.exitCode = failures ? 1 : 0;
