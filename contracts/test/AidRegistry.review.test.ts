import { expect } from "chai";
import { network } from "hardhat";
const ref = (n: string) => "0x" + n.repeat(32);
const A = ref("10"), B = ref("11"), C = ref("12");
async function fixture(shared = false) {
  const { ethers } = await network.getOrCreate();
  const [admin, relayer, stranger] = await ethers.getSigners();
  const registry = await ethers.deployContract("AidRegistry", [admin.address], admin);
  await registry.waitForDeployment();
  await registry.registerOrganization(A, relayer.address);
  if (shared) await registry.registerOrganization(B, relayer.address);
  const role = await registry.RELAYER_ROLE();
  return { registry, admin, relayer, stranger, role };
}
async function write(registry: any, actor: any, org = A) {
  return registry.connect(actor).recordCaseVerification(org, ref("20"), ref("30"), 1n, ref("40"), 1);
}
// Regression tests for the user-confirmed persistent global withdrawal policy.
describe("C03 independent review: direct AccessControl lifecycle", function () {
  for (const action of ["revoke", "renounce"] as const) {
    it(`${action}: shared relayer stays denied after one org is reactivated`, async function () {
      const { registry, relayer, role } = await fixture(true);
      if (action === "revoke") await registry.revokeRole(role, relayer.address);
      else await registry.connect(relayer).renounceRole(role, relayer.address);
      await registry.setOrganizationStatus(A, false);
      await registry.setOrganizationStatus(A, true);
      const [registered, active, assigned] = await registry.getOrganization(A);
      expect([registered, active, assigned]).to.deep.equal([true, true, relayer.address]);
      expect(await registry.hasRole(role, relayer.address)).to.equal(false);
      await expect(write(registry, relayer)).to.be.revertedWithCustomError(registry, "UnauthorizedRelayer");
      expect((await registry.getEventRecord(ref("30")))[0]).to.equal(false);
      // Explicit grant restores service; this is an admin action, not an automatic recovery.
      await registry.grantRole(role, relayer.address);
      await expect(write(registry, relayer)).to.emit(registry, "CaseVerified");
    });
    it(`${action}: single-org cycles never restore withdrawn permission`, async function () {
      const { registry, relayer, role } = await fixture();
      if (action === "revoke") await registry.revokeRole(role, relayer.address);
      else await registry.connect(relayer).renounceRole(role, relayer.address);
      await expect(write(registry, relayer)).to.be.revertedWithCustomError(registry, "UnauthorizedRelayer");
      for (let cycle = 0; cycle < 3; cycle++) {
        await registry.setOrganizationStatus(A, false);
        await registry.setOrganizationStatus(A, true);
        expect(await registry.hasRole(role, relayer.address)).to.equal(false);
        await expect(write(registry, relayer)).to.be.revertedWithCustomError(registry, "UnauthorizedRelayer");
      }
      await registry.grantRole(role, relayer.address);
      await expect(write(registry, relayer)).to.emit(registry, "CaseVerified");
    });
  }
  it("direct grant never bypasses organization scope or revocation of the org assignment", async function () {
    const { registry, relayer, stranger, role } = await fixture();
    await registry.grantRole(role, stranger.address);
    await expect(write(registry, stranger)).to.be.revertedWithCustomError(registry, "UnauthorizedRelayer");
    await registry.revokeOrganizationRelayer(A);
    await registry.grantRole(role, relayer.address);
    await expect(write(registry, relayer)).to.be.revertedWithCustomError(registry, "UnauthorizedRelayer");
    expect((await registry.getEventRecord(ref("30")))[0]).to.equal(false);
  });
  it("non-admin cannot grant/revoke; renounce can only target the caller", async function () {
    const { registry, relayer, stranger, role } = await fixture();
    await expect(registry.connect(stranger).grantRole(role, stranger.address)).to.be.revertedWithCustomError(registry, "AccessControlUnauthorizedAccount");
    await expect(registry.connect(stranger).revokeRole(role, relayer.address)).to.be.revertedWithCustomError(registry, "AccessControlUnauthorizedAccount");
    await expect(registry.connect(stranger).renounceRole(role, relayer.address)).to.be.revertedWithCustomError(registry, "AccessControlBadConfirmation");
    await expect(write(registry, relayer)).to.emit(registry, "CaseVerified");
  });
  it("registration with an already serving globally revoked relayer does not restore its role", async function () {
    const { registry, relayer, role } = await fixture();
    await registry.revokeRole(role, relayer.address);
    await registry.registerOrganization(C, relayer.address);
    expect(await registry.hasRole(role, relayer.address)).to.equal(false);
    await expect(write(registry, relayer, C)).to.be.revertedWithCustomError(registry, "UnauthorizedRelayer");
  });
  for (const shared of [false, true]) {
    it(`all-org cycles and rotation preserve withdrawal until explicit grant (shared=${shared})`, async function () {
      const { registry, relayer, stranger, role } = await fixture(shared);
      for (let cycle = 0; cycle < 3; cycle++) {
        await registry.revokeRole(role, relayer.address);
        await registry.setOrganizationStatus(A, false);
        if (shared) await registry.setOrganizationStatus(B, false);
        await registry.revokeRole(role, relayer.address);
        await registry.setOrganizationStatus(A, true);
        if (shared) await registry.setOrganizationStatus(B, true);
        await registry.updateOrganizationRelayer(A, stranger.address);
        await registry.updateOrganizationRelayer(A, relayer.address);
        expect(await registry.hasRole(role, relayer.address)).to.equal(false);
        await expect(write(registry, relayer)).to.be.revertedWithCustomError(registry, "UnauthorizedRelayer");
        await expect(registry.connect(stranger).grantRole(role, relayer.address)).to.be.revertedWithCustomError(registry, "AccessControlUnauthorizedAccount");
        expect(await registry.hasRole(role, relayer.address)).to.equal(false);
        await registry.grantRole(role, relayer.address);
        await registry.grantRole(role, relayer.address);
        expect(await registry.hasRole(role, relayer.address)).to.equal(true);
      }
      await expect(write(registry, relayer)).to.emit(registry, "CaseVerified");
    });
  }
  it("org-specific revoke preserves the other organization's writer", async function () {
    const { registry, relayer, role } = await fixture(true);
    await registry.revokeOrganizationRelayer(A);
    expect(await registry.hasRole(role, relayer.address)).to.equal(true);
    await expect(write(registry, relayer, A)).to.be.revertedWithCustomError(registry, "UnauthorizedRelayer");
    await expect(write(registry, relayer, B)).to.emit(registry, "CaseVerified");
  });
  it("renounce without assignment survives registration until explicit grant", async function () {
    const { registry, stranger, role } = await fixture();
    await registry.connect(stranger).renounceRole(role, stranger.address);
    await registry.registerOrganization(C, stranger.address);
    expect(await registry.hasRole(role, stranger.address)).to.equal(false);
    await registry.grantRole(role, stranger.address);
    await expect(write(registry, stranger, C)).to.emit(registry, "CaseVerified");
  });
});
