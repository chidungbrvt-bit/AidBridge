// Public evidence + independent Fuji RPC only. No wallet/DPAPI access or writes.
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {Contract,JsonRpcProvider,formatEther} from 'ethers';
import {artifact,FUJI_RPC} from './deployment.mjs';
import {context,readback,validateRequest} from './relayer-handoff.mjs';
const check=(ok,code)=>{if(!ok)throw Error(code);};
const eq=(a,b)=>a.toLowerCase()===b.toLowerCase();
export async function verifyHandoff(p,c,proof) {
  check(proof.state==='PASS'&&JSON.stringify(proof.context)===JSON.stringify(context),'HANDOFF_CONTEXT_MISMATCH');
  const data=c.interface.encodeFunctionData('updateOrganizationRelayer',[context.orgRef,context.newRelayer]);
  const receipts={};
  for(const step of ['rotation','funding']) {
    const entry=proof.steps[step]; validateRequest(step,entry.request,data);
    const r=await p.getTransactionReceipt(entry.hash),tx=await p.getTransaction(entry.hash);
    check(r?.status===1&&tx,'HANDOFF_RECEIPT_MISSING');
    const expectedFrom=step==='rotation'?context.admin:context.oldRelayer;
    check(tx.chainId===43113n&&eq(tx.from,expectedFrom)&&eq(tx.to,entry.request.to)&&tx.data===entry.request.data&&tx.nonce===entry.request.nonce&&tx.value===BigInt(entry.request.value)&&tx.gasLimit===BigInt(entry.request.gasLimit)&&tx.gasPrice===BigInt(entry.request.gasPrice),'HANDOFF_TX_MISMATCH');
    const block=await p.getBlock(r.blockNumber);
    check(block.hash===r.blockHash&&r.blockNumber===entry.receipt.blockNumber&&r.blockHash===entry.receipt.blockHash&&String(r.gasUsed)===entry.receipt.gasUsed,'HANDOFF_BLOCK_OR_GAS_MISMATCH');
    if(step==='rotation') {
      const events=r.logs.filter(log=>eq(log.address,context.contract)).map(log=>c.interface.parseLog(log)).filter(log=>log?.name==='OrganizationRelayerUpdated');
      check(events.length===1&&events[0].args.orgRef===context.orgRef&&eq(events[0].args.previousRelayer,context.oldRelayer)&&eq(events[0].args.newRelayer,context.newRelayer)&&events[0].args.active,'HANDOFF_EVENT_MISMATCH');
    }
    receipts[step]={hash:entry.hash,block:r.blockNumber,status:r.status,gasUsed:String(r.gasUsed),feeWei:String(r.fee)};
  }
  const current=await readback(p,c,Object.keys(proof.before.organizations));
  const org=current.organizations[context.orgRef],w=current.wallets;
  check(org.registered&&org.active&&eq(org.relayer,context.newRelayer)&&w.newRelayer.relayer&&!w.newRelayer.admin&&!w.oldRelayer.relayer&&w.admin.admin&&!w.admin.relayer,'HANDOFF_CURRENT_AUTHORIZATION_MISMATCH');
  for(const ref of Object.keys(proof.before.organizations)) if(ref!==context.orgRef) check(JSON.stringify(current.organizations[ref])===JSON.stringify(proof.before.organizations[ref]),'OTHER_ORGANIZATION_CHANGED');
  return {status:'PASS',chainId:43113,context,receipts,fundedAVAX:formatEther(proof.steps.funding.request.value),readback:current};
}
if(process.argv[1]&&fileURLToPath(import.meta.url)===process.argv[1]) {
  const p=new JsonRpcProvider(FUJI_RPC,43113,{staticNetwork:true,cacheTimeout:-1});
  try {const proof=JSON.parse(readFileSync(new URL('../deployment/fuji-relayer-handoff.public.json',import.meta.url),'utf8'));console.log(JSON.stringify(await verifyHandoff(p,new Contract(context.contract,artifact().abi,p),proof)));}
  catch(e){console.error(JSON.stringify({status:'FAIL',code:/^[A-Z0-9_]+$/.test(e.message??'')?e.message:'READBACK_RPC_ERROR'}));process.exitCode=1;}
  finally {p.destroy();}
}
