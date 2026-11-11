import { readFileSync } from 'node:fs';
import { ContractFactory, getAddress, keccak256, parseUnits, ZeroAddress, getCreateAddress } from 'ethers';
import { createHash } from 'node:crypto';
export const FUJI_RPC='https://api.avax-test.network/ext/bc/C/rpc';
export const CREATION_HASH='0x595854a953b927a241515105022d9aa15f4ebb3eee41daa482d9e59c3f5642fb';
export const RUNTIME_HASH='0x4c778ba83052da23d357828dca603bddb14c6e26a0fdbda94143cafb2a39e7ad';
export const ABI_HASH='8042b783190a40d0843157dca4e273528bc49176ad134011e48cbb97aef340be';
export const FUNDING_TARGET=parseUnits('0.1',18);
export function validateArtifact(a) {
 if(keccak256(a.bytecode)!==CREATION_HASH || keccak256(a.deployedBytecode)!==RUNTIME_HASH || createHash('sha256').update(JSON.stringify(a.abi)).digest('hex')!==ABI_HASH)throw Error('UNREVIEWED_ARTIFACT');
 return a;
}
export function artifact() {
 const source=readFileSync(new URL('../contracts/AidRegistry.sol',import.meta.url),'utf8').replaceAll('\r\n','\n');
 if(createHash('sha256').update(source).digest('hex')!=="7154cf04f21198cfed6d4930ce7fb7243bef9a2f8d5ecda44c7dcd00dd66735a")throw Error('UNREVIEWED_SOURCE');
 const a=JSON.parse(readFileSync(new URL('../artifacts/contracts/AidRegistry.sol/AidRegistry.json',import.meta.url),'utf8'));
 if(keccak256(a.bytecode)!==CREATION_HASH || keccak256(a.deployedBytecode)!==RUNTIME_HASH || createHash('sha256').update(JSON.stringify(a.abi)).digest('hex')!==ABI_HASH)throw Error('UNREVIEWED_ARTIFACT');
 return validateArtifact(a);
}
export function addresses(config) {
 if(!config?.deployer||!config?.admin||!config?.relayer)throw Error('MISSING_PUBLIC_ADDRESSES');
 const c=Object.fromEntries(Object.entries(config).map(([k,v])=>[k,getAddress(v)]));
 if(Object.values(c).includes(ZeroAddress))throw Error('ZERO_ADDRESS');
 if(c.relayer===c.admin||c.relayer===c.deployer)throw Error('KEY_SEPARATION_VIOLATION');
 // Current runner performs org setup with the same dedicated deploy/admin wallet.
 if(c.admin!==c.deployer)throw Error('ADMIN_SIGNER_NOT_SUPPORTED');
 return c;
}
export async function chain(provider,expected=43113n) {
 if(expected!==43113n&&expected!==31337n)throw Error('WRONG_CHAIN');
 if(BigInt(await provider.send('eth_chainId',[]))!==expected)throw Error('WRONG_CHAIN');
}
export async function prepare(provider,config,expected=43113n) {
 await chain(provider,expected);
 const c=addresses(config),a=artifact();
 for(const address of [c.deployer,c.relayer])if(await provider.getCode(address)!=='0x')throw Error('EOA_REQUIRED');
 const balance=await provider.getBalance(c.deployer);
 const relayerBalance=await provider.getBalance(c.relayer);
 const factory=new ContractFactory(a.abi,a.bytecode);
 const unsigned=await factory.getDeployTransaction(c.admin);
 const gasPrice=(await provider.getFeeData()).gasPrice;
 if(gasPrice===null)throw Error('MISSING_FEE_DATA');
 const price=gasPrice*12n/10n>parseUnits('1',9)?gasPrice*12n/10n:parseUnits('1',9);
 if(price>parseUnits('25',9))throw Error('FEE_CAP_EXCEEDED');
 if(balance<FUNDING_TARGET)throw Error('INSUFFICIENT_BALANCE_TARGET_0_1_AVAX');
 const estimate=await provider.estimateGas({...unsigned,from:c.deployer});
 const gasLimit=estimate*120n/100n;
 if(gasLimit>3000000n)throw Error('GAS_CAP_EXCEEDED');
 const nonce=await provider.getTransactionCount(c.deployer,'pending');
 if(nonce!==await provider.getTransactionCount(c.deployer,'latest'))throw Error('PENDING_SIGNER_TRANSACTION');
 return {config:c,unsigned,estimate,gasLimit,gasPrice:price,balance,relayerBalance,nonce,predictedAddress:getCreateAddress({from:c.deployer,nonce}),maxDeploymentCost:gasLimit*price};
}
// Shared by local dry-run and Fuji CLI. Never selects an external network itself.
export async function execute(provider,signer,plan,orgRef,expected=43113n,onTransaction=()=>{}) {
 await chain(provider,expected);
 if(!signer)throw Error('MISSING_SIGNER');
 if(getAddress(await signer.getAddress())!==plan.config.deployer)throw Error('SIGNER_ADDRESS_MISMATCH');
 if(!/^0x[0-9a-fA-F]{64}$/.test(orgRef)||/^0x0{64}$/.test(orgRef))throw Error('INVALID_ORG_REF');
 const a=artifact();
 const registry=await new ContractFactory(a.abi,a.bytecode,signer).deploy(plan.config.admin,{chainId:expected,nonce:plan.nonce,gasLimit:plan.gasLimit,gasPrice:plan.gasPrice});
 onTransaction({stage:'deployment',hash:registry.deploymentTransaction().hash});
 const receipt=await registry.deploymentTransaction().wait();
 if(receipt?.status!==1)throw Error('DEPLOY_RECEIPT_FAILED');
 const address=await registry.getAddress();
 if(keccak256(await provider.getCode(address))!==RUNTIME_HASH)throw Error('RUNTIME_MISMATCH');
 if(!await registry.hasRole(await registry.DEFAULT_ADMIN_ROLE(),plan.config.admin))throw Error('ADMIN_READBACK_FAILED');
 if(await registry.hasRole(await registry.RELAYER_ROLE(),plan.config.admin))throw Error('ADMIN_IMPLICIT_RELAYER');
 await chain(provider,expected);
 const registration=await registry.registerOrganization(orgRef,plan.config.relayer,{chainId:expected,nonce:plan.nonce+1,gasLimit:200000n,gasPrice:plan.gasPrice});
 onTransaction({stage:'registration',hash:registration.hash,address});
 const orgReceipt=await registration.wait();
 if(orgReceipt?.status!==1)throw Error('REGISTRATION_FAILED');
 const [registered,active,relayer]=await registry.getOrganization(orgRef);
 if(!registered||!active||relayer!==plan.config.relayer||!await registry.hasRole(await registry.RELAYER_ROLE(),relayer))throw Error('ORG_READBACK_FAILED');
 return {chainId:expected.toString(),address,deploymentHash:receipt.hash,deploymentBlock:receipt.blockNumber,registrationHash:orgReceipt.hash,orgRef,constructorArguments:[plan.config.admin],...plan.config,runtimeHash:RUNTIME_HASH,creationHash:CREATION_HASH,explorerSourceStatus:'NOT_VERIFIED',sourceCommit:'61cc738385e8b374d38996e8a725a9da6fa7f12b',compiler:{version:'0.8.28',evmVersion:'paris',optimizer:{enabled:true,runs:200}},abi:a.abi,deploymentGasUsed:receipt.gasUsed.toString(),registrationBlock:orgReceipt.blockNumber,registrationGasUsed:orgReceipt.gasUsed.toString()};
}
