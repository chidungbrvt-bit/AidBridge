import {test} from 'node:test';
import assert from 'node:assert/strict';
import {context,validateRequest} from './relayer-handoff.mjs';
import {Interface} from 'ethers';
import {artifact} from './deployment.mjs';
const data=new Interface(artifact().abi).encodeFunctionData('updateOrganizationRelayer',[context.orgRef,context.newRelayer]);
const rotation={type:0,chainId:43113,nonce:2,to:context.contract,data,value:'0',gasLimit:'115000',gasPrice:'1000000000'};
test('approved rotation/funding requests pass without invoking any signer/RPC',()=>{
  validateRequest('rotation',rotation,data);
  validateRequest('funding',{...rotation,to:context.newRelayer,data:'0x',value:'149679527000000000',gasLimit:'21000'},data);
});
test('reject mainnet, wrong recipient, unexpected calldata, value, type and gas caps',()=>{
  for(const patch of [{chainId:43114},{type:2},{to:context.oldRelayer},{data:'0x'},{value:'1'},{gasLimit:'300001'},{gasPrice:'26000000000'},{nonce:-1}]) assert.throws(()=>validateRequest('rotation',{...rotation,...patch},data));
  assert.throws(()=>validateRequest('funding',{...rotation,to:context.admin,data:'0x',value:'1'},data));
  assert.throws(()=>validateRequest('funding',{...rotation,to:context.newRelayer,data:'0x',value:'0'},data));
  assert.throws(()=>validateRequest('unknown',rotation,data));
});
