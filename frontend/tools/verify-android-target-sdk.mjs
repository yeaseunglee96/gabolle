import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const required = 36;
const catalogPath = resolve('node_modules/react-native/gradle/libs.versions.toml');

let catalog;
try {
  catalog = await readFile(catalogPath, 'utf8');
} catch {
  console.error('React Native 버전 카탈로그가 없습니다. 먼저 npm install을 실행하세요.');
  process.exit(1);
}

function readVersion(name) {
  const match = catalog.match(new RegExp(`^${name}\\s*=\\s*"(\\d+)"`, 'm'));
  return match ? Number(match[1]) : null;
}

const compileSdk = readVersion('compileSdk');
const targetSdk = readVersion('targetSdk');
if (compileSdk === null || targetSdk === null) {
  console.error('compileSdk 또는 targetSdk 값을 확인할 수 없습니다.');
  process.exit(1);
}

console.log(`Android compileSdk=${compileSdk}, targetSdk=${targetSdk}, required>=${required}`);
if (compileSdk < required || targetSdk < required) {
  console.error(`Google Play 제출 요건을 충족하지 않습니다. API ${required} 이상으로 올리세요.`);
  process.exit(1);
}
