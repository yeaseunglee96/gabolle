# S15P21E201-2029 장소 한 줄 소개 만들기. 관광공사 개요가 있으면 그것을 줄이고, 없으면 저장된 사실만으로 쓴다.
# GMS(SSAFY AI 중계, 앱의 여행 이름 짓기·번역과 같은 길)로 부른다. 결과는 one_line.json 에 이어 쓴다(중단해도 이어 받음).
import json,re,os,sys,urllib.request,concurrent.futures as cf
KEY=open(os.environ.get('GMS_KEY_FILE','gms.key')).read().strip()
MODEL=os.environ.get('GMS_MODEL','gpt-4.1-mini')
LIMIT=int(sys.argv[1]) if len(sys.argv)>1 else 10**9
F=[l.rstrip('\n').split('\t') for l in open('facts.tsv',encoding='utf-8') if re.match(r'[0-9a-f]{8}-',l) and l.count('\t')>=7][:LIMIT]
OV=json.load(open('raw/overview.json',encoding='utf-8'))
OUT='one_line.json'; done=json.load(open(OUT,encoding='utf-8')) if os.path.exists(OUT) else {}
CAT={'FOOD':'음식점','CAFE_HEALING':'카페','CULTURE_TEMPLE':'문화·역사·사찰','CITY':'도시 명소','NATURE_WALK':'자연·산책','SEA_BEACH':'바다·해변','FESTIVAL_EVENT':'축제·행사'}
def facts(f):
    pid,_,name,cat,src,sid,addr,j=f[:8]
    try: x=json.loads(j)
    except: x={}
    d={'id':pid,'이름':name,'분류':CAT.get(cat,cat)}
    m=re.match(r'(?:부산광역시|부산)\s+(\S+[구군])',addr)
    if m: d['구']=m.group(1)
    if 'BUSINESS_SUBCATEGORY' in x: d['업종']=x['BUSINESS_SUBCATEGORY'].get('name')
    cu=[k.split(':',1)[1] for k in x if k.startswith('CUISINE_TAG:')]
    if cu: d['음식 종류']=cu
    if 'WHY_VISIT' in x: d['방문 이유']=[r['note'] for r in x['WHY_VISIT'].get('reasons',[])][:3]
    if 'MENU_ITEMS' in x:
        items=x['MENU_ITEMS'] if isinstance(x['MENU_ITEMS'],list) else x['MENU_ITEMS'].get('items',[])
        d['메뉴']=[i.get('name') or i.get('nameKo') for i in items[:4] if isinstance(i,dict)]
    if 'RECOGNITION' in x: d['인증']=x['RECOGNITION']
    ov=OV.get(pid,{}).get('overview','')
    if ov: d['관광공사 개요']=re.sub(r'<[^>]+>',' ',ov)[:700]
    return d
SYS="""너는 부산 여행 앱의 장소 카드에 붙는 한 줄 소개를 쓴다. 규칙:
- 주어진 사실(관광공사 개요·업종·메뉴·방문 이유·분류·구)에 있는 것만 쓴다. 없는 사실(전망, 역사, 맛 평가, 유명세, 가격)을 지어내지 않는다.
- 관광공사 개요가 있으면 그것을 한 줄로 줄인다. 없으면 업종·메뉴·방문 이유로 쓴다. 사실이 분류뿐이면 「해운대구의 카페」처럼 짧게만 쓴다.
- 한국어(ko)는 40자 이하의 명사형 구(예: 「순메밀 평양냉면과 한우 수육을 내는 냉면집」). 장소 이름을 되풀이하지 않는다. 마침표·따옴표·이모지 없음.
- 같은 뜻을 en, ja, zh-Hans, zh-Hant, vi 로 옮긴다. 각각 짧게(en 70자 이하). 고유명사(지명·음식)는 통용 표기를 쓰고 모르면 로마자로 둔다. 외국어 문장에 한글을 넣지 않는다.
- 용어: 곰장어=hagfish(ja ヌタウナギ, zh 盲鳗/盲鰻), 밀면=milmyeon(wheat cold noodles), 돼지국밥=dwaeji-gukbap(pork rice soup), 회=raw fish(sashimi), 모범음식점=Busan-certified model restaurant, 화상 중국집=Chinese restaurant run by ethnic Chinese.
- 출력은 JSON 객체 하나: {"<id>": {"ko":..,"en":..,"ja":..,"zh-Hans":..,"zh-Hant":..,"vi":..}, ...}"""
def call(batch):
    body={'model':MODEL,'temperature':0.2,'response_format':{'type':'json_object'},
          'messages':[{'role':'system','content':SYS},{'role':'user','content':json.dumps(batch,ensure_ascii=False)}]}
    r=urllib.request.Request('https://gms.ssafy.io/gmsapi/api.openai.com/v1/chat/completions',data=json.dumps(body).encode(),
        headers={'Authorization':'Bearer '+KEY,'Content-Type':'application/json'})
    for a in range(3):
        try: return json.loads(json.load(urllib.request.urlopen(r,timeout=180))['choices'][0]['message']['content'])
        except Exception as e: err=e
    print('fail',err,file=sys.stderr); return {}
todo=[facts(f) for f in F if f[0] not in done]
batches=[todo[i:i+12] for i in range(0,len(todo),12)]
with cf.ThreadPoolExecutor(4) as ex:
    for res,b in zip(ex.map(call,batches),batches):
        src={d['id']:('TOURAPI_OVERVIEW' if '관광공사 개요' in d else 'STORED_FACTS') for d in b}
        for pid,v in res.items():
            if pid in src and isinstance(v,dict): v['_source']=src[pid]; done[pid]=v
        json.dump(done,open(OUT,'w',encoding='utf-8'),ensure_ascii=False,indent=0)
print('done',len(done))
