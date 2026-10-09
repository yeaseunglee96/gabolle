import json,urllib.request,sys,time
K=__import__('os').environ['DATA_GO_KR_KEY']
for s in ['EngService2','JpnService2','ChsService2','ChtService2','KorService2']:
    out=[];p=1
    while True:
        u=f'https://apis.data.go.kr/B551011/{s}/areaBasedList2?serviceKey={K}&MobileOS=ETC&MobileApp=gabolle&_type=json&lDongRegnCd=26&numOfRows=1000&pageNo={p}'
        b=json.load(urllib.request.urlopen(u,timeout=60))['response']['body']
        it=b['items']['item'] if b['items'] else []
        out+=it
        if p*1000>=b['totalCount']: break
        p+=1
    json.dump(out,open(f'raw/{s}.json','w',encoding='utf-8'),ensure_ascii=False)
    print(s,len(out))
