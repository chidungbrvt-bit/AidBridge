// Read-only Fuji preflight: no wallet, signer, secret values, or send methods.
import { readFileSync, readdirSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { resolve, dirname } from 'node:path';
import { keccak256, getAddress } from 'ethers';
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const artifact = JSON.parse(readFileSync(resolve(root, 'artifacts/contracts/AidRegistry.sol/AidRegistry.json')));
const source = readFileSync(resolve(root, 'contracts/AidRegistry.sol'), 'utf8');
const sha = x => createHash('sha256').update(x).digest('hex');
const build = readdirSync(resolve(root, 'artifacts/build-info')).filter(x => !x.endsWith('.output.json')).map(x => JSON.parse(readFileSync(resolve(root, 'artifacts/build-info', x)))).find(x => x.input.sources['project/contracts/AidRegistry.sol']?.content === source);
if (!build || build.solcVersion !== '0.8.28' || build.input.settings.evmVersion !== 'paris' || !build.input.settings.optimizer.enabled || build.input.settings.optimizer.runs !== 200) throw Error('BUILD_SOURCE_OR_SETTINGS_MISMATCH');
const constructor = artifact.abi.find(x => x.type === 'constructor');
if (constructor?.inputs.length !== 1 || constructor.inputs[0].type !== 'address') throw Error('CONSTRUCTOR_MISMATCH');
console.log(JSON.stringify({sourceSha256:sha(source),abiJsonSha256:sha(JSON.stringify(artifact.abi)),creationBytecodeKeccak256:keccak256(artifact.bytecode),runtimeBytecodeKeccak256:keccak256(artifact.deployedBytecode),creationBytes:(artifact.bytecode.length-2)/2,runtimeBytes:(artifact.deployedBytecode.length-2)/2,compiler:build.solcVersion,evmVersion:build.input.settings.evmVersion,optimizer:build.input.settings.optimizer},null,2));
// Inspect environment variable names only, never secret values or local .env contents.
const names = new Set(Object.keys(process.env));
console.log(JSON.stringify({secretConfigurationNamesPresent:['FUJI_DEPLOYER_PRIVATE_KEY','FUJI_RELAYER_PRIVATE_KEY'].map(name => ({name,present:names.has(name)}))}));
const rpcUrl = 'https://api.avax-test.network/ext/bc/C/rpc';
let id=0;
async function rpc(method,params=[]) {
 if (!['eth_chainId','eth_blockNumber','eth_getBalance','eth_getCode','eth_gasPrice'].includes(method)) throw Error('READ_ONLY_METHOD_REQUIRED');
 const response=await fetch(rpcUrl,{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({jsonrpc:'2.0',id:++id,method,params}),signal:AbortSignal.timeout(15000)});
 if(!response.ok) throw Error('RPC_HTTP_'+response.status);
 const body=await response.json();if(body.error)throw Error('RPC_ERROR');return body.result;
}
const chainId=Number(BigInt(await rpc('eth_chainId')));
if(chainId!==43113)throw Error('WRONG_CHAIN_REFUSED');
console.log(JSON.stringify({rpcUrl,chainId,blockNumber:Number(BigInt(await rpc('eth_blockNumber'))),gasPriceWei:BigInt(await rpc('eth_gasPrice')).toString()}));
for(const name of ['FUJI_DEPLOYER_ADDRESS','FUJI_ADMIN_ADDRESS','FUJI_RELAYER_ADDRESS']) {
 const raw=process.env[name];
 if(!raw){console.log(JSON.stringify({name,status:'MISSING_PUBLIC_ADDRESS'}));continue;}
 const address=getAddress(raw);if(/^0x0{40}$/i.test(address))throw Error('ZERO_PUBLIC_ADDRESS');
 console.log(JSON.stringify({name,address,balanceWei:BigInt(await rpc('eth_getBalance',[address,'latest'])).toString(),code:await rpc('eth_getCode',[address,'latest'])}));
}
