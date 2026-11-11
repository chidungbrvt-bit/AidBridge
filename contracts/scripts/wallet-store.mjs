// Windows CurrentUser DPAPI; secrets only cross anonymous pipes, never argv/logs.
import { spawnSync } from 'node:child_process';
import { join } from 'node:path';
import { existsSync } from 'node:fs';
import { Wallet } from 'ethers';
export const storePath = join(process.env.LOCALAPPDATA ?? '', 'AidBridge', 'fuji-wallets.clixml');
const shell = join(process.env.SystemRoot ?? 'C:\\Windows', 'System32', 'WindowsPowerShell', 'v1.0', 'powershell.exe');
function ps(script, input='') {
 if(process.platform!=='win32') throw Error('WINDOWS_DPAPI_REQUIRED');
 const childEnv={...process.env,AIDBRIDGE_WALLET_STORE:storePath};
 delete childEnv.PSModulePath; // Do not inherit PowerShell 7 module paths into Windows PowerShell 5.1.
 const result=spawnSync(shell,['-NoProfile','-NonInteractive','-Command',script],{input,encoding:'utf8',windowsHide:true,env:childEnv});
 if(result.status!==0) throw Error('DPAPI_OPERATION_FAILED');
 return result.stdout.trim();
}
export function initializeWallets() {
 if(existsSync(storePath)) return publicWallets();
 const a=Wallet.createRandom(),r=Wallet.createRandom();
 const payload=JSON.stringify({deployer:a.privateKey,relayer:r.privateKey});
 ps(`$ErrorActionPreference='Stop'; $p=$env:AIDBRIDGE_WALLET_STORE; if(Test-Path -LiteralPath $p){throw 'EXISTS'}; $v=[Console]::In.ReadToEnd(); $s=ConvertTo-SecureString $v -AsPlainText -Force; New-Item -ItemType Directory -Force -Path (Split-Path $p) | Out-Null; $s | Export-Clixml -LiteralPath $p;`,payload);
 return publicWallets();
}
export function unlockWallets() {
 if(!existsSync(storePath)) throw Error('MISSING_DPAPI_SIGNER');
 const raw=ps(`$ErrorActionPreference='Stop'; $s=Import-Clixml -LiteralPath $env:AIDBRIDGE_WALLET_STORE; $b=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($s); try{[Console]::Out.Write([Runtime.InteropServices.Marshal]::PtrToStringBSTR($b))}finally{[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($b)}`);
 const keys=JSON.parse(raw);
 return {deployer:new Wallet(keys.deployer),relayer:new Wallet(keys.relayer)};
}
export function publicWallets() {
 const w=unlockWallets();
 return {deployer:w.deployer.address,admin:w.deployer.address,relayer:w.relayer.address};
}
