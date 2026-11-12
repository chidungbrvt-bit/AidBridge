import { expect } from "chai";
import { network } from "hardhat";
import {
  AbiCoder,
  keccak256,
  toUtf8Bytes,
  type ContractTransactionReceipt,
  type ContractTransactionResponse,
  ZeroHash,
} from "ethers";

// ---------------------------------------------------------------------------
// C06 milestone tests (part 1 of C06): recordAidCommitment, recordCommitmentReduction,
// recordGoodsReceipt, recordDelivery. confirm/dispute/resolve are out of scope (12/11).
// Test names start with the TEST_STRATEGY.md IDs they cover.
// ---------------------------------------------------------------------------

/** Deterministic, readable bytes32 value for opaque refs and event IDs (test-only labels). */
const ref = (label: string) => keccak256(toUtf8Bytes(label));

const ORG_A = ref("org-a");
const ORG_B = ref("org-b");
const CASE_A1 = ref("case-a1");
const CASE_A2 = ref("case-a2");
const CASE_B = ref("case-b");
const CASE_UNVERIFIED = ref("case-unverified");
const EVIDENCE = ref("evidence-commitment");
const SCHEMA_VERSION = 1;

const COMMITMENT_REF = ref("commitment-1");
const RECEIPT_REF = ref("receipt-1");
const DELIVERY_REF = ref("delivery-1");

const abiCoder = AbiCoder.defaultAbiCoder();

async function deployMilestoneFixture() {
  const { ethers } = await network.getOrCreate();
  const [admin, relayerA, relayerB, stranger] = await ethers.getSigners();
  const registry = await ethers.deployContract("AidRegistry", [admin.address], admin);
  await registry.waitForDeployment();

  await registry.connect(admin).registerOrganization(ORG_A, relayerA.address);
  await registry.connect(admin).registerOrganization(ORG_B, relayerB.address);

  // Every verified case starts at chainSequence 1, so its first milestone uses sequence 2.
  await registry
    .connect(relayerA)
    .recordCaseVerification(ORG_A, CASE_A1, ref("verify-a1"), 1n, EVIDENCE, SCHEMA_VERSION);
  await registry
    .connect(relayerA)
    .recordCaseVerification(ORG_A, CASE_A2, ref("verify-a2"), 1n, EVIDENCE, SCHEMA_VERSION);
  await registry
    .connect(relayerB)
    .recordCaseVerification(ORG_B, CASE_B, ref("verify-b"), 1n, EVIDENCE, SCHEMA_VERSION);

  return { ethers, registry, admin, relayerA, relayerB, stranger };
}

type Fixture = Awaited<ReturnType<typeof deployMilestoneFixture>>;

function selectorOf(fx: Fixture, functionName: string): string {
  return fx.registry.interface.getFunction(functionName)!.selector;
}

/** Same formula as the contract: keccak256(abi.encode(selector, envelope..., operation refs...)). */
function expectedDigest(
  selector: string,
  orgRef: string,
  caseRef: string,
  chainSequence: bigint,
  operationRefs: string[]
): string {
  const types = ["bytes4", "bytes32", "bytes32", "uint64", "bytes32", "uint16", ...operationRefs.map(() => "bytes32")];
  return keccak256(
    abiCoder.encode(types, [selector, orgRef, caseRef, chainSequence, EVIDENCE, SCHEMA_VERSION, ...operationRefs])
  );
}

async function blockTimestampOf(fx: Fixture, receipt: ContractTransactionReceipt | null): Promise<bigint> {
  const block = await fx.ethers.provider.getBlock(receipt!.blockNumber);
  return BigInt(block!.timestamp);
}

/** Exactly one log from the registry: topic0 + orgRef/caseRef/eventId topics, and `dataWords` 32-byte data words. */
function expectSingleMilestoneLog(
  fx: Fixture,
  receipt: ContractTransactionReceipt | null,
  eventName: string,
  orgRef: string,
  caseRef: string,
  eventId: string,
  dataWords: number
) {
  expect(receipt!.logs.length).to.equal(1);
  const [log] = receipt!.logs;
  const topic0 = fx.registry.interface.getEvent(eventName)!.topicHash;
  expect(log.topics).to.deep.equal([topic0, orgRef, caseRef, eventId]);
  expect((log.data.length - 2) / 2).to.equal(dataWords * 32);
}

async function expectStoredEvent(
  fx: Fixture,
  eventId: string,
  functionName: string,
  orgRef: string,
  caseRef: string,
  chainSequence: bigint,
  operationRefs: string[]
) {
  const selector = selectorOf(fx, functionName);
  expect([...(await fx.registry.getEventRecord(eventId))]).to.deep.equal([
    true,
    selector,
    orgRef,
    caseRef,
    chainSequence,
    EVIDENCE,
    BigInt(SCHEMA_VERSION),
    expectedDigest(selector, orgRef, caseRef, chainSequence, operationRefs),
  ]);
}

async function expectLastSequence(fx: Fixture, caseRef: string, expected: bigint) {
  const [verified, openDisputeCount, lastChainSequence] = await fx.registry.getCaseSummary(caseRef);
  expect(verified).to.equal(true);
  expect(openDisputeCount).to.equal(0n);
  expect(lastChainSequence).to.equal(expected);
}

describe("C06 AidRegistry milestones - happy paths (BR12-P, SC05, SC06 partial, SC10)", function () {
  it("BR12-P/SC10: recordAidCommitment stores the commitment, emits AidCommitted and advances sequence 1 -> 2", async function () {
    const fx = await deployMilestoneFixture();
    const eventId = ref("evt-commit-1");

    const tx = await fx.registry
      .connect(fx.relayerA)
      .recordAidCommitment(ORG_A, CASE_A1, eventId, 2n, EVIDENCE, SCHEMA_VERSION, COMMITMENT_REF);
    const receipt = await tx.wait();
    const timestamp = await blockTimestampOf(fx, receipt);

    await expect(tx)
      .to.emit(fx.registry, "AidCommitted")
      .withArgs(ORG_A, CASE_A1, eventId, 2n, EVIDENCE, SCHEMA_VERSION, timestamp, COMMITMENT_REF);
    expectSingleMilestoneLog(fx, receipt, "AidCommitted", ORG_A, CASE_A1, eventId, 5);
    await expectStoredEvent(fx, eventId, "recordAidCommitment", ORG_A, CASE_A1, 2n, [COMMITMENT_REF]);
    await expectLastSequence(fx, CASE_A1, 2n);
  });

  it("SC05: recordCommitmentReduction can reduce the same commitment twice (sequence 3 and 4) without touching it", async function () {
    const fx = await deployMilestoneFixture();
    await fx.registry
      .connect(fx.relayerA)
      .recordAidCommitment(ORG_A, CASE_A1, ref("evt-commit-1"), 2n, EVIDENCE, SCHEMA_VERSION, COMMITMENT_REF);

    const reductions: Array<[string, bigint]> = [
      [ref("evt-reduce-1"), 3n],
      [ref("evt-reduce-2"), 4n],
    ];
    for (const [eventId, sequence] of reductions) {
      const tx = await fx.registry
        .connect(fx.relayerA)
        .recordCommitmentReduction(ORG_A, CASE_A1, eventId, sequence, EVIDENCE, SCHEMA_VERSION, COMMITMENT_REF);
      const receipt = await tx.wait();
      const timestamp = await blockTimestampOf(fx, receipt);

      await expect(tx)
        .to.emit(fx.registry, "CommitmentReduced")
        .withArgs(ORG_A, CASE_A1, eventId, sequence, EVIDENCE, SCHEMA_VERSION, timestamp, COMMITMENT_REF);
      expectSingleMilestoneLog(fx, receipt, "CommitmentReduced", ORG_A, CASE_A1, eventId, 5);
      await expectStoredEvent(fx, eventId, "recordCommitmentReduction", ORG_A, CASE_A1, sequence, [COMMITMENT_REF]);
    }

    const reducedLogs = await fx.registry.queryFilter(fx.registry.filters.CommitmentReduced());
    expect(reducedLogs.length).to.equal(2);
    await expectLastSequence(fx, CASE_A1, 4n);
  });

  it("SC05/SC10: recordGoodsReceipt links receiptRef to an existing same-case commitment and emits 192 data bytes", async function () {
    const fx = await deployMilestoneFixture();
    await fx.registry
      .connect(fx.relayerA)
      .recordAidCommitment(ORG_A, CASE_A1, ref("evt-commit-1"), 2n, EVIDENCE, SCHEMA_VERSION, COMMITMENT_REF);
    const eventId = ref("evt-receipt-1");

    const tx = await fx.registry
      .connect(fx.relayerA)
      .recordGoodsReceipt(ORG_A, CASE_A1, eventId, 3n, EVIDENCE, SCHEMA_VERSION, RECEIPT_REF, COMMITMENT_REF);
    const receipt = await tx.wait();
    const timestamp = await blockTimestampOf(fx, receipt);

    await expect(tx)
      .to.emit(fx.registry, "GoodsReceived")
      .withArgs(ORG_A, CASE_A1, eventId, 3n, EVIDENCE, SCHEMA_VERSION, timestamp, RECEIPT_REF, COMMITMENT_REF);
    expectSingleMilestoneLog(fx, receipt, "GoodsReceived", ORG_A, CASE_A1, eventId, 6);
    await expectStoredEvent(fx, eventId, "recordGoodsReceipt", ORG_A, CASE_A1, 3n, [RECEIPT_REF, COMMITMENT_REF]);
    await expectLastSequence(fx, CASE_A1, 3n);
  });

  it("SC06 (record part)/SC10: recordDelivery needs no prior commitment and advances sequence 1 -> 2", async function () {
    const fx = await deployMilestoneFixture();
    const eventId = ref("evt-delivery-1");

    const tx = await fx.registry
      .connect(fx.relayerA)
      .recordDelivery(ORG_A, CASE_A1, eventId, 2n, EVIDENCE, SCHEMA_VERSION, DELIVERY_REF);
    const receipt = await tx.wait();
    const timestamp = await blockTimestampOf(fx, receipt);

    await expect(tx)
      .to.emit(fx.registry, "DeliveryRecorded")
      .withArgs(ORG_A, CASE_A1, eventId, 2n, EVIDENCE, SCHEMA_VERSION, timestamp, DELIVERY_REF);
    expectSingleMilestoneLog(fx, receipt, "DeliveryRecorded", ORG_A, CASE_A1, eventId, 5);
    await expectStoredEvent(fx, eventId, "recordDelivery", ORG_A, CASE_A1, 2n, [DELIVERY_REF]);
    await expectLastSequence(fx, CASE_A1, 2n);
  });

  it("BR12-P/SC09: full lifecycle verify -> commit -> reduce -> receive -> deliver uses sequences 1..5 and stores each operation", async function () {
    const fx = await deployMilestoneFixture();
    const steps: Array<{ eventId: string; functionName: string; sequence: bigint; refs: string[] }> = [
      { eventId: ref("life-commit"), functionName: "recordAidCommitment", sequence: 2n, refs: [COMMITMENT_REF] },
      { eventId: ref("life-reduce"), functionName: "recordCommitmentReduction", sequence: 3n, refs: [COMMITMENT_REF] },
      { eventId: ref("life-receipt"), functionName: "recordGoodsReceipt", sequence: 4n, refs: [RECEIPT_REF, COMMITMENT_REF] },
      { eventId: ref("life-delivery"), functionName: "recordDelivery", sequence: 5n, refs: [DELIVERY_REF] },
    ];

    const relayer = fx.registry.connect(fx.relayerA);
    await relayer.recordAidCommitment(ORG_A, CASE_A1, steps[0].eventId, 2n, EVIDENCE, SCHEMA_VERSION, COMMITMENT_REF);
    await relayer.recordCommitmentReduction(ORG_A, CASE_A1, steps[1].eventId, 3n, EVIDENCE, SCHEMA_VERSION, COMMITMENT_REF);
    await relayer.recordGoodsReceipt(ORG_A, CASE_A1, steps[2].eventId, 4n, EVIDENCE, SCHEMA_VERSION, RECEIPT_REF, COMMITMENT_REF);
    await relayer.recordDelivery(ORG_A, CASE_A1, steps[3].eventId, 5n, EVIDENCE, SCHEMA_VERSION, DELIVERY_REF);

    for (const step of steps) {
      await expectStoredEvent(fx, step.eventId, step.functionName, ORG_A, CASE_A1, step.sequence, step.refs);
    }
    await expectLastSequence(fx, CASE_A1, 5n);
    // Other cases are untouched: milestones on CASE_A1 never advance CASE_A2.
    await expectLastSequence(fx, CASE_A2, 1n);
  });
});

// ---------------------------------------------------------------------------
// Shared table for running the same negative scenario against all four milestone functions.
// ---------------------------------------------------------------------------

type MilestoneSpec = {
  name: "recordAidCommitment" | "recordCommitmentReduction" | "recordGoodsReceipt" | "recordDelivery";
  needsCommitment: boolean; // reduction/receipt reference an existing commitment
  refs: string[]; // operation-specific refs, in ABI order
};

const MILESTONES: MilestoneSpec[] = [
  { name: "recordAidCommitment", needsCommitment: false, refs: [ref("commitment-2")] },
  { name: "recordCommitmentReduction", needsCommitment: true, refs: [COMMITMENT_REF] },
  { name: "recordGoodsReceipt", needsCommitment: true, refs: [RECEIPT_REF, COMMITMENT_REF] },
  { name: "recordDelivery", needsCommitment: false, refs: [DELIVERY_REF] },
];

/** Prepares CASE_A1 for `spec` and returns the next valid chainSequence. */
async function prepareCase(fx: Fixture, spec: MilestoneSpec): Promise<bigint> {
  if (!spec.needsCommitment) {
    return 2n;
  }
  await fx.registry
    .connect(fx.relayerA)
    .recordAidCommitment(ORG_A, CASE_A1, ref("evt-prepare-commitment"), 2n, EVIDENCE, SCHEMA_VERSION, COMMITMENT_REF);
  return 3n;
}

type Signer = Fixture["relayerA"];

/** Calls `spec.name` with the common envelope followed by the operation refs (does not await). */
function sendMilestone(
  fx: Fixture,
  signer: Signer,
  spec: MilestoneSpec,
  orgRef: string,
  caseRef: string,
  eventId: string,
  chainSequence: bigint,
  refs: string[] = spec.refs
): Promise<ContractTransactionResponse> {
  const contract = fx.registry.connect(signer) as unknown as Record<
    string,
    (...args: unknown[]) => Promise<ContractTransactionResponse>
  >;
  return contract[spec.name](orgRef, caseRef, eventId, chainSequence, EVIDENCE, SCHEMA_VERSION, ...refs);
}

/** Captures everything a reverted call must not change: the event record, the case summary and the log count. */
async function snapshotState(fx: Fixture, caseRef: string, eventId: string) {
  const logs = await fx.ethers.provider.getLogs({
    address: await fx.registry.getAddress(),
    fromBlock: 0,
    toBlock: "latest",
  });
  return {
    event: [...(await fx.registry.getEventRecord(eventId))],
    caseSummary: [...(await fx.registry.getCaseSummary(caseRef))],
    registryLogCount: logs.length,
  };
}

async function expectUnchanged(
  fx: Fixture,
  caseRef: string,
  eventId: string,
  before: Awaited<ReturnType<typeof snapshotState>>
) {
  expect(await snapshotState(fx, caseRef, eventId)).to.deep.equal(before);
}

describe("C06 AidRegistry milestones - authorization and case guards (BR12-N, SC02, SC09)", function () {
  for (const spec of MILESTONES) {
    it(`BR12-N/SC02: unknown caller cannot call ${spec.name}`, async function () {
      const fx = await deployMilestoneFixture();
      const sequence = await prepareCase(fx, spec);
      const eventId = ref(`evt-${spec.name}-stranger`);
      const before = await snapshotState(fx, CASE_A1, eventId);

      await expect(sendMilestone(fx, fx.stranger, spec, ORG_A, CASE_A1, eventId, sequence))
        .to.be.revertedWithCustomError(fx.registry, "UnauthorizedRelayer")
        .withArgs(ORG_A, fx.stranger.address);
      await expectUnchanged(fx, CASE_A1, eventId, before);
    });

    it(`BR12-N/SC02: admin without relayer authorization cannot call ${spec.name}`, async function () {
      const fx = await deployMilestoneFixture();
      const sequence = await prepareCase(fx, spec);
      const eventId = ref(`evt-${spec.name}-admin`);
      const before = await snapshotState(fx, CASE_A1, eventId);

      await expect(sendMilestone(fx, fx.admin, spec, ORG_A, CASE_A1, eventId, sequence))
        .to.be.revertedWithCustomError(fx.registry, "UnauthorizedRelayer")
        .withArgs(ORG_A, fx.admin.address);
      await expectUnchanged(fx, CASE_A1, eventId, before);
    });

    it(`BR12-N/SC02: org B relayer cannot call ${spec.name} for org A`, async function () {
      const fx = await deployMilestoneFixture();
      const sequence = await prepareCase(fx, spec);
      const eventId = ref(`evt-${spec.name}-wrong-org`);
      const before = await snapshotState(fx, CASE_A1, eventId);

      await expect(sendMilestone(fx, fx.relayerB, spec, ORG_A, CASE_A1, eventId, sequence))
        .to.be.revertedWithCustomError(fx.registry, "UnauthorizedRelayer")
        .withArgs(ORG_A, fx.relayerB.address);
      await expectUnchanged(fx, CASE_A1, eventId, before);
    });

    it(`BR12-N/SC01: ${spec.name} is rejected while the organization is deactivated`, async function () {
      const fx = await deployMilestoneFixture();
      const sequence = await prepareCase(fx, spec);
      await fx.registry.connect(fx.admin).setOrganizationStatus(ORG_A, false);
      const eventId = ref(`evt-${spec.name}-inactive`);
      const before = await snapshotState(fx, CASE_A1, eventId);

      await expect(sendMilestone(fx, fx.relayerA, spec, ORG_A, CASE_A1, eventId, sequence))
        .to.be.revertedWithCustomError(fx.registry, "OrganizationNotActive")
        .withArgs(ORG_A);
      await expectUnchanged(fx, CASE_A1, eventId, before);
    });

    it(`BR12-N/SC02: revoked relayer cannot call ${spec.name}`, async function () {
      const fx = await deployMilestoneFixture();
      const sequence = await prepareCase(fx, spec);
      await fx.registry.connect(fx.admin).revokeOrganizationRelayer(ORG_A);
      const eventId = ref(`evt-${spec.name}-revoked`);
      const before = await snapshotState(fx, CASE_A1, eventId);

      await expect(sendMilestone(fx, fx.relayerA, spec, ORG_A, CASE_A1, eventId, sequence))
        .to.be.revertedWithCustomError(fx.registry, "UnauthorizedRelayer")
        .withArgs(ORG_A, fx.relayerA.address);
      await expectUnchanged(fx, CASE_A1, eventId, before);
    });

    it(`BR12-N/SC04: unknown caller replaying a stored ${spec.name} eventId gets UnauthorizedRelayer, not a duplicate error`, async function () {
      const fx = await deployMilestoneFixture();
      const sequence = await prepareCase(fx, spec);
      const eventId = ref(`evt-${spec.name}-stored`);
      await sendMilestone(fx, fx.relayerA, spec, ORG_A, CASE_A1, eventId, sequence);
      const before = await snapshotState(fx, CASE_A1, eventId);

      await expect(sendMilestone(fx, fx.stranger, spec, ORG_A, CASE_A1, eventId, sequence))
        .to.be.revertedWithCustomError(fx.registry, "UnauthorizedRelayer")
        .withArgs(ORG_A, fx.stranger.address);
      await expectUnchanged(fx, CASE_A1, eventId, before);
    });

    it(`SC09: ${spec.name} on an unverified case reverts with CaseNotVerified`, async function () {
      const fx = await deployMilestoneFixture();
      await prepareCase(fx, spec);
      const eventId = ref(`evt-${spec.name}-unverified`);
      const before = await snapshotState(fx, CASE_UNVERIFIED, eventId);

      await expect(sendMilestone(fx, fx.relayerA, spec, ORG_A, CASE_UNVERIFIED, eventId, 2n))
        .to.be.revertedWithCustomError(fx.registry, "CaseNotVerified")
        .withArgs(CASE_UNVERIFIED);
      await expectUnchanged(fx, CASE_UNVERIFIED, eventId, before);
    });

    it(`BR12-N/SC03: org B relayer using its own orgRef cannot call ${spec.name} on a case owned by org A`, async function () {
      const fx = await deployMilestoneFixture();
      const sequence = await prepareCase(fx, spec);
      const eventId = ref(`evt-${spec.name}-foreign-case`);
      const before = await snapshotState(fx, CASE_A1, eventId);

      await expect(sendMilestone(fx, fx.relayerB, spec, ORG_B, CASE_A1, eventId, sequence))
        .to.be.revertedWithCustomError(fx.registry, "CaseOrganizationMismatch")
        .withArgs(CASE_A1, ORG_A, ORG_B);
      await expectUnchanged(fx, CASE_A1, eventId, before);
    });
  }
});

/** Same operation, but every operation-specific ref replaced by a different value. */
function mutatedRefs(spec: MilestoneSpec): string[] {
  return spec.refs.map((value, index) => (index === 0 ? ref(`mutated-${spec.name}`) : value));
}

describe("C06 AidRegistry milestones - duplicates and sequence (BR11-N, SC04, SC09, C01-T17)", function () {
  MILESTONES.forEach((spec, index) => {
    it(`BR11-N/SC04: exact retry of a successful ${spec.name} reverts EventAlreadyRecorded (not InvalidChainSequence) and emits nothing`, async function () {
      const fx = await deployMilestoneFixture();
      const sequence = await prepareCase(fx, spec);
      const eventId = ref(`evt-${spec.name}-retry`);
      await sendMilestone(fx, fx.relayerA, spec, ORG_A, CASE_A1, eventId, sequence);
      const before = await snapshotState(fx, CASE_A1, eventId);

      await expect(sendMilestone(fx, fx.relayerA, spec, ORG_A, CASE_A1, eventId, sequence))
        .to.be.revertedWithCustomError(fx.registry, "EventAlreadyRecorded")
        .withArgs(eventId);
      await expectUnchanged(fx, CASE_A1, eventId, before);
    });

    it(`BR11-N/SC04: reusing a ${spec.name} eventId with a different ref reverts EventIdConflict and keeps the original record`, async function () {
      const fx = await deployMilestoneFixture();
      const sequence = await prepareCase(fx, spec);
      const eventId = ref(`evt-${spec.name}-conflict`);
      await sendMilestone(fx, fx.relayerA, spec, ORG_A, CASE_A1, eventId, sequence);
      const before = await snapshotState(fx, CASE_A1, eventId);

      await expect(
        sendMilestone(fx, fx.relayerA, spec, ORG_A, CASE_A1, eventId, sequence, mutatedRefs(spec))
      )
        .to.be.revertedWithCustomError(fx.registry, "EventIdConflict")
        .withArgs(eventId);
      await expectUnchanged(fx, CASE_A1, eventId, before);
    });

    const otherSpec = MILESTONES[(index + 1) % MILESTONES.length];
    it(`C01-T17/SC04: reusing a ${spec.name} eventId and envelope through ${otherSpec.name} reverts EventIdConflict`, async function () {
      const fx = await deployMilestoneFixture();
      const sequence = await prepareCase(fx, spec);
      const eventId = ref(`evt-${spec.name}-cross-operation`);
      await sendMilestone(fx, fx.relayerA, spec, ORG_A, CASE_A1, eventId, sequence);
      const before = await snapshotState(fx, CASE_A1, eventId);

      // Same eventId, same six envelope fields; only the operation (selector) and its refs differ.
      await expect(sendMilestone(fx, fx.relayerA, otherSpec, ORG_A, CASE_A1, eventId, sequence))
        .to.be.revertedWithCustomError(fx.registry, "EventIdConflict")
        .withArgs(eventId);
      await expectUnchanged(fx, CASE_A1, eventId, before);
    });

    it(`BR11-N/SC04: the CaseVerified eventId cannot be reused for ${spec.name}`, async function () {
      const fx = await deployMilestoneFixture();
      const sequence = await prepareCase(fx, spec);
      const verificationEventId = ref("verify-a1"); // recorded by recordCaseVerification in the fixture
      const before = await snapshotState(fx, CASE_A1, verificationEventId);

      await expect(sendMilestone(fx, fx.relayerA, spec, ORG_A, CASE_A1, verificationEventId, sequence))
        .to.be.revertedWithCustomError(fx.registry, "EventIdConflict")
        .withArgs(verificationEventId);
      await expectUnchanged(fx, CASE_A1, verificationEventId, before);
    });

    it(`SC09: ${spec.name} that skips a sequence number reverts InvalidChainSequence`, async function () {
      const fx = await deployMilestoneFixture();
      const expected = await prepareCase(fx, spec);
      const eventId = ref(`evt-${spec.name}-skip`);
      const before = await snapshotState(fx, CASE_A1, eventId);

      await expect(sendMilestone(fx, fx.relayerA, spec, ORG_A, CASE_A1, eventId, expected + 1n))
        .to.be.revertedWithCustomError(fx.registry, "InvalidChainSequence")
        .withArgs(CASE_A1, expected, expected + 1n);
      await expectUnchanged(fx, CASE_A1, eventId, before);
    });

    it(`SC09: ${spec.name} that repeats the last sequence number with a fresh eventId reverts InvalidChainSequence`, async function () {
      const fx = await deployMilestoneFixture();
      const expected = await prepareCase(fx, spec);
      const eventId = ref(`evt-${spec.name}-repeat`);
      const before = await snapshotState(fx, CASE_A1, eventId);

      await expect(sendMilestone(fx, fx.relayerA, spec, ORG_A, CASE_A1, eventId, expected - 1n))
        .to.be.revertedWithCustomError(fx.registry, "InvalidChainSequence")
        .withArgs(CASE_A1, expected, expected - 1n);
      await expectUnchanged(fx, CASE_A1, eventId, before);
    });
  });
});

/** Calls a milestone function with fully explicit arguments (used to vary schemaVersion or zero refs). */
function sendMilestoneRaw(fx: Fixture, signer: Signer, functionName: string, args: unknown[]) {
  const contract = fx.registry.connect(signer) as unknown as Record<
    string,
    (...callArgs: unknown[]) => Promise<ContractTransactionResponse>
  >;
  return contract[functionName](...args);
}

async function recordBaseCommitment(fx: Fixture) {
  await fx.registry
    .connect(fx.relayerA)
    .recordAidCommitment(ORG_A, CASE_A1, ref("evt-base-commitment"), 2n, EVIDENCE, SCHEMA_VERSION, COMMITMENT_REF);
}

describe("C06 AidRegistry milestones - operation refs (SC05)", function () {
  it("SC05: a commitmentRef cannot be recorded twice, even with a fresh eventId and next sequence", async function () {
    const fx = await deployMilestoneFixture();
    await recordBaseCommitment(fx);
    const eventId = ref("evt-commit-dup");
    const before = await snapshotState(fx, CASE_A1, eventId);

    await expect(
      fx.registry
        .connect(fx.relayerA)
        .recordAidCommitment(ORG_A, CASE_A1, eventId, 3n, EVIDENCE, SCHEMA_VERSION, COMMITMENT_REF)
    )
      .to.be.revertedWithCustomError(fx.registry, "RefAlreadyUsed")
      .withArgs(COMMITMENT_REF);
    await expectUnchanged(fx, CASE_A1, eventId, before);
  });

  it("SC05: a commitmentRef used on one case cannot be reused on another case of the same organization", async function () {
    const fx = await deployMilestoneFixture();
    await recordBaseCommitment(fx);
    const eventId = ref("evt-commit-other-case");
    const before = await snapshotState(fx, CASE_A2, eventId);

    await expect(
      fx.registry
        .connect(fx.relayerA)
        .recordAidCommitment(ORG_A, CASE_A2, eventId, 2n, EVIDENCE, SCHEMA_VERSION, COMMITMENT_REF)
    )
      .to.be.revertedWithCustomError(fx.registry, "RefAlreadyUsed")
      .withArgs(COMMITMENT_REF);
    await expectUnchanged(fx, CASE_A2, eventId, before);
  });

  it("SC05: a receiptRef cannot be recorded twice", async function () {
    const fx = await deployMilestoneFixture();
    await recordBaseCommitment(fx);
    await fx.registry
      .connect(fx.relayerA)
      .recordGoodsReceipt(ORG_A, CASE_A1, ref("evt-receipt-1"), 3n, EVIDENCE, SCHEMA_VERSION, RECEIPT_REF, COMMITMENT_REF);
    const eventId = ref("evt-receipt-dup");
    const before = await snapshotState(fx, CASE_A1, eventId);

    await expect(
      fx.registry
        .connect(fx.relayerA)
        .recordGoodsReceipt(ORG_A, CASE_A1, eventId, 4n, EVIDENCE, SCHEMA_VERSION, RECEIPT_REF, COMMITMENT_REF)
    )
      .to.be.revertedWithCustomError(fx.registry, "RefAlreadyUsed")
      .withArgs(RECEIPT_REF);
    await expectUnchanged(fx, CASE_A1, eventId, before);
  });

  it("SC05/SC06: a deliveryRef cannot be recorded twice", async function () {
    const fx = await deployMilestoneFixture();
    await fx.registry
      .connect(fx.relayerA)
      .recordDelivery(ORG_A, CASE_A1, ref("evt-delivery-1"), 2n, EVIDENCE, SCHEMA_VERSION, DELIVERY_REF);
    const eventId = ref("evt-delivery-dup");
    const before = await snapshotState(fx, CASE_A1, eventId);

    await expect(
      fx.registry
        .connect(fx.relayerA)
        .recordDelivery(ORG_A, CASE_A1, eventId, 3n, EVIDENCE, SCHEMA_VERSION, DELIVERY_REF)
    )
      .to.be.revertedWithCustomError(fx.registry, "RefAlreadyUsed")
      .withArgs(DELIVERY_REF);
    await expectUnchanged(fx, CASE_A1, eventId, before);
  });

  it("SC05: receiptRef uniqueness is checked before the commitment link (RefAlreadyUsed wins over RefNotFound)", async function () {
    const fx = await deployMilestoneFixture();
    await recordBaseCommitment(fx);
    await fx.registry
      .connect(fx.relayerA)
      .recordGoodsReceipt(ORG_A, CASE_A1, ref("evt-receipt-1"), 3n, EVIDENCE, SCHEMA_VERSION, RECEIPT_REF, COMMITMENT_REF);
    const eventId = ref("evt-receipt-dup-bad-commitment");
    const before = await snapshotState(fx, CASE_A1, eventId);

    await expect(
      fx.registry
        .connect(fx.relayerA)
        .recordGoodsReceipt(ORG_A, CASE_A1, eventId, 4n, EVIDENCE, SCHEMA_VERSION, RECEIPT_REF, ref("missing-commitment"))
    )
      .to.be.revertedWithCustomError(fx.registry, "RefAlreadyUsed")
      .withArgs(RECEIPT_REF);
    await expectUnchanged(fx, CASE_A1, eventId, before);
  });

  for (const spec of MILESTONES.filter((candidate) => candidate.needsCommitment)) {
    it(`SC05/SC09: ${spec.name} referencing a commitment that does not exist reverts RefNotFound`, async function () {
      const fx = await deployMilestoneFixture();
      const eventId = ref(`evt-${spec.name}-missing`);
      const before = await snapshotState(fx, CASE_A1, eventId);

      await expect(sendMilestone(fx, fx.relayerA, spec, ORG_A, CASE_A1, eventId, 2n))
        .to.be.revertedWithCustomError(fx.registry, "RefNotFound")
        .withArgs(COMMITMENT_REF);
      await expectUnchanged(fx, CASE_A1, eventId, before);
    });

    it(`SC05/BR12-N: ${spec.name} by org B on its own case referencing org A's commitment reverts RefOrganizationMismatch`, async function () {
      const fx = await deployMilestoneFixture();
      await recordBaseCommitment(fx); // COMMITMENT_REF belongs to ORG_A / CASE_A1
      const eventId = ref(`evt-${spec.name}-foreign-commitment`);
      const before = await snapshotState(fx, CASE_B, eventId);

      await expect(sendMilestone(fx, fx.relayerB, spec, ORG_B, CASE_B, eventId, 2n))
        .to.be.revertedWithCustomError(fx.registry, "RefOrganizationMismatch")
        .withArgs(COMMITMENT_REF, ORG_A, ORG_B);
      await expectUnchanged(fx, CASE_B, eventId, before);
    });

    it(`SC05: ${spec.name} on CASE_A2 referencing a CASE_A1 commitment reverts RefCaseMismatch`, async function () {
      const fx = await deployMilestoneFixture();
      await recordBaseCommitment(fx);
      const eventId = ref(`evt-${spec.name}-other-case-commitment`);
      const before = await snapshotState(fx, CASE_A2, eventId);

      await expect(sendMilestone(fx, fx.relayerA, spec, ORG_A, CASE_A2, eventId, 2n))
        .to.be.revertedWithCustomError(fx.registry, "RefCaseMismatch")
        .withArgs(COMMITMENT_REF, CASE_A1, CASE_A2);
      await expectUnchanged(fx, CASE_A2, eventId, before);
    });
  }
});

describe("C06 AidRegistry milestones - input validation (SC01/SC10, C01-T12 analogue)", function () {
  for (const spec of MILESTONES) {
    spec.refs.forEach((_, refIndex) => {
      it(`SC10: ${spec.name} with operation ref #${refIndex + 1} = 0 reverts InvalidZeroInput`, async function () {
        const fx = await deployMilestoneFixture();
        const sequence = await prepareCase(fx, spec);
        const eventId = ref(`evt-${spec.name}-zero-ref-${refIndex}`);
        const refs = spec.refs.map((value, index) => (index === refIndex ? ZeroHash : value));
        const before = await snapshotState(fx, CASE_A1, eventId);

        await expect(sendMilestone(fx, fx.relayerA, spec, ORG_A, CASE_A1, eventId, sequence, refs))
          .to.be.revertedWithCustomError(fx.registry, "InvalidZeroInput");
        await expectUnchanged(fx, CASE_A1, eventId, before);
      });
    });

    it(`SC09/SC10: ${spec.name} with chainSequence 0 reverts InvalidZeroInput`, async function () {
      const fx = await deployMilestoneFixture();
      await prepareCase(fx, spec);
      const eventId = ref(`evt-${spec.name}-zero-sequence`);
      const before = await snapshotState(fx, CASE_A1, eventId);

      await expect(sendMilestone(fx, fx.relayerA, spec, ORG_A, CASE_A1, eventId, 0n))
        .to.be.revertedWithCustomError(fx.registry, "InvalidZeroInput");
      await expectUnchanged(fx, CASE_A1, eventId, before);
    });

    it(`SC10: ${spec.name} with schemaVersion 2 reverts UnsupportedSchemaVersion`, async function () {
      const fx = await deployMilestoneFixture();
      const sequence = await prepareCase(fx, spec);
      const eventId = ref(`evt-${spec.name}-schema-2`);
      const before = await snapshotState(fx, CASE_A1, eventId);

      await expect(
        sendMilestoneRaw(fx, fx.relayerA, spec.name, [ORG_A, CASE_A1, eventId, sequence, EVIDENCE, 2, ...spec.refs])
      )
        .to.be.revertedWithCustomError(fx.registry, "UnsupportedSchemaVersion")
        .withArgs(2);
      await expectUnchanged(fx, CASE_A1, eventId, before);
    });
  }
});

describe("C06 AidRegistry milestones - ABI allowlist (BR08-P/N, SC10, SC11)", function () {
  it("BR08-P/SC11: the four milestone functions and events have the exact frozen ABI, nonpayable, fixed-size types only", async function () {
    const fx = await deployMilestoneFixture();
    const iface = fx.registry.interface;
    const envelope = [
      "orgRef:bytes32",
      "caseRef:bytes32",
      "eventId:bytes32",
      "chainSequence:uint64",
      "evidenceCommitment:bytes32",
      "schemaVersion:uint16",
    ];
    const eventEnvelope = [
      "orgRef:bytes32:true",
      "caseRef:bytes32:true",
      "eventId:bytes32:true",
      "chainSequence:uint64:false",
      "evidenceCommitment:bytes32:false",
      "schemaVersion:uint16:false",
      "timestamp:uint64:false",
    ];
    const expected: Array<{ fn: string; event: string; refs: string[] }> = [
      { fn: "recordAidCommitment", event: "AidCommitted", refs: ["commitmentRef"] },
      { fn: "recordCommitmentReduction", event: "CommitmentReduced", refs: ["commitmentRef"] },
      { fn: "recordGoodsReceipt", event: "GoodsReceived", refs: ["receiptRef", "commitmentRef"] },
      { fn: "recordDelivery", event: "DeliveryRecorded", refs: ["deliveryRef"] },
    ];

    for (const { fn, event, refs } of expected) {
      const fragment = iface.getFunction(fn)!;
      expect(fragment.stateMutability, `${fn} mutability`).to.equal("nonpayable");
      expect(fragment.outputs.length, `${fn} outputs`).to.equal(0);
      expect(fragment.inputs.map((p) => `${p.name}:${p.type}`), `${fn} inputs`).to.deep.equal([
        ...envelope,
        ...refs.map((name) => `${name}:bytes32`),
      ]);

      const eventFragment = iface.getEvent(event)!;
      expect(eventFragment.anonymous, `${event} anonymous`).to.equal(false);
      expect(eventFragment.inputs.map((p) => `${p.name}:${p.type}:${p.indexed}`), `${event} inputs`).to.deep.equal([
        ...eventEnvelope,
        ...refs.map((name) => `${name}:bytes32:false`),
      ]);
    }
  });

  // Decision 3 (11/11): the guard exists in code, but openDisputeCount can only become non-zero
  // through flagDispute, which is scheduled for 12/11. Kept visible as pending, not silently omitted.
  it.skip("SC07 (Not Run until flagDispute, 12/11): recordAidCommitment/recordDelivery revert CaseHasOpenDispute while a dispute is open", async function () {});
});
