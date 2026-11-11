import { JsonRpcProvider, formatEther } from 'ethers';
import { readFileSync, writeFileSync, existsSync, mkdirSync } from 'node:fs';
import { dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { FUJI_RPC, CREATION_HASH, prepare, execute } from './deployment.mjs';
import { initializeWallets, publicWallets, unlockWallets, storePath } from './wallet-store.mjs';
const publicPath=new URL('../deployment/fuji-wallets.public.json',import.meta.url);
const manifestPath=new URL('../deployment/fuji-deployment.json',import.meta.url);
try {
 if(process.argv.includes('--init-wallets')) {
  const config=initializeWallets();
  mkdirSync(dirname(fileURLToPath(publicPath)),{recursive:true});
  writeFileSync(publicPath,JSON.stringify(config,null,2),{flag:'wx'});
  console.log(JSON.stringify({config,encryptedStore:storePath}));
 } else {
  if(!existsSync(publicPath))throw Error('MISSING_PUBLIC_CONFIG');
  const config=JSON.parse(readFileSync(publicPath,'utf8'));
  const provider=new JsonRpcProvider(FUJI_RPC);
  try {
   const actual=publicWallets();
   if(actual.deployer!==config.deployer||actual.admin!==config.admin||actual.relayer!==config.relayer)throw Error('SIGNER_CONFIG_MISMATCH');
   const plan=await prepare(provider,config);
   console.log(JSON.stringify({status:'READY_TO_DEPLOY',chainId:43113,...config,estimate:plan.estimate.toString(),gasLimit:plan.gasLimit.toString(),gasPriceWei:plan.gasPrice.toString(),balanceAVAX:formatEther(plan.balance),relayerBalanceAVAX:formatEther(plan.relayerBalance),creationHash:CREATION_HASH}));
   if(process.argv.includes('--broadcast')) {
    if(process.env.AIDBRIDGE_DEPLOY_APPROVAL!==CREATION_HASH)throw Error('FINAL_APPROVAL_REQUIRED');
    if(existsSync(manifestPath))throw Error('DEPLOYMENT_JOURNAL_EXISTS_RECONCILE_FIRST');
    const orgRef=JSON.parse(readFileSync(new URL('../deployment/fuji-plan.public.json',import.meta.url),'utf8')).orgRef;
    if(!/^0x[0-9a-fA-F]{64}$/.test(orgRef??'')||/^0x0{64}$/.test(orgRef??''))throw Error('INVALID_ORG_REF');
    const wallets=unlockWallets();
    writeFileSync(manifestPath,JSON.stringify({status:'STARTED',config,orgRef,nonce:plan.nonce,predictedAddress:plan.predictedAddress}),{flag:'wx'});
    const events=[];
    const result=await execute(provider,wallets.deployer.connect(provider),plan,orgRef,43113n,event=>{events.push(event);writeFileSync(manifestPath,JSON.stringify({status:'IN_PROGRESS',config,orgRef,events},null,2));});
    writeFileSync(manifestPath,JSON.stringify({status:'CONFIRMED',...result},null,2));
    console.log(JSON.stringify(result));
   }
  } finally {provider.destroy();}
 }
} catch(error) {
 // Avoid raw RPC/signer errors that may embed signed transaction data.
 const code=error instanceof Error?error.message:'';
 console.error(/^[A-Z][A-Z0-9_]*$/.test(code)?code:'DEPLOYMENT_OPERATION_FAILED');process.exitCode=1;
}
