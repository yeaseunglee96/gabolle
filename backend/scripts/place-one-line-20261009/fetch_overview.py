import json,re,urllib.request,math,os,time
K=__import__('os').environ['DATA_GO_KR_KEY']
def key(t): return re.sub(r'[^0-9a-z가-힣]','',re.sub(r'[(（][^)）]*[)）]','',t.lower()))
P={l.split('\t')[0]:l.split('\t') for l in open('places.tsv',encoding='utf-8') if re.match(r'[0-9a-f]{8}-',l)}
F=[l.rstrip('\n').split('\t') for l in open('facts.tsv',encoding='utf-8') if re.match(r'[0-9a-f]{8}-',l) and l.count('	')>=7]
kor=json.load(open('raw/KorService2.json',encoding='utf-8'))
byk={}
for it in kor: byk.setdefault(key(it['title']),[]).append(it); byk.setdefault(key(it['title']).replace('부산',''),[]).append(it)
cid={}
for f in F:
    pid=f[0]; p=P[pid]
    if f[4]=='TOURAPI': cid[pid]=f[5]; continue
    k=key(f[2]); best=None
    for it in byk.get(k,[])+byk.get(k.replace('부산',''),[]):
        dl,dg=abs(float(p[6])-float(it['mapy'])),abs(float(p[7])-float(it['mapx']))
        if dl>0.0045 or dg>0.0055: continue
        d=math.hypot(dl*111000,dg*91000)
        if not best or d<best[0]: best=(d,it['contentid'])
    if best: cid[pid]=best[1]
out=json.load(open('raw/overview.json',encoding='utf-8')) if os.path.exists('raw/overview.json') else {}
for pid,c in cid.items():
    if pid in out: continue
    u=f'https://apis.data.go.kr/B551011/KorService2/detailCommon2?serviceKey={K}&MobileOS=ETC&MobileApp=gabolle&_type=json&contentId={c}'
    try:
        it=json.load(urllib.request.urlopen(u,timeout=30))['response']['body']['items']
        it=it['item'][0] if it else {}
        out[pid]={'contentid':c,'overview':it.get('overview','')}
    except Exception as e: print(pid,c,e)
json.dump(out,open('raw/overview.json','w',encoding='utf-8'),ensure_ascii=False)
print(len(cid),len(out),sum(1 for v in out.values() if v['overview']))
