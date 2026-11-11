import { expect } from "chai";
import { network } from "hardhat";
import { ZeroAddress, type Interface } from "ethers";

const b32 = (byte: string) => "0x" + byte.repeat(32);

const ZERO = b32("00");
const ORG_A = b32("10");
const ORG_B = b32("11");
const ORG_C = b32("12");
const ORG_UNKNOWN = b32("1f");
const CASE_REF = b32("20");
const EVENT_ID = b32("30");
const EVENT_ID_2 = b32("31");
const COMMITMENT = b32("40");
const OTHER_COMMITMENT = b32("55");
const CASE_REF_2 = b32("21");
const EVENT_ID_3 = b32("32");
const UINT64_MAX = 2n ** 64n - 1n;
const UINT16_MAX = 65535n;

async function deployFixture() {
  const { ethers } = await network.getOrCreate();
  const [admin, relayerA, relayerB, stranger] = await ethers.getSigners();
  const registry = await ethers.deployContract("AidRegistry", [admin.address], admin);
  await registry.waitForDeployment();
  await registry.connect(admin).registerOrganization(ORG_A, relayerA.address);
  await registry.connect(admin).registerOrganization(ORG_B, relayerB.address);
  return { ethers, registry, admin, relayerA, relayerB, stranger };
}

type Registry = Awaited<ReturnType<typeof deployFixture>>["registry"];

async function expectNothingRecorded(registry: Registry, eventId: string, caseRef: string) {
  const [exists] = await registry.getEventRecord(eventId);
  expect(exists).to.equal(false);
  const [verified, openDisputeCount, lastChainSequence] = await registry.getCaseSummary(caseRef);
  expect(verified).to.equal(false);
  expect(openDisputeCount).to.equal(0n);
  expect(lastChainSequence).to.equal(0n);
}

type Fixture = Awaited<ReturnType<typeof deployFixture>>;

async function countCaseVerified(registry: Registry): Promise<number> {
  const logs = await registry.queryFilter(registry.filters.CaseVerified());
  return logs.length;
}

async function snapshotState(registry: Registry, eventId: string, caseRef: string) {
  return {
    event: [...(await registry.getEventRecord(eventId))],
    caseSummary: [...(await registry.getCaseSummary(caseRef))],
    caseVerifiedCount: await countCaseVerified(registry),
  };
}

/** Records the canonical baseline verification (ORG_A, CASE_REF, EVENT_ID, sequence 1) and snapshots it. */
async function recordBaseline(fx: Fixture) {
  await fx.registry
    .connect(fx.relayerA)
    .recordCaseVerification(ORG_A, CASE_REF, EVENT_ID, 1n, COMMITMENT, 1);
  const snapshot = await snapshotState(fx.registry, EVENT_ID, CASE_REF);
  expect(snapshot.caseVerifiedCount).to.equal(1);
  return snapshot;
}

async function expectBaselineUnchanged(
  fx: Fixture,
  baseline: Awaited<ReturnType<typeof recordBaseline>>
) {
  expect(await snapshotState(fx.registry, EVENT_ID, CASE_REF)).to.deep.equal(baseline);
}

/** Asserts ethers rejects the call while ABI-encoding it, before anything is signed or broadcast. */
async function expectClientRejection(send: () => Promise<unknown>) {
  let caught: unknown;
  try {
    await send();
  } catch (error) {
    caught = error;
  }
  expect(caught, "ethers must reject the call before broadcasting").to.not.equal(undefined);
  expect((caught as { code?: string }).code).to.be.oneOf(["INVALID_ARGUMENT", "NUMERIC_FAULT"]);
}

describe("C03 AidRegistry Part 2 - Negative paths (guard order D49)", function () {
  it("C01-T02: unknown caller is rejected before any event lookup, even for an existing eventId", async function () {
    const { registry, relayerA, stranger } = await deployFixture();
    await registry
      .connect(relayerA)
      .recordCaseVerification(ORG_A, CASE_REF, EVENT_ID, 1n, COMMITMENT, 1);

    await expect(
      registry
        .connect(stranger)
        .recordCaseVerification(ORG_A, CASE_REF, EVENT_ID, 1n, COMMITMENT, 1)
    )
      .to.be.revertedWithCustomError(registry, "UnauthorizedRelayer")
      .withArgs(ORG_A, stranger.address);

    const [, , , , sequence] = await registry.getEventRecord(EVENT_ID);
    expect(sequence).to.equal(1n);
  });

  it("C01-T03: admin has no implicit business-write permission", async function () {
    const { registry, admin } = await deployFixture();
    await expect(
      registry
        .connect(admin)
        .recordCaseVerification(ORG_A, CASE_REF, EVENT_ID, 1n, COMMITMENT, 1)
    )
      .to.be.revertedWithCustomError(registry, "UnauthorizedRelayer")
      .withArgs(ORG_A, admin.address);
    await expectNothingRecorded(registry, EVENT_ID, CASE_REF);
  });

  it("C01-T04: unregistered organization is rejected", async function () {
    const { registry, relayerA } = await deployFixture();
    await expect(
      registry
        .connect(relayerA)
        .recordCaseVerification(ORG_UNKNOWN, CASE_REF, EVENT_ID, 1n, COMMITMENT, 1)
    )
      .to.be.revertedWithCustomError(registry, "OrganizationNotActive")
      .withArgs(ORG_UNKNOWN);
    await expectNothingRecorded(registry, EVENT_ID, CASE_REF);
  });

  it("C01-T04: deactivated organization is rejected", async function () {
    const { registry, admin, relayerA } = await deployFixture();
    await registry.connect(admin).setOrganizationStatus(ORG_A, false);
    await expect(
      registry
        .connect(relayerA)
        .recordCaseVerification(ORG_A, CASE_REF, EVENT_ID, 1n, COMMITMENT, 1)
    )
      .to.be.revertedWithCustomError(registry, "OrganizationNotActive")
      .withArgs(ORG_A);
    await expectNothingRecorded(registry, EVENT_ID, CASE_REF);
  });

  it("C01-T05: relayer of another organization cannot write for this organization", async function () {
    const { registry, relayerB } = await deployFixture();
    await expect(
      registry
        .connect(relayerB)
        .recordCaseVerification(ORG_A, CASE_REF, EVENT_ID, 1n, COMMITMENT, 1)
    )
      .to.be.revertedWithCustomError(registry, "UnauthorizedRelayer")
      .withArgs(ORG_A, relayerB.address);
    await expectNothingRecorded(registry, EVENT_ID, CASE_REF);
  });

  it("C01-T05: authorized relayer cannot take a case owned by another organization", async function () {
    const { registry, relayerA, relayerB } = await deployFixture();
    await registry
      .connect(relayerA)
      .recordCaseVerification(ORG_A, CASE_REF, EVENT_ID, 1n, COMMITMENT, 1);

    await expect(
      registry
        .connect(relayerB)
        .recordCaseVerification(ORG_B, CASE_REF, EVENT_ID_2, 1n, COMMITMENT, 1)
    )
      .to.be.revertedWithCustomError(registry, "CaseOrganizationMismatch")
      .withArgs(CASE_REF, ORG_A, ORG_B);

    const [exists] = await registry.getEventRecord(EVENT_ID_2);
    expect(exists).to.equal(false);
  });

  it("C01-T06: revoked relayer is rejected", async function () {
    const { registry, admin, relayerA } = await deployFixture();
    await registry.connect(admin).revokeOrganizationRelayer(ORG_A);
    await expect(
      registry
        .connect(relayerA)
        .recordCaseVerification(ORG_A, CASE_REF, EVENT_ID, 1n, COMMITMENT, 1)
    )
      .to.be.revertedWithCustomError(registry, "UnauthorizedRelayer")
      .withArgs(ORG_A, relayerA.address);
    await expectNothingRecorded(registry, EVENT_ID, CASE_REF);
  });

  it("C01-T07: chainSequence 0 hits the zero-input guard, chainSequence 2 hits the sequence guard", async function () {
    const { registry, relayerA } = await deployFixture();
    await expect(
      registry
        .connect(relayerA)
        .recordCaseVerification(ORG_A, CASE_REF, EVENT_ID, 0n, COMMITMENT, 1)
    ).to.be.revertedWithCustomError(registry, "InvalidZeroInput");

    await expect(
      registry
        .connect(relayerA)
        .recordCaseVerification(ORG_A, CASE_REF, EVENT_ID, 2n, COMMITMENT, 1)
    )
      .to.be.revertedWithCustomError(registry, "InvalidChainSequence")
      .withArgs(CASE_REF, 1n, 2n);
    await expectNothingRecorded(registry, EVENT_ID, CASE_REF);
  });

  it("C01-T08: an already verified case cannot be verified again with a new eventId", async function () {
    const { registry, relayerA } = await deployFixture();
    await registry
      .connect(relayerA)
      .recordCaseVerification(ORG_A, CASE_REF, EVENT_ID, 1n, COMMITMENT, 1);

    await expect(
      registry
        .connect(relayerA)
        .recordCaseVerification(ORG_A, CASE_REF, EVENT_ID_2, 2n, COMMITMENT, 1)
    )
      .to.be.revertedWithCustomError(registry, "CaseAlreadyVerified")
      .withArgs(CASE_REF);
    const [exists] = await registry.getEventRecord(EVENT_ID_2);
    expect(exists).to.equal(false);
  });

  it("C01-T09: replaying the identical payload reverts with EventAlreadyRecorded", async function () {
    const { registry, relayerA } = await deployFixture();
    await registry
      .connect(relayerA)
      .recordCaseVerification(ORG_A, CASE_REF, EVENT_ID, 1n, COMMITMENT, 1);

    await expect(
      registry
        .connect(relayerA)
        .recordCaseVerification(ORG_A, CASE_REF, EVENT_ID, 1n, COMMITMENT, 1)
    )
      .to.be.revertedWithCustomError(registry, "EventAlreadyRecorded")
      .withArgs(EVENT_ID);
  });

  it("C01-T10: same eventId with a different payload reverts with EventIdConflict and keeps the original record", async function () {
    const { registry, relayerA } = await deployFixture();
    await registry
      .connect(relayerA)
      .recordCaseVerification(ORG_A, CASE_REF, EVENT_ID, 1n, COMMITMENT, 1);

    await expect(
      registry
        .connect(relayerA)
        .recordCaseVerification(ORG_A, CASE_REF, EVENT_ID, 1n, OTHER_COMMITMENT, 1)
    )
      .to.be.revertedWithCustomError(registry, "EventIdConflict")
      .withArgs(EVENT_ID);

    const [, , , , , storedCommitment] = await registry.getEventRecord(EVENT_ID);
    expect(storedCommitment).to.equal(COMMITMENT);
  });

  it("C01-T11: same eventId with a different orgRef conflicts when the caller serves both organizations", async function () {
    const { registry, admin, relayerA } = await deployFixture();
    await registry.connect(admin).registerOrganization(ORG_C, relayerA.address);
    await registry
      .connect(relayerA)
      .recordCaseVerification(ORG_A, CASE_REF, EVENT_ID, 1n, COMMITMENT, 1);

    await expect(
      registry
        .connect(relayerA)
        .recordCaseVerification(ORG_C, CASE_REF, EVENT_ID, 1n, COMMITMENT, 1)
    )
      .to.be.revertedWithCustomError(registry, "EventIdConflict")
      .withArgs(EVENT_ID);
  });

  it("C01-T12: zero caseRef, eventId or evidenceCommitment is rejected", async function () {
    const { registry, relayerA } = await deployFixture();
    const cases: Array<[string, string, string]> = [
      [ZERO, EVENT_ID, COMMITMENT],
      [CASE_REF, ZERO, COMMITMENT],
      [CASE_REF, EVENT_ID, ZERO],
    ];
    for (const [caseRef, eventId, commitment] of cases) {
      await expect(
        registry
          .connect(relayerA)
          .recordCaseVerification(ORG_A, caseRef, eventId, 1n, commitment, 1)
      ).to.be.revertedWithCustomError(registry, "InvalidZeroInput");
    }
    await expectNothingRecorded(registry, EVENT_ID, CASE_REF);
  });

  it("C01-T12: unsupported schemaVersion 0 and 2 are rejected", async function () {
    const { registry, relayerA } = await deployFixture();
    for (const version of [0n, 2n]) {
      await expect(
        registry
          .connect(relayerA)
          .recordCaseVerification(ORG_A, CASE_REF, EVENT_ID, 1n, COMMITMENT, version)
      )
        .to.be.revertedWithCustomError(registry, "UnsupportedSchemaVersion")
        .withArgs(version);
    }
    await expectNothingRecorded(registry, EVENT_ID, CASE_REF);
  });
});

describe("C03 AidRegistry Part 2 - Organization and relayer lifecycle", function () {
  it("rotating the relayer moves RELAYER_ROLE to the new wallet", async function () {
    const { registry, admin, relayerA, stranger } = await deployFixture();
    const role = await registry.RELAYER_ROLE();

    await registry.connect(admin).updateOrganizationRelayer(ORG_A, stranger.address);

    expect(await registry.hasRole(role, relayerA.address)).to.equal(false);
    expect(await registry.hasRole(role, stranger.address)).to.equal(true);

    await expect(
      registry
        .connect(relayerA)
        .recordCaseVerification(ORG_A, CASE_REF, EVENT_ID, 1n, COMMITMENT, 1)
    ).to.be.revertedWithCustomError(registry, "UnauthorizedRelayer");

    await registry
      .connect(stranger)
      .recordCaseVerification(ORG_A, CASE_REF, EVENT_ID, 1n, COMMITMENT, 1);
    const [verified] = await registry.getCaseSummary(CASE_REF);
    expect(verified).to.equal(true);
  });

  it("keeps RELAYER_ROLE while the wallet still serves another active organization", async function () {
    const { registry, admin, relayerA } = await deployFixture();
    const role = await registry.RELAYER_ROLE();
    await registry.connect(admin).registerOrganization(ORG_C, relayerA.address);

    await registry.connect(admin).revokeOrganizationRelayer(ORG_A);
    expect(await registry.hasRole(role, relayerA.address)).to.equal(true);

    await registry.connect(admin).setOrganizationStatus(ORG_C, false);
    expect(await registry.hasRole(role, relayerA.address)).to.equal(false);
  });

  it("reactivating an organization restores RELAYER_ROLE and write access", async function () {
    const { registry, admin, relayerA } = await deployFixture();
    const role = await registry.RELAYER_ROLE();

    await registry.connect(admin).setOrganizationStatus(ORG_A, false);
    expect(await registry.hasRole(role, relayerA.address)).to.equal(false);

    await registry.connect(admin).setOrganizationStatus(ORG_A, true);
    expect(await registry.hasRole(role, relayerA.address)).to.equal(true);

    await registry
      .connect(relayerA)
      .recordCaseVerification(ORG_A, CASE_REF, EVENT_ID, 1n, COMMITMENT, 1);
    const [verified] = await registry.getCaseSummary(CASE_REF);
    expect(verified).to.equal(true);
  });

  it("only DEFAULT_ADMIN_ROLE can register or manage organizations", async function () {
    const { registry, stranger } = await deployFixture();
    await expect(
      registry.connect(stranger).registerOrganization(ORG_C, stranger.address)
    ).to.be.revertedWithCustomError(registry, "AccessControlUnauthorizedAccount");
    await expect(
      registry.connect(stranger).setOrganizationStatus(ORG_A, false)
    ).to.be.revertedWithCustomError(registry, "AccessControlUnauthorizedAccount");
  });

  it("rejects duplicate registration and zero inputs", async function () {
    const { registry, admin, relayerA } = await deployFixture();
    await expect(
      registry.connect(admin).registerOrganization(ORG_A, relayerA.address)
    )
      .to.be.revertedWithCustomError(registry, "OrganizationAlreadyRegistered")
      .withArgs(ORG_A);
    await expect(
      registry.connect(admin).registerOrganization(ZERO, relayerA.address)
    ).to.be.revertedWithCustomError(registry, "InvalidZeroInput");
  });
});

describe("C03 AidRegistry Part 2 - Supplementary negative coverage (C01-T02..T12)", function () {
  it("C01-T02: unknown caller on a fresh eventId is rejected and nothing is written", async function () {
    const { registry, stranger } = await deployFixture();
    await expect(
      registry
        .connect(stranger)
        .recordCaseVerification(ORG_A, CASE_REF, EVENT_ID, 1n, COMMITMENT, 1)
    )
      .to.be.revertedWithCustomError(registry, "UnauthorizedRelayer")
      .withArgs(ORG_A, stranger.address);
    await expectNothingRecorded(registry, EVENT_ID, CASE_REF);
    expect(await countCaseVerified(registry)).to.equal(0);
  });

  it("C01-T02: unknown caller replaying or mutating a stored eventId gets UnauthorizedRelayer, never a duplicate-lookup error", async function () {
    const fx = await deployFixture();
    const baseline = await recordBaseline(fx);

    for (const commitment of [COMMITMENT, OTHER_COMMITMENT]) {
      await expect(
        fx.registry
          .connect(fx.stranger)
          .recordCaseVerification(ORG_A, CASE_REF, EVENT_ID, 1n, commitment, 1)
      )
        .to.be.revertedWithCustomError(fx.registry, "UnauthorizedRelayer")
        .withArgs(ORG_A, fx.stranger.address);
    }
    await expectBaselineUnchanged(fx, baseline);
  });

  it("C01-T03: admin replaying a stored eventId is still rejected as UnauthorizedRelayer", async function () {
    const fx = await deployFixture();
    const baseline = await recordBaseline(fx);
    await expect(
      fx.registry
        .connect(fx.admin)
        .recordCaseVerification(ORG_A, CASE_REF, EVENT_ID, 1n, COMMITMENT, 1)
    )
      .to.be.revertedWithCustomError(fx.registry, "UnauthorizedRelayer")
      .withArgs(ORG_A, fx.admin.address);
    await expectBaselineUnchanged(fx, baseline);
  });

  it("C01-T03: non-admin accounts, including an org relayer, cannot register, rotate or revoke organizations", async function () {
    const { registry, relayerA, stranger } = await deployFixture();
    const adminRole = await registry.DEFAULT_ADMIN_ROLE();

    await expect(registry.connect(stranger).revokeOrganizationRelayer(ORG_A))
      .to.be.revertedWithCustomError(registry, "AccessControlUnauthorizedAccount")
      .withArgs(stranger.address, adminRole);
    await expect(registry.connect(stranger).updateOrganizationRelayer(ORG_A, stranger.address))
      .to.be.revertedWithCustomError(registry, "AccessControlUnauthorizedAccount")
      .withArgs(stranger.address, adminRole);
    await expect(registry.connect(relayerA).registerOrganization(ORG_C, relayerA.address))
      .to.be.revertedWithCustomError(registry, "AccessControlUnauthorizedAccount")
      .withArgs(relayerA.address, adminRole);
    await expect(registry.connect(relayerA).revokeOrganizationRelayer(ORG_A))
      .to.be.revertedWithCustomError(registry, "AccessControlUnauthorizedAccount")
      .withArgs(relayerA.address, adminRole);

    const [registered, active, relayer] = await registry.getOrganization(ORG_A);
    expect([registered, active, relayer]).to.deep.equal([true, true, relayerA.address]);
    const [orgCRegistered] = await registry.getOrganization(ORG_C);
    expect(orgCRegistered).to.equal(false);
  });

  it("C01-T04: registering a zero relayer address is rejected", async function () {
    const { registry, admin } = await deployFixture();
    await expect(
      registry.connect(admin).registerOrganization(ORG_C, ZeroAddress)
    ).to.be.revertedWithCustomError(registry, "InvalidZeroInput");
    const [registered] = await registry.getOrganization(ORG_C);
    expect(registered).to.equal(false);
  });

  it("C01-T04: deactivated organization fails before duplicate lookup, even for an exact replay", async function () {
    const fx = await deployFixture();
    const baseline = await recordBaseline(fx);
    await fx.registry.connect(fx.admin).setOrganizationStatus(ORG_A, false);

    await expect(
      fx.registry
        .connect(fx.relayerA)
        .recordCaseVerification(ORG_A, CASE_REF, EVENT_ID, 1n, COMMITMENT, 1)
    )
      .to.be.revertedWithCustomError(fx.registry, "OrganizationNotActive")
      .withArgs(ORG_A);
    await expectBaselineUnchanged(fx, baseline);
  });

  it("C01-T06: revoked relayer replaying a stored eventId is rejected and loses RELAYER_ROLE", async function () {
    const fx = await deployFixture();
    const baseline = await recordBaseline(fx);
    const relayerRole = await fx.registry.RELAYER_ROLE();
    await fx.registry.connect(fx.admin).revokeOrganizationRelayer(ORG_A);

    expect(await fx.registry.hasRole(relayerRole, fx.relayerA.address)).to.equal(false);
    await expect(
      fx.registry
        .connect(fx.relayerA)
        .recordCaseVerification(ORG_A, CASE_REF, EVENT_ID, 1n, COMMITMENT, 1)
    )
      .to.be.revertedWithCustomError(fx.registry, "UnauthorizedRelayer")
      .withArgs(ORG_A, fx.relayerA.address);
    await expectBaselineUnchanged(fx, baseline);
  });

  it("C01-T06: after rotation the old relayer is rejected on replay while the new relayer sees the original record", async function () {
    const fx = await deployFixture();
    const baseline = await recordBaseline(fx);
    await fx.registry.connect(fx.admin).updateOrganizationRelayer(ORG_A, fx.stranger.address);

    await expect(
      fx.registry
        .connect(fx.relayerA)
        .recordCaseVerification(ORG_A, CASE_REF, EVENT_ID, 1n, COMMITMENT, 1)
    )
      .to.be.revertedWithCustomError(fx.registry, "UnauthorizedRelayer")
      .withArgs(ORG_A, fx.relayerA.address);
    await expect(
      fx.registry
        .connect(fx.stranger)
        .recordCaseVerification(ORG_A, CASE_REF, EVENT_ID, 1n, COMMITMENT, 1)
    )
      .to.be.revertedWithCustomError(fx.registry, "EventAlreadyRecorded")
      .withArgs(EVENT_ID);
    await expectBaselineUnchanged(fx, baseline);
  });

  it("C01-T07: chainSequence 2 on a fresh case is rejected in an isolated deployment", async function () {
    const { registry, relayerA } = await deployFixture();
    await expect(
      registry
        .connect(relayerA)
        .recordCaseVerification(ORG_A, CASE_REF, EVENT_ID, 2n, COMMITMENT, 1)
    )
      .to.be.revertedWithCustomError(registry, "InvalidChainSequence")
      .withArgs(CASE_REF, 1n, 2n);
    await expectNothingRecorded(registry, EVENT_ID, CASE_REF);
  });

  it("C01-T08: an already verified case rejects a fresh eventId with chainSequence 1", async function () {
    const fx = await deployFixture();
    const baseline = await recordBaseline(fx);
    await expect(
      fx.registry
        .connect(fx.relayerA)
        .recordCaseVerification(ORG_A, CASE_REF, EVENT_ID_2, 1n, COMMITMENT, 1)
    )
      .to.be.revertedWithCustomError(fx.registry, "CaseAlreadyVerified")
      .withArgs(CASE_REF);
    const [exists] = await fx.registry.getEventRecord(EVENT_ID_2);
    expect(exists).to.equal(false);
    await expectBaselineUnchanged(fx, baseline);
  });

  it("C01-T09: exact replay emits no second CaseVerified and keeps lastChainSequence at 1", async function () {
    const fx = await deployFixture();
    const baseline = await recordBaseline(fx);
    await expect(
      fx.registry
        .connect(fx.relayerA)
        .recordCaseVerification(ORG_A, CASE_REF, EVENT_ID, 1n, COMMITMENT, 1)
    )
      .to.be.revertedWithCustomError(fx.registry, "EventAlreadyRecorded")
      .withArgs(EVENT_ID);

    await expectBaselineUnchanged(fx, baseline);
    const [verified, , lastChainSequence] = await fx.registry.getCaseSummary(CASE_REF);
    expect(verified).to.equal(true);
    expect(lastChainSequence).to.equal(1n);
  });

  it("C01-T10: mutating caseRef or chainSequence under a stored eventId is an EventIdConflict, checked before case guards", async function () {
    const fx = await deployFixture();
    const baseline = await recordBaseline(fx);

    // Without the duplicate check this would succeed (fresh case), so EventIdConflict proves precedence.
    await expect(
      fx.registry
        .connect(fx.relayerA)
        .recordCaseVerification(ORG_A, CASE_REF_2, EVENT_ID, 1n, COMMITMENT, 1)
    )
      .to.be.revertedWithCustomError(fx.registry, "EventIdConflict")
      .withArgs(EVENT_ID);

    // Without the duplicate check this would hit CaseAlreadyVerified.
    await expect(
      fx.registry
        .connect(fx.relayerA)
        .recordCaseVerification(ORG_A, CASE_REF, EVENT_ID, 2n, COMMITMENT, 1)
    )
      .to.be.revertedWithCustomError(fx.registry, "EventIdConflict")
      .withArgs(EVENT_ID);

    await expectBaselineUnchanged(fx, baseline);
    const [case2Verified] = await fx.registry.getCaseSummary(CASE_REF_2);
    expect(case2Verified).to.equal(false);
  });

  it("C01-T10: unsupported schemaVersion on a stored eventId is a validation error, raised before duplicate lookup", async function () {
    const fx = await deployFixture();
    const baseline = await recordBaseline(fx);
    await expect(
      fx.registry
        .connect(fx.relayerA)
        .recordCaseVerification(ORG_A, CASE_REF, EVENT_ID, 1n, COMMITMENT, 2)
    )
      .to.be.revertedWithCustomError(fx.registry, "UnsupportedSchemaVersion")
      .withArgs(2);
    await expectBaselineUnchanged(fx, baseline);
  });

  it("C01-T11: reusing a stored eventId under an orgRef the caller cannot serve fails authorization first", async function () {
    const fx = await deployFixture();
    const baseline = await recordBaseline(fx);

    await expect(
      fx.registry
        .connect(fx.relayerA)
        .recordCaseVerification(ORG_B, CASE_REF, EVENT_ID, 1n, COMMITMENT, 1)
    )
      .to.be.revertedWithCustomError(fx.registry, "UnauthorizedRelayer")
      .withArgs(ORG_B, fx.relayerA.address);
    await expect(
      fx.registry
        .connect(fx.relayerA)
        .recordCaseVerification(ORG_UNKNOWN, CASE_REF, EVENT_ID, 1n, COMMITMENT, 1)
    )
      .to.be.revertedWithCustomError(fx.registry, "OrganizationNotActive")
      .withArgs(ORG_UNKNOWN);
    await expectBaselineUnchanged(fx, baseline);
  });

  it("C01-T12: zero orgRef fails the D49 step-1 organization guard (OrganizationNotActive), not InvalidZeroInput", async function () {
    // D49 C01-O05 evaluates organization/caller authorization before zero-input validation,
    // and orgRef 0x00..00 can never be registered, so the call fails closed at step 1.
    const { registry, relayerA } = await deployFixture();
    await expect(
      registry
        .connect(relayerA)
        .recordCaseVerification(ZERO, CASE_REF, EVENT_ID, 1n, COMMITMENT, 1)
    )
      .to.be.revertedWithCustomError(registry, "OrganizationNotActive")
      .withArgs(ZERO);
    await expectNothingRecorded(registry, EVENT_ID, CASE_REF);
  });

  it("C01-T12: out-of-range uint64/uint16 values are rejected by the ABI encoder before any transaction is sent", async function () {
    const { ethers, registry, relayerA } = await deployFixture();
    const iface = registry.interface as unknown as Interface;
    const blockBefore = await ethers.provider.getBlockNumber();

    const invalidArgs: Array<[bigint, bigint]> = [
      [UINT64_MAX + 1n, 1n],
      [-1n, 1n],
      [1n, UINT16_MAX + 1n],
    ];
    for (const [chainSequence, schemaVersion] of invalidArgs) {
      expect(() =>
        iface.encodeFunctionData("recordCaseVerification", [
          ORG_A, CASE_REF, EVENT_ID, chainSequence, COMMITMENT, schemaVersion,
        ])
      ).to.throw();
      await expectClientRejection(() =>
        registry
          .connect(relayerA)
          .recordCaseVerification(ORG_A, CASE_REF, EVENT_ID, chainSequence, COMMITMENT, schemaVersion)
      );
    }

    expect(await ethers.provider.getBlockNumber()).to.equal(blockBefore);
    await expectNothingRecorded(registry, EVENT_ID, CASE_REF);
  });

  it("C01-T12: in-range boundary values reach the contract guards (distinguishes contract revert from encoder rejection)", async function () {
    const { registry, relayerA } = await deployFixture();
    await expect(
      registry
        .connect(relayerA)
        .recordCaseVerification(ORG_A, CASE_REF, EVENT_ID, UINT64_MAX, COMMITMENT, 1)
    )
      .to.be.revertedWithCustomError(registry, "InvalidChainSequence")
      .withArgs(CASE_REF, 1n, UINT64_MAX);
    await expect(
      registry
        .connect(relayerA)
        .recordCaseVerification(ORG_A, CASE_REF, EVENT_ID, 1n, COMMITMENT, UINT16_MAX)
    )
      .to.be.revertedWithCustomError(registry, "UnsupportedSchemaVersion")
      .withArgs(UINT16_MAX);
    await expectNothingRecorded(registry, EVENT_ID, CASE_REF);
  });
});
