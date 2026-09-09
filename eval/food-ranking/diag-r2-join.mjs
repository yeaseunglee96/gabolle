import { loadSbizFood } from "./lib/sbiz.mjs";
import { loadFoodPois, buildGrid, neighbors } from "./lib/pool.mjs";
function norm(s){return String(s??"").replace(/\(.*?\)/g,"").replace(/[\s\-·.,'"’`]/g,"").toLowerCase();}
function cp(a,b){let i=0;while(i<a.length&&i<b.length&&a[i]===b[i])i++;return i;}
const sbiz=await loadSbizFood();
const osm=loadFoodPois().filter(p=>p.tags["name:ko"]??p.tags.name);
const g=buildGrid(sbiz.map(s=>({lat:s.lat,lon:s.lon})));
for(const R of [120,200,300]){
  for(const PMIN of [3,2]){
    let hit=0;
    for(const p of osm){
      const pn=norm(p.tags["name:ko"]??p.tags.name); if(pn.length<2) continue;
      const near=neighbors(g,p.lat,p.lon,R);
      let ok=false;
      for(const {i} of near){
        const sn=norm(sbiz[i].name); if(sn.length<2) continue;
        if(sn===pn||sn.includes(pn)||pn.includes(sn)||(sn.length>=PMIN&&pn.length>=PMIN&&cp(sn,pn)>=PMIN)){ok=true;break;}
      }
      if(ok)hit++;
    }
    console.log(`R=${R}m prefix>=${PMIN}: ${hit}/${osm.length} = ${(hit/osm.length*100).toFixed(1)}%`);
  }
}
// 안 붙은 것 표본
const R=120;let shown=0;
for(const p of osm){
  const pn=norm(p.tags["name:ko"]??p.tags.name); if(pn.length<2) continue;
  const near=neighbors(g,p.lat,p.lon,R);
  let ok=false;
  for(const {i} of near){const sn=norm(sbiz[i].name);if(sn.length<2)continue;
    if(sn===pn||sn.includes(pn)||pn.includes(sn)||(sn.length>=3&&pn.length>=3&&cp(sn,pn)>=3)){ok=true;break;}}
  if(!ok&&shown<12){shown++;console.log(`  안붙음: "${p.tags["name:ko"]??p.tags.name}" 주변 상가정보 ${near.length}곳 예:`,near.slice(0,3).map(n=>sbiz[n.i].name).join(" / "));}
}
