import {ReceiptWorkspace} from '../../../../../../features/receipts/ReceiptWorkspace';
export default async function ReceiptPage({params}:{params:Promise<{id:string}>}){const {id}=await params;return <ReceiptWorkspace id={id}/>;}
