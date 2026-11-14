'use client';
import Link from 'next/link';
import {useEffect,useRef,useState} from 'react';
import {useSession} from '../../src/components/session-provider';
import {ApiError,canManageCampaigns} from '../../src/lib/api-client';
import {buildReceipt,submitReceipt,type ReceiptWork,type StaffContext} from './model';
export function ReceiptWorkspace({id}:{id:string}) {
  const s=useSession();return <ReceiptForm key={`${s.user?.userId}:${s.organizationId}:${s.generation}:${id}`} id={id}/>;
}
function ReceiptForm({id}:{id:string}) {
  const {api,mode,user,organizationId,work,setNavigationLocked}=useSession();
  const [journal]=useState<ReceiptWork>(()=>{const key=`${user?.userId}:${organizationId}:${id}`,value=work.receipts.get(key)??{};work.receipts.set(key,value);return value;});
  const [context,setContext]=useState<StaffContext>();
  const [selected,setSelected]=useState(''),[draft,setDraft]=useState<Record<string,string>>({});
  const [at,setAt]=useState(()=>new Date().toISOString().slice(0,16)),[note,setNote]=useState('');
  const [loading,setLoading]=useState(false),[busy,setBusy]=useState(false),[message,setMessage]=useState(''),[revision,setRevision]=useState(0);
  const mounted=useRef(true),feedback=useRef<HTMLParagraphElement>(null);
  const allowed=mode==='real'&&canManageCampaigns(user,organizationId),locked=busy||!!journal.pending||!!journal.busy;
  useEffect(()=>{mounted.current=true;return()=>{mounted.current=false;};},[]);
  useEffect(()=>{setNavigationLocked(locked);const warn=(e:BeforeUnloadEvent)=>{e.preventDefault();e.returnValue='';};if(locked)window.addEventListener('beforeunload',warn);return()=>{setNavigationLocked(false);window.removeEventListener('beforeunload',warn);};},[locked,setNavigationLocked]);
  useEffect(()=>{
    let live=true;setContext(undefined);if(!allowed)return;setLoading(true);
    api.getReceiptContext(id).then(value=>{
      if(value.id!==id||value.organizationId!==organizationId||!Array.isArray(value.outstandingCommitments))throw new Error('Invalid staff context.');
      if(live){setContext(value);setSelected(old=>value.outstandingCommitments.some(row=>row.contributionId===old)?old:value.outstandingCommitments[0]?.contributionId??'');}
    }).catch(e=>{if(live)setMessage(`Context unavailable. ${describe(e)} Refresh staff quantities to retry.`);}).finally(()=>{if(live)setLoading(false);});
    return()=>{live=false;};
  },[api,id,organizationId,allowed,revision]);
  async function send(e:React.FormEvent){
    e.preventDefault();if(!allowed||busy||journal.busy)return;setBusy(true);setMessage('Submitting receipt…');
    try{await submitReceipt(api,journal,journal.pending?undefined:{id:selected,body:buildReceipt(context!,selected,draft,at+'Z',note)});if(mounted.current){setDraft({});setNote('');setMessage('Receipt accepted by backend. Refreshing staff quantities.');}}
    catch(error){if(mounted.current)setMessage(journal.pending?`Outcome unknown. Retry the same receipt with preserved quantities, time and key. ${describe(error)}`:describe(error));}
    finally{if(mounted.current){setBusy(false);setRevision(v=>v+1);feedback.current?.focus();}}
  }
  if(mode!=='real')return <section className="card"><h1>R01 API receipt</h1><p>Available in real API mode only.</p><Link href="/goods-demo">Open independent mock R01/D01 workspace</Link></section>;
  if(!allowed)return <section className="card"><h1>R01 API receipt</h1><p role="alert">403 FORBIDDEN — Active organization admin membership required. Backend remains authoritative.</p></section>;
  const target=context?.outstandingCommitments.find(row=>row.contributionId===selected);
  return <section className="card"><p className="eyebrow">R01 · REAL API</p><h1>Receive pledged goods</h1><p>B08 backend receipt. D01/F09 fixtures remain separate.</p>
    <p role="status" ref={feedback} tabIndex={-1}>{message}</p>
    {journal.receipt&&<section aria-label="Backend receipt"><h2>Receipt {journal.receipt.id}</h2><p>{journal.receipt.blockchainStatus} · {journal.receipt.recordedAt}</p>{journal.receipt.items.map(line=><p key={line.contributionItemId}>{line.contributionItemId}: {line.quantity}</p>)}<p>Pending blockchain is not confirmed.</p></section>}
    <button className="button secondary" disabled={busy} onClick={()=>setRevision(v=>v+1)}>Refresh staff quantities</button>
    {loading?<p role="status">Loading staff quantities…</p>:context&&!context.outstandingCommitments.length?<p>No outstanding goods remain.</p>:null}
    <form noValidate onSubmit={send}><fieldset disabled={locked||loading||!context} style={{border:0,padding:0}}>
      <label className="field">Commitment<select value={selected} onChange={e=>{setSelected(e.target.value);setDraft({});}}>{context?.outstandingCommitments.map(row=><option key={row.contributionId} value={row.contributionId}>{row.contributionId} · {row.status}</option>)}</select></label>
      {target?.items.map(line=><div key={line.contributionItemId}><label className="checkbox"><input type="checkbox" checked={draft[line.contributionItemId]!==undefined} onChange={e=>setDraft(old=>{const next={...old};if(e.target.checked)next[line.contributionItemId]='1';else delete next[line.contributionItemId];return next;})}/>{line.itemName} ({line.unit}) · received {line.receivedQty} · outstanding {line.outstandingQty}</label><label className="field">Receive {line.itemName}<input type="number" min="1" max={line.outstandingQty} step="1" disabled={draft[line.contributionItemId]===undefined} value={draft[line.contributionItemId]??''} onChange={e=>setDraft(old=>({...old,[line.contributionItemId]:e.target.value}))}/></label></div>)}
      <label className="field">Received at (UTC)<input type="datetime-local" value={at} onChange={e=>setAt(e.target.value)}/></label>
      <label className="field">Receipt note (optional)<textarea maxLength={500} value={note} onChange={e=>setNote(e.target.value)}/></label>
    </fieldset><button className="button" disabled={busy||journal.busy||(!journal.pending&&(!context||loading||!target))}>{busy?'Submitting…':journal.pending?'Retry same receipt':'Record API receipt'}</button></form>
    <Link href={`/cases/${id}`}>Back to case</Link></section>;
}
function describe(e:unknown){return e instanceof ApiError?`${e.status} ${e.code}: ${e.message}`:e instanceof Error?e.message:'Request failed.';}
