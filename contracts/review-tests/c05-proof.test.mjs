import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { Interface, keccak256 } from 'ethers';
import { verifyCaseVerified, collectProof, normalizeReceiptLogs, payloadDigest } from '../scripts/c05-proof.mjs';

const abi = JSON.parse(readFileSync(new URL('../deployment/AidRegistry.fuji.abi.json', import.meta.url)));
const iface = new Interface(abi);
const bytes = n => '0x' + n.repeat(64);
function fixture() {
  const e = { chainId: 43113, contractAddress: '0x1111111111111111111111111111111111111111',
    relayer: '0x2222222222222222222222222222222222222222', nonce: 0,
    orgRef: bytes('3'), caseRef: bytes('4'), eventId: bytes('5'), chainSequence: '1',
    evidenceCommitment: bytes('6'), schemaVersion: 1, transactionHash: bytes('7') };
  const args = [e.orgRef, e.caseRef, e.eventId, 1n, e.evidenceCommitment, 1n];
  const encoded = iface.encodeEventLog(iface.getEvent('CaseVerified'), [...args, 100n]);
  const context = { blockHash: bytes('8'), blockNumber: 10, transactionHash: e.transactionHash };
  return { abi, expected: e, chainId: 43113n,
    transaction: { hash: e.transactionHash, chainId: 43113n, to: e.contractAddress, from: e.relayer,
      value: 0n, nonce: 0, data: iface.encodeFunctionData('recordCaseVerification', args),
      blockHash: context.blockHash, blockNumber: 10 },
    receipt: { hash: e.transactionHash, status: 1, to: e.contractAddress, from: e.relayer,
      blockHash: context.blockHash, blockNumber: 10, gasUsed: 100n, gasPrice: 2n,
      logs: [{ ...context, address: e.contractAddress, removed: false, index: 0, ...encoded }] },
    block: { number: 10, hash: context.blockHash, timestamp: 100 } };
}

test('accepts exact receipt and decodes D49 fields, topic count and fee', () => {
  const proof = verifyCaseVerified(fixture());
  assert.equal(proof.status, 'PASS'); assert.equal(proof.decoded.chainSequence, '1');
  assert.equal(proof.topics.length, 4); assert.equal(proof.data.length, 258);
  assert.equal(proof.feeWei, '200');
});
const mutations = [
  ['RPC mainnet', x => { x.chainId = 43114n; }, 'WRONG_CHAIN'],
  ['expected mainnet', x => { x.expected.chainId = 43114; }, 'WRONG_CHAIN'],
  ['null receipt stays pending', x => { x.receipt = null; }, 'RECEIPT_PENDING'],
  ['reverted receipt', x => { x.receipt.status = 0; }, 'RECEIPT_REVERTED'],
  ['missing transaction', x => { x.transaction = null; }, 'TRANSACTION_CHAIN_MISMATCH'],
  ['transaction wrong chain', x => { x.transaction.chainId = 43114n; }, 'TRANSACTION_CHAIN_MISMATCH'],
  ['wrong receipt hash', x => { x.receipt.hash = bytes('9'); }, 'TRANSACTION_HASH_MISMATCH'],
  ['wrong transaction hash', x => { x.transaction.hash = bytes('9'); }, 'TRANSACTION_HASH_MISMATCH'],
  ['wrong destination', x => { x.transaction.to = x.expected.relayer; }, 'WRONG_CONTRACT'],
  ['wrong receipt destination', x => { x.receipt.to = x.expected.relayer; }, 'WRONG_CONTRACT'],
  ['admin replacing relayer', x => { x.transaction.from = x.expected.contractAddress; }, 'WRONG_SIGNER'],
  ['nonzero value', x => { x.transaction.value = 1n; }, 'TRANSACTION_CONTEXT_MISMATCH'],
  ['wrong nonce', x => { x.transaction.nonce = 1; }, 'TRANSACTION_CONTEXT_MISMATCH'],
  ['noncanonical block', x => { x.block.hash = bytes('9'); }, 'NON_CANONICAL_BLOCK'],
  ['missing canonical block', x => { x.block = null; }, 'NON_CANONICAL_BLOCK'],
  ['transaction on another block', x => { x.transaction.blockHash = bytes('9'); }, 'TRANSACTION_BLOCK_MISMATCH'],
  ['calldata different eventId', x => { x.transaction.data = iface.encodeFunctionData('recordCaseVerification',
    [x.expected.orgRef, x.expected.caseRef, bytes('9'), 1, x.expected.evidenceCommitment, 1]); }, 'CALLDATA_MISMATCH'],
  ['wrong function', x => { x.transaction.data = iface.encodeFunctionData('getCaseSummary', [x.expected.caseRef]); }, 'WRONG_FUNCTION'],
  ['missing expected event', x => { x.receipt.logs = []; }, 'EXPECTED_EVENT_COUNT'],
  ['event emitted by other contract', x => { x.receipt.logs[0].address = x.expected.relayer; }, 'EXPECTED_EVENT_COUNT'],
  ['wrong topic0', x => { x.receipt.logs[0].topics[0] = bytes('9'); }, 'EXPECTED_EVENT_COUNT'],
  ['duplicate expected event', x => { x.receipt.logs.push(structuredClone(x.receipt.logs[0])); }, 'EXPECTED_EVENT_COUNT'],
  ['removed log', x => { x.receipt.logs[0].removed = true; }, 'INVALID_LOG_CONTEXT'],
  ['log on different transaction', x => { x.receipt.logs[0].transactionHash = bytes('9'); }, 'INVALID_LOG_CONTEXT'],
  ['log on different block', x => { x.receipt.logs[0].blockHash = bytes('9'); }, 'INVALID_LOG_CONTEXT'],
  ['wrong topic count', x => { x.receipt.logs[0].topics.pop(); }, 'INVALID_EVENT_LAYOUT'],
  ['truncated data', x => { x.receipt.logs[0].data = '0x'; }, 'INVALID_EVENT_LAYOUT'],
];
for (const [label, mutate, error] of mutations) test(`rejects ${label}`, () => {
  const input = fixture(); mutate(input);
  assert.throws(() => verifyCaseVerified(input), { message: error });
});
for (const [label, index, value] of [
  ['orgRef', 0, bytes('9')], ['caseRef', 1, bytes('9')], ['eventId', 2, bytes('9')],
  ['sequence', 3, 2n], ['commitment', 4, bytes('9')], ['schema', 5, 2n], ['timestamp', 6, 101n],
]) test(`rejects wrong decoded ${label}`, () => {
  const input = fixture(), e = input.expected;
  const args = [e.orgRef, e.caseRef, e.eventId, 1n, e.evidenceCommitment, 1n, 100n]; args[index] = value;
  Object.assign(input.receipt.logs[0], iface.encodeEventLog(iface.getEvent('CaseVerified'), args));
  assert.throws(() => verifyCaseVerified(input), { message: index === 6 ? 'EVENT_TIMESTAMP_MISMATCH' : 'EVENT_FIELDS_MISMATCH' });
});
test('read-only collector rejects mainnet before reading code or receipt', async () => {
  await assert.rejects(() => collectProof({ send: async () => '0xa86a' }, abi, fixture().expected, bytes('1')),
    { message: 'WRONG_CHAIN' });
});
test('normalizes removed=false from raw RPC when ethers receipt omits the field', () => {
  const x = fixture(), l = x.receipt.logs[0]; delete l.removed;
  const raw = { logs: [{ ...l, logIndex: '0x0', blockNumber: '0xa', removed: false }] };
  x.receipt = normalizeReceiptLogs(x.receipt, raw);
  assert.equal(verifyCaseVerified(x).status, 'PASS');
});
test('rejects a raw log differing from the ethers receipt', () => {
  const x = fixture();
  const raw = { logs: [{ ...x.receipt.logs[0], logIndex: '0x0', blockNumber: '0xa', data: '0x' }] };
  assert.throws(() => normalizeReceiptLogs(x.receipt, raw), { message: 'RAW_LOG_MISMATCH' });
});
test('does not assume an absent RPC removed flag is false', () => {
  const x = fixture(), l = x.receipt.logs[0]; delete l.removed;
  x.receipt = normalizeReceiptLogs(x.receipt, { logs: [{ ...l, logIndex: '0x0', blockNumber: '0xa' }] });
  assert.throws(() => verifyCaseVerified(x), { message: 'INVALID_LOG_CONTEXT' });
});
function providerFixture(recordMutation = () => {}, summaryMutation = () => {}) {
  const x = fixture(), e = x.expected;
  const record = [true, iface.getFunction('recordCaseVerification').selector, e.orgRef, e.caseRef,
    1n, e.evidenceCommitment, 1n, payloadDigest(abi, e)];
  const summary = [true, 0n, 1n]; recordMutation(record); summaryMutation(summary);
  const raw = { logs: [{ ...x.receipt.logs[0], logIndex: '0x0', blockNumber: '0xa' }] };
  const p = {
    send: async method => method === 'eth_chainId' ? '0xa869' : raw,
    getCode: async () => '0x00', getTransactionReceipt: async () => x.receipt,
    getTransaction: async () => x.transaction, getBlock: async () => x.block,
    call: async tx => {
      assert.equal(tx.blockTag, 10);
      const input = iface.parseTransaction(tx);
      return iface.encodeFunctionResult(input.name, input.name === 'getEventRecord' ? record : summary);
    },
  };
  return { p, e };
}
test('collector verifies stored digest and case state at the receipt block', async () => {
  const { p, e } = providerFixture();
  const proof = await collectProof(p, abi, e, keccak256('0x00'));
  assert.equal(proof.readback.caseVerified, true);
});
for (const [label, mutateRecord, mutateSummary, error] of [
  ['absent record', r => { r[0] = false; }, null, 'EVENT_RECORD_MISMATCH'],
  ['wrong stored digest', r => { r[7] = bytes('9'); }, null, 'EVENT_RECORD_MISMATCH'],
  ['wrong stored org', r => { r[2] = bytes('9'); }, null, 'EVENT_RECORD_MISMATCH'],
  ['wrong stored sequence', r => { r[4] = 2n; }, null, 'EVENT_RECORD_MISMATCH'],
  ['unverified case', null, s => { s[0] = false; }, 'CASE_SUMMARY_MISMATCH'],
  ['wrong case sequence', null, s => { s[2] = 2n; }, 'CASE_SUMMARY_MISMATCH'],
]) test(`collector rejects ${label}`, async () => {
  const { p, e } = providerFixture(mutateRecord ?? undefined, mutateSummary ?? undefined);
  await assert.rejects(() => collectProof(p, abi, e, keccak256('0x00')), { message: error });
});
