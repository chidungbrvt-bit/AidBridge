import {createRequire} from 'node:module';
import {dirname,join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';
const require=createRequire(import.meta.url);
const cli=join(dirname(require.resolve('eslint/package.json')),'bin/eslint.js');
const result=spawnSync(process.execPath,[cli,'--config',fileURLToPath(new URL('./eslint.config.mjs',import.meta.url)),'frontend/features/donor','frontend/dev/donor-preview'],{cwd:fileURLToPath(new URL('../../../',import.meta.url)),stdio:'inherit'});
process.exit(result.status??1);
