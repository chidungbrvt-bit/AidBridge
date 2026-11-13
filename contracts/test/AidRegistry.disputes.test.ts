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
// C06 part 2 tests: confirmDelivery, flagDispute, resolveDispute (UPHOLD / VOID_DELIVERY).
// Own fixture: two organizations with different relayers, two verified cases in org A.
// Test names start with the TEST_STRATEGY.md IDs they cover.
// BR-06 (confirmer != recorder) is a backend rule and is intentionally NOT tested here:
// both writes are signed by the same organization relayer.
// ---------------------------------------------------------------------------

const ref = (label: string) => keccak256(toUtf8Bytes(label));

const ORG_A = ref("d-org-a");
const ORG_B = ref("d-org-b");
const CASE_A1 = ref("d-case-a1");
const CASE_A2 = ref("d-case-a2");
const CASE_B = ref("d-case-b");
const CASE_UNVERIFIED = ref("d-case-unverified");
const EVIDENCE = ref("d-evidence-commitment");
const SCHEMA_VERSION = 1;

const DELIVERY = ref("d-delivery-1");
const DELIVERY_2 = ref("d-delivery-2");
const DISPUTE = ref("d-dispute-1");
const DISPUTE_2 = ref("d-dispute-2");

const UPHOLD = 1;
const VOID = 2;
// DeliveryStatus enum values as encoded in InvalidDeliveryState(bytes32,uint8)
const STATUS_CONFIRMED = 2;
const STATUS_VOIDED = 3;

const abiCoder = AbiCoder.defaultAbiCoder();

async function deployDisputeFixture() {
  const { ethers } = await network.getOrCreate();
  const [admin, relayerA, relayerB, stranger] = await ethers.getSigners();
  const registry = await ethers.deployContract("AidRegistry", [admin.address], admin);
  await registry.waitForDeployment();

  await registry.connect(admin).registerOrganization(ORG_A, relayerA.address);
  await registry.connect(admin).registerOrganization(ORG_B, relayerB.address);
  // Every verified case starts at chainSequence 1.
  await registry.connect(relayerA).recordCaseVerification(ORG_A, CASE_A1, ref("d-verify-a1"), 1n, EVIDENCE, SCHEMA_VERSION);
  await registry.connect(relayerA).recordCaseVerification(ORG_A, CASE_A2, ref("d-verify-a2"), 1n, EVIDENCE, SCHEMA_VERSION);
  await registry.connect(relayerB).recordCaseVerification(ORG_B, CASE_B, ref("d-verify-b"), 1n, EVIDENCE, SCHEMA_VERSION);

  return { ethers, registry, admin, relayerA, relayerB, stranger };
}

type Fixture = Awaited<ReturnType<typeof deployDisputeFixture>>;
type Signer = Fixture["relayerA"];
type OpName = "confirmDelivery" | "flagDispute" | "resolveDispute";
type Call = (...args: unknown[]) => Promise<ContractTransactionResponse>;

/** Calls any registry write with the common envelope followed by the operation fields (does not await). */
function send(
  fx: Fixture,
  signer: Signer,
  fn: string,
  orgRef: string,
  caseRef: string,
  eventId: string,
  chainSequence: bigint,
  fields: unknown[],
  schemaVersion: number = SCHEMA_VERSION
): Promise<ContractTransactionResponse> {
  const contract = fx.registry.connect(signer) as unknown as Record<string, Call>;
  return contract[fn](orgRef, caseRef, eventId, chainSequence, EVIDENCE, schemaVersion, ...fields);
}

const recordDelivery = (fx: Fixture, signer: Signer, org: string, kase: string, id: string, seq: bigint, deliveryRef: string) =>
  send(fx, signer, "recordDelivery", org, kase, ref(id), seq, [deliveryRef]);
const confirm = (fx: Fixture, signer: Signer, org: string, kase: string, id: string, seq: bigint, deliveryRef: string) =>
  send(fx, signer, "confirmDelivery", org, kase, ref(id), seq, [deliveryRef]);
const flag = (fx: Fixture, signer: Signer, org: string, kase: string, id: string, seq: bigint, disputeRef: string, deliveryRef: string) =>
  send(fx, signer, "flagDispute", org, kase, ref(id), seq, [disputeRef, deliveryRef]);
const resolve = (
  fx: Fixture,
  signer: Signer,
  org: string,
  kase: string,
  id: string,
  seq: bigint,
  disputeRef: string,
  resolution: number,
  deliveryRef: string
) => send(fx, signer, "resolveDispute", org, kase, ref(id), seq, [disputeRef, resolution, deliveryRef]);

const FIELD_TYPES: Record<OpName, string[]> = {
  confirmDelivery: ["bytes32"],
  flagDispute: ["bytes32", "bytes32"],
  resolveDispute: ["bytes32", "uint8", "bytes32"],
};

/** Same formula as the contract: keccak256(abi.encode(selector, orgRef, caseRef, seq, commitment, schema, fields...)). */
function expectedDigest(fx: Fixture, fn: OpName, orgRef: string, caseRef: string, seq: bigint, fields: unknown[]): string {
  const selector = fx.registry.interface.getFunction(fn)!.selector;
  return keccak256(
    abiCoder.encode(
      ["bytes4", "bytes32", "bytes32", "uint64", "bytes32", "uint16", ...FIELD_TYPES[fn]],
      [selector, orgRef, caseRef, seq, EVIDENCE, SCHEMA_VERSION, ...fields]
    )
  );
}

async function expectStoredEvent(fx: Fixture, eventLabel: string, fn: OpName, orgRef: string, caseRef: string, seq: bigint, fields: unknown[]) {
  expect([...(await fx.registry.getEventRecord(ref(eventLabel)))]).to.deep.equal([
    true,
    fx.registry.interface.getFunction(fn)!.selector,
    orgRef,
    caseRef,
    seq,
    EVIDENCE,
    BigInt(SCHEMA_VERSION),
    expectedDigest(fx, fn, orgRef, caseRef, seq, fields),
  ]);
}

async function expectCase(fx: Fixture, caseRef: string, openDisputes: bigint, lastSequence: bigint) {
  expect([...(await fx.registry.getCaseSummary(caseRef))]).to.deep.equal([true, openDisputes, lastSequence]);
}

async function timestampOf(fx: Fixture, receipt: ContractTransactionReceipt | null): Promise<bigint> {
  const block = await fx.ethers.provider.getBlock(receipt!.blockNumber);
  return BigInt(block!.timestamp);
}

/** Checks one registry log: topic0 + orgRef/caseRef/eventId topics and the number of 32-byte data words. */
function expectLog(fx: Fixture, log: { topics: readonly string[]; data: string }, eventName: string, orgRef: string, caseRef: string, eventId: string, words: number) {
  expect(log.topics).to.deep.equal([fx.registry.interface.getEvent(eventName)!.topicHash, orgRef, caseRef, ref(eventId)]);
  expect((log.data.length - 2) / 2).to.equal(words * 32);
}

/** Everything a reverted call must leave unchanged. */
async function snapshot(fx: Fixture, caseRef: string, eventLabel: string) {
  const logs = await fx.ethers.provider.getLogs({ address: await fx.registry.getAddress(), fromBlock: 0, toBlock: "latest" });
  return {
    event: [...(await fx.registry.getEventRecord(ref(eventLabel)))],
    caseSummary: [...(await fx.registry.getCaseSummary(caseRef))],
    logs: logs.length,
  };
}

// ---------------------------------------------------------------------------
// Per-operation preparation used by the table-driven negative tests.
// Each returns the next valid chainSequence on CASE_A1 and valid operation fields.
// ---------------------------------------------------------------------------
type OpSpec = { fn: OpName; prepare: (fx: Fixture) => Promise<{ seq: bigint; fields: unknown[]; altFields: unknown[] }> };

const OPS: OpSpec[] = [
  {
    fn: "confirmDelivery",
    prepare: async (fx) => {
      await recordDelivery(fx, fx.relayerA, ORG_A, CASE_A1, "prep-delivery", 2n, DELIVERY);
      return { seq: 3n, fields: [DELIVERY], altFields: [DELIVERY_2] };
    },
  },
  {
    fn: "flagDispute",
    prepare: async () => ({ seq: 2n, fields: [DISPUTE, ZeroHash], altFields: [DISPUTE_2, ZeroHash] }),
  },
  {
    fn: "resolveDispute",
    prepare: async (fx) => {
      await flag(fx, fx.relayerA, ORG_A, CASE_A1, "prep-flag", 2n, DISPUTE, ZeroHash);
      return { seq: 3n, fields: [DISPUTE, UPHOLD, ZeroHash], altFields: [DISPUTE, VOID, ZeroHash] };
    },
  },
];

describe("C06 part 2 - happy paths (SC06, SC07, SC08, BR12-P, SC10)", function () {
  it("SC06/BR12-P/SC10: recordDelivery then confirmDelivery emits DeliveryConfirmed and advances sequence 2 -> 3", async function () {
    const fx = await deployDisputeFixture();
    await recordDelivery(fx, fx.relayerA, ORG_A, CASE_A1, "evt-deliver", 2n, DELIVERY);

    const tx = await confirm(fx, fx.relayerA, ORG_A, CASE_A1, "evt-confirm", 3n, DELIVERY);
    const receipt = await tx.wait();
    const timestamp = await timestampOf(fx, receipt);

    await expect(tx)
      .to.emit(fx.registry, "DeliveryConfirmed")
      .withArgs(ORG_A, CASE_A1, ref("evt-confirm"), 3n, EVIDENCE, SCHEMA_VERSION, timestamp, DELIVERY);
    expect(receipt!.logs.length).to.equal(1);
    expectLog(fx, receipt!.logs[0], "DeliveryConfirmed", ORG_A, CASE_A1, "evt-confirm", 5);
    await expectStoredEvent(fx, "evt-confirm", "confirmDelivery", ORG_A, CASE_A1, 3n, [DELIVERY]);
    await expectCase(fx, CASE_A1, 0n, 3n);
  });

  it("SC07: two disputes on one case (case-level and delivery-level) resolve independently, count 0 -> 1 -> 2 -> 1 -> 0", async function () {
    const fx = await deployDisputeFixture();
    await recordDelivery(fx, fx.relayerA, ORG_A, CASE_A1, "evt-deliver", 2n, DELIVERY);

    const flag1 = await flag(fx, fx.relayerA, ORG_A, CASE_A1, "evt-flag-1", 3n, DISPUTE, ZeroHash);
    const flag1Receipt = await flag1.wait();
    await expect(flag1)
      .to.emit(fx.registry, "DisputeFlagged")
      .withArgs(ORG_A, CASE_A1, ref("evt-flag-1"), 3n, EVIDENCE, SCHEMA_VERSION, await timestampOf(fx, flag1Receipt), DISPUTE, ZeroHash);
    expectLog(fx, flag1Receipt!.logs[0], "DisputeFlagged", ORG_A, CASE_A1, "evt-flag-1", 6);
    await expectStoredEvent(fx, "evt-flag-1", "flagDispute", ORG_A, CASE_A1, 3n, [DISPUTE, ZeroHash]);
    await expectCase(fx, CASE_A1, 1n, 3n);

    await flag(fx, fx.relayerA, ORG_A, CASE_A1, "evt-flag-2", 4n, DISPUTE_2, DELIVERY);
    await expectCase(fx, CASE_A1, 2n, 4n);

    const res2 = await resolve(fx, fx.relayerA, ORG_A, CASE_A1, "evt-resolve-2", 5n, DISPUTE_2, UPHOLD, DELIVERY);
    const res2Receipt = await res2.wait();
    await expect(res2)
      .to.emit(fx.registry, "DisputeResolved")
      .withArgs(ORG_A, CASE_A1, ref("evt-resolve-2"), 5n, EVIDENCE, SCHEMA_VERSION, await timestampOf(fx, res2Receipt), DISPUTE_2, UPHOLD, DELIVERY);
    expect(res2Receipt!.logs.length).to.equal(1);
    expectLog(fx, res2Receipt!.logs[0], "DisputeResolved", ORG_A, CASE_A1, "evt-resolve-2", 7);
    await expectStoredEvent(fx, "evt-resolve-2", "resolveDispute", ORG_A, CASE_A1, 5n, [DISPUTE_2, UPHOLD, DELIVERY]);
    await expectCase(fx, CASE_A1, 1n, 5n);

    await resolve(fx, fx.relayerA, ORG_A, CASE_A1, "evt-resolve-1", 6n, DISPUTE, UPHOLD, ZeroHash);
    await expectCase(fx, CASE_A1, 0n, 6n);
  });

  it("SC08: dispute after confirmation, VOID emits DeliveryVoided then DisputeResolved under one eventId; replacement delivery works", async function () {
    const fx = await deployDisputeFixture();
    await recordDelivery(fx, fx.relayerA, ORG_A, CASE_A1, "evt-deliver", 2n, DELIVERY);
    await confirm(fx, fx.relayerA, ORG_A, CASE_A1, "evt-confirm", 3n, DELIVERY);
    await flag(fx, fx.relayerA, ORG_A, CASE_A1, "evt-flag", 4n, DISPUTE, DELIVERY);
    await expectCase(fx, CASE_A1, 1n, 4n);

    const tx = await resolve(fx, fx.relayerA, ORG_A, CASE_A1, "evt-void", 5n, DISPUTE, VOID, DELIVERY);
    const receipt = await tx.wait();
    const timestamp = await timestampOf(fx, receipt);

    expect(receipt!.logs.length).to.equal(2);
    expectLog(fx, receipt!.logs[0], "DeliveryVoided", ORG_A, CASE_A1, "evt-void", 6);
    expectLog(fx, receipt!.logs[1], "DisputeResolved", ORG_A, CASE_A1, "evt-void", 7);
    await expect(tx)
      .to.emit(fx.registry, "DeliveryVoided")
      .withArgs(ORG_A, CASE_A1, ref("evt-void"), 5n, EVIDENCE, SCHEMA_VERSION, timestamp, DELIVERY, DISPUTE);
    await expect(tx)
      .to.emit(fx.registry, "DisputeResolved")
      .withArgs(ORG_A, CASE_A1, ref("evt-void"), 5n, EVIDENCE, SCHEMA_VERSION, timestamp, DISPUTE, VOID, DELIVERY);
    await expectStoredEvent(fx, "evt-void", "resolveDispute", ORG_A, CASE_A1, 5n, [DISPUTE, VOID, DELIVERY]);
    await expectCase(fx, CASE_A1, 0n, 5n);

    // The voided delivery can neither be confirmed again nor reused; a new deliveryRef replaces it.
    await expect(confirm(fx, fx.relayerA, ORG_A, CASE_A1, "evt-confirm-voided", 6n, DELIVERY))
      .to.be.revertedWithCustomError(fx.registry, "InvalidDeliveryState")
      .withArgs(DELIVERY, STATUS_VOIDED);
    await expect(recordDelivery(fx, fx.relayerA, ORG_A, CASE_A1, "evt-reuse", 6n, DELIVERY))
      .to.be.revertedWithCustomError(fx.registry, "RefAlreadyUsed")
      .withArgs(DELIVERY);
    await recordDelivery(fx, fx.relayerA, ORG_A, CASE_A1, "evt-replacement", 6n, DELIVERY_2);
    await confirm(fx, fx.relayerA, ORG_A, CASE_A1, "evt-confirm-replacement", 7n, DELIVERY_2);
    await expectCase(fx, CASE_A1, 0n, 7n);
  });

  it("SC08: UPHOLD keeps the delivery Recorded and decrements the count; confirmDelivery then succeeds", async function () {
    const fx = await deployDisputeFixture();
    await recordDelivery(fx, fx.relayerA, ORG_A, CASE_A1, "evt-deliver", 2n, DELIVERY);
    await flag(fx, fx.relayerA, ORG_A, CASE_A1, "evt-flag", 3n, DISPUTE, DELIVERY);

    const tx = await resolve(fx, fx.relayerA, ORG_A, CASE_A1, "evt-uphold", 4n, DISPUTE, UPHOLD, DELIVERY);
    const receipt = await tx.wait();
    expect(receipt!.logs.length).to.equal(1);
    await expect(tx).to.not.emit(fx.registry, "DeliveryVoided");
    await expectCase(fx, CASE_A1, 0n, 4n);

    await confirm(fx, fx.relayerA, ORG_A, CASE_A1, "evt-confirm", 5n, DELIVERY);
    await expectCase(fx, CASE_A1, 0n, 5n);
  });

  it("SC07 (decision 11/11): an open dispute blocks recordAidCommitment/recordDelivery/confirmDelivery but not reduction/receipt", async function () {
    const fx = await deployDisputeFixture();
    const commitmentRef = ref("d-commitment-1");
    await send(fx, fx.relayerA, "recordAidCommitment", ORG_A, CASE_A1, ref("evt-commit"), 2n, [commitmentRef]);
    await recordDelivery(fx, fx.relayerA, ORG_A, CASE_A1, "evt-deliver", 3n, DELIVERY);
    await flag(fx, fx.relayerA, ORG_A, CASE_A1, "evt-flag", 4n, DISPUTE, ZeroHash);

    await send(fx, fx.relayerA, "recordCommitmentReduction", ORG_A, CASE_A1, ref("evt-reduce"), 5n, [commitmentRef]);
    await send(fx, fx.relayerA, "recordGoodsReceipt", ORG_A, CASE_A1, ref("evt-receipt"), 6n, [ref("d-receipt-1"), commitmentRef]);
    await expectCase(fx, CASE_A1, 1n, 6n);

    await expect(send(fx, fx.relayerA, "recordAidCommitment", ORG_A, CASE_A1, ref("evt-commit-2"), 7n, [ref("d-commitment-2")]))
      .to.be.revertedWithCustomError(fx.registry, "CaseHasOpenDispute")
      .withArgs(CASE_A1);
    await expect(recordDelivery(fx, fx.relayerA, ORG_A, CASE_A1, "evt-deliver-2", 7n, DELIVERY_2))
      .to.be.revertedWithCustomError(fx.registry, "CaseHasOpenDispute")
      .withArgs(CASE_A1);
    await expect(confirm(fx, fx.relayerA, ORG_A, CASE_A1, "evt-confirm", 7n, DELIVERY))
      .to.be.revertedWithCustomError(fx.registry, "CaseHasOpenDispute")
      .withArgs(CASE_A1);
    await expectCase(fx, CASE_A1, 1n, 6n);
  });
});

describe("C06 part 2 - authorization and case guards (BR12-N, SC09)", function () {
  for (const op of OPS) {
    const cases: Array<{ title: string; setup?: (fx: Fixture) => Promise<void>; signer: (fx: Fixture) => Signer; error: string; args: (fx: Fixture) => unknown[] }> = [
      { title: "unknown caller", signer: (fx) => fx.stranger, error: "UnauthorizedRelayer", args: (fx) => [ORG_A, fx.stranger.address] },
      { title: "admin without relayer authorization", signer: (fx) => fx.admin, error: "UnauthorizedRelayer", args: (fx) => [ORG_A, fx.admin.address] },
      { title: "relayer of another organization", signer: (fx) => fx.relayerB, error: "UnauthorizedRelayer", args: (fx) => [ORG_A, fx.relayerB.address] },
      {
        title: "relayer of a deactivated organization",
        setup: async (fx) => { await fx.registry.connect(fx.admin).setOrganizationStatus(ORG_A, false); },
        signer: (fx) => fx.relayerA,
        error: "OrganizationNotActive",
        args: () => [ORG_A],
      },
      {
        title: "revoked relayer",
        setup: async (fx) => { await fx.registry.connect(fx.admin).revokeOrganizationRelayer(ORG_A); },
        signer: (fx) => fx.relayerA,
        error: "UnauthorizedRelayer",
        args: (fx) => [ORG_A, fx.relayerA.address],
      },
    ];
    for (const c of cases) {
      it(`BR12-N: ${c.title} cannot call ${op.fn}; no state or log change`, async function () {
        const fx = await deployDisputeFixture();
        const { seq, fields } = await op.prepare(fx);
        if (c.setup) await c.setup(fx);
        const before = await snapshot(fx, CASE_A1, `evt-${op.fn}-denied`);
        await expect(send(fx, c.signer(fx), op.fn, ORG_A, CASE_A1, ref(`evt-${op.fn}-denied`), seq, fields))
          .to.be.revertedWithCustomError(fx.registry, c.error)
          .withArgs(...c.args(fx));
        expect(await snapshot(fx, CASE_A1, `evt-${op.fn}-denied`)).to.deep.equal(before);
      });
    }

    it(`SC09: ${op.fn} on an unverified case reverts CaseNotVerified`, async function () {
      const fx = await deployDisputeFixture();
      const { fields } = await op.prepare(fx);
      await expect(send(fx, fx.relayerA, op.fn, ORG_A, CASE_UNVERIFIED, ref(`evt-${op.fn}-unverified`), 2n, fields))
        .to.be.revertedWithCustomError(fx.registry, "CaseNotVerified")
        .withArgs(CASE_UNVERIFIED);
    });

    it(`SC09: ${op.fn} with a skipped or repeated chainSequence reverts InvalidChainSequence`, async function () {
      const fx = await deployDisputeFixture();
      const { seq, fields } = await op.prepare(fx);
      await expect(send(fx, fx.relayerA, op.fn, ORG_A, CASE_A1, ref(`evt-${op.fn}-skip`), seq + 1n, fields))
        .to.be.revertedWithCustomError(fx.registry, "InvalidChainSequence")
        .withArgs(CASE_A1, seq, seq + 1n);
      await expect(send(fx, fx.relayerA, op.fn, ORG_A, CASE_A1, ref(`evt-${op.fn}-repeat`), seq - 1n, fields))
        .to.be.revertedWithCustomError(fx.registry, "InvalidChainSequence")
        .withArgs(CASE_A1, seq, seq - 1n);
    });

    it(`SC10: ${op.fn} rejects schemaVersion 2 and chainSequence 0`, async function () {
      const fx = await deployDisputeFixture();
      const { seq, fields } = await op.prepare(fx);
      await expect(send(fx, fx.relayerA, op.fn, ORG_A, CASE_A1, ref(`evt-${op.fn}-schema`), seq, fields, 2))
        .to.be.revertedWithCustomError(fx.registry, "UnsupportedSchemaVersion")
        .withArgs(2);
      await expect(send(fx, fx.relayerA, op.fn, ORG_A, CASE_A1, ref(`evt-${op.fn}-zero-seq`), 0n, fields))
        .to.be.revertedWithCustomError(fx.registry, "InvalidZeroInput");
    });

    it(`BR11-N: ${op.fn} identical retry reverts EventAlreadyRecorded, changed fields revert EventIdConflict`, async function () {
      const fx = await deployDisputeFixture();
      const { seq, fields, altFields } = await op.prepare(fx);
      const eventId = ref(`evt-${op.fn}-once`);
      await send(fx, fx.relayerA, op.fn, ORG_A, CASE_A1, eventId, seq, fields);
      const before = await snapshot(fx, CASE_A1, `evt-${op.fn}-once`);

      await expect(send(fx, fx.relayerA, op.fn, ORG_A, CASE_A1, eventId, seq, fields))
        .to.be.revertedWithCustomError(fx.registry, "EventAlreadyRecorded")
        .withArgs(eventId);
      await expect(send(fx, fx.relayerA, op.fn, ORG_A, CASE_A1, eventId, seq, altFields))
        .to.be.revertedWithCustomError(fx.registry, "EventIdConflict")
        .withArgs(eventId);
      expect(await snapshot(fx, CASE_A1, `evt-${op.fn}-once`)).to.deep.equal(before);
    });
  }

  it("BR11-N: an eventId used by one operation conflicts when reused by another operation", async function () {
    const fx = await deployDisputeFixture();
    await recordDelivery(fx, fx.relayerA, ORG_A, CASE_A1, "evt-deliver", 2n, DELIVERY);
    await confirm(fx, fx.relayerA, ORG_A, CASE_A1, "evt-shared", 3n, DELIVERY);

    await expect(flag(fx, fx.relayerA, ORG_A, CASE_A1, "evt-shared", 4n, DISPUTE, DELIVERY))
      .to.be.revertedWithCustomError(fx.registry, "EventIdConflict")
      .withArgs(ref("evt-shared"));
    await flag(fx, fx.relayerA, ORG_A, CASE_A1, "evt-flag", 4n, DISPUTE, DELIVERY);
    await expect(resolve(fx, fx.relayerA, ORG_A, CASE_A1, "evt-flag", 5n, DISPUTE, UPHOLD, DELIVERY))
      .to.be.revertedWithCustomError(fx.registry, "EventIdConflict")
      .withArgs(ref("evt-flag"));
  });
});

describe("C06 part 2 - confirmDelivery state rules (SC06)", function () {
  it("SC06: confirming twice reverts InvalidDeliveryState(Confirmed)", async function () {
    const fx = await deployDisputeFixture();
    await recordDelivery(fx, fx.relayerA, ORG_A, CASE_A1, "evt-deliver", 2n, DELIVERY);
    await confirm(fx, fx.relayerA, ORG_A, CASE_A1, "evt-confirm", 3n, DELIVERY);
    await expect(confirm(fx, fx.relayerA, ORG_A, CASE_A1, "evt-confirm-again", 4n, DELIVERY))
      .to.be.revertedWithCustomError(fx.registry, "InvalidDeliveryState")
      .withArgs(DELIVERY, STATUS_CONFIRMED);
  });

  it("SC06: unknown delivery reverts RefNotFound; zero deliveryRef reverts InvalidZeroInput", async function () {
    const fx = await deployDisputeFixture();
    await expect(confirm(fx, fx.relayerA, ORG_A, CASE_A1, "evt-confirm", 2n, DELIVERY))
      .to.be.revertedWithCustomError(fx.registry, "RefNotFound")
      .withArgs(DELIVERY);
    await expect(confirm(fx, fx.relayerA, ORG_A, CASE_A1, "evt-confirm-zero", 2n, ZeroHash))
      .to.be.revertedWithCustomError(fx.registry, "InvalidZeroInput");
  });

  it("SC06: a delivery of another organization or another case cannot be confirmed", async function () {
    const fx = await deployDisputeFixture();
    await recordDelivery(fx, fx.relayerB, ORG_B, CASE_B, "evt-deliver-b", 2n, DELIVERY);
    await recordDelivery(fx, fx.relayerA, ORG_A, CASE_A2, "evt-deliver-a2", 2n, DELIVERY_2);

    await expect(confirm(fx, fx.relayerA, ORG_A, CASE_A1, "evt-confirm-b", 2n, DELIVERY))
      .to.be.revertedWithCustomError(fx.registry, "RefOrganizationMismatch")
      .withArgs(DELIVERY, ORG_B, ORG_A);
    await expect(confirm(fx, fx.relayerA, ORG_A, CASE_A1, "evt-confirm-a2", 2n, DELIVERY_2))
      .to.be.revertedWithCustomError(fx.registry, "RefCaseMismatch")
      .withArgs(DELIVERY_2, CASE_A2, CASE_A1);
  });
});

describe("C06 part 2 - flagDispute state rules (SC07, SC08)", function () {
  it("SC07: a reused disputeRef reverts RefAlreadyUsed; a zero disputeRef reverts InvalidZeroInput", async function () {
    const fx = await deployDisputeFixture();
    await flag(fx, fx.relayerA, ORG_A, CASE_A1, "evt-flag", 2n, DISPUTE, ZeroHash);
    await expect(flag(fx, fx.relayerA, ORG_A, CASE_A1, "evt-flag-dup", 3n, DISPUTE, ZeroHash))
      .to.be.revertedWithCustomError(fx.registry, "RefAlreadyUsed")
      .withArgs(DISPUTE);
    await expect(flag(fx, fx.relayerA, ORG_A, CASE_A1, "evt-flag-zero", 3n, ZeroHash, ZeroHash))
      .to.be.revertedWithCustomError(fx.registry, "InvalidZeroInput");
  });

  it("SC07: a referenced delivery must exist and belong to the same organization and case", async function () {
    const fx = await deployDisputeFixture();
    await recordDelivery(fx, fx.relayerB, ORG_B, CASE_B, "evt-deliver-b", 2n, DELIVERY);
    await recordDelivery(fx, fx.relayerA, ORG_A, CASE_A2, "evt-deliver-a2", 2n, DELIVERY_2);

    await expect(flag(fx, fx.relayerA, ORG_A, CASE_A1, "evt-flag-missing", 2n, DISPUTE, ref("d-no-such-delivery")))
      .to.be.revertedWithCustomError(fx.registry, "RefNotFound")
      .withArgs(ref("d-no-such-delivery"));
    await expect(flag(fx, fx.relayerA, ORG_A, CASE_A1, "evt-flag-b", 2n, DISPUTE, DELIVERY))
      .to.be.revertedWithCustomError(fx.registry, "RefOrganizationMismatch")
      .withArgs(DELIVERY, ORG_B, ORG_A);
    await expect(flag(fx, fx.relayerA, ORG_A, CASE_A1, "evt-flag-a2", 2n, DISPUTE, DELIVERY_2))
      .to.be.revertedWithCustomError(fx.registry, "RefCaseMismatch")
      .withArgs(DELIVERY_2, CASE_A2, CASE_A1);
  });

  it("SC08: a voided delivery cannot be flagged again", async function () {
    const fx = await deployDisputeFixture();
    await recordDelivery(fx, fx.relayerA, ORG_A, CASE_A1, "evt-deliver", 2n, DELIVERY);
    await flag(fx, fx.relayerA, ORG_A, CASE_A1, "evt-flag", 3n, DISPUTE, DELIVERY);
    await resolve(fx, fx.relayerA, ORG_A, CASE_A1, "evt-void", 4n, DISPUTE, VOID, DELIVERY);
    await expect(flag(fx, fx.relayerA, ORG_A, CASE_A1, "evt-flag-voided", 5n, DISPUTE_2, DELIVERY))
      .to.be.revertedWithCustomError(fx.registry, "InvalidDeliveryState")
      .withArgs(DELIVERY, STATUS_VOIDED);
  });
});

describe("C06 part 2 - resolveDispute state rules (SC07, SC08)", function () {
  it("SC07: unknown dispute reverts RefNotFound; a resolved dispute reverts DisputeNotOpen", async function () {
    const fx = await deployDisputeFixture();
    await expect(resolve(fx, fx.relayerA, ORG_A, CASE_A1, "evt-resolve-missing", 2n, DISPUTE, UPHOLD, ZeroHash))
      .to.be.revertedWithCustomError(fx.registry, "RefNotFound")
      .withArgs(DISPUTE);

    await flag(fx, fx.relayerA, ORG_A, CASE_A1, "evt-flag", 2n, DISPUTE, ZeroHash);
    await resolve(fx, fx.relayerA, ORG_A, CASE_A1, "evt-resolve", 3n, DISPUTE, UPHOLD, ZeroHash);
    await expect(resolve(fx, fx.relayerA, ORG_A, CASE_A1, "evt-resolve-again", 4n, DISPUTE, UPHOLD, ZeroHash))
      .to.be.revertedWithCustomError(fx.registry, "DisputeNotOpen")
      .withArgs(DISPUTE);
  });

  it("SC07: resolution 0 or 3 reverts InvalidResolution; zero disputeRef reverts InvalidZeroInput", async function () {
    const fx = await deployDisputeFixture();
    await flag(fx, fx.relayerA, ORG_A, CASE_A1, "evt-flag", 2n, DISPUTE, ZeroHash);
    for (const bad of [0, 3]) {
      await expect(resolve(fx, fx.relayerA, ORG_A, CASE_A1, `evt-resolve-${bad}`, 3n, DISPUTE, bad, ZeroHash))
        .to.be.revertedWithCustomError(fx.registry, "InvalidResolution")
        .withArgs(bad);
    }
    await expect(resolve(fx, fx.relayerA, ORG_A, CASE_A1, "evt-resolve-zero", 3n, ZeroHash, UPHOLD, ZeroHash))
      .to.be.revertedWithCustomError(fx.registry, "InvalidZeroInput");
  });

  it("SC08: VOID of a case-level dispute reverts DisputeHasNoDelivery", async function () {
    const fx = await deployDisputeFixture();
    await flag(fx, fx.relayerA, ORG_A, CASE_A1, "evt-flag", 2n, DISPUTE, ZeroHash);
    await expect(resolve(fx, fx.relayerA, ORG_A, CASE_A1, "evt-void", 3n, DISPUTE, VOID, ZeroHash))
      .to.be.revertedWithCustomError(fx.registry, "DisputeHasNoDelivery")
      .withArgs(DISPUTE);
  });

  it("SC08: deliveryRef different from the stored one reverts DisputeDeliveryMismatch for VOID and UPHOLD", async function () {
    const fx = await deployDisputeFixture();
    await recordDelivery(fx, fx.relayerA, ORG_A, CASE_A1, "evt-deliver", 2n, DELIVERY);
    await recordDelivery(fx, fx.relayerA, ORG_A, CASE_A1, "evt-deliver-2", 3n, DELIVERY_2);
    await flag(fx, fx.relayerA, ORG_A, CASE_A1, "evt-flag", 4n, DISPUTE, DELIVERY);

    for (const resolution of [VOID, UPHOLD]) {
      await expect(resolve(fx, fx.relayerA, ORG_A, CASE_A1, `evt-mismatch-${resolution}`, 5n, DISPUTE, resolution, DELIVERY_2))
        .to.be.revertedWithCustomError(fx.registry, "DisputeDeliveryMismatch")
        .withArgs(DISPUTE, DELIVERY, DELIVERY_2);
    }
  });

  it("SC08: VOID of a delivery already voided by another dispute reverts InvalidDeliveryState(Voided)", async function () {
    const fx = await deployDisputeFixture();
    await recordDelivery(fx, fx.relayerA, ORG_A, CASE_A1, "evt-deliver", 2n, DELIVERY);
    await flag(fx, fx.relayerA, ORG_A, CASE_A1, "evt-flag-1", 3n, DISPUTE, DELIVERY);
    await flag(fx, fx.relayerA, ORG_A, CASE_A1, "evt-flag-2", 4n, DISPUTE_2, DELIVERY);
    await resolve(fx, fx.relayerA, ORG_A, CASE_A1, "evt-void-1", 5n, DISPUTE, VOID, DELIVERY);

    await expect(resolve(fx, fx.relayerA, ORG_A, CASE_A1, "evt-void-2", 6n, DISPUTE_2, VOID, DELIVERY))
      .to.be.revertedWithCustomError(fx.registry, "InvalidDeliveryState")
      .withArgs(DELIVERY, STATUS_VOIDED);
    // The second dispute can still be closed with UPHOLD.
    await resolve(fx, fx.relayerA, ORG_A, CASE_A1, "evt-uphold-2", 6n, DISPUTE_2, UPHOLD, DELIVERY);
    await expectCase(fx, CASE_A1, 0n, 6n);
  });

  it("SC07: a dispute of another organization or another case cannot be resolved", async function () {
    const fx = await deployDisputeFixture();
    await flag(fx, fx.relayerB, ORG_B, CASE_B, "evt-flag-b", 2n, DISPUTE, ZeroHash);
    await flag(fx, fx.relayerA, ORG_A, CASE_A2, "evt-flag-a2", 2n, DISPUTE_2, ZeroHash);

    await expect(resolve(fx, fx.relayerA, ORG_A, CASE_A1, "evt-resolve-b", 2n, DISPUTE, UPHOLD, ZeroHash))
      .to.be.revertedWithCustomError(fx.registry, "RefOrganizationMismatch")
      .withArgs(DISPUTE, ORG_B, ORG_A);
    await expect(resolve(fx, fx.relayerA, ORG_A, CASE_A1, "evt-resolve-a2", 2n, DISPUTE_2, UPHOLD, ZeroHash))
      .to.be.revertedWithCustomError(fx.registry, "RefCaseMismatch")
      .withArgs(DISPUTE_2, CASE_A2, CASE_A1);
    await expectCase(fx, CASE_B, 1n, 2n);
    await expectCase(fx, CASE_A2, 1n, 2n);
  });
});

describe("C06 part 2 - ABI allowlist (SC11)", function () {
  it("SC11: the three functions and four events have the exact ABI, nonpayable, fixed-size types only", async function () {
    const fx = await deployDisputeFixture();
    const iface = fx.registry.interface;
    const envelope = ["orgRef:bytes32", "caseRef:bytes32", "eventId:bytes32", "chainSequence:uint64", "evidenceCommitment:bytes32", "schemaVersion:uint16"];
    const eventEnvelope = [
      "orgRef:bytes32:true",
      "caseRef:bytes32:true",
      "eventId:bytes32:true",
      "chainSequence:uint64:false",
      "evidenceCommitment:bytes32:false",
      "schemaVersion:uint16:false",
      "timestamp:uint64:false",
    ];
    const functions: Record<OpName, string[]> = {
      confirmDelivery: ["deliveryRef:bytes32"],
      flagDispute: ["disputeRef:bytes32", "deliveryRef:bytes32"],
      resolveDispute: ["disputeRef:bytes32", "resolution:uint8", "deliveryRef:bytes32"],
    };
    for (const [fn, fields] of Object.entries(functions)) {
      const fragment = iface.getFunction(fn)!;
      expect(fragment.stateMutability, `${fn} mutability`).to.equal("nonpayable");
      expect(fragment.outputs.length, `${fn} outputs`).to.equal(0);
      expect(fragment.inputs.map((p) => `${p.name}:${p.type}`), `${fn} inputs`).to.deep.equal([...envelope, ...fields]);
    }
    const events: Record<string, string[]> = {
      DeliveryConfirmed: ["deliveryRef:bytes32:false"],
      DisputeFlagged: ["disputeRef:bytes32:false", "deliveryRef:bytes32:false"],
      DisputeResolved: ["disputeRef:bytes32:false", "resolution:uint8:false", "deliveryRef:bytes32:false"],
      DeliveryVoided: ["deliveryRef:bytes32:false", "disputeRef:bytes32:false"],
    };
    for (const [name, fields] of Object.entries(events)) {
      const fragment = iface.getEvent(name)!;
      expect(fragment.anonymous, `${name} anonymous`).to.equal(false);
      expect(fragment.inputs.map((p) => `${p.name}:${p.type}:${p.indexed}`), `${name} inputs`).to.deep.equal([...eventEnvelope, ...fields]);
    }
    const errors: Record<string, string[]> = {
      InvalidDeliveryState: ["bytes32", "uint8"],
      DisputeNotOpen: ["bytes32"],
      InvalidResolution: ["uint8"],
      DisputeDeliveryMismatch: ["bytes32", "bytes32", "bytes32"],
      DisputeHasNoDelivery: ["bytes32"],
    };
    for (const [name, types] of Object.entries(errors)) {
      expect(iface.getError(name)!.inputs.map((p) => p.type), `${name} inputs`).to.deep.equal(types);
    }
    // No payable function, no receive/fallback anywhere in the ABI.
    const fragments = iface.fragments;
    expect(fragments.some((f) => f.type === "receive" || f.type === "fallback")).to.equal(false);
    iface.forEachFunction((f) => expect(f.payable, `${f.name} payable`).to.equal(false));
  });

  it("SC11: sending AVAX to the registry or with a dispute call reverts", async function () {
    const fx = await deployDisputeFixture();
    const registryAddress = await fx.registry.getAddress();
    const calldata = fx.registry.interface.encodeFunctionData("flagDispute", [
      ORG_A,
      CASE_A1,
      ref("evt-flag-paid"),
      2n,
      EVIDENCE,
      SCHEMA_VERSION,
      DISPUTE,
      ZeroHash,
    ]);
    const attempts = [
      () => fx.stranger.sendTransaction({ to: registryAddress, value: 1n }),
      () => fx.relayerA.sendTransaction({ to: registryAddress, data: calldata, value: 1n }),
    ];
    for (const attempt of attempts) {
      let failed = false;
      try {
        const tx = await attempt();
        await tx.wait();
      } catch {
        failed = true;
      }
      expect(failed).to.equal(true);
    }
    expect(await fx.ethers.provider.getBalance(registryAddress)).to.equal(0n);
    await expectCase(fx, CASE_A1, 0n, 1n);
  });
});
