// Approved Fuji-only rotation + old-relayer funding. Default read-only; no business jobs.
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import { join } from 'node:path';
import { Contract, JsonRpcProvider, Transaction, keccak256, parseUnits, formatEther } from 'ethers';
import { artifact, chain, FUJI_RPC, RUNTIME_HASH } from './deployment.mjs';
import { unlockWallets, unlockRelayer } from './wallet-store.mjs';

export const context = Object.freeze({ chainId:43113,
  contract:'0x015D941c679b0c402c8262BE237adAD36cEf6726',
  orgRef:'0x2395bfe7f91467046a39bd8440313164c6442839b2be6698832b7b4941b80a46',
  admin:'0x131DD6d404f8D0B118D6DB122194bc15b4E4601E',
  oldRelayer:'0x559994839f19F40ac704918C3FF1a913AC02dC01',
  newRelayer:'0x95a9564899849CF566DE5713205C4B747F361f55' });
const base=new URL('../deployment/',import.meta.url);
const publicFile=new URL('fuji-relayer-handoff.public.json',base);
const load=p=>JSON.parse(readFileSync(p,'utf8'));
const save=(p,v,flag='w')=>writeFileSync(p,JSON.stringify(v,null,2)+'\n',{flag});
const check=(ok,code)=>{if(!ok)throw Error(code);};
const eq=(a,b)=>a.toLowerCase()===b.toLowerCase();

// The DPAPI journal contains signed transactions, not keys. Never print raw RPC errors.
function journal(step,action,payload='') {
  check(['rotation','funding'].includes(step),'UNKNOWN_STEP');
  const file=new URL(`fuji-relayer-${step}.private.clixml`,base);
  const env={...process.env,AIDBRIDGE_HANDOFF_STORE:fileURLToPath(file)}; delete env.PSModulePath;
  const script=action==='save'
    ? "$ErrorActionPreference='Stop'; if(Test-Path -LiteralPath $env:AIDBRIDGE_HANDOFF_STORE){throw 'EXISTS'}; ConvertTo-SecureString ([Console]::In.ReadToEnd()) -AsPlainText -Force | Export-Clixml -LiteralPath $env:AIDBRIDGE_HANDOFF_STORE -NoClobber"
    : "$ErrorActionPreference='Stop'; $s=Import-Clixml -LiteralPath $env:AIDBRIDGE_HANDOFF_STORE; $b=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($s); try{[Console]::Out.Write([Runtime.InteropServices.Marshal]::PtrToStringBSTR($b))}finally{[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($b)}";
  const r=spawnSync(join(process.env.SystemRoot,'System32/WindowsPowerShell/v1.0/powershell.exe'),['-NoProfile','-NonInteractive','-Command',script],{input:payload,encoding:'utf8',env,windowsHide:true});
  check(r.status===0,'DPAPI_JOURNAL_FAILED'); return r.stdout.trim();
}
export function validateRequest(step,r,data) {
  check(r.chainId===43113 && r.type===0,'WRONG_CHAIN_OR_TYPE');
  check(Number.isSafeInteger(r.nonce)&&r.nonce>=0,'INVALID_NONCE');
  check(BigInt(r.gasPrice)>0n&&BigInt(r.gasPrice)<=parseUnits('25',9),'GAS_PRICE_CAP');
  check(BigInt(r.gasLimit)>0n&&BigInt(r.gasLimit)<=300000n,'GAS_LIMIT_CAP');
  check(BigInt(r.gasPrice)*BigInt(r.gasLimit)<=parseUnits('0.01',18),'FEE_CAP');
  if(step==='rotation') check(eq(r.to,context.contract)&&r.data===data&&BigInt(r.value)===0n,'UNAPPROVED_ROTATION');
  else if(step==='funding') check(eq(r.to,context.newRelayer)&&r.data==='0x'&&BigInt(r.value)>0n,'UNAPPROVED_FUNDING');
  else throw Error('UNKNOWN_STEP');
}
export async function readback(p,c,refs) {
  await chain(p); check(keccak256(await p.getCode(context.contract))===RUNTIME_HASH,'RUNTIME_MISMATCH');
  const block=await p.getBlockNumber();
  const adminRole=await c.DEFAULT_ADMIN_ROLE(),relayerRole=await c.RELAYER_ROLE();
  const options={blockTag:block};
  const organizations={};
  for(const ref of refs) {const o=await c.getOrganization(ref,options); organizations[ref]={registered:o[0],active:o[1],relayer:o[2]};}
  const wallets={};
  for(const key of ['admin','oldRelayer','newRelayer']) {
    const address=context[key],balance=await p.getBalance(address,block);
    wallets[key]={address,balanceWei:String(balance),balanceAVAX:formatEther(balance),
      latestNonce:await p.getTransactionCount(address,block),pendingNonce:await p.getTransactionCount(address,'pending'),
      admin:await c.hasRole(adminRole,address,options),relayer:await c.hasRole(relayerRole,address,options)};
  }
  return {checkedAt:new Date().toISOString(),chainId:43113,blockNumber:block,runtimeHash:RUNTIME_HASH,organizations,wallets};
}
async function organizationRefs(p,c,first,last) {
  const refs=new Set([context.orgRef]);
  const topic=c.interface.getEvent('OrganizationRegistered').topicHash;
  // Fuji public RPC limits eth_getLogs to 100 blocks. Use the testnet indexer for
  // discovery, then independently validate each indexed log against its RPC receipt.
  const url=new URL('https://api.routescan.io/v2/network/testnet/evm/43113/etherscan/api');
  for(const [k,v] of Object.entries({module:'logs',action:'getLogs',address:context.contract,fromBlock:String(first),toBlock:String(last),topic0:topic,page:'1',offset:'1000'})) url.searchParams.set(k,v);
  const response=await fetch(url,{signal:AbortSignal.timeout(20000)}),result=await response.json();
  check(response.ok&&result.status==='1'&&Array.isArray(result.result)&&result.result.length<1000,'ORG_INDEX_INCOMPLETE');
  for(const log of result.result) {
    const receipt=await p.getTransactionReceipt(log.transactionHash);
    check(receipt?.status===1&&receipt.blockHash===log.blockHash,'INDEX_RECEIPT_MISMATCH');
    const verified=receipt.logs.find(x=>eq(x.address,context.contract)&&x.topics.join()===log.topics.join()&&x.data===log.data);
    check(verified,'INDEX_LOG_MISMATCH'); refs.add(c.interface.parseLog(verified).args.orgRef);
  }
  // Cover the unindexed recent tail as well (read-only, bounded public RPC range).
  for(const log of await p.getLogs({address:context.contract,topics:[topic],fromBlock:Math.max(first,last-99),toBlock:last})) refs.add(c.interface.parseLog(log).args.orgRef);
  return [...refs];
}
export async function main(execute=false) {
  const p=new JsonRpcProvider(FUJI_RPC,43113,{staticNetwork:true,cacheTimeout:-1});
  try {
    await chain(p); const c=new Contract(context.contract,artifact().abi,p);
    const m=load(new URL('fuji-deployment.json',base));
    const refs=await organizationRefs(p,c,m.deploymentBlock,await p.getBlockNumber());
    const now=await readback(p,c,refs),org=now.organizations[context.orgRef];
    check(org.registered&&org.active,'ORG_NOT_ACTIVE');
    check(now.wallets.admin.admin&&!now.wallets.admin.relayer,'ADMIN_AUTHORIZATION_MISMATCH');
    check(eq(org.relayer,context.oldRelayer)||eq(org.relayer,context.newRelayer),'UNEXPECTED_ORG_RELAYER');
    check(await p.getCode(context.newRelayer)==='0x','NEW_RELAYER_MUST_BE_EOA');
    check(!now.wallets.newRelayer.admin,'NEW_RELAYER_UNEXPECTED_ADMIN');
    check(!Object.entries(now.organizations).some(([ref,o])=>ref!==context.orgRef&&o.active&&eq(o.relayer,context.oldRelayer)),'OLD_RELAYER_OTHER_ACTIVE_ORG');
    const data=c.interface.encodeFunctionData('updateOrganizationRelayer',[context.orgRef,context.newRelayer]);
    if(!execute) {console.log(JSON.stringify({scope:'READ_ONLY',context,readback:now,rotationGasEstimate:eq(org.relayer,context.oldRelayer)?String(await p.estimateGas({from:context.admin,to:context.contract,data})):null}));return;}
    let proof;
    if(existsSync(publicFile)) {proof=load(publicFile); check(JSON.stringify(proof.context)===JSON.stringify(context),'JOURNAL_CONTEXT_MISMATCH');}
    else {
      check(eq(org.relayer,context.oldRelayer)&&now.wallets.oldRelayer.relayer,'INITIAL_RELAYER_MISMATCH');
      proof={scope:'C04_C05_OPERATIONAL_HANDOFF_NOT_B05_E2E',context,workerSafety:'User relayed Dung confirmation: worker disabled; no old-signer transactions/jobs/DB bindings. Keep worker disabled until handoff.',before:now,steps:{},state:'PREPARED'}; save(publicFile,proof,'wx');
    }
    async function transaction(step,from,to,callData,valueFor) {
      let entry=proof.steps[step];
      if(!entry) {
        check(!existsSync(new URL(`fuji-relayer-${step}.private.clixml`,base)),'ORPHAN_SIGNED_JOURNAL_RECONCILE');
        const nonce=await p.getTransactionCount(from,'latest');
        check(nonce===await p.getTransactionCount(from,'pending'),'SIGNER_HAS_PENDING_TX');
        const fee=(await p.getFeeData()).gasPrice; check(fee!==null,'MISSING_GAS_PRICE');
        const gasPrice=fee*12n/10n>parseUnits('1',9)?fee*12n/10n:parseUnits('1',9);
        const estimate=await p.estimateGas({from,to,data:callData,value:step==='funding'?1n:0n});
        const gasLimit=step==='funding'?estimate:(estimate*120n+99n)/100n;
        check(step!=='funding'||gasLimit===21000n,'UNEXPECTED_TRANSFER_GAS');
        const balance=await p.getBalance(from),value=valueFor(balance,gasLimit*gasPrice);
        check(balance>=value+gasLimit*gasPrice,'INSUFFICIENT_BALANCE');
        const request={type:0,chainId:43113,nonce,to,data:callData,value:String(value),gasLimit:String(gasLimit),gasPrice:String(gasPrice)};
        validateRequest(step,request,data);
        const signer=step==='rotation'?unlockWallets().deployer:unlockRelayer();
        check(eq(signer.address,from),'SIGNER_MISMATCH');
        const raw=await signer.signTransaction(request);
        entry={state:'PREPARED',from,request,hash:keccak256(raw),preparedAt:new Date().toISOString(),gasEstimate:String(estimate),balanceBeforeWei:String(balance)};
        journal(step,'save',raw); proof.steps[step]=entry; save(publicFile,proof);
      }
      validateRequest(step,entry.request,data); check(eq(entry.from,from),'JOURNAL_SIGNER_MISMATCH');
      let receipt=await p.getTransactionReceipt(entry.hash);
      if(!receipt) {
        const raw=journal(step,'load'),tx=Transaction.from(raw);
        check(tx.hash===entry.hash&&eq(tx.from,from)&&tx.chainId===43113n&&eq(tx.to,to)&&tx.data===callData&&tx.nonce===entry.request.nonce&&tx.value===BigInt(entry.request.value)&&tx.gasLimit===BigInt(entry.request.gasLimit)&&tx.gasPrice===BigInt(entry.request.gasPrice),'SIGNED_TRANSACTION_MISMATCH');
        if(!await p.getTransaction(entry.hash)) {
          check(await p.getTransactionCount(from,'latest')===tx.nonce&&await p.getTransactionCount(from,'pending')===tx.nonce,'NONCE_CONFLICT_RECONCILE');
          await chain(p); await p.broadcastTransaction(raw);
        }
        entry.state='SUBMITTED';save(publicFile,proof);
        receipt=await p.waitForTransaction(entry.hash,1,60000);
      }
      check(receipt?.status===1,'RECEIPT_NOT_SUCCESSFUL_KEEP_ORIGINAL_HASH');
      const tx=await p.getTransaction(entry.hash),block=await p.getBlock(receipt.blockNumber);
      check(block.hash===receipt.blockHash&&tx.chainId===43113n&&eq(tx.from,from)&&eq(tx.to,to)&&tx.data===callData&&tx.value===BigInt(entry.request.value),'RECEIPT_TRANSACTION_MISMATCH');
      entry.state='CONFIRMED'; entry.receipt={status:receipt.status,blockNumber:receipt.blockNumber,blockHash:receipt.blockHash,gasUsed:String(receipt.gasUsed),gasPrice:String(receipt.gasPrice),feeWei:String(receipt.fee)};
      if(step==='rotation') {
        const events=receipt.logs.filter(log=>eq(log.address,context.contract)).map(log=>c.interface.parseLog(log)).filter(log=>log?.name==='OrganizationRelayerUpdated');
        check(events.length===1&&events[0].args.orgRef===context.orgRef&&eq(events[0].args.previousRelayer,context.oldRelayer)&&eq(events[0].args.newRelayer,context.newRelayer)&&events[0].args.active,'ROTATION_EVENT_MISMATCH');
      }
      save(publicFile,proof); return entry;
    }
    await transaction('rotation',context.admin,context.contract,data,()=>0n);
    const rotated=await readback(p,c,refs);
    check(eq(rotated.organizations[context.orgRef].relayer,context.newRelayer)&&rotated.organizations[context.orgRef].active&&rotated.wallets.newRelayer.relayer&&!rotated.wallets.oldRelayer.relayer&&rotated.wallets.admin.admin&&!rotated.wallets.admin.relayer,'ROTATION_READBACK_FAILED');
    for(const ref of refs) if(ref!==context.orgRef) check(JSON.stringify(rotated.organizations[ref])===JSON.stringify(proof.before.organizations[ref]),'OTHER_ORGANIZATION_CHANGED');
    proof.afterRotation=rotated;save(publicFile,proof);
    await transaction('funding',context.oldRelayer,context.newRelayer,'0x',(balance,fee)=>{check(balance>fee,'INSUFFICIENT_FUNDING_BALANCE');return balance-fee;});
    // Independent provider readback rather than signer response/cache.
    const independent=new JsonRpcProvider(FUJI_RPC,43113,{staticNetwork:true,cacheTimeout:-1});
    try {proof.after=await readback(independent,new Contract(context.contract,artifact().abi,independent),refs);} finally {independent.destroy();}
    const finalOrg=proof.after.organizations[context.orgRef],finalWallets=proof.after.wallets;
    check(finalOrg.registered&&finalOrg.active&&eq(finalOrg.relayer,context.newRelayer)&&finalWallets.newRelayer.relayer&&!finalWallets.newRelayer.admin&&!finalWallets.oldRelayer.relayer&&finalWallets.admin.admin&&!finalWallets.admin.relayer,'INDEPENDENT_FINAL_READBACK_FAILED');
    proof.state='PASS';proof.abiVersion='D49/schemaVersion 1';proof.completedAt=new Date().toISOString();save(publicFile,proof);
    console.log(JSON.stringify({state:proof.state,rotationHash:proof.steps.rotation.hash,fundingHash:proof.steps.funding.hash,fundedAVAX:formatEther(proof.steps.funding.request.value),wallets:proof.after.wallets}));
  } finally {p.destroy();}
}
if(process.argv[1]&&fileURLToPath(import.meta.url)===process.argv[1]) {
  try {check(process.argv.slice(2).every(arg=>arg==='--execute'),'UNKNOWN_ARGUMENT');await main(process.argv.includes('--execute'));}
  catch(error) {console.error(JSON.stringify({status:'NOT_CONFIRMED',code:/^[A-Z0-9_]+$/.test(error.message??'')?error.message:'HANDOFF_RPC_OR_OPERATION_ERROR',instruction:'Reconcile existing public hash before retry; never create a replacement attempt.'}));process.exitCode=1;}
}
