#!/usr/bin/env node
/**
 * 경사 기준선 보정 — slope.mjs 의 BASELINE_M 을 정하는 근거
 *
 * 방법: 해안 매립지(최고 고도 12m 이하) 도로를 "실제로는 평지" 대조군으로 놓는다.
 *       거기서 "8% 이상 경사" 가 나오면 그것은 전부 거짓 양성이다.
 *       거짓 양성이 0 이 되면서 산지 신호는 살아있는 기준선을 고른다.
 *
 * 🔴 DEM 을 국가 5m 로 교체하면 이 스크립트를 다시 돌려 기준선을 다시 정한다.
 *    5m 는 수직오차가 훨씬 작아 30m 기준선으로 되돌릴 수 있을 것이다.
 *
 *   node process/calibrate-slope.mjs
 */
import { readFile, readdir } from 'node:fs/promises'
import { decodePNG, terrariumToElevation } from './process/png.mjs'
const ZOOM=15,TILE=256,tiles=new Map()
for (const f of (await readdir('data/raw/dem/15')).filter(x=>x.endsWith('.png'))) {
  const [x,y]=f.replace('.png','').split('_')
  tiles.set(`${x}_${y}`,terrariumToElevation(decodePNG(await readFile('data/raw/dem/15/'+f))))
}
const R=6371000,rad=d=>d*Math.PI/180
const dist=(a,b)=>{const dLat=rad(b.lat-a.lat),dLon=rad(b.lon-a.lon)
  const h=Math.sin(dLat/2)**2+Math.cos(rad(a.lat))*Math.cos(rad(b.lat))*Math.sin(dLon/2)**2
  return 2*R*Math.asin(Math.sqrt(h))}
const gx=lon=>(lon+180)/360*2**ZOOM*TILE
const gy=lat=>{const r=rad(lat);return (1-Math.log(Math.tan(r)+1/Math.cos(r))/Math.PI)/2*2**ZOOM*TILE}
const el=(lat,lon)=>{const X=gx(lon),Y=gy(lat)
  const t=tiles.get(`${Math.floor(X/TILE)}_${Math.floor(Y/TILE)}`);if(!t)return null
  return t[(Math.floor(Y)%TILE)*TILE+(Math.floor(X)%TILE)]}

const ways=[...JSON.parse(await readFile('data/raw/overpass/road.json','utf8')).elements,
            ...JSON.parse(await readFile('data/raw/overpass/walk.json','utf8')).elements]
  .filter(w=>w.type==='way'&&w.geometry&&w.geometry.length>3)

// 대조군: 전 지점 고도 12m 이하 = 해안 매립지. 실제로는 평지여야 한다
const flat=[], hilly=[]
for (const w of ways) {
  const e=w.geometry.map(p=>el(p.lat,p.lon))
  if (e.some(v=>v==null)) continue
  const mx=Math.max(...e)
  ;(mx<=12?flat:mx>=60?hilly:[]).push?.({w,e})
}
console.log(`대조군(해안 평지) ${flat.length}개 / 산복도로(60m+) ${hilly.length}개\n`)

for (const BASE of [30,60,100,200]) {
  const calc=set=>{
    const all=[]
    for (const {w,e} of set) {
      let acc=0; const s=[{d:0,h:e[0]}]
      for(let i=1;i<w.geometry.length;i++){acc+=dist(w.geometry[i-1],w.geometry[i]);s.push({d:acc,h:e[i]})}
      for(let i=0;i<s.length;i++){let j=i
        while(j<s.length-1&&s[j].d-s[i].d<BASE)j++
        const run=s[j].d-s[i].d; if(run<BASE*0.6)break
        all.push(Math.abs(s[j].h-s[i].h)/run)}
    }
    if(!all.length)return null
    all.sort((a,b)=>a-b)
    return {p50:all[Math.floor(all.length*.5)],p90:all[Math.floor(all.length*.9)],
            over8:all.filter(v=>v>=.08).length/all.length}
  }
  const f=calc(flat),h=calc(hilly)
  if(!f||!h)continue
  console.log(`기준선 ${String(BASE).padStart(3)}m │ 평지 중앙 ${(f.p50*100).toFixed(1)}% p90 ${(f.p90*100).toFixed(1)}% · 8%↑ ${(f.over8*100).toFixed(0)}%  ‖  산지 중앙 ${(h.p50*100).toFixed(1)}% 8%↑ ${(h.over8*100).toFixed(0)}%`)
}
console.log('\n평지의 "8%↑" 는 전부 거짓 양성이다. 이 값이 떨어지는 기준선을 써야 한다.')
