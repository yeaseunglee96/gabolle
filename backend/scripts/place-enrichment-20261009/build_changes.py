# S15P21E201-2030 장소 다국어 이름·영업시간 채우기 — 바뀔 줄을 만든다(DB 는 안 바꾼다).
# 출처 우선순위: 관광공사 다국어(공식) > OSM 그 장소 자신의 태그(같은 OSM 번호) > 위키데이터(이름 열쇠 같고 500m 안).
# 빈 칸만 채운다. 한글이 섞인 외국어 이름, ';' 로 여러 값이 붙은 이름은 버린다.
import json,re,csv,math
from collections import Counter
H=re.compile('[가-힣ㄱ-ㅎ]')
def key(t): return re.sub(r'[^0-9a-z가-힣]','',re.sub(r'[(（][^)）]*[)）]','',t.lower()))
P={}
for l in open('places.tsv',encoding='utf-8'):
    c=l.rstrip('\n').split('\t')
    if len(c)<16 or not re.match(r'[0-9a-f]{8}-',c[0]): continue
    P[c[0]]=c
FIELD={'en':2,'ja':3,'zh-Hans':4,'zh-Hant':5}
COL={'en':'name_en','ja':'name_ja','zh-Hans':'name_zh_hans','zh-Hant':'name_zh_hant'}
picked={}  # (pid,lang)->row
def ok(lang,v,p):
    if not v: return False
    v=v.strip()
    if H.search(v) or ';' in v or len(v)>200 or len(v)<2: return False
    if lang=='en' and not re.fullmatch(r"[\x20-\x7E\u00C0-\u024F’‘–—·]+",v): return False
    if lang!='en' and re.fullmatch(r'[\x20-\x7E]+',v) and P[p][2] and v.lower()!=P[p][2].lower(): return False
    return True
def offer(pid,lang,v,src,ev):
    p=P[pid]
    if p[FIELD[lang]] or (pid,lang) in picked or not ok(lang,v,pid): return
    picked[(pid,lang)]=dict(place_id=pid,name_ko=p[1],column=COL[lang],old='',new=v.strip(),source=src,evidence=ev,rec_count=p[11],itin_count=p[12])
# 1 TourAPI
for r in csv.DictReader(open('changes-names-tourapi.csv',encoding='utf-8-sig')):
    offer(r['place_id'],r['field'],r['new'],r['source'],f"제목 속 한국어 「{r['source_title_ko']}」 이름 열쇠 같음, {r['distance_m']}m")
# 2 OSM same id — 영어 이름은 한국어 이름의 로마자 앞 세 글자가 들어 있을 때만(「달맞이 벚꽃길」→「Cherry Blossom Viewing」 같은
#   뜻풀이·다른 이름을 거른다). 체인점은 OSM 에 brand:en/brand:wikidata 가 있으면 통과.
I='g kk n d tt r m b pp s ss  j jj ch k t p h'.split(' ')
V='a ae ya yae eo e yeo ye o wa wae oe yo u wo we wi yu eu ui i'.split(' ')
F=['','k','k','k','n','n','n','t','l','k','m','l','l','l','p','l','m','p','p','t','t','ng','t','t','k','t','p','t']
def rom(t):
    o=''
    for ch in t:
        c=ord(ch)-0xAC00
        if 0<=c<11172: o+=I[c//588]+V[(c%588)//28]+F[c%28]
        elif ch.isascii() and ch.isalnum(): o+=ch.lower()
    return o
def en_plausible(ko,en,tags):
    if tags.get('brand:en') or tags.get('brand:wikidata'): return True
    r=rom(ko.replace('부산',''))[:3]; e=re.sub('[^a-z0-9]','',en.lower())
    return len(r)==3 and r in e
# 2 OSM same id
dropped=[]
osm=json.load(open('raw/osm_by_id.json',encoding='utf-8'))
for pid,p in P.items():
    if p[9]!='OSM' or p[10] not in osm: continue
    t=osm[p[10]]['tags']
    if t.get('name')!=p[1] and t.get('name:ko')!=p[1]: continue
    src=f"OSM {osm[p[10]]['type']}/{p[10]} (ODbL)"
    if t.get('name:en') and en_plausible(p[1],t['name:en'],t): offer(pid,'en',t.get('name:en'),src,'같은 OSM 항목의 name:en')
    elif t.get('name:en'): dropped.append((p[1],t['name:en']))
    offer(pid,'ja',t.get('name:ja'),src,'같은 OSM 항목의 name:ja')
    offer(pid,'zh-Hans',t.get('name:zh-Hans') or t.get('name:zh'),src,'같은 OSM 항목의 name:zh(-Hans)')
    offer(pid,'zh-Hant',t.get('name:zh-Hant'),src,'같은 OSM 항목의 name:zh-Hant')
# 3 Wikidata
wd={}
for b in json.load(open('raw/wikidata.json',encoding='utf-8')):
    q=b['item']['value'].split('/')[-1]; it=wd.setdefault(q,{'ko':b['ko']['value']})
    for f in ['en','ja','zhHans','zhHant','zh']:
        if f in b: it[f]=b[f]['value']
    lng,lat=re.match(r'Point\(([-0-9.]+) ([-0-9.]+)\)',b['coord']['value']).groups(); it['lat'],it['lng']=float(lat),float(lng)
byk={}
for pid,p in P.items():
    k=key(p[1]); byk.setdefault(k,set()).add(pid); byk.setdefault(k.replace('부산',''),set()).add(pid)
for q,it in wd.items():
    k=key(it['ko'])
    if len(k)<2: continue
    best=None
    for pid in byk.get(k,set())|byk.get(k.replace('부산',''),set()):
        p=P[pid]
        if not p[6]: continue
        dl,dg=abs(float(p[6])-it['lat']),abs(float(p[7])-it['lng'])
        if dl>0.0045 or dg>0.0055: continue
        d=math.hypot(dl*111000,dg*91000)
        if not best or d<best[0]: best=(d,pid)
    if not best: continue
    d,pid=best; src=f'Wikidata {q} (CC0)'; ev=f"한국어 이름 「{it['ko']}」 열쇠 같음, {round(d)}m"
    offer(pid,'en',it.get('en'),src,ev); offer(pid,'ja',it.get('ja'),src,ev)
    offer(pid,'zh-Hans',it.get('zhHans') or it.get('zh'),src,ev); offer(pid,'zh-Hant',it.get('zhHant'),src,ev)
rows=sorted(picked.values(),key=lambda r:(-int(r['rec_count'])-int(r['itin_count']),r['name_ko']))
w=csv.DictWriter(open('changes-names.csv','w',encoding='utf-8-sig',newline=''),fieldnames=list(rows[0])); w.writeheader(); w.writerows(rows)
open('dropped-osm-en.txt','w',encoding='utf-8').write(chr(10).join(a+' | '+b for a,b in dropped))
print('dropped',len(dropped))
print(Counter((r['column'],r['source'].split()[0]) for r in rows))
# 영업시간 (OSM opening_hours) — 단순한 꼴만 읽는다
DAYS=['mo','tu','we','th','fr','sa','su']; NAMES=['mon','tue','wed','thu','fri','sat','sun']
def parse(oh):
    by={n:None for n in NAMES}; closed=[]
    for rule in [r.strip() for r in oh.split(';') if r.strip()]:
        m=re.fullmatch(r'(?:((?:Mo|Tu|We|Th|Fr|Sa|Su)(?:-(?:Mo|Tu|We|Th|Fr|Sa|Su))?(?:,(?:Mo|Tu|We|Th|Fr|Sa|Su)(?:-(?:Mo|Tu|We|Th|Fr|Sa|Su))?)*)(?:,PH)?\s+)?(off|closed|(?:\d\d:\d\d-\d\d:\d\d)(?:,\d\d:\d\d-\d\d:\d\d)*)',rule)
        if not m:
            if rule=='24/7': return {n:[['00:00','24:00']] for n in NAMES},[]
            if re.fullmatch(r'PH\s+(off|closed)',rule): continue
            return None
        ds=set()
        if m.group(1):
            for part in m.group(1).split(','):
                a,_,b=part.partition('-'); i=DAYS.index(a.lower()); j=DAYS.index((b or a).lower())
                k=i
                while True:
                    ds.add(NAMES[k])
                    if k==j: break
                    k=(k+1)%7
        else: ds=set(NAMES)
        if m.group(2) in ('off','closed'):
            for d in ds: by[d]=[]
        else:
            sp=[]
            for seg in m.group(2).split(','):
                a,b=seg.split('-')
                if int(a[:2])>24 or int(b[:2])>48: return None
                if b<a: b='24:00' if b=='00:00' else f'{int(b[:2])+24:02d}:{b[3:]}'
                if b>'24:00': b='24:00'  # 자정 넘김은 24:00 에서 자른다(기존 판들과 같은 칸 규칙을 넘지 않게)
                sp.append([a,b])
            for d in ds: by[d]=sp
    if all(v is None for v in by.values()): return None
    for d in NAMES:
        if by[d] is None: return None   # 요일이 빠지면 모른다 — 넣지 않는다
    closed=[d for d in NAMES if by[d]==[]]
    return by,closed
hrows=[];skip=Counter()
for pid,p in P.items():
    if p[9]!='OSM' or p[10] not in osm or int(p[13])>0: continue
    t=osm[p[10]]['tags']; oh=t.get('opening_hours')
    if not oh or (t.get('name')!=p[1] and t.get('name:ko')!=p[1]): continue
    r=parse(oh)
    if not r: skip['unparsed']+=1; continue
    by,closed=r
    v={'raw':{'hoursField':'opening_hours','hoursValue':oh,'restField':None,'restValue':None},'byDay':by,'status':'PARSED'}
    if closed: v['closedDays']=closed
    hrows.append(dict(place_id=pid,name_ko=p[1],osm=f"{osm[p[10]]['type']}/{p[10]}",opening_hours=oh,value=json.dumps(v,ensure_ascii=False),rec_count=p[11],itin_count=p[12]))
w=csv.DictWriter(open('changes-hours.csv','w',encoding='utf-8-sig',newline=''),fieldnames=list(hrows[0])); w.writeheader(); w.writerows(hrows)
print('hours',len(hrows),skip)
