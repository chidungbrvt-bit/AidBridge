// Explicit C06 CLI. prepare is offline; preflight/post-deploy are read-only; broadcast is approval-gated.
import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import { resolve } from 'node:path';
import { collectCandidate, ROOT, sha, verifyManifest, verifyApproval, preflight, chain,
  seal, unseal, validateSigned, writeNew, updateJournal, verifyDeployment,
  broadcastPreparedDeployment } from './c06-release.mjs';

let provider;
try {
  const args = process.argv.slice(2), mode = args.shift() ?? 'prepare';
  assert(['prepare', 'preflight', 'broadcast', 'post-deploy'].includes(mode), 'INVALID_MODE');
  const options = {};
  while (args.length) {
    const name = args.shift();
    assert(['--manifest', '--approval', '--execute-approved'].includes(name), 'UNKNOWN_ARGUMENT');
    assert(!(name in options), 'DUPLICATE_ARGUMENT');
    options[name] = name === '--execute-approved' ? true : args.shift();
    assert(options[name], 'MISSING_ARGUMENT');
  }
  const bundle = collectCandidate();
  if (mode === 'prepare') {
    assert(Object.keys(options).length === 0, 'PREPARE_OFFLINE_ONLY');
    console.log(JSON.stringify(bundle.manifest, null, 2));
  } else {
    const manifestBytes = readFileSync(options['--manifest'] ?? resolve(ROOT, 'deployment/c06-freeze-candidate.json'));
    const manifest = JSON.parse(manifestBytes); verifyManifest(manifest, bundle);
    const { planBytes } = bundle;
    const manifestSha256 = sha(manifestBytes), planSha256 = sha(planBytes);
    const publicPath = resolve(ROOT, bundle.plan.publicJournal), privatePath = resolve(ROOT, bundle.plan.privateJournal);
    let approval;
    const assertApproved = () => verifyApproval(manifest, manifestBytes, planBytes, approval);
    // Validate local approval BEFORE creating a provider or reading secret environment variables.
    if (mode === 'broadcast') {
      assert(options['--execute-approved'] === true && options['--approval'], 'EXPLICIT_APPROVAL_REQUIRED');
      approval = JSON.parse(readFileSync(options['--approval']));
      assertApproved();
    } else assert(!options['--approval'] && !options['--execute-approved'], 'READ_ONLY_MODE');
    const { JsonRpcProvider, Wallet, keccak256, getAddress } = await import('ethers');
    provider = new JsonRpcProvider(bundle.plan.rpcUrl, undefined, { cacheTimeout: -1 });
    await chain(provider);
    if (mode === 'preflight') {
      assert(!existsSync(publicPath) && !existsSync(privatePath), 'EXISTING_C06_JOURNAL_RECONCILE');
      const p = await preflight(provider, bundle);
      console.log(JSON.stringify({ status: 'READ_ONLY_PREFLIGHT_NOT_APPROVAL', chainId: 43113,
        manifestSha256, planSha256, expectedNonce: p.nonce, predictedAddress: p.address,
        gasLimit: p.gasLimit.toString(), gasPriceWei: p.gasPrice.toString(), relayerBalance: p.relayerBalance,
        manualGates: ['freeze', 'combined PREPARED wrong-chain evidence', 'deployment approval',
          'organization setup approval', 'C03 and staging disposition', 'separate cutover approval'] }, null, 2));
    } else if (mode === 'post-deploy') {
      const record = JSON.parse(readFileSync(publicPath));
      assert.equal(record.manifestSha256, manifestSha256); assert.equal(record.planSha256, planSha256);
      console.log(JSON.stringify(await verifyDeployment(provider, bundle, record), null, 2));
    } else {
      let record, raw;
      const key = process.env.C06_RECOVERY_KEY; // AES key only; never logged or written.
      assert(/^[0-9a-fA-F]{64}$/.test(key ?? ''), 'RECOVERY_KEY_REQUIRED');
      if (existsSync(publicPath) || existsSync(privatePath)) {
        assert(existsSync(publicPath) && existsSync(privatePath), 'PARTIAL_JOURNAL_MANUAL_RECONCILE');
        record = JSON.parse(readFileSync(publicPath));
        assert.equal(record.manifestSha256, manifestSha256); assert.equal(record.planSha256, planSha256);
        assert.equal(record.nonce, approval.expectedNonce);
        assert.equal(record.address.toLowerCase(), approval.predictedAddress.toLowerCase());
        if (await provider.getTransactionReceipt(record.transactionHash)) {
          const result = await verifyDeployment(provider, bundle, record);
          updateJournal(publicPath, result); console.log(JSON.stringify(result));
          provider.destroy(); provider = null; process.exit(0);
        }
        const sealed = JSON.parse(readFileSync(privatePath));
        assert.equal(sealed.context, manifestSha256 + ':' + planSha256);
        raw = unseal(sealed, key, sealed.context);
        validateSigned(raw, bundle.plan, record, bundle.artifact);
      } else {
        const p = await preflight(provider, bundle);
        assert.equal(p.nonce, approval.expectedNonce, 'APPROVED_NONCE_CHANGED');
        assert.equal(p.address.toLowerCase(), getAddress(approval.predictedAddress).toLowerCase(), 'APPROVED_TARGET_CHANGED');
        const signer = new Wallet(process.env.C06_DEPLOYER_PRIVATE_KEY);
        assert.equal(signer.address.toLowerCase(), bundle.plan.deployer.toLowerCase(), 'SIGNER_MISMATCH');
        await chain(provider); // immediately before signing
        assertApproved();
        raw = await signer.signTransaction({ ...p.unsigned, type: 0, chainId: 43113,
          nonce: p.nonce, gasLimit: p.gasLimit, gasPrice: p.gasPrice });
        record = { status: 'PREPARED', chainId: 43113, address: p.address, nonce: p.nonce,
          manifestSha256, planSha256, transactionHash: keccak256(raw),
          constructorArguments: bundle.manifest.constructor.values, buildInfoId: bundle.manifest.buildInfoId,
          admin: bundle.plan.admin, relayer: bundle.plan.relayer, orgRef: bundle.plan.orgRef };
        validateSigned(raw, bundle.plan, record, bundle.artifact);
        const context = manifestSha256 + ':' + planSha256;
        writeNew(privatePath, { context, ...seal(raw, key, context) });
        writeNew(publicPath, record); // both journals exist BEFORE any broadcast
      }
      await broadcastPreparedDeployment(provider, bundle.plan, record, raw, assertApproved);
      updateJournal(publicPath, { ...record, status: 'SUBMITTED' });
      // Bounded receipt wait; timeout preserves journals and hash for the next approved resume.
      const receipt = await provider.waitForTransaction(record.transactionHash, 1, 120000);
      assert(receipt, 'RECEIPT_PENDING');
      const result = await verifyDeployment(provider, bundle, record);
      updateJournal(publicPath, result); console.log(JSON.stringify(result, null, 2));
    }
  }
} catch (error) {
  // Never emit RPC/signer/crypto errors, which may contain secret material or signed transaction bytes.
  const safeCodes = new Set(['EXPLICIT_APPROVAL_REQUIRED', 'ARTIFACT_NOT_FROZEN', 'DEPLOY_APPROVAL_MISSING',
    'APPROVAL_MANIFEST_MISMATCH', 'APPROVAL_PLAN_MISMATCH', 'APPROVAL_EXPIRED', 'REGRESSION_GATE_NOT_MET',
    'PRESERVATION_PLAN_NOT_REVIEWED', 'RPC_WRONG_CHAIN', 'EXISTING_C06_JOURNAL_RECONCILE',
    'PARTIAL_JOURNAL_MANUAL_RECONCILE', 'APPROVED_NONCE_CHANGED', 'APPROVED_TARGET_CHANGED', 'SIGNER_MISMATCH',
    'PENDING_SIGNER_TX', 'DEPLOYMENT_TARGET_OCCUPIED', 'GAS_CAP', 'FEE_CAP', 'INSUFFICIENT_GAS_BALANCE',
    'RECOVERY_KEY_REQUIRED', 'DEPLOYMENT_NONCE_UNRESOLVED', 'OBSERVED_DEPLOYMENT_MISMATCH',
    'RPC_HASH_MISMATCH', 'RECEIPT_PENDING', 'DEPLOY_REVERTED', 'BLOCK_NOT_CANONICAL',
    'RUNTIME_BYTES_MISMATCH', 'REGISTRY_ALREADY_CONFIGURED']);
  const code = String(error?.message ?? '').split('\n')[0];
  console.error(safeCodes.has(code) ? code : 'C06_OPERATION_STOPPED_RECONCILE_JOURNALS_AND_REVIEW_LOCAL_GATES');
  process.exitCode = 1;
} finally { provider?.destroy(); }
