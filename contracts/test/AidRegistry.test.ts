import { expect } from "chai";
import { network } from "hardhat";

describe("C03 AidRegistry Verification Slice - Part 1 (Happy Path & Fixture Parity)", function () {
  const ORG_REF =
    "0x1010101010101010101010101010101010101010101010101010101010101010";
  const CASE_REF =
    "0x2020202020202020202020202020202020202020202020202020202020202020";
  const EVENT_ID =
    "0x3030303030303030303030303030303030303030303030303030303030303030";
  const CHAIN_SEQUENCE = 1n;
  const SCHEMA_VERSION = 1;

  // Golden-vector candidate commitment & digest verified via docs/fixtures/c01/verify-c01.mjs --ethers
  const GOLDEN_EVIDENCE_COMMITMENT =
    "0x0a00e80664506e74cbc0fa19c4e17cf3fdace8c2d70e5717b08c6804dc5c8882";
  const EXPECTED_OPERATION_SELECTOR = "0x1bd1fadf";
  const EXPECTED_CASE_VERIFIED_TOPIC0 =
    "0xc52bfb1c97b1de28337fe01b8bf6dce1267ee4b6f337c271935032cdf0629ea2";
  const EXPECTED_PAYLOAD_DIGEST =
    "0xcff6ea47eb121a61875dceef9200e998d5096f69d9fb7e7435ee6b2b8f74b1f8";

  it("SC01: allows DEFAULT_ADMIN_ROLE to register an organization, grants RELAYER_ROLE to its relayer, and reads back state", async function () {
    const { ethers } = await network.getOrCreate();
    const [admin, orgRelayer, otherAccount] = await ethers.getSigners();

    const registry = await ethers.deployContract(
      "AidRegistry",
      [admin.address],
      admin
    );
    await registry.waitForDeployment();

    const adminRole = await registry.DEFAULT_ADMIN_ROLE();
    const relayerRole = await registry.RELAYER_ROLE();

    // Admin has DEFAULT_ADMIN_ROLE only, NOT RELAYER_ROLE
    expect(await registry.hasRole(adminRole, admin.address)).to.equal(true);
    expect(await registry.hasRole(relayerRole, admin.address)).to.equal(false);
    expect(await registry.hasRole(relayerRole, orgRelayer.address)).to.equal(
      false
    );

    const tx = await registry
      .connect(admin)
      .registerOrganization(ORG_REF, orgRelayer.address);
    const receipt = await tx.wait();
    expect(receipt).to.not.equal(null);

    const block = await ethers.provider.getBlock(receipt!.blockNumber);
    await expect(tx)
      .to.emit(registry, "OrganizationRegistered")
      .withArgs(ORG_REF, orgRelayer.address, BigInt(block!.timestamp));

    expect(await registry.hasRole(relayerRole, orgRelayer.address)).to.equal(
      true
    );
    expect(await registry.hasRole(relayerRole, otherAccount.address)).to.equal(
      false
    );

    const [registered, active, storedRelayer] =
      await registry.getOrganization(ORG_REF);
    expect(registered).to.equal(true);
    expect(active).to.equal(true);
    expect(storedRelayer).to.equal(orgRelayer.address);
  });

  it("C01-T01 (SC01/SC02/SC03/SC10, BR12-P): records case verification for authorized org relayer with exact ABI log layout and golden digest parity", async function () {
    const { ethers } = await network.getOrCreate();
    const [admin, orgRelayer] = await ethers.getSigners();

    const registry = await ethers.deployContract(
      "AidRegistry",
      [admin.address],
      admin
    );
    await registry.waitForDeployment();

    await registry
      .connect(admin)
      .registerOrganization(ORG_REF, orgRelayer.address);

    const tx = await registry
      .connect(orgRelayer)
      .recordCaseVerification(
        ORG_REF,
        CASE_REF,
        EVENT_ID,
        CHAIN_SEQUENCE,
        GOLDEN_EVIDENCE_COMMITMENT,
        SCHEMA_VERSION
      );
    const receipt = await tx.wait();
    expect(receipt).to.not.equal(null);
    expect(receipt!.status).to.equal(1);

    const block = await ethers.provider.getBlock(receipt!.blockNumber);
    const expectedTimestamp = BigInt(block!.timestamp);

    await expect(tx)
      .to.emit(registry, "CaseVerified")
      .withArgs(
        ORG_REF,
        CASE_REF,
        EVENT_ID,
        CHAIN_SEQUENCE,
        GOLDEN_EVIDENCE_COMMITMENT,
        SCHEMA_VERSION,
        expectedTimestamp
      );

    // Verify raw log topics count (4) and data length (128 bytes = 256 hex chars + '0x')
    const caseVerifiedLogs = receipt!.logs.filter(
      (log) => log.topics[0] === EXPECTED_CASE_VERIFIED_TOPIC0
    );
    expect(caseVerifiedLogs.length).to.equal(1);

    const rawLog = caseVerifiedLogs[0];
    expect(rawLog.topics.length).to.equal(4);
    expect(rawLog.topics[0]).to.equal(EXPECTED_CASE_VERIFIED_TOPIC0);
    expect(rawLog.topics[1]).to.equal(ORG_REF);
    expect(rawLog.topics[2]).to.equal(CASE_REF);
    expect(rawLog.topics[3]).to.equal(EVENT_ID);
    expect((rawLog.data.length - 2) / 2).to.equal(128);

    // Verify getEventRecord flat outputs & operation-bound payloadDigest
    const [
      exists,
      operation,
      recordOrgRef,
      recordCaseRef,
      recordChainSequence,
      recordCommitment,
      recordSchemaVersion,
      payloadDigest,
    ] = await registry.getEventRecord(EVENT_ID);

    expect(exists).to.equal(true);
    expect(operation).to.equal(EXPECTED_OPERATION_SELECTOR);
    expect(recordOrgRef).to.equal(ORG_REF);
    expect(recordCaseRef).to.equal(CASE_REF);
    expect(recordChainSequence).to.equal(CHAIN_SEQUENCE);
    expect(recordCommitment).to.equal(GOLDEN_EVIDENCE_COMMITMENT);
    expect(recordSchemaVersion).to.equal(BigInt(SCHEMA_VERSION));
    expect(payloadDigest).to.equal(EXPECTED_PAYLOAD_DIGEST);

    // Verify getCaseSummary flat outputs
    const [verified, openDisputeCount, lastChainSequence] =
      await registry.getCaseSummary(CASE_REF);
    expect(verified).to.equal(true);
    expect(openDisputeCount).to.equal(0n);
    expect(lastChainSequence).to.equal(1n);
  });
});