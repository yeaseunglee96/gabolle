import json,urllib.request,urllib.parse
Q='''SELECT ?item ?ko ?en ?ja ?zhHans ?zhHant ?zh ?vi ?coord WHERE {
  SERVICE wikibase:box { ?item wdt:P625 ?coord .
    bd:serviceParam wikibase:cornerSouthWest "Point(128.75 34.88)"^^geo:wktLiteral .
    bd:serviceParam wikibase:cornerNorthEast "Point(129.32 35.40)"^^geo:wktLiteral . }
  ?item rdfs:label ?ko FILTER(LANG(?ko)="ko")
  OPTIONAL{?item rdfs:label ?en FILTER(LANG(?en)="en")}
  OPTIONAL{?item rdfs:label ?ja FILTER(LANG(?ja)="ja")}
  OPTIONAL{?item rdfs:label ?zhHans FILTER(LANG(?zhHans)="zh-hans")}
  OPTIONAL{?item rdfs:label ?zhHant FILTER(LANG(?zhHant)="zh-hant")}
  OPTIONAL{?item rdfs:label ?zh FILTER(LANG(?zh)="zh")}
  OPTIONAL{?item rdfs:label ?vi FILTER(LANG(?vi)="vi")}
}'''
r=urllib.request.urlopen(urllib.request.Request('https://query.wikidata.org/sparql?query='+urllib.parse.quote(Q),headers={'Accept':'application/sparql-results+json','User-Agent':'gabolle-busan-travel-app/1.0 (S15P21E201)'}),timeout=120)
b=json.load(r)['results']['bindings']
json.dump(b,open('raw/wikidata.json','w',encoding='utf-8'),ensure_ascii=False);print(len(b))
