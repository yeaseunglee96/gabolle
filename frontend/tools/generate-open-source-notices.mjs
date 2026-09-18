import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const lock = JSON.parse(await readFile(resolve(root, 'package-lock.json'), 'utf8'));

const notices = Object.entries(lock.packages ?? {})
  .filter(([path, value]) => path.startsWith('node_modules/') && value?.version)
  .map(([path, value]) => ({
    name: path.slice(path.lastIndexOf('node_modules/') + 'node_modules/'.length),
    version: value.version,
    license: typeof value.license === 'string' ? value.license : 'UNKNOWN',
  }))
  .sort((a, b) => a.name.localeCompare(b.name));

const output = `// 이 파일은 npm run legal:generate로 만든다. 직접 수정하지 않는다.\nexport const OPEN_SOURCE_NOTICES = ${JSON.stringify(notices, null, 2)} as const;\n`;
const target = resolve(root, 'src/legal/openSourceNotices.generated.ts');
await mkdir(dirname(target), { recursive: true });
await writeFile(target, output, 'utf8');
console.log(`오픈소스 고지 ${notices.length}건 생성: ${target}`);
