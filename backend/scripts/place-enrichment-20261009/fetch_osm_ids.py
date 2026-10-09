import json,urllib.request,urllib.error,time
ids=sorted({l.split('\t')[10] for l in open('places.tsv',encoding='utf-8') if l.count('\t')>10 and l.split('\t')[9]=='OSM'})
H={'User-Agent':'gabolle-busan-travel-app/1.0 (S15P21E201)'}
got={}
def fetch(kind,batch):
    u=f'https://api.openstreetmap.org/api/0.6/{kind}s.json?{kind}s='+','.join(batch)
    try: return json.load(urllib.request.urlopen(urllib.request.Request(u,headers=H),timeout=60))['elements']
    except urllib.error.HTTPError as e:
        if e.code in (404,410) and len(batch)>1:
            m=len(batch)//2; return fetch(kind,batch[:m])+fetch(kind,batch[m:])
        if e.code in (404,410): return []
        raise
for kind in ['node','way']:
    todo=[i for i in ids if i not in got]
    for i in range(0,len(todo),150):
        for e in fetch(kind,todo[i:i+150]):
            if e.get('visible',True) and e.get('tags'): got[str(e['id'])]={'type':kind,'tags':e['tags']}
        time.sleep(1)
    print(kind,len(got))
json.dump(got,open('raw/osm_by_id.json','w',encoding='utf-8'),ensure_ascii=False)
