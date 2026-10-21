import json,re,csv,math
H=re.compile('[가-힣]')
def key(t): return re.sub(r'[^0-9a-z가-힣]','',re.sub(r'[(（][^)）]*[)）]','',t.lower()))
places=[l.rstrip('\n').split('\t') for l in open('places.tsv',encoding='utf-8') if l.count('\t')>10]
cols={'EngService2':2,'JpnService2':3,'ChsService2':4,'ChtService2':5}
lang={'EngService2':'en','JpnService2':'ja','ChsService2':'zh-Hans','ChtService2':'zh-Hant'}
def split_title(t):
    m=re.search(r'[(（]([^()（）]*[가-힣][^()（）]*)[)）]\s*$',t)
    if not m: return None,None
    local=t[:m.start()].strip()
    return local,m.group(1)
byk={}
for p in places:
    k=key(p[1]); byk.setdefault(k,[]).append(p); byk.setdefault(k.replace('부산',''),[]).append(p)
changes=[]
for s,ci in cols.items():
    items=json.load(open(f'raw/{s}.json',encoding='utf-8'))
    best={}
    for it in items:
        local,ko=split_title(it['title'])
        if not local or H.search(local) or len(local)>200: continue
        lat,lng=float(it['mapy'] or 0),float(it['mapx'] or 0)
        for k in {key(ko),key(ko).replace('부산','')}:
            if len(k)<2: continue
            for p in byk.get(k,[]):
                if not p[6]: continue
                dlat,dlng=abs(float(p[6])-lat),abs(float(p[7])-lng)
                if dlat>0.0045 or dlng>0.0055: continue
                d=math.hypot(dlat*111000,dlng*91000)
                if p[0] not in best or d<best[p[0]][0]: best[p[0]]=(d,local,it['contentid'],ko,p)
    for pid,(d,local,cid,ko,p) in best.items():
        if p[ci]: continue
        changes.append(dict(place_id=pid,name_ko=p[1],field=lang[s],old='',new=local,source=f'TourAPI {s} contentid={cid}',source_title_ko=ko,distance_m=round(d),rec=p[11],itin=p[12]))
w=csv.DictWriter(open('changes-names-tourapi.csv','w',encoding='utf-8-sig',newline=''),fieldnames=list(changes[0]))
w.writeheader();w.writerows(changes)
from collections import Counter;print(Counter(c['field'] for c in changes))
