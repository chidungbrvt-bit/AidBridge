import { expect } from "chai";
import { network } from "hardhat";
import { AbiCoder, hexlify, keccak256, toUtf8Bytes, type Interface } from "ethers";

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

describe("C03 AidRegistry Part 2 - Privacy boundary and ABI allowlist (C01-T13, SC10/SC11, BR08-P/N)", function () {
  const ORG_REF =
    "0x1010101010101010101010101010101010101010101010101010101010101010";
  const CASE_REF =
    "0x2020202020202020202020202020202020202020202020202020202020202020";
  const EVENT_ID =
    "0x3030303030303030303030303030303030303030303030303030303030303030";

  // Only fixed-size ABI types may cross the public boundary; dynamic `string`/`bytes`, arrays and tuples could carry PII.
  const FIXED_SIZE_TYPE = /^(bytes([1-9]|[12][0-9]|3[0-2])|uint(8|16|32|64|128|256)|address|bool)$/;

  async function deployWithRelayer() {
    const { ethers } = await network.getOrCreate();
    const [admin, orgRelayer, stranger] = await ethers.getSigners();
    const registry = await ethers.deployContract("AidRegistry", [admin.address], admin);
    await registry.waitForDeployment();
    await registry.connect(admin).registerOrganization(ORG_REF, orgRelayer.address);
    return { ethers, registry, admin, orgRelayer, stranger };
  }

  it("C01-T13: ABI exposes only fixed-size inputs, no payable function, no receive/fallback, and the exact CaseVerified envelope", async function () {
    const { registry } = await deployWithRelayer();
    const iface = registry.interface as unknown as Interface;

    const offending: string[] = [];
    iface.forEachFunction((fn) => {
      if (fn.payable || fn.stateMutability === "payable") offending.push(`payable function ${fn.format()}`);
      for (const input of fn.inputs) {
        if (!FIXED_SIZE_TYPE.test(input.type)) offending.push(`function ${fn.name}.${input.name}: ${input.type}`);
      }
    });
    iface.forEachEvent((ev) => {
      for (const input of ev.inputs) {
        if (!FIXED_SIZE_TYPE.test(input.type)) offending.push(`event ${ev.name}.${input.name}: ${input.type}`);
      }
    });
    iface.forEachError((err) => {
      for (const input of err.inputs) {
        if (!FIXED_SIZE_TYPE.test(input.type)) offending.push(`error ${err.name}.${input.name}: ${input.type}`);
      }
    });
    expect(offending).to.deep.equal([]);
    expect(iface.receive).to.equal(false);
    expect(iface.fallback).to.equal(null);

    const write = iface.getFunction("recordCaseVerification")!;
    expect(write.stateMutability).to.equal("nonpayable");
    expect(write.outputs.length).to.equal(0);
    expect(write.inputs.map((p) => `${p.name}:${p.type}`)).to.deep.equal([
      "orgRef:bytes32",
      "caseRef:bytes32",
      "eventId:bytes32",
      "chainSequence:uint64",
      "evidenceCommitment:bytes32",
      "schemaVersion:uint16",
    ]);

    const caseVerified = iface.getEvent("CaseVerified")!;
    expect(caseVerified.anonymous).to.equal(false);
    expect(caseVerified.inputs.map((p) => `${p.name}:${p.type}:${p.indexed}`)).to.deep.equal([
      "orgRef:bytes32:true",
      "caseRef:bytes32:true",
      "eventId:bytes32:true",
      "chainSequence:uint64:false",
      "evidenceCommitment:bytes32:false",
      "schemaVersion:uint16:false",
      "timestamp:uint64:false",
    ]);
  });

  it("C01-T13: sending native value to the registry reverts and the balance stays zero", async function () {
    const { ethers, registry, orgRelayer, stranger } = await deployWithRelayer();
    const registryAddress = await registry.getAddress();
    const calldata = registry.interface.encodeFunctionData("recordCaseVerification", [
      ORG_REF, CASE_REF, EVENT_ID, 1n, "0x" + "40".repeat(32), 1,
    ]);

    const attempts = [
      () => stranger.sendTransaction({ to: registryAddress, value: 1n }),
      // Valid calldata from the authorized relayer, but with value attached to a nonpayable function.
      () => orgRelayer.sendTransaction({ to: registryAddress, data: calldata, value: 1n }),
    ];
    for (const attempt of attempts) {
      let failed = false;
      try {
        const tx = await attempt();
        await tx.wait();
      } catch {
        failed = true;
      }
      expect(failed, "value transfer to AidRegistry must revert").to.equal(true);
    }

    expect(await ethers.provider.getBalance(registryAddress)).to.equal(0n);
    const [exists] = await registry.getEventRecord(EVENT_ID);
    expect(exists).to.equal(false);
  });

  it("C01-T13: calldata and the emitted log carry only the allowlisted envelope; injected private markers never appear", async function () {
    const { ethers, registry, orgRelayer } = await deployWithRelayer();
    const registryAddress = await registry.getAddress();
    const { chainId } = await ethers.provider.getNetwork();
    const abiCoder = AbiCoder.defaultAbiCoder();

    // Synthetic private fixture (off-chain only). Markers are fictional strings used to detect leaks.
    const privateFixture = {
      beneficiaryName: "MARKER-PII-Demo Household A",
      phone: "MARKER-PII-+84-000-000-000",
      privateEvidenceUrl: "/api/evidence-versions/MARKER-PRIVATE-URL/content",
      manifestDigestSha256: "0x" + "61".repeat(32),
      salt: "0x" + "60".repeat(32), // fixed TEST-ONLY salt, never used for real records
    };

    // D49 C01-O03 commitment: keccak256(abi.encode(domainV1, chainId, registry, caseRef, eventId, manifestDigest, salt)).
    const domainV1 = keccak256(toUtf8Bytes("AIDBRIDGE_EVIDENCE_V1"));
    const evidenceCommitment = keccak256(
      abiCoder.encode(
        ["bytes32", "uint256", "address", "bytes32", "bytes32", "bytes32", "bytes32"],
        [domainV1, chainId, registryAddress, CASE_REF, EVENT_ID, privateFixture.manifestDigestSha256, privateFixture.salt]
      )
    );
    const envelope = [ORG_REF, CASE_REF, EVENT_ID, 1n, evidenceCommitment, 1n] as const;

    const calldata = registry.interface.encodeFunctionData("recordCaseVerification", [...envelope]);
    // 4-byte selector + 6 static ABI words = 196 bytes.
    expect((calldata.length - 2) / 2).to.equal(4 + 6 * 32);
    expect(calldata.slice(0, 10)).to.equal("0x1bd1fadf");

    const tx = await registry
      .connect(orgRelayer)
      .recordCaseVerification(ORG_REF, CASE_REF, EVENT_ID, 1n, evidenceCommitment, 1);
    const receipt = await tx.wait();
    expect(receipt!.status).to.equal(1);

    const minedTx = await ethers.provider.getTransaction(tx.hash);
    expect(minedTx!.data).to.equal(calldata);
    expect(minedTx!.value).to.equal(0n);
    expect([...registry.interface.decodeFunctionData("recordCaseVerification", minedTx!.data)]).to.deep.equal([...envelope]);

    // Exactly one log, from the registry, with 4 topics and 4 data words.
    expect(receipt!.logs.length).to.equal(1);
    const [log] = receipt!.logs;
    expect(log.address).to.equal(registryAddress);
    expect(log.topics.length).to.equal(4);
    expect(log.topics.slice(1)).to.deep.equal([ORG_REF, CASE_REF, EVENT_ID]);
    expect((log.data.length - 2) / 2).to.equal(128);
    const block = await ethers.provider.getBlock(receipt!.blockNumber);
    expect([...abiCoder.decode(["uint64", "bytes32", "uint16", "uint64"], log.data)]).to.deep.equal([
      1n,
      evidenceCommitment,
      1n,
      BigInt(block!.timestamp),
    ]);

    // No private marker, salt or manifest digest may appear in calldata or log bytes.
    const forbiddenHex = [
      hexlify(toUtf8Bytes(privateFixture.beneficiaryName)),
      hexlify(toUtf8Bytes(privateFixture.phone)),
      hexlify(toUtf8Bytes(privateFixture.privateEvidenceUrl)),
      hexlify(toUtf8Bytes("MARKER-")),
      privateFixture.manifestDigestSha256,
      privateFixture.salt,
    ].map((hex) => hex.slice(2).toLowerCase());
    const publicBytes = [minedTx!.data, log.data, ...log.topics].map((hex) => hex.toLowerCase());
    for (const marker of forbiddenHex) {
      for (const bytes of publicBytes) {
        expect(bytes.includes(marker), `private marker ${marker.slice(0, 16)}... leaked`).to.equal(false);
      }
    }
  });
});
