// C06-only release preparation and guarded deployment primitives. No implicit network or wallet access.
import assert from 'node:assert/strict';
import { readFileSync, readdirSync, existsSync, writeFileSync, renameSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';
import { createHash, randomBytes, createCipheriv, createDecipheriv } from 'node:crypto';
import { ContractFactory, getAddress, getCreateAddress, keccak256, Transaction, ZeroAddress } from 'ethers';
export const ROOT = fileURLToPath(new URL('../', import.meta.url));
export const sha = x => createHash('sha256').update(x).digest('hex');
const norm = x => x.replace(/\r\n/g, '\n');
const read = (root, p) => readFileSync(resolve(root, p));
export function validatePlan(p) {
  assert.equal(p.chainId, 43113, 'WRONG_CHAIN_CONFIG');
  assert.equal(p.rpcUrl, 'https://api.avax-test.network/ext/bc/C/rpc', 'RPC_NOT_APPROVED');
  for (const k of ['deployer', 'admin', 'relayer']) assert.notEqual(getAddress(p[k]), ZeroAddress);
  assert.equal(getAddress(p.admin), getAddress(p.deployer), 'ADMIN_SIGNER_PLAN_MISMATCH');
  assert.notEqual(getAddress(p.admin), getAddress(p.relayer), 'KEY_SEPARATION');
  assert(/^0x[0-9a-f]{64}$/.test(p.orgRef) && !/^0x0{64}$/.test(p.orgRef), 'INVALID_ORG_REF');
  assert(BigInt(p.maxGasLimit) > 0n && BigInt(p.maxGasLimit) <= 4000000n, 'GAS_CAP');
  assert(BigInt(p.maxGasPriceWei) > 0n && BigInt(p.maxGasPriceWei) <= 25000000000n, 'FEE_CAP');
  assert.equal(p.publicJournal, 'deployment/c06-fuji-deployment.public.json');
  assert.equal(p.privateJournal, 'deployment/c06-fuji-recovery.private.aes.json');
  return p;
}
export function collectCandidate(root = ROOT) {
  const plan = validatePlan(JSON.parse(read(root, 'deployment/c06-fuji-plan.public.json')));
  const source = norm(read(root, 'contracts/AidRegistry.sol').toString('utf8'));
  const bytes = read(root, 'artifacts/contracts/AidRegistry.sol/AidRegistry.json');
  const artifact = JSON.parse(bytes);
  const buildName = readdirSync(resolve(root, 'artifacts/build-info')).filter(x => !x.endsWith('.output.json'))
    .find(name => { const b = JSON.parse(read(root, 'artifacts/build-info/' + name));
      return Object.entries(b.input.sources).some(([p, v]) => p.endsWith('contracts/AidRegistry.sol') && norm(v.content) === source); });
  assert(buildName, 'BUILD_SOURCE_MISMATCH');
  const build = JSON.parse(read(root, 'artifacts/build-info/' + buildName));
  const result = JSON.parse(read(root, 'artifacts/build-info/' + buildName.replace(/\.json$/, '.output.json')));
  const sourceKey = Object.keys(build.input.sources).find(p => p.endsWith('contracts/AidRegistry.sol'));
  const compiled = (result.output ?? result).contracts[sourceKey].AidRegistry;
  assert.deepEqual(artifact.abi, compiled.abi); assert.equal(artifact.bytecode, '0x' + compiled.evm.bytecode.object);
  assert.equal(artifact.deployedBytecode, '0x' + compiled.evm.deployedBytecode.object);
  assert.deepEqual(artifact.abi, JSON.parse(read(root, '../docs/fixtures/c06/AidRegistry.c06.abi.json')));
  assert.equal(build.solcVersion, '0.8.28'); assert.equal(build.input.settings.evmVersion, 'paris');
  assert.equal(build.input.settings.optimizer.enabled, true); assert.equal(build.input.settings.optimizer.runs, 200);
  const sourceCommit = execFileSync('git', ['log', '-1', '--format=%H', '--', 'contracts/AidRegistry.sol'], { cwd: root, encoding: 'utf8' }).trim();
  const manifest = {
    status: 'FREEZE CANDIDATE', sourceCommit, sourcePath: 'contracts/contracts/AidRegistry.sol',
    sourceSha256LF: sha(source), artifactPath: 'contracts/artifacts/contracts/AidRegistry.sol/AidRegistry.json',
    artifactSha256: sha(bytes), abiSha256: sha(JSON.stringify(artifact.abi)),
    creationKeccak256: keccak256(artifact.bytecode), runtimeKeccak256: keccak256(artifact.deployedBytecode),
    buildInfoId: buildName.replace(/\.json$/, ''), compilerVersion: build.solcVersion,
    compilerLongVersion: build.solcLongVersion ?? null, compilationSettings: build.input.settings,
    standardInputSha256: sha(JSON.stringify(build.input)),
    buildSourcesSha256: Object.fromEntries(Object.entries(build.input.sources).map(([p, v]) => [p, sha(v.content)])),
    constructor: { types: ['address'], values: [getAddress(plan.admin)] }, chainId: 43113,
    approval: 'PENDING_GROUP_FREEZE_AND_DEPLOY_DECISION',
  };
  return { manifest, artifact, plan };
}
export function verifyManifest(manifest, bundle) {
  const expected = { ...bundle.manifest, status: manifest.status, approval: manifest.approval };
  assert.deepEqual(manifest, expected, 'FREEZE_MANIFEST_MISMATCH');
  assert(['FREEZE CANDIDATE', 'FROZEN'].includes(manifest.status), 'INVALID_FREEZE_STATUS');
}
export function verifyApproval(manifest, manifestBytes, planBytes, approval) {
  assert.equal(manifest.status, 'FROZEN', 'ARTIFACT_NOT_FROZEN');
  assert.equal(approval.status, 'APPROVED', 'DEPLOY_APPROVAL_MISSING');
  assert.equal(approval.chainId, 43113); assert.equal(approval.scope, 'C06_DEPLOY_ONLY');
  assert.equal(approval.combinedPreparedWrongChainRegression, 'PASS', 'REGRESSION_GATE_NOT_MET');
  assert.equal(approval.preservationPlanReviewed, true, 'PRESERVATION_PLAN_NOT_REVIEWED');
  assert.equal(approval.manifestSha256, sha(manifestBytes), 'APPROVAL_MANIFEST_MISMATCH');
  assert.equal(approval.planSha256, sha(planBytes), 'APPROVAL_PLAN_MISMATCH');
  assert(typeof approval.approvedBy === 'string' && approval.approvedBy.trim(), 'APPROVER_MISSING');
  assert(Number.isSafeInteger(approval.expectedNonce) && approval.expectedNonce >= 0);
  assert(Number.isFinite(Date.parse(approval.expiresAt)) && Date.now() < Date.parse(approval.expiresAt), 'APPROVAL_EXPIRED');
}
export async function chain(provider) { assert.equal(BigInt(await provider.send('eth_chainId', [])), 43113n, 'RPC_WRONG_CHAIN'); }
export async function preflight(provider, bundle) {
  const { artifact, plan } = bundle; await chain(provider);
  for (const a of [plan.deployer, plan.relayer]) assert.equal(await provider.getCode(a), '0x', 'EOA_REQUIRED');
  const nonce = await provider.getTransactionCount(plan.deployer, 'pending');
  assert.equal(nonce, await provider.getTransactionCount(plan.deployer, 'latest'), 'PENDING_SIGNER_TX');
  const address = getCreateAddress({ from: plan.deployer, nonce });
  assert.equal(await provider.getCode(address), '0x', 'DEPLOYMENT_TARGET_OCCUPIED');
  const unsigned = await new ContractFactory(artifact.abi, artifact.bytecode).getDeployTransaction(plan.admin);
  const estimate = await provider.estimateGas({ ...unsigned, from: plan.deployer });
  const gasLimit = estimate * 120n / 100n; assert(gasLimit <= BigInt(plan.maxGasLimit), 'GAS_CAP');
  const fee = (await provider.getFeeData()).gasPrice; assert(fee !== null && fee > 0n, 'NO_FEE_DATA');
  const gasPrice = fee * 120n / 100n; assert(gasPrice <= BigInt(plan.maxGasPriceWei), 'FEE_CAP');
  assert(await provider.getBalance(plan.deployer) >= gasLimit * gasPrice, 'INSUFFICIENT_GAS_BALANCE');
  return { unsigned, nonce, address, gasLimit, gasPrice, relayerBalance: (await provider.getBalance(plan.relayer)).toString() };
}
export function seal(raw, keyHex, aad) {
  assert(/^[0-9a-fA-F]{64}$/.test(keyHex ?? ''), 'INVALID_RECOVERY_KEY');
  const iv = randomBytes(12), cipher = createCipheriv('aes-256-gcm', Buffer.from(keyHex, 'hex'), iv);
  cipher.setAAD(Buffer.from(aad)); const data = Buffer.concat([cipher.update(raw, 'utf8'), cipher.final()]);
  return { iv: iv.toString('hex'), ciphertext: data.toString('hex'), tag: cipher.getAuthTag().toString('hex') };
}
export function unseal(sealed, keyHex, aad) {
  assert(/^[0-9a-fA-F]{64}$/.test(keyHex ?? ''), 'INVALID_RECOVERY_KEY');
  const c = createDecipheriv('aes-256-gcm', Buffer.from(keyHex, 'hex'), Buffer.from(sealed.iv, 'hex'));
  c.setAAD(Buffer.from(aad)); c.setAuthTag(Buffer.from(sealed.tag, 'hex'));
  return Buffer.concat([c.update(Buffer.from(sealed.ciphertext, 'hex')), c.final()]).toString('utf8');
}
export function validateSigned(raw, plan, record, artifact) {
  assert.equal(record.chainId, 43113, 'JOURNAL_CHAIN_MISMATCH');
  const t = Transaction.from(raw);
  assert.equal(t.chainId, 43113n); assert.equal(t.to, null); assert.equal(t.value, 0n);
  assert.equal(t.from.toLowerCase(), plan.deployer.toLowerCase()); assert.equal(t.nonce, record.nonce);
  assert.equal(keccak256(raw), record.transactionHash);
  assert.equal(getCreateAddress({ from: t.from, nonce: t.nonce }).toLowerCase(), record.address.toLowerCase());
  assert(t.gasLimit <= BigInt(plan.maxGasLimit) && t.gasLimit > 0n);
  assert(t.gasPrice !== null && t.gasPrice > 0n && t.gasPrice <= BigInt(plan.maxGasPriceWei));
  assert.equal(t.data, artifact.bytecode + new ContractFactory(artifact.abi, artifact.bytecode).interface.encodeDeploy([plan.admin]).slice(2), 'SIGNED_CONSTRUCTOR_MISMATCH');
}
export function writeNew(path, data) { writeFileSync(path, JSON.stringify(data, null, 2) + '\n', { flag: 'wx', mode: 0o600 }); }
export function updateJournal(path, data) {
  const temporary = path + '.next'; writeNew(temporary, data); renameSync(temporary, path);
}
export async function verifyDeployment(provider, bundle, record) {
  assert.equal(record.chainId, 43113, 'JOURNAL_CHAIN_MISMATCH');
  await chain(provider); const receipt = await provider.getTransactionReceipt(record.transactionHash);
  assert(receipt, 'RECEIPT_PENDING'); assert.equal(receipt.status, 1, 'DEPLOY_REVERTED');
  assert.equal(receipt.hash.toLowerCase(), record.transactionHash.toLowerCase());
  assert.equal(receipt.to, null); assert.equal(receipt.from.toLowerCase(), bundle.plan.deployer.toLowerCase());
  assert.equal(receipt.contractAddress.toLowerCase(), record.address.toLowerCase());
  const block = await provider.getBlock(receipt.blockNumber); assert(block && block.hash === receipt.blockHash, 'BLOCK_NOT_CANONICAL');
  const tx = await provider.getTransaction(record.transactionHash); assert(tx && tx.chainId === 43113n);
  assert.equal(tx.nonce, record.nonce); assert.equal(tx.to, null); assert.equal(tx.value, 0n);
  const unsigned = await new ContractFactory(bundle.artifact.abi, bundle.artifact.bytecode).getDeployTransaction(bundle.plan.admin);
  assert.equal(tx.data, unsigned.data); assert.equal(tx.from.toLowerCase(), bundle.plan.deployer.toLowerCase());
  const code = await provider.getCode(record.address); assert.equal(code, bundle.artifact.deployedBytecode, 'RUNTIME_BYTES_MISMATCH');
  const { Contract } = await import('ethers'); const registry = new Contract(record.address, bundle.artifact.abi, provider);
  assert(await registry.hasRole(await registry.DEFAULT_ADMIN_ROLE(), bundle.plan.admin), 'ADMIN_ROLE_MISMATCH');
  assert(!await registry.hasRole(await registry.RELAYER_ROLE(), bundle.plan.admin), 'ADMIN_RELAYER_NOT_SEPARATED');
  const org = await registry.getOrganization(bundle.plan.orgRef);
  assert(!org[0] && !org[1] && org[2] === ZeroAddress, 'REGISTRY_ALREADY_CONFIGURED');
  assert(!await registry.hasRole(await registry.RELAYER_ROLE(), bundle.plan.relayer), 'UNEXPECTED_RELAYER_ROLE');
  return { ...record, status: 'DEPLOYED_VERIFIED_EMPTY_REGISTRY', blockNumber: receipt.blockNumber,
    blockHash: receipt.blockHash, gasUsed: receipt.gasUsed.toString(), runtimeKeccak256: keccak256(code),
    sourceVerification: 'NOT RUN', organizationSetup: 'NOT RUN', backendCutover: 'NOT RUN' };
}
