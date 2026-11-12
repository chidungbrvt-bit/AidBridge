// Independent read-only receipt, code, organization and role validation. No signer.
import {JsonRpcProvider,Contract,keccak256} from 'ethers';
import {readFileSync,existsSync} from 'node:fs';
import {FUJI_RPC,artifact,chain} from './deployment.mjs';
import {verifyHandoff} from './verify-relayer-handoff.mjs';
const p=new JsonRpcProvider(FUJI_RPC);
try{
 await chain(p);
 const m=JSON.parse(readFileSync(new URL('../deployment/fuji-deployment.json',import.meta.url),'utf8'));
 const a=artifact(), c=new Contract(m.address,a.abi,p);
 const r=await p.getTransactionReceipt(m.deploymentHash),orgReceipt=await p.getTransactionReceipt(m.registrationHash);
 if(r?.status!==1||orgReceipt?.status!==1||r.contractAddress?.toLowerCase()!==m.address.toLowerCase())throw Error('RECEIPT_MISMATCH');
 if(await p.getCode(m.address)!==a.deployedBytecode)throw Error('CODE_MISMATCH');
 const admin=await c.DEFAULT_ADMIN_ROLE(),relayer=await c.RELAYER_ROLE();
 const handoffFile=new URL('../deployment/fuji-relayer-handoff.public.json',import.meta.url);
 let currentRelayer=m.relayer;
 if(existsSync(handoffFile)) {
   const h=JSON.parse(readFileSync(handoffFile,'utf8'));
   await verifyHandoff(p,c,h); // Verify public evidence, receipts, calldata, events and current authorization.
   currentRelayer=h.context.newRelayer;
 }
 const org=await c.getOrganization(m.orgRef);
 if(!await c.hasRole(admin,m.admin)||await c.hasRole(relayer,m.admin)||!await c.hasRole(relayer,currentRelayer)||await c.hasRole(admin,currentRelayer)||!org[0]||!org[1]||org[2]!==currentRelayer)throw Error('AUTHORIZATION_MISMATCH');
 const tx=await p.getTransaction(m.registrationHash);const decoded=c.interface.parseTransaction({data:tx.data,value:tx.value});
 if(tx.chainId!==43113n||tx.from.toLowerCase()!==m.admin.toLowerCase()||decoded?.name!=='registerOrganization'||decoded.args[0]!==m.orgRef||decoded.args[1]!==m.relayer)throw Error('REGISTRATION_MISMATCH');
 console.log(JSON.stringify({status:'PASS',chainId:43113,address:m.address,orgRef:m.orgRef,currentRelayer,runtimeHash:keccak256(a.deployedBytecode),deploymentGasUsed:r.gasUsed.toString(),registrationGasUsed:orgReceipt.gasUsed.toString()}));
}finally{p.destroy();}
