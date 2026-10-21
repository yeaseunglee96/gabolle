# S15P21E201-2029 한 줄 소개 사실 점검. 1) 규칙 검사(전부) 2) Gemini 판정(인기 상위 TOP 곳) → qa/verdicts.json
# 판정: 언어마다 ok | fix(사실만으로 고친 문장) | drop(사실이 모자라 뺀다). 사람이 결과를 보고 SQL 을 만든다.
import json,re,os,sys,csv,urllib.request,concurrent.futures as cf
B=os.path.dirname(os.path.abspath(__file__))+'/..'
TOP=int(sys.argv[1]) if len(sys.argv)>1 else 500
KEY=open(os.environ['GMS_KEY_FILE']).read().strip()
MODEL=os.environ.get('GMS_MODEL','gpt-4.1')
LANG=['ko','en','ja','zh-Hans','zh-Hant','vi']
H=re.compile('[가-힣]')
F={l.split('\t')[0]:l.rstrip('\n').split('\t') for l in open(B+'/facts.tsv',encoding='utf-8') if re.match(r'[0-9a-f]{8}-',l) and l.count('\t')>=7}
order={pid:i for i,pid in enumerate(F)}
OV=json.load(open(B+'/raw/overview.json',encoding='utf-8'))
CAT={'FOOD':'음식점','CAFE_HEALING':'카페','CULTURE_TEMPLE':'문화·역사·사찰','CITY':'도시 명소','NATURE_WALK':'자연·산책','SEA_BEACH':'바다·해변','FESTIVAL_EVENT':'축제·행사'}
def facts(pid):
    _,cnt,name,cat,src,sid,addr,j=F[pid][:8]
    try: x=json.loads(j)
    except: x={}
    d={'이름':name,'분류':CAT.get(cat,cat),'추천 횟수':int(cnt)}
    m=re.match(r'(?:부산광역시|부산)\s+(\S+[구군])',addr)
    if m: d['구']=m.group(1)
    if 'BUSINESS_SUBCATEGORY' in x: d['업종']=x['BUSINESS_SUBCATEGORY'].get('name')
    cu=[k.split(':',1)[1] for k in x if k.startswith('CUISINE_TAG:')]
    if cu: d['음식 종류']=cu
    if 'WHY_VISIT' in x: d['방문 이유']=[r['note'] for r in x['WHY_VISIT'].get('reasons',[])]
    if 'MENU_ITEMS' in x:
        it=x['MENU_ITEMS'] if isinstance(x['MENU_ITEMS'],list) else x['MENU_ITEMS'].get('items',[])
        d['메뉴']=[i.get('name') or i.get('nameKo') for i in it if isinstance(i,dict)]
    if 'RECOGNITION' in x: d['인증']=x['RECOGNITION']
    ov=OV.get(pid,{}).get('overview','')
    if ov: d['관광공사 개요']=re.sub(r'<[^>]+>',' ',ov)[:900]
    return d
rows=list(csv.DictReader(open(B+'/changes-one-line.csv',encoding='utf-8-sig')))
rows.sort(key=lambda r:order.get(r['place_id'],10**9))
# (첫 90곳은 Gemini 3.8 flash 로, 나머지는 무료 한도가 끝나 GMS gpt-4.1 로 판정했다)
# 1) 규칙 검사
rule={}
BEEF=re.compile('한우|소고기|우육|소 ?수육|양지|차돌|갈비살'); 
for r in rows:
    f=[]
    for l in LANG[1:]:
        t=r[l]
        if t and H.search(t): f.append((l,'KOREAN_LEAK'))
    ko=r['ko']
    if BEEF.search(ko) and '돼지' not in ko and re.search(r'\bpork\b',r['en'],re.I): f.append(('en','BEEF_AS_PORK'))
    if BEEF.search(ko) and '돼지' not in ko and 'heo' in r['vi'].lower().split(): f.append(('vi','BEEF_AS_PORK'))
    if '돼지' in ko and '소' not in ko and re.search(r'\bbeef\b',r['en'],re.I): f.append(('en','PORK_AS_BEEF'))
    if f: rule[r['place_id']]=f
SYS="""너는 부산 여행 앱 장소 카드의 「한 줄 소개」를 사실과 대조하는 검수자다. 입력: 장소마다 facts(저장된 사실)와 lines(언어별 한 줄).
언어마다 판정한다:
- ok: facts 와 ko 줄에 맞고 자연스럽다.
- fix: 틀렸지만 facts 만으로 바로잡을 수 있다 → 고친 문장을 쓴다. 고칠 때 facts 에 없는 것(맛 평가·전망·역사·유명세·가격·분위기)을 넣지 않는다. ko 는 40자 이하 명사형 구, 장소 이름 반복 금지, 마침표 없음. 다른 언어는 고친 ko 와 같은 뜻.
- drop: 틀렸는데 facts 가 너무 빈약해 바로잡을 수 없다.
틀린 것의 예: 음식·고기 종류 오역(한우 수육→boiled pork, 소→돼지), 분류와 다른 장소 유형(카페를 식당이라 함, 절을 공원이라 함), facts 에 없는 지어낸 특징, 외국어 줄에 한글, ko 와 뜻이 다른 번역, 아무 장소에나 붙는 너무 일반적인 줄(「맛있는 음식을 즐길 수 있는 곳」).
ko 가 fix/drop 이면 다른 언어도 그 ko 에 맞춰 fix 또는 drop 한다(ko 를 drop 하면 전부 drop).
「업종」 값은 공공 업종 분류라 실제와 다를 수 있다 — 메뉴·방문 이유·개요가 다른 것을 말하면 그쪽을 따른다. 「구」는 사실이다.
사소한 표현 차이·자연스러운 의역은 ok. 확신이 없으면 ok.
출력: JSON {"<id>": {"<lang>": {"v":"ok|fix|drop","why":"한 구절 이유(ok면 생략)","text":"fix일 때 고친 문장"}}} — 여섯 언어 모두."""
def call(batch):
    user=json.dumps({r['place_id']:{'facts':facts(r['place_id']),'lines':{l:r[l] for l in LANG if r[l]}} for r in batch},ensure_ascii=False)
    body={'model':MODEL,'temperature':0,'response_format':{'type':'json_object'},'messages':[{'role':'system','content':SYS},{'role':'user','content':user}]}
    req=urllib.request.Request('https://gms.ssafy.io/gmsapi/api.openai.com/v1/chat/completions',data=json.dumps(body).encode(),headers={'Authorization':'Bearer '+KEY,'Content-Type':'application/json'})
    for a in range(4):
        try: return json.loads(json.load(urllib.request.urlopen(req,timeout=300))['choices'][0]['message']['content'])
        except Exception as e:
            err=e; import time; time.sleep(20*(a+1))
    print('fail',err,file=sys.stderr); return {}
OUT=B+'/qa/verdicts.json'
done=json.load(open(OUT,encoding='utf-8')) if os.path.exists(OUT) else {}
top=[r for r in rows[:TOP] if r['place_id'] not in done]
bs=[top[i:i+10] for i in range(0,len(top),10)]
with cf.ThreadPoolExecutor(4) as ex:
    for res,b in zip(ex.map(call,bs),bs):
        for r in b:
            if r['place_id'] in res: done[r['place_id']]=res[r['place_id']]
        json.dump(done,open(OUT,'w',encoding='utf-8'),ensure_ascii=False,indent=0)
json.dump({k:v for k,v in rule.items()},open(B+'/qa/rule-flags.json','w',encoding='utf-8'),ensure_ascii=False,indent=0)
print('rows',len(rows),'judged',len(done),'ruleFlagged',len(rule))
