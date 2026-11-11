// Test-only host. Not imported by preview main or a production entry.
import {useState} from 'react';
import {createRoot} from 'react-dom/client';
import {DonorApp} from '../../../features/donor/DonorApp';
import {createMockDonorApi,DEMO_CAMPAIGN_ID} from '../../../features/donor/mock';
import type {DonorApi} from '../../../features/donor/model';
const mock=createMockDonorApi(0);
const api:DonorApi={...mock.api,getCampaign:async id=>({...await mock.api.getCampaign(DEMO_CAMPAIGN_ID),id,title:id==='other'?'Other campaign':'Original campaign'}),pledge:async(...args)=>{await new Promise(resolve=>setTimeout(resolve,900));return mock.api.pledge(...args);}};
function Host(){const[view,setView]=useState<'pledge'|'history'>('pledge');const[id,setId]=useState(DEMO_CAMPAIGN_ID);return <><button onClick={()=>setId('other')}>Switch campaign</button><DonorApp api={api} campaignId={id} view={view} navigate={setView} donorEnabled={true} mode="mock"/></>;}
createRoot(document.getElementById('root')!).render(<Host/>);
