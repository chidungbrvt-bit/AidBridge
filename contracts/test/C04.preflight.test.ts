import { expect } from "chai";
import { network } from "hardhat";
import { readFileSync } from "node:fs";

describe("C04 local deployment dry-run (never Fuji)", function () {
  it("deploys current artifact, checks runtime and constructor admin, then reads back org authorization", async function () {
    const { ethers } = await network.getOrCreate();
    expect((await ethers.provider.getNetwork()).chainId).to.equal(31337n);
    const [deployer, admin, relayer] = await ethers.getSigners();
    const registry = await ethers.deployContract("AidRegistry", [admin.address], deployer);
    const receipt = await registry.deploymentTransaction()!.wait();
    expect(receipt!.status).to.equal(1);
    const artifact = JSON.parse(readFileSync(new URL("../artifacts/contracts/AidRegistry.sol/AidRegistry.json", import.meta.url), "utf8"));
    expect(await ethers.provider.getCode(await registry.getAddress())).to.equal(artifact.deployedBytecode);
    const role = await registry.DEFAULT_ADMIN_ROLE();
    expect(await registry.hasRole(role, admin.address)).to.equal(true);
    expect(await registry.hasRole(role, deployer.address)).to.equal(false);
    const orgRef = "0x" + "70".repeat(32);
    await registry.connect(admin).registerOrganization(orgRef, relayer.address);
    expect([...(await registry.getOrganization(orgRef))]).to.deep.equal([true, true, relayer.address]);
    expect(await registry.hasRole(await registry.RELAYER_ROLE(), relayer.address)).to.equal(true);
    await expect(ethers.deployContract("AidRegistry", [ethers.ZeroAddress], deployer)).to.be.revertedWithCustomError(registry, "InvalidZeroInput");
  });
});
