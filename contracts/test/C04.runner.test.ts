import { expect } from "chai";
import { network } from "hardhat";
// @ts-ignore JavaScript deployment module is shared with the operator CLI.
import { prepare, execute, artifact, addresses, validateArtifact } from "../scripts/deployment.mjs";
async function rejection(action: () => Promise<unknown>, message: string) {
  try { await action(); } catch (e) { expect((e as Error).message).to.equal(message); return; }
  throw Error("Expected rejection: " + message);
}
describe("C04 deployment runner", function () {
  it("rejects bytecode or ABI drift from the reviewed artifact", function () {
    const a=artifact();
    expect(()=>validateArtifact({...a,bytecode:"0x00"})).to.throw("UNREVIEWED_ARTIFACT");
    expect(()=>validateArtifact({...a,abi:[]})).to.throw("UNREVIEWED_ARTIFACT");
  });
  it("rejects mainnet before any estimate or transaction", async function () {
    await rejection(() => prepare({ send: async () => "0xa86a" }, {}), "WRONG_CHAIN");
  });
  it("rejects missing/zero constructor addresses and relayer/admin overlap", function () {
    expect(() => addresses({})).to.throw("MISSING_PUBLIC_ADDRESSES");
    expect(() => addresses({deployer:"0x0000000000000000000000000000000000000000",admin:"0x0000000000000000000000000000000000000000",relayer:"0x1111111111111111111111111111111111111111"})).to.throw("ZERO_ADDRESS");
    expect(() => addresses({deployer:"0x1111111111111111111111111111111111111111",admin:"0x1111111111111111111111111111111111111111",relayer:"0x1111111111111111111111111111111111111111"})).to.throw("KEY_SEPARATION_VIOLATION");
  });
  it("rejects insufficient balance, excessive fees and gas", async function () {
    const config={deployer:"0x1111111111111111111111111111111111111111",admin:"0x1111111111111111111111111111111111111111",relayer:"0x2222222222222222222222222222222222222222"};
    const p={send:async()=>"0xa869",getCode:async()=>"0x",getBalance:async()=>0n,getFeeData:async()=>({gasPrice:1000000000n}),estimateGas:async()=>1000000n};
    await rejection(()=>prepare(p,config),"INSUFFICIENT_BALANCE_TARGET_0_1_AVAX");
    await rejection(()=>prepare({...p,getFeeData:async()=>({gasPrice:26000000000n})},config),"FEE_CAP_EXCEEDED");
    await rejection(()=>prepare({...p,getBalance:async()=>10n**18n,estimateGas:async()=>4000000n},config),"GAS_CAP_EXCEEDED");
  });
  it("dry-runs the actual deploy + registration runner with local accounts and checks wrong signer", async function () {
    const { ethers }=await network.getOrCreate();
    const [deployer,relayer,other]=await ethers.getSigners();
    const config={deployer:deployer.address,admin:deployer.address,relayer:relayer.address};
    const plan=await prepare(ethers.provider,config,31337n);
    await rejection(()=>execute(ethers.provider,other,plan,"0x"+"71".repeat(32),31337n),"SIGNER_ADDRESS_MISMATCH");
    await rejection(()=>execute(ethers.provider,null,plan,"0x"+"71".repeat(32),31337n),"MISSING_SIGNER");
    console.log("LOCAL deployment estimate / gasLimit",plan.estimate.toString(),plan.gasLimit.toString());
    const events:unknown[]=[];
    const result=await execute(ethers.provider,deployer,plan,"0x"+"71".repeat(32),31337n,(e:unknown)=>events.push(e));
    expect(result.chainId).to.equal("31337");expect(result.explorerSourceStatus).to.equal("NOT_VERIFIED");expect(events.length).to.equal(2);
    const registry=await ethers.getContractAt("AidRegistry",result.address);
    expect(await registry.hasRole(await registry.DEFAULT_ADMIN_ROLE(),deployer.address)).to.equal(true);
    expect(artifact().deployedBytecode).to.equal(await ethers.provider.getCode(result.address));
  });
});
