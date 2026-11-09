import { expect } from "chai";
import { network } from "hardhat";
import config, {
  EXPECTED_EVM_VERSION,
  EXPECTED_FUJI_CHAIN_ID,
  EXPECTED_OPTIMIZER_RUNS,
  EXPECTED_SOLIDITY_VERSION,
  validateExternalNetworkEnv,
} from "../hardhat.config.js";

describe("C02 Local Hardhat Toolchain Smoke", function () {
  it("pins Solidity 0.8.28, Paris EVM target, and optimizer runs=200", function () {
    const solidityConfig = config.solidity as {
      version: string;
      settings: {
        evmVersion: string;
        optimizer: { enabled: boolean; runs: number };
      };
    };

    expect(solidityConfig.version).to.equal(EXPECTED_SOLIDITY_VERSION);
    expect(solidityConfig.settings.evmVersion).to.equal(EXPECTED_EVM_VERSION);
    expect(solidityConfig.settings.optimizer.enabled).to.equal(true);
    expect(solidityConfig.settings.optimizer.runs).to.equal(EXPECTED_OPTIMIZER_RUNS);
  });

  it("provisions distinct admin, relayer, and unauthorized local test accounts and executes role-guarded smoke probe", async function () {
    const { ethers } = await network.getOrCreate();
    const [admin, relayer, unauthorizedCaller] = await ethers.getSigners();

    expect(admin.address).to.not.equal(ethers.ZeroAddress);
    expect(relayer.address).to.not.equal(ethers.ZeroAddress);
    expect(unauthorizedCaller.address).to.not.equal(ethers.ZeroAddress);

    expect(admin.address).to.not.equal(relayer.address);
    expect(admin.address).to.not.equal(unauthorizedCaller.address);
    expect(relayer.address).to.not.equal(unauthorizedCaller.address);

    const probe = await ethers.deployContract(
      "ToolchainSmokeProbe",
      [admin.address, relayer.address],
      admin
    );
    await probe.waitForDeployment();

    const relayerRole = await probe.RELAYER_ROLE();
    const adminRole = await probe.DEFAULT_ADMIN_ROLE();

    expect(await probe.hasRole(adminRole, admin.address)).to.equal(true);
    expect(await probe.hasRole(relayerRole, admin.address)).to.equal(false);
    expect(await probe.hasRole(relayerRole, relayer.address)).to.equal(true);
    expect(await probe.hasRole(relayerRole, unauthorizedCaller.address)).to.equal(false);

    await expect(
      probe.connect(unauthorizedCaller).recordSmokeCheck()
    ).to.be.revertedWithCustomError(probe, "AccessControlUnauthorizedAccount");

    await expect(
      probe.connect(admin).recordSmokeCheck()
    ).to.be.revertedWithCustomError(probe, "AccessControlUnauthorizedAccount");

    await expect(probe.connect(relayer).recordSmokeCheck())
      .to.emit(probe, "SmokeCheckRecorded")
      .withArgs(relayer.address, 31337n);
  });

  it("rejects wrong chain ID, missing RPC URL, missing secrets, and non-distinct admin/relayer keys", function () {
    const validKeyA = "0x" + "11".repeat(32);
    const validKeyB = "0x" + "22".repeat(32);

    expect(() =>
      validateExternalNetworkEnv({
        REQUIRE_FUJI_CONFIG: "false",
        FUJI_CHAIN_ID: "1",
      })
    ).to.not.throw();

    expect(() =>
      validateExternalNetworkEnv({
        REQUIRE_FUJI_CONFIG: "true",
        FUJI_CHAIN_ID: "1",
        FUJI_RPC_URL: "https://api.avax-test.network/ext/bc/C/rpc",
        FUJI_DEPLOYER_PRIVATE_KEY: validKeyA,
        FUJI_RELAYER_PRIVATE_KEY: validKeyB,
      })
    ).to.throw("INVALID_CHAIN_ID");

    expect(() =>
      validateExternalNetworkEnv({
        REQUIRE_FUJI_CONFIG: "true",
        FUJI_CHAIN_ID: String(EXPECTED_FUJI_CHAIN_ID),
        FUJI_RPC_URL: "http://localhost:8545",
        FUJI_DEPLOYER_PRIVATE_KEY: validKeyA,
        FUJI_RELAYER_PRIVATE_KEY: validKeyB,
      })
    ).to.throw("MISSING_RPC_URL");

    expect(() =>
      validateExternalNetworkEnv({
        REQUIRE_FUJI_CONFIG: "true",
        FUJI_CHAIN_ID: String(EXPECTED_FUJI_CHAIN_ID),
        FUJI_RPC_URL: "https://api.avax-test.network/ext/bc/C/rpc",
        FUJI_DEPLOYER_PRIVATE_KEY: validKeyA,
      })
    ).to.throw("MISSING_OR_INVALID_SECRET");

    expect(() =>
      validateExternalNetworkEnv({
        REQUIRE_FUJI_CONFIG: "true",
        FUJI_CHAIN_ID: String(EXPECTED_FUJI_CHAIN_ID),
        FUJI_RPC_URL: "https://api.avax-test.network/ext/bc/C/rpc",
        FUJI_DEPLOYER_PRIVATE_KEY: validKeyA,
        FUJI_RELAYER_PRIVATE_KEY: validKeyA,
      })
    ).to.throw("KEY_SEPARATION_VIOLATION");

    expect(() =>
      validateExternalNetworkEnv({
        REQUIRE_FUJI_CONFIG: "true",
        FUJI_CHAIN_ID: String(EXPECTED_FUJI_CHAIN_ID),
        FUJI_RPC_URL: "https://api.avax-test.network/ext/bc/C/rpc",
        FUJI_DEPLOYER_PRIVATE_KEY: validKeyA,
        FUJI_RELAYER_PRIVATE_KEY: validKeyB,
      })
    ).to.not.throw();
  });
});
