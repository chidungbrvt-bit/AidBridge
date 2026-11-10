import { expect } from "chai";
import { network } from "hardhat";

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

async function deployFixture() {
  const { ethers } = await network.getOrCreate();
  const [admin, relayerA, relayerB, stranger] = await ethers.getSigners();
  const registry = await ethers.deployContract("AidRegistry", [admin.address], admin);
  await registry.waitForDeployment();
  await registry.connect(admin).registerOrganization(ORG_A, relayerA.address);
  await registry.connect(admin).registerOrganization(ORG_B, relayerB.address);
  return { registry, admin, relayerA, relayerB, stranger };
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