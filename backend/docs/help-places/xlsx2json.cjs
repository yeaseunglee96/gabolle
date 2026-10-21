// 아주 작은 xlsx 읽기 — 첫 시트를 [머리, ...행] 로. node xlsx2json.cjs <풀어 둔 폴더> <out.json> [행 수 제한]
const fs = require('fs');
const path = require('path');
const [dir, out, limitArg] = process.argv.slice(2);
const limit = limitArg ? Number(limitArg) : Infinity;
const unxml = (s) => s.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&amp;/g, '&');
const sstPath = path.join(dir, 'xl', 'sharedStrings.xml');
const shared = [];
if (fs.existsSync(sstPath)) {
  const sst = fs.readFileSync(sstPath, 'utf8');
  for (const si of sst.matchAll(/<si>([\s\S]*?)<\/si>/g)) {
    shared.push(unxml([...si[1].matchAll(/<t[^>]*>([\s\S]*?)<\/t>/g)].map((m) => m[1]).join('')));
  }
}
const sheetDir = path.join(dir, 'xl', 'worksheets');
const sheet = fs.readdirSync(sheetDir).filter((f) => /^sheet\d+\.xml$/.test(f)).sort()[0];
const xml = fs.readFileSync(path.join(sheetDir, sheet), 'utf8');
const col = (ref) => { const letters = ref.match(/^[A-Z]+/)[0]; let n = 0; for (const ch of letters) n = n * 26 + (ch.charCodeAt(0) - 64); return n - 1; };
const rows = [];
for (const row of xml.matchAll(/<row[^>]*>([\s\S]*?)<\/row>/g)) {
  const cells = [];
  for (const c of row[1].matchAll(/<c r="([A-Z]+\d+)"([^>]*?)(?:\/>|>([\s\S]*?)<\/c>)/g)) {
    const [, ref, attrs, body = ''] = c;
    const t = (attrs.match(/t="(\w+)"/) || [])[1];
    let v = (body.match(/<v>([\s\S]*?)<\/v>/) || [])[1];
    if (t === 's' && v !== undefined) v = shared[Number(v)];
    else if (t === 'inlineStr') v = unxml([...body.matchAll(/<t[^>]*>([\s\S]*?)<\/t>/g)].map((m) => m[1]).join(''));
    else if (v !== undefined) v = unxml(v);
    cells[col(ref)] = v ?? '';
  }
  rows.push(Array.from(cells, (x) => x ?? ''));
  if (rows.length > limit) break;
}
fs.writeFileSync(out, JSON.stringify(rows));
console.log(sheet, 'rows', rows.length, 'header', JSON.stringify(rows[0]));
