// One-off, read-only: rebuilds the reviewed C03 artifact snapshot from the live Fuji deployment.
// No private key, no transaction. Writes the file only if creation/runtime/ABI hashes match the
// values pinned in deployment.mjs (CREATION_HASH, RUNTIME_HASH, ABI_HASH).
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { AbiCoder, JsonRpcProvider } from 'ethers';
import { FUJI_RPC, REVIEWED_ARTIFACT_URL, chain, validateArtifact } from './deployment.mjs';

const manifest = JSON.parse(readFileSync(new URL('../deployment/fuji-deployment.json', import.meta.url), 'utf8'));
if (existsSync(REVIEWED_ARTIFACT_URL)) {
  validateArtifact(JSON.parse(readFileSync(REVIEWED_ARTIFACT_URL, 'utf8')));
  console.log('SNAPSHOT_ALREADY_PRESENT_AND_VALID');
  process.exit(0);
}

const provider = new JsonRpcProvider(FUJI_RPC, 43113, { staticNetwork: true });
await chain(provider); // must be 43113

const tx = await provider.getTransaction(manifest.deploymentHash);
if (!tx || tx.to !== null) throw Error('NOT_A_DEPLOYMENT_TRANSACTION');
const encodedArgs = AbiCoder.defaultAbiCoder().encode(['address'], manifest.constructorArguments).slice(2).toLowerCase();
const input = tx.data.toLowerCase();
if (!input.endsWith(encodedArgs)) throw Error('CONSTRUCTOR_ARGUMENTS_MISMATCH');
const bytecode = input.slice(0, input.length - encodedArgs.length);
const deployedBytecode = (await provider.getCode(manifest.address)).toLowerCase();

const snapshot = validateArtifact({
  contractName: 'AidRegistry',
  sourceCommit: manifest.sourceCommit,
  chainId: manifest.chainId,
  address: manifest.address,
  deploymentHash: manifest.deploymentHash,
  abi: manifest.abi,
  bytecode,
  deployedBytecode,
});
writeFileSync(REVIEWED_ARTIFACT_URL, JSON.stringify(snapshot, null, 2) + '\n', { flag: 'wx' });
console.log('SNAPSHOT_WRITTEN', JSON.stringify({ sourceCommit: snapshot.sourceCommit, address: snapshot.address }));
