/**
 * 신호 하나하나가 **정답을 조금이라도 가려내는가** 를 본다. 조정용 26곳만 쓴다.
 *
 * AUC(Area Under ROC — *아무 정답 하나와 아무 비정답 하나를 뽑았을 때 정답이 더 위에
 * 올 확률*). 0.5 면 동전 던지기와 같다. 0.5 보다 크면 그 신호가 크다는 것이 정답 쪽이고,
 * 작으면 반대 방향이다. Recall@200 은 정답 26곳에서 너무 튀어서, 신호가 있는지 없는지는
 * 이 값으로 본다.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const feat = JSON.parse(fs.readFileSync(path.join(HERE, "data", "features.json"), "utf8"));
const tune = JSON.parse(fs.readFileSync(path.join(HERE, "data", "split-tune.json"), "utf8"));
const pos = new Set(tune.items.map((i) => i.poiId));

const rows = feat.rows;
const P = rows.filter((r) => pos.has(r.id));
const Nn = rows.filter((r) => !pos.has(r.id));

function auc(f) {
  let win = 0, tie = 0;
  for (const p of P) for (const n of Nn) {
    const a = f(p), b = f(n);
    if (a > b) win++;
    else if (a === b) tie++;
  }
  return (win + tie / 2) / (P.length * Nn.length);
}
const med = (a) => {
  const s = [...a].sort((x, y) => x - y);
  return s.length % 2 ? s[(s.length - 1) / 2] : (s[s.length / 2 - 1] + s[s.length / 2]) / 2;
};

const FEATURES = {
  dens150: (r) => r.dens150,
  dens300: (r) => r.dens300,
  "transitM(작을수록)": (r) => -r.transitM,
  "subwayM(작을수록)": (r) => -r.subwayM,
  "touristM(작을수록)": (r) => -r.touristM,
  tagRichness: (r) => r.tagRichness,
  hasCuisine: (r) => (r.hasCuisine ? 1 : 0),
  hasOpeningHours: (r) => (r.hasOpeningHours ? 1 : 0),
  hasPhone: (r) => (r.hasPhone ? 1 : 0),
  hasWebsite: (r) => (r.hasWebsite ? 1 : 0),
  "브랜드없음(비프랜차이즈)": (r) => (r.hasBrand ? 0 : 1),
  "이름있음": (r) => (r.name ? 1 : 0),
  "영문이름있음": (r) => (r.nameEn ? 1 : 0),
  "식당(카페아님)": (r) => (r.kind === "restaurant" ? 1 : 0),
};

console.log(`조정용 정답 ${P.length}곳 vs 비정답 ${Nn.length}곳\n`);
console.log("신호\tAUC\t정답 중앙값\t전체 중앙값");
const out = [];
for (const [name, f] of Object.entries(FEATURES)) {
  out.push({ name, a: auc(f), mp: med(P.map(f)), ma: med(rows.map(f)) });
}
out.sort((a, b) => Math.abs(b.a - 0.5) - Math.abs(a.a - 0.5));
for (const o of out) {
  console.log(`${o.name}\t${o.a.toFixed(3)}\t${o.mp}\t${o.ma}`);
}
