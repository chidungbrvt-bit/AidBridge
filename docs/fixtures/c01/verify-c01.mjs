// C01 fixture review helper. No installs, network, blockchain calls, or file writes.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { resolve, dirname } from 'node:path';

const base = dirname(fileURLToPath(import.meta.url));
const load = name => JSON.parse(readFileSync(resolve(base, name), 'utf8'));
const abi = load('AidRegistry.abi.draft.json');
const sample = load('case-verified.reviewed.json');
const vector = load('golden-vector.input.json');
const tests = load('test-expectations.json');
const call = abi.find(x => x.name === 'recordCaseVerification');
const event = abi.find(x => x.name === 'CaseVerified');
assert.equal(call.stateMutability, 'nonpayable');
assert.equal(call.outputs.length, 0);
assert.equal(event.anonymous, false);
assert.deepEqual(event.inputs.filter(x => x.indexed).map(x => x.name), ['orgRef', 'caseRef', 'eventId']);
const signature = entry => `${entry.name}(${entry.inputs.map(x => x.type).join(',')})`;
assert.equal(signature(call), sample.publicEnvelope.functionSignature);
assert.equal(signature(event), sample.expectedLog.eventSignature);
assert.deepEqual(call.inputs.map(x => x.name), Object.keys(sample.publicEnvelope.arguments));
const bytes32 = x => assert.match(x, /^0x[0-9a-f]{64}$/);
for (const x of call.inputs.filter(x => x.type === 'bytes32')) bytes32(sample.publicEnvelope.arguments[x.name]);
const toWord = (type, x) => {
  if (type === 'bytes32') { bytes32(x); return x.slice(2); }
  assert.match(type, /^uint(16|64)$/);
  const n = BigInt(x), bits = BigInt(type.slice(4));
  assert(n >= 0n && n < (1n << bits));
  return n.toString(16).padStart(64, '0');
};
const args = sample.publicEnvelope.arguments;
assert.equal('0x' + call.inputs.map(x => toWord(x.type, args[x.name])).join(''), sample.encodingExpectation.encodedArguments);
assert.equal((sample.encodingExpectation.encodedArguments.length - 2) / 2, 192);
const nonIndexed = event.inputs.filter(x => !x.indexed);
assert.equal('0x' + nonIndexed.map(x => toWord(x.type, sample.expectedLog.decodedData[x.name])).join(''), sample.expectedLog.data);
assert.equal((sample.expectedLog.data.length - 2) / 2, 128);
assert.equal(sample.expectedLog.decodedData.timestamp, Date.parse('2026-11-09T01:00:00.000Z') / 1000);
assert.deepEqual(sample.expectedLog.indexedTopics, [args.orgRef, args.caseRef, args.eventId]);
assert.notEqual(sample.offChainOnly.idMapping.sqlAuditEventId, args.eventId);
assert.equal(sample.offChainOnly.idMapping.sqlEventKey, sample.offChainOnly.sourcePrivateFixture.decisionRecord.verificationId);
const canonicalize = x => {
  if (Array.isArray(x)) return '[' + x.map(canonicalize).join(',') + ']';
  if (x !== null && typeof x === 'object') return '{' + Object.keys(x).sort().map(k => JSON.stringify(k) + ':' + canonicalize(x[k])).join(',') + '}';
  if (typeof x === 'number') assert(Number.isSafeInteger(x) && x >= 0);
  if (typeof x === 'string') assert.equal(x, x.normalize('NFC'));
  assert.notEqual(x, null);
  return JSON.stringify(x);
};
const canonical = canonicalize(vector.manifest);
assert.equal(canonical, vector.canonicalManifestUtf8);
assert.equal('0x' + Buffer.from(canonical, 'utf8').toString('hex'), vector.canonicalManifestUtf8Hex);
assert.equal('0x' + createHash('sha256').update(canonical, 'utf8').digest('hex'), vector.manifestDigestSha256);
for (const [key, id] of [['goodsLines','itemId'], ['evidenceVersions','evidenceVersionId']]) {
  const ids = vector.manifest[key].map(x => x[id]);
  assert.equal(new Set(ids).size, ids.length);
  assert.deepEqual(ids, [...ids].sort());
}
for (const id of ['orgRef','caseRef','eventId']) assert.equal(vector.manifest[id], args[id]);
assert.equal(args.chainSequence, 1);
assert.equal(args.schemaVersion, 1);
assert.equal(tests.cases.length, 17);
assert.equal(new Set(tests.cases.map(x => x.id)).size, 17);
assert(tests.cases.every(x => x.executionStatus === 'NOT_RUN'));
assert.equal(sample.runtimeEvidence.transactionHash, null);
assert.equal(sample.runtimeEvidence.fujiReceipt, 'NOT_RUN');
assert(Object.values(vector.expected).includes('NOT_RUN'));
assert.equal(vector.expected.evidenceCommitment, null);
console.log('PASS C01 static fixture checks: ABI layout, ID mapping, canonical UTF-8/SHA-256, array IDs, test labels, no fabricated chain proof.');

if (process.argv.includes('--ethers')) {
  const requireFromC02 = createRequire(resolve(base, '../../..', 'contracts/package.json'));
  let ethers;
  try { ethers = requireFromC02('ethers'); }
  catch { console.error('NOT_RUN: existing C02 ethers dependency unavailable. No package was installed.'); process.exitCode = 2; }
  if (ethers) {
    const iface = new ethers.Interface(abi);
    const values = call.inputs.map(x => args[x.name]);
    const encodedCall = iface.encodeFunctionData(call.name, values);
    assert.equal(encodedCall.slice(10), sample.encodingExpectation.encodedArguments.slice(2));
    const encodedLog = iface.encodeEventLog(event.name, event.inputs.map(x => x.indexed ? args[x.name] : sample.expectedLog.decodedData[x.name]));
    assert.equal(encodedLog.data, sample.expectedLog.data);
    assert.deepEqual(encodedLog.topics.slice(1), sample.expectedLog.indexedTopics);
    const decodedLog = iface.decodeEventLog(event.name, encodedLog.data, encodedLog.topics);
    for (const f of event.inputs) {
      const expected = f.indexed ? args[f.name] : sample.expectedLog.decodedData[f.name];
      assert.equal(decodedLog[f.name], f.type.startsWith('uint') ? BigInt(expected) : expected);
    }
    const c = vector.commitmentInputs;
    const coder = ethers.AbiCoder.defaultAbiCoder();
    const domain = ethers.id(c.domainPreimageUtf8);
    const commitment = ethers.keccak256(coder.encode(vector.commitmentAbiTypes, [domain,c.chainId,c.registryAddress,c.caseRef,c.eventId,vector.manifestDigestSha256,c.salt]));
    const operation = iface.getFunction(call.name).selector;
    const payloadDigest = ethers.keccak256(coder.encode(vector.payloadDigestAbiTypes, [operation,args.orgRef,args.caseRef,args.chainSequence,commitment,args.schemaVersion]));
    console.log(JSON.stringify({status:'CANDIDATE_NOT_ACCEPTED_GOLDEN_VECTOR',operation,topic0:encodedLog.topics[0],domainV1:domain,evidenceCommitment:commitment,payloadDigest,javaAgreement:'NOT_RUN'},null,2));
    console.log('PASS ethers ABI encode/decode checks; not a contract execution or Java compatibility result.');
  }
} else console.log('NOT_RUN: Keccak, ethers decode, Java, contract tests and Fuji.');
