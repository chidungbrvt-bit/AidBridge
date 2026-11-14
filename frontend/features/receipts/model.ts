import { ApiError, type ApiClient } from '../../src/lib/api-client';
export type ReceiptRequest = {receivedAt:string; items:{contributionItemId:string;quantity:number}[];note?:string};
export type ReceiptResponse = {id:string;contributionId:string;items:ReceiptRequest['items'];receivedAt:string;recordedAt:string;recorderId:string;auditEventId:string;blockchainStatus:string};
export type StaffContext = {id:string;organizationId:string;outstandingCommitments:{contributionId:string;status:string;pledgedAt:string;items:{contributionItemId:string;campaignItemId:string;itemName:string;unit:string;pledgedQty:number;receivedQty:number;outstandingQty:number}[]}[]};
export type ReceiptWork = {pending?:{id:string;body:ReceiptRequest;key:string};receipt?:ReceiptResponse;busy?:boolean;uncertain?:boolean};
export const outcomeUnknown=(e:unknown)=>!(e instanceof ApiError)||e.status>=500||e.status===408;
export function buildReceipt(context:StaffContext,id:string,draft:Record<string,string>,at:string,note:string):ReceiptRequest {
  const target=context.outstandingCommitments.find(row=>row.contributionId===id);
  if(!target)throw new Error('Select an eligible commitment.');
  const items=Object.entries(draft).map(([contributionItemId,raw])=>{
    const line=target.items.find(row=>row.contributionItemId===contributionItemId),quantity=Number(raw);
    if(!line||!raw.trim()||!Number.isSafeInteger(quantity)||quantity<=0||quantity>2147483647||quantity>line.outstandingQty)throw new Error('Enter positive whole quantities within outstanding goods.');
    return {contributionItemId,quantity};
  });
  note=note.trim();
  if(!items.length||!Number.isFinite(Date.parse(at))||note.length>500||/[<>\0]|:\/\//.test(note))throw new Error('Select goods, enter a valid UTC time and a safe note of at most 500 characters.');
  return {receivedAt:new Date(at).toISOString(),items,...(note?{note}:{})};
}
export async function submitReceipt(api:Pick<ApiClient,'receiveGoods'>,work:ReceiptWork,next?:{id:string;body:ReceiptRequest}) {
  if(work.busy)throw new Error('Receipt already in flight.');
  if(!work.pending){if(!next)throw new Error('No receipt to retry.');work.pending={id:next.id,body:structuredClone(next.body),key:crypto.randomUUID()};}
  work.busy=true;
  try {const p=work.pending;const result=await api.receiveGoods(p.id,p.body,p.key);work.receipt=result;work.pending=undefined;work.uncertain=false;return result;}
  // A later rejection cannot prove that an earlier lost response did not commit.
  catch(e){if(outcomeUnknown(e))work.uncertain=true;else if(!work.uncertain)work.pending=undefined;throw e;}
  finally{work.busy=false;}
}
