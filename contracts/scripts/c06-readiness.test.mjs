// Offline only: no signer, provider connection, RPC, deployment, or real journal creation.
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { collectCandidate, verifyManifest, verifyApproval, validatePlan, preflight,
  verifyDeployment, seal, unseal, sha, broadcastPreparedDeployment } from './c06-release.mjs';
const bundle = collectCandidate();
const candidate = JSON.parse(readFileSync(new URL('../deployment/c06-freeze-candidate.json', import.meta.url)));
const { planBytes } = bundle;
const prepared = { transactionHash: '0x' + '11'.repeat(32), address: '0x' + '22'.repeat(20), nonce: 4 };
const offlineBytes = 'offline-signed-transaction-fixture';
const unseenProvider = {
  getTransaction: async () => null,
  getTransactionCount: async () => prepared.nonce,
  getCode: async () => '0x',
  send: async () => '0xa869',
};
test('manifest is reproducible from current compiler input/output and committed ABI', () => {
  assert.deepEqual(candidate, bundle.manifest); verifyManifest(candidate, bundle);
  assert.deepEqual(JSON.parse(planBytes), bundle.plan);
  assert.deepEqual(planBytes, readFileSync(new URL('../deployment/c06-fuji-plan.public.json', import.meta.url)));
  assert.throws(() => verifyManifest({ ...candidate, runtimeKeccak256: '0x' + '00'.repeat(32) }, bundle));
  assert.throws(() => validatePlan({ ...bundle.plan, chainId: 43114 }));
  assert.throws(() => validatePlan({ ...bundle.plan, publicJournal: 'deployment/fuji-deployment.json' }));
});
test('candidate cannot broadcast; approval must pin manifest, config, expiry and outstanding gates', () => {
  const frozen = { ...candidate, status: 'FROZEN', approval: 'Operator freeze record TEST ONLY' };
  const bytes = Buffer.from(JSON.stringify(frozen));
  const approval = { status: 'APPROVED', chainId: 43113, scope: 'C06_DEPLOY_ONLY',
    manifestSha256: sha(bytes), planSha256: sha(planBytes), approvedBy: 'offline-test-operator',
    expectedNonce: 0, expiresAt: new Date(Date.now() + 60000).toISOString(),
    combinedPreparedWrongChainRegression: 'PASS', preservationPlanReviewed: true };
  assert.throws(() => verifyApproval(candidate, bytes, planBytes, approval), /ARTIFACT_NOT_FROZEN/);
  verifyApproval(frozen, bytes, planBytes, approval);
  for (const patch of [{ manifestSha256: 'wrong' }, { planSha256: 'wrong' }, { status: 'PENDING' },
    { expiresAt: '2000-01-01' }, { combinedPreparedWrongChainRegression: 'PENDING' }, { preservationPlanReviewed: false }]) {
    assert.throws(() => verifyApproval(frozen, bytes, planBytes, { ...approval, ...patch }));
  }
});
test('wrong RPC chain is rejected before further preflight requests', async () => {
  let calls = 0;
  await assert.rejects(preflight({ send: async () => { calls++; return '0xa86a'; } }, bundle), /RPC_WRONG_CHAIN/);
  assert.equal(calls, 1);
});
test('preflight refuses pending nonce and occupied target using mocked public RPC only', async () => {
  const p = { send: async () => '0xa869', getCode: async () => '0x',
    getTransactionCount: async (_, tag) => tag === 'pending' ? 2 : 1 };
  await assert.rejects(preflight(p, bundle), /PENDING_SIGNER_TX/);
  let count = 0;
  await assert.rejects(preflight({ ...p, getTransactionCount: async () => 0,
    getCode: async () => ++count > 2 ? '0x60' : '0x' }, bundle), /DEPLOYMENT_TARGET_OCCUPIED/);
});
test('an already observed deployment waits for its receipt without rebroadcasting', async () => {
  const provider = {
    getTransaction: async hash => ({ hash, chainId: 43113n, nonce: prepared.nonce, from: bundle.plan.deployer, to: null }),
    broadcastTransaction: async () => assert.fail('An observed transaction must not be sent again'),
  };
  await broadcastPreparedDeployment(provider, bundle.plan, prepared, offlineBytes, () => {});
  await assert.rejects(broadcastPreparedDeployment({
    ...provider, getTransaction: async hash => ({ ...(await provider.getTransaction(hash)), nonce: 5 }),
  }, bundle.plan, prepared, offlineBytes, () => {}), /OBSERVED_DEPLOYMENT_MISMATCH/);
});
test('an unseen deployment stops when the nonce or target needs reconciliation', async () => {
  const provider = {
    ...unseenProvider,
    broadcastTransaction: async () => assert.fail('An unresolved transaction must not be sent'),
  };
  for (const [latest, pending] of [[4, 5], [5, 5], [3, 3]]) {
    await assert.rejects(broadcastPreparedDeployment({
      ...provider, getTransactionCount: async (_, tag) => tag === 'latest' ? latest : pending,
    }, bundle.plan, prepared, offlineBytes, () => {}), /DEPLOYMENT_NONCE_UNRESOLVED/);
  }
  await assert.rejects(broadcastPreparedDeployment({
    ...provider, getCode: async () => '0x60',
  }, bundle.plan, prepared, offlineBytes, () => {}), /DEPLOYMENT_TARGET_OCCUPIED/);
});
test('approval expiring during the final chain check prevents broadcast', async () => {
  let expired = false;
  await assert.rejects(broadcastPreparedDeployment({
    ...unseenProvider,
    send: async () => { expired = true; return '0xa869'; },
    broadcastTransaction: async () => assert.fail('Expired approval must prevent broadcast'),
  }, bundle.plan, prepared, offlineBytes, () => assert(!expired, 'APPROVAL_EXPIRED')), /APPROVAL_EXPIRED/);
});
test('a cleared nonce and current approval allow only the prepared bytes to be sent', async () => {
  const sent = [];
  await broadcastPreparedDeployment({
    ...unseenProvider,
    broadcastTransaction: async raw => { sent.push(raw); return { hash: prepared.transactionHash }; },
  }, bundle.plan, prepared, offlineBytes, () => {});
  assert.deepEqual(sent, [offlineBytes]);
});
test('recovery ciphertext is authenticated to release context and encryption key', () => {
  const fixtureKey = Buffer.alloc(32, 7).toString('hex');
  const sealed = seal('offline-fixture-not-a-signed-transaction', fixtureKey, 'release-context');
  assert.equal(unseal(sealed, fixtureKey, 'release-context'), 'offline-fixture-not-a-signed-transaction');
  assert.throws(() => unseal(sealed, fixtureKey, 'different-context'));
  assert.throws(() => unseal({ ...sealed, tag: '00'.repeat(16) }, fixtureKey, 'release-context'));
});
test('post-deploy rejects wrong chain and noncanonical receipt with fake observations', async () => {
  const record = { chainId: 43113, transactionHash: '0x' + '11'.repeat(32), address: '0x' + '22'.repeat(20) };
  await assert.rejects(verifyDeployment({ send: async () => '0xa86a' }, bundle, record), /RPC_WRONG_CHAIN/);
  const provider = { send: async () => '0xa869', getTransactionReceipt: async () => ({ status: 1,
    hash: record.transactionHash, to: null, from: bundle.plan.deployer, contractAddress: record.address,
    blockNumber: 1, blockHash: 'expected' }), getBlock: async () => ({ hash: 'different' }) };
  await assert.rejects(verifyDeployment(provider, bundle, record), /BLOCK_NOT_CANONICAL/);
});
test('CLI defaults to offline prepare and rejects broadcast without explicit approval', () => {
  const cli = new URL('./c06-deploy.mjs', import.meta.url);
  const prepared = spawnSync(process.execPath, [cli.pathname.replace(/^\/([A-Za-z]:)/, '$1')], { encoding: 'utf8' });
  assert.equal(prepared.status, 0, prepared.stderr);
  assert.equal(JSON.parse(prepared.stdout).status, 'FREEZE CANDIDATE');
  const blocked = spawnSync(process.execPath, [cli.pathname.replace(/^\/([A-Za-z]:)/, '$1'), 'broadcast'], { encoding: 'utf8' });
  assert.notEqual(blocked.status, 0); assert(!blocked.stdout.includes('transactionHash'));
});
