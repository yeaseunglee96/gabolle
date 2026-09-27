// 「오늘」·「n일차」를 UTC 날짜로 잡지 않는다 (S15P21E201-1782).
// 🔴 toISOString().slice(0, 10) 은 UTC 날짜다. 한국 0~9시엔 어제가 되고, 현지 자정으로 만든 날짜는 늘 하루 앞이 된다.
//    이 다섯 곳이 그랬다 — 다시 들어오지 않게 소스를 본다. 날짜 키는 tripProgress.localDateKey 를 쓴다.
import { readFileSync } from 'fs';
import { join } from 'path';

const ROOT = join(__dirname, '..', '..', '..');
const FILES = [
  'app/(trip)/[id]/prepare.tsx',
  'app/festivals.tsx',
  'app/field/dialect.tsx',
  'src/home/useHomeData.ts',
  'src/components/AddPlaceToItineraryModal.tsx',
];

it.each(FILES)('🔴 %s — UTC 로 날짜를 떼지 않는다', (file) => {
  const source = readFileSync(join(ROOT, file), 'utf8');
  expect(source).not.toMatch(/toISOString\(\)\.slice\(0, ?10\)/);
});
