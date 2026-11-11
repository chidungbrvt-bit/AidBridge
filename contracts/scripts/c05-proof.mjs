// C05 receipt verifier. No signer, SQL writes, or broadcast side effects.
import { AbiCoder, Contract, Interface, getAddress, keccak256 } from 'ethers';

export const CASE_VERIFIED_SIGNATURE = 'CaseVerified(bytes32,bytes32,bytes32,uint64,bytes32,uint16,uint64)';
const coder = AbiCoder.defaultAbiCoder();
const same = (a, b) => typeof a === 'string' && typeof b === 'string' && a.toLowerCase() === b.toLowerCase();
function requireProof(ok, reason) { if (!ok) throw Error(reason); }

export function payloadDigest(abi, expected) {
  const operation = new Interface(abi).getFunction('recordCaseVerification').selector;
  return keccak256(coder.encode(
    ['bytes4', 'bytes32', 'bytes32', 'uint64', 'bytes32', 'uint16'],
    [operation, expected.orgRef, expected.caseRef, expected.chainSequence, expected.evidenceCommitment, expected.schemaVersion],
  ));
}

// ethers v6 receipt Log omits `removed`; do not interpret undefined as false.
// Bind the raw JSON-RPC flag to exactly the same log before validation.
export function normalizeReceiptLogs(receipt, raw) {
  requireProof(raw != null && Array.isArray(raw.logs) && raw.logs.length === receipt.logs.length,
    'RAW_LOG_MISMATCH');
  return { ...receipt, logs: receipt.logs.map(log => {
    const match = raw.logs.find(item => Number(BigInt(item.logIndex)) === log.index);
    requireProof(match && same(match.address, log.address) && same(match.transactionHash, log.transactionHash)
      && same(match.blockHash, log.blockHash) && Number(BigInt(match.blockNumber)) === log.blockNumber
      && same(match.data, log.data) && match.topics.length === log.topics.length
      && match.topics.every((topic, index) => same(topic, log.topics[index])), 'RAW_LOG_MISMATCH');
    return { ...log, removed: match.removed };
  }) };
}

export function verifyCaseVerified({ abi, expected: e, chainId, transaction: tx, receipt: r, block }) {
  requireProof(BigInt(chainId) === 43113n && BigInt(e.chainId) === 43113n, 'WRONG_CHAIN');
  requireProof(e.schemaVersion === 1 && BigInt(e.chainSequence) === 1n, 'INVALID_EXPECTATION');
  for (const key of ['orgRef', 'caseRef', 'eventId', 'evidenceCommitment']) {
    requireProof(/^0x[0-9a-fA-F]{64}$/.test(e[key]) && !/^0x0{64}$/.test(e[key]), 'INVALID_EXPECTATION');
  }
  getAddress(e.contractAddress); getAddress(e.relayer);
  requireProof(r != null, 'RECEIPT_PENDING');
  requireProof(r.status === 1, 'RECEIPT_REVERTED');
  requireProof(tx != null && BigInt(tx.chainId) === 43113n, 'TRANSACTION_CHAIN_MISMATCH');
  requireProof(same(r.hash, e.transactionHash) && same(tx.hash, e.transactionHash), 'TRANSACTION_HASH_MISMATCH');
  requireProof(same(tx.to, e.contractAddress) && same(r.to, e.contractAddress), 'WRONG_CONTRACT');
  requireProof(same(tx.from, e.relayer) && same(r.from, e.relayer), 'WRONG_SIGNER');
  requireProof(BigInt(tx.value) === 0n && BigInt(tx.nonce) === BigInt(e.nonce), 'TRANSACTION_CONTEXT_MISMATCH');
  requireProof(block != null && same(block.hash, r.blockHash) && block.number === r.blockNumber,
    'NON_CANONICAL_BLOCK');
  requireProof(same(tx.blockHash, r.blockHash) && tx.blockNumber === r.blockNumber, 'TRANSACTION_BLOCK_MISMATCH');
  const iface = new Interface(abi);
  const fields = ['orgRef', 'caseRef', 'eventId', 'chainSequence', 'evidenceCommitment', 'schemaVersion'];
  const input = iface.parseTransaction({ data: tx.data, value: tx.value });
  requireProof(input?.name === 'recordCaseVerification', 'WRONG_FUNCTION');
  const matches = (args) => fields.every((key, i) => i < 3 || i === 4
    ? same(args[i], e[key]) : BigInt(args[i]) === BigInt(e[key]));
  requireProof(matches(input.args), 'CALLDATA_MISMATCH');
  const topic = iface.getEvent('CaseVerified').topicHash;
  const logs = r.logs.filter(log => same(log.address, e.contractAddress) && same(log.topics[0], topic));
  requireProof(logs.length === 1, 'EXPECTED_EVENT_COUNT');
  const log = logs[0];
  requireProof(log.removed === false && same(log.transactionHash, e.transactionHash)
    && same(log.blockHash, r.blockHash) && log.blockNumber === r.blockNumber
    && Number.isInteger(log.index) && log.index >= 0, 'INVALID_LOG_CONTEXT');
  requireProof(log.topics.length === 4 && /^0x[0-9a-fA-F]{256}$/.test(log.data), 'INVALID_EVENT_LAYOUT');
  const event = iface.parseLog(log);
  requireProof(event?.signature === CASE_VERIFIED_SIGNATURE && matches(event.args), 'EVENT_FIELDS_MISMATCH');
  requireProof(BigInt(event.args[6]) === BigInt(block.timestamp), 'EVENT_TIMESTAMP_MISMATCH');
  return {
    status: 'PASS', chainId: 43113, contractAddress: e.contractAddress,
    transactionHash: e.transactionHash, blockNumber: r.blockNumber, blockHash: r.blockHash,
    receiptStatus: r.status, gasUsed: r.gasUsed.toString(), gasPrice: r.gasPrice.toString(),
    feeWei: (r.gasUsed * r.gasPrice).toString(), signer: tx.from, nonce: tx.nonce,
    eventSignature: CASE_VERIFIED_SIGNATURE, topic0: topic, logIndex: log.index,
    topics: Array.from(log.topics), data: log.data,
    decoded: Object.fromEntries([...fields, 'timestamp'].map((key, i) => [key, event.args[i].toString()])),
    expected: e, payloadDigest: payloadDigest(abi, e),
    checks: ['chainId', 'transactionHash', 'destination', 'relayer', 'nonce', 'zeroValue',
      'calldata', 'receiptStatus', 'canonicalBlock', 'eventSignature', 'indexedFields',
      'eventData', 'sequence', 'commitment', 'schemaVersion', 'blockTimestamp', 'notRemoved'],
  };
}

export async function collectProof(provider, abi, expected, runtimeHash) {
  const chainId = BigInt(await provider.send('eth_chainId', []));
  requireProof(chainId === 43113n, 'WRONG_CHAIN');
  requireProof(keccak256(await provider.getCode(expected.contractAddress)) === runtimeHash, 'RUNTIME_MISMATCH');
  const receipt = await provider.getTransactionReceipt(expected.transactionHash);
  requireProof(receipt != null, 'RECEIPT_PENDING');
  const transaction = await provider.getTransaction(expected.transactionHash);
  const block = await provider.getBlock(receipt.blockNumber);
  const raw = await provider.send('eth_getTransactionReceipt', [expected.transactionHash]);
  const normalized = normalizeReceiptLogs(receipt, raw);
  const proof = verifyCaseVerified({ abi, expected, chainId, transaction, receipt: normalized, block });
  const registry = new Contract(expected.contractAddress, abi, provider);
  const record = await registry.getEventRecord(expected.eventId, { blockTag: receipt.blockNumber });
  const summary = await registry.getCaseSummary(expected.caseRef, { blockTag: receipt.blockNumber });
  requireProof(record[0] === true && same(record[1], registry.interface.getFunction('recordCaseVerification').selector)
    && same(record[2], expected.orgRef) && same(record[3], expected.caseRef)
    && record[4] === BigInt(expected.chainSequence) && same(record[5], expected.evidenceCommitment)
    && record[6] === 1n && same(record[7], proof.payloadDigest), 'EVENT_RECORD_MISMATCH');
  requireProof(summary[0] === true && summary[1] === 0n && summary[2] === 1n, 'CASE_SUMMARY_MISMATCH');
  proof.readback = { blockNumber: receipt.blockNumber, eventExists: true, operation: record[1],
    payloadDigest: record[7], caseVerified: true, openDisputeCount: '0', lastChainSequence: '1' };
  return proof;
}
