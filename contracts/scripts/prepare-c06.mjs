// Offline C06 candidate verification only. No provider, wallet, environment secrets or file writes.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { ContractFactory, Interface, getAddress, keccak256, ZeroAddress } from 'ethers';

if (process.argv.length !== 2) throw new Error('OFFLINE_ONLY_NO_ARGUMENTS');
const read = relative => readFileSync(new URL(relative, import.meta.url));
const sha = data => createHash('sha256').update(data).digest('hex');
const pins = {
  sourceSha256: 'c27379bba82dd88897a3fe2bf1b0f5c2ad77e7f29830afef4dde7380ed272153',
  artifactSha256: '976825c365d0dd99c2097e90b0bbced2d36556524fcb785de4ac19367499c592',
  abiSha256: '548b46168243852ef455752f09afa6fba7989bb1fcc0b13ac0f6a64f24175eae',
  creationKeccak256: '0x9e8943c4e5dc82f7a0e9c60feaa6a2fc28d1b9c2b616aac80e20beccc143258c',
  runtimeKeccak256: '0x525314d10311e01e01a61a6f9e75ee432f59ca09ff3aa27445aed3962e09011e',
};
const source = read('../contracts/AidRegistry.sol').toString('utf8').replace(/\r\n/g, '\n');
const raw = read('../artifacts/contracts/AidRegistry.sol/AidRegistry.json');
const artifact = JSON.parse(raw);
assert.equal(sha(source), pins.sourceSha256, 'C06_SOURCE_MISMATCH');
assert.equal(sha(raw), pins.artifactSha256, 'C06_ARTIFACT_MISMATCH');
assert.equal(sha(JSON.stringify(artifact.abi)), pins.abiSha256, 'C06_ABI_MISMATCH');
assert.equal(keccak256(artifact.bytecode), pins.creationKeccak256, 'C06_CREATION_MISMATCH');
assert.equal(keccak256(artifact.deployedBytecode), pins.runtimeKeccak256, 'C06_RUNTIME_MISMATCH');
assert.deepEqual(artifact.abi, JSON.parse(read('../../docs/fixtures/c06/AidRegistry.c06.abi.json')));
const buildId = 'solc-0_8_28-6d69e2cf1ddbaa7f61c90daac742b8efd4cf518a';
const build = JSON.parse(read('../artifacts/build-info/' + buildId + '.json'));
assert.equal(build.solcVersion, '0.8.28');
assert.equal(build.input.settings.evmVersion, 'paris');
assert.deepEqual(build.input.settings.optimizer, { enabled: true, runs: 200 });
const entry = Object.entries(build.input.sources).find(([name, value]) =>
  name.endsWith('contracts/AidRegistry.sol') && value.content.replace(/\r\n/g, '\n') === source);
assert(entry, 'BUILD_SOURCE_MISMATCH');
const output = JSON.parse(read('../artifacts/build-info/' + buildId + '.output.json'));
const compiled = (output.output ?? output).contracts[entry[0]].AidRegistry;
assert.deepEqual(compiled.abi, artifact.abi);
assert.equal('0x' + compiled.evm.bytecode.object, artifact.bytecode);
assert.equal('0x' + compiled.evm.deployedBytecode.object, artifact.deployedBytecode);
const abi = new Interface(artifact.abi);
for (const name of ['recordCaseVerification', 'recordAidCommitment', 'recordCommitmentReduction',
  'recordGoodsReceipt', 'recordDelivery', 'confirmDelivery', 'flagDispute', 'resolveDispute']) {
  assert(abi.getFunction(name), 'MISSING_C06_FUNCTION_' + name);
}
for (const name of ['CaseVerified', 'AidCommitted', 'CommitmentReduced', 'GoodsReceived',
  'DeliveryRecorded', 'DeliveryConfirmed', 'DisputeFlagged', 'DeliveryVoided', 'DisputeResolved']) {
  assert(abi.getEvent(name), 'MISSING_C06_EVENT_' + name);
}
const admin = getAddress('0x131DD6d404f8D0B118D6DB122194bc15b4E4601E');
const relayer = getAddress('0x95a9564899849CF566DE5713205C4B747F361f55');
assert(admin !== relayer && admin !== ZeroAddress && relayer !== ZeroAddress);
const constructor = artifact.abi.find(x => x.type === 'constructor');
assert.deepEqual(constructor.inputs.map(x => x.type), ['address']);
const unsigned = await new ContractFactory(artifact.abi, artifact.bytecode).getDeployTransaction(admin);
assert.equal(unsigned.value ?? 0n, 0n);
const c03 = JSON.parse(read('../deployment/fuji-deployment.json'));
assert.notEqual(c03.runtimeHash, pins.runtimeKeccak256, 'C03_IS_NOT_C06');
console.log(JSON.stringify({
  status: 'OFFLINE_CANDIDATE_VERIFIED_NOT_APPROVED', chainId: 43113, buildId, pins,
  sourceCommit: '6944fffd9858565f36c909ae9e5f83804afbe36f',
  proposedPublicBinding: { admin, relayer,
    orgRef: '0x2395bfe7f91467046a39bd8440313164c6442839b2be6698832b7b4941b80a46',
    state: 'HISTORICAL_ADDRESSES_PENDING_CURRENT_PREFLIGHT' },
  constructorArguments: [admin], deploymentDataKeccak256: keccak256(unsigned.data),
  c06Registry: null, deploymentTxHash: null, nonce: null, gasLimit: null, feeCap: null,
  proposedJournalNamespace: {
    public: 'contracts/deployment/c06-fuji-deployment.public.json',
    encryptedRecovery: 'contracts/deployment/c06-fuji-recovery.private.clixml',
    state: 'RESERVED_NAMES_ONLY_NOT_CREATED' },
  c03Preserved: { registry: c03.address, runtimeHash: c03.runtimeHash },
  approval: { artifactFreeze: false, deployment: false, cutover: false },
  operations: 'NO_RPC_NO_SIGN_NO_BROADCAST_NO_FILE_WRITES',
}, null, 2));
