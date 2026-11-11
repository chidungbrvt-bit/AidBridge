// Independent C05 Fuji smoke only. This is NOT a B05 SQL worker.
// Default is read-only. --broadcast-independent resumes exactly one persisted attempt.
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { join } from 'node:path';
import { createHash } from 'node:crypto';
import { AbiCoder, Contract, JsonRpcProvider, Transaction, hexlify, id, keccak256, parseUnits, randomBytes } from 'ethers';
import { artifact, chain, FUJI_RPC, RUNTIME_HASH } from './deployment.mjs';
import { storePath, unlockRelayer } from './wallet-store.mjs';
import { collectProof } from './c05-proof.mjs';

const base = new URL('../deployment/', import.meta.url);
const attemptFile = new URL('c05-attempt.public.json', base);
const privateFile = new URL('c05-attempt.private.clixml', base);
const proofFile = new URL('../../docs/fixtures/c05/fuji-case-verified.proof.json', import.meta.url);
const load = (file) => JSON.parse(readFileSync(file, 'utf8'));
const save = (file, value, flag = 'w') => writeFileSync(file, JSON.stringify(value, null, 2) + '\n', { flag });
const check = (ok, reason) => { if (!ok) throw Error(reason); };

function privateStore(action, payload = '') {
  check(process.platform === 'win32', 'WINDOWS_DPAPI_REQUIRED');
  const path = decodeURIComponent(privateFile.pathname).replace(/^\//, '').replaceAll('/', '\\');
  const env = { ...process.env, AIDBRIDGE_C05_STORE: path };
  delete env.PSModulePath;
  const script = action === 'save'
    ? "$ErrorActionPreference='Stop'; if(Test-Path -LiteralPath $env:AIDBRIDGE_C05_STORE){throw 'EXISTS'}; $v=[Console]::In.ReadToEnd(); $s=ConvertTo-SecureString $v -AsPlainText -Force; $s | Export-Clixml -LiteralPath $env:AIDBRIDGE_C05_STORE -NoClobber"
    : "$ErrorActionPreference='Stop'; $s=Import-Clixml -LiteralPath $env:AIDBRIDGE_C05_STORE; $b=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($s); try{[Console]::Out.Write([Runtime.InteropServices.Marshal]::PtrToStringBSTR($b))}finally{[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($b)}";
  const result = spawnSync(join(process.env.SystemRoot, 'System32/WindowsPowerShell/v1.0/powershell.exe'),
    ['-NoProfile', '-NonInteractive', '-Command', script], { input: payload, encoding: 'utf8', windowsHide: true, env });
  check(result.status === 0, 'C05_DPAPI_OPERATION_FAILED');
  return result.stdout.trim();
}

const provider = new JsonRpcProvider(FUJI_RPC);
try {
  check(process.argv.slice(2).every(arg => ['--broadcast-independent', '--save-proof'].includes(arg)), 'UNKNOWN_ARGUMENT');
  const broadcast = process.argv.includes('--broadcast-independent');
  await chain(provider);
  const m = load(new URL('fuji-deployment.json', base));
  const a = artifact(); // Pins reviewed C03 source, creation/runtime bytecode, full ABI.
  check(m.chainId === '43113' && m.runtimeHash === RUNTIME_HASH, 'DEPLOYMENT_CONTEXT_MISMATCH');
  check(keccak256(await provider.getCode(m.address)) === RUNTIME_HASH, 'RUNTIME_MISMATCH');
  const registry = new Contract(m.address, a.abi, provider);
  const [registered, active, relayer] = await registry.getOrganization(m.orgRef);
  check(registered && active && relayer.toLowerCase() === m.relayer.toLowerCase()
    && await registry.hasRole(await registry.RELAYER_ROLE(), relayer), 'RELAYER_NOT_AUTHORIZED');
  check(m.admin.toLowerCase() !== relayer.toLowerCase(), 'KEY_SEPARATION_VIOLATION');
  if (!existsSync(attemptFile)) {
    check(broadcast, 'NO_ATTEMPT_USE_EXPLICIT_BROADCAST_FLAG');
    check(!existsSync(privateFile), 'ORPHAN_PRIVATE_ATTEMPT_RECONCILE_BEFORE_RETRY');
    check(existsSync(storePath), 'MISSING_DPAPI_SIGNER');
    const nonce = await provider.getTransactionCount(relayer, 'latest');
    check(nonce === await provider.getTransactionCount(relayer, 'pending'), 'PENDING_RELAYER_TRANSACTION');
    const e = { chainId: 43113, contractAddress: m.address, relayer, nonce,
      orgRef: m.orgRef, caseRef: hexlify(randomBytes(32)), eventId: hexlify(randomBytes(32)),
      chainSequence: '1', schemaVersion: 1 };
    // Existing C01 synthetic manifest; never substitute it for an approved B05 snapshot.
    const vector = load(new URL('../../docs/fixtures/c01/golden-vector.input.json', import.meta.url));
    const manifest = { ...vector.manifest, caseRef: e.caseRef, eventId: e.eventId,
      orgRef: e.orgRef, occurredAt: new Date().toISOString() };
    const canonical = value => typeof value === 'string' ? JSON.stringify(value.normalize('NFC'))
      : Array.isArray(value) ? '[' + value.map(canonical).join(',') + ']'
      : value !== null && typeof value === 'object' ? '{' + Object.keys(value).sort().map(k => JSON.stringify(k) + ':' + canonical(value[k])).join(',') + '}'
      : JSON.stringify(value);
    const manifestDigest = '0x' + createHash('sha256').update(canonical(manifest)).digest('hex');
    const salt = hexlify(randomBytes(32));
    e.evidenceCommitment = keccak256(AbiCoder.defaultAbiCoder().encode(
      ['bytes32', 'uint256', 'address', 'bytes32', 'bytes32', 'bytes32', 'bytes32'],
      [id('AIDBRIDGE_EVIDENCE_V1'), 43113, m.address, e.caseRef, e.eventId, manifestDigest, salt]));
    const data = registry.interface.encodeFunctionData('recordCaseVerification',
      [e.orgRef, e.caseRef, e.eventId, 1, e.evidenceCommitment, 1]);
    check(!(await registry.getEventRecord(e.eventId))[0] && !(await registry.getCaseSummary(e.caseRef))[0], 'REF_ALREADY_USED');
    const gasEstimate = await provider.estimateGas({ from: relayer, to: m.address, data });
    const gasLimit = (gasEstimate * 120n + 99n) / 100n;
    const fee = (await provider.getFeeData()).gasPrice;
    check(fee !== null, 'MISSING_FEE_DATA');
    const gasPrice = fee * 12n / 10n > parseUnits('1', 9) ? fee * 12n / 10n : parseUnits('1', 9);
    check(gasLimit <= 600000n && gasPrice <= parseUnits('25', 9)
      && gasLimit * gasPrice <= parseUnits('0.01', 18), 'FEE_OR_GAS_CAP_EXCEEDED');
    const balance = await provider.getBalance(relayer);
    check(balance >= gasLimit * gasPrice, 'INSUFFICIENT_RELAYER_BALANCE');
    const signer = unlockRelayer();
    check(signer.address.toLowerCase() === relayer.toLowerCase(), 'SIGNER_MISMATCH');
    const request = { type: 0, chainId: 43113, nonce, to: m.address, data, value: 0,
      gasLimit: gasLimit.toString(), gasPrice: gasPrice.toString() };
    const raw = await signer.signTransaction(request);
    e.transactionHash = keccak256(raw);
    // Durable private recovery journal before public PREPARED record and before broadcast.
    // Contains signed bytes and synthetic commitment inputs, NEVER any wallet private key.
    privateStore('save', JSON.stringify({ signedTransaction: raw, manifest, manifestDigest, salt }));
    save(attemptFile, { scope: 'INDEPENDENT_CONTRACT_SMOKE_NOT_B05_JOB', backendJobId: null,
      preparedAt: new Date().toISOString(), state: 'PREPARED', expected: e, request,
      gasEstimate: gasEstimate.toString(), balanceBeforeWei: balance.toString() }, 'wx');
  }
  const attempt = load(attemptFile), e = attempt.expected;
  check(attempt.scope === 'INDEPENDENT_CONTRACT_SMOKE_NOT_B05_JOB' && attempt.backendJobId === null,
    'INVALID_ATTEMPT_SCOPE');
  check(e.chainId === 43113 && e.contractAddress === m.address && e.orgRef === m.orgRef
    && e.relayer.toLowerCase() === relayer.toLowerCase(), 'ATTEMPT_CONTEXT_MISMATCH');
  let receipt = await provider.getTransactionReceipt(e.transactionHash);
  if (!receipt && broadcast) {
    const recovery = JSON.parse(privateStore('load'));
    const tx = Transaction.from(recovery.signedTransaction);
    check(tx.hash === e.transactionHash && tx.from.toLowerCase() === relayer.toLowerCase()
      && tx.chainId === 43113n && tx.to === m.address && tx.nonce === e.nonce
      && tx.data === attempt.request.data && tx.value === 0n
      && tx.gasLimit === BigInt(attempt.request.gasLimit) && tx.gasPrice === BigInt(attempt.request.gasPrice),
    'SIGNED_ATTEMPT_MISMATCH');
    const expectedData = registry.interface.encodeFunctionData('recordCaseVerification',
      [e.orgRef, e.caseRef, e.eventId, e.chainSequence, e.evidenceCommitment, e.schemaVersion]);
    check(tx.data === expectedData, 'SIGNED_CALLDATA_MISMATCH');
    const known = await provider.getTransaction(e.transactionHash);
    if (!known) {
      check(await provider.getTransactionCount(relayer, 'latest') === tx.nonce
        && await provider.getTransactionCount(relayer, 'pending') === tx.nonce, 'NONCE_CONFLICT_RECONCILE');
      await chain(provider);
      await provider.broadcastTransaction(recovery.signedTransaction); // Retries reuse EXACT bytes/hash.
    }
    receipt = await provider.waitForTransaction(e.transactionHash, 1, 120000);
  }
  check(receipt !== null, 'RECEIPT_PENDING_KEEP_ORIGINAL_ATTEMPT');
  const proof = await collectProof(provider, a.abi, e, RUNTIME_HASH);
  proof.scope = attempt.scope;
  proof.backendJobId = null;
  proof.backendIntegration = 'BLOCKED_JOB_AVAILABILITY_UNKNOWN_NO_DB_ENV_NO_WORKER_NO_CONFIRMED_ORG_BINDING';
  proof.expectedSource = 'Persisted independent smoke plan; NOT a B05 job';
  proof.evidenceOrigin = 'C01 synthetic manifest metadata; not evidence-file hashes from an approved backend snapshot';
  proof.abiVersion = 'D49'; proof.schemaVersion = 1;
  proof.sourceCommit = m.sourceCommit;
  proof.checkedAt = new Date().toISOString();
  proof.relayerBalanceAfterWei = (await provider.getBalance(relayer)).toString();
  proof.finalityCheck = 'Receipt and canonical block-number/hash readback; no independent consensus-finality claim';
  if (broadcast || process.argv.includes('--save-proof')) save(proofFile, proof);
  console.log(JSON.stringify({ status: 'PASS', scope: proof.scope, transactionHash: proof.transactionHash,
    blockNumber: proof.blockNumber, gasUsed: proof.gasUsed, readOnly: !broadcast }));
} catch (error) {
  // Never dump RPC error objects (they can contain raw signed transaction bytes).
  const code = /^[A-Z0-9_]+$/.test(error.message ?? '') ? error.message : 'C05_OPERATION_ERROR';
  console.error(JSON.stringify({ status: 'NOT_CONFIRMED', code })); process.exitCode = 1;
} finally { provider.destroy(); }
