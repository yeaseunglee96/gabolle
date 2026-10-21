# one_line.json → 검사 → place.one_line 을 채우는 SQL(빈 칸만) + 바뀐 목록 CSV.
import json,re,csv
H=re.compile('[가-힣ㄱ-ㅎ]')
d=json.load(open('one_line.json',encoding='utf-8'))
names={l.split('\t')[0]:l.split('\t')[2] for l in open('facts.tsv',encoding='utf-8') if re.match(r'[0-9a-f]{8}-',l) and l.count('\t')>=7}
LANG=['ko','en','ja','zh-Hans','zh-Hant','vi']
rows=[];bad=[]
for pid,v in d.items():
    out={}
    for l in LANG:
        t=(v.get(l) or '').strip().strip('"「」').rstrip('.。')
        if not t: continue
        if l=='ko' and (len(t)>45 or not H.search(t)): bad.append((pid,l,t)); continue
        if l!='ko' and (H.search(t) or len(t)>120): bad.append((pid,l,t)); continue
        out[l]=t
    # 뜻 없는 줄(「음식점」「부산진구의 음식점」)은 넣지 않는다 — 카드에 한 줄을 늘릴 값이 없다
    if 'ko' not in out or len(out['ko'])<6 or re.fullmatch(r'(\S+[구군]의?\s*)?(음식점|카페|식당)',out['ko']): continue
    rows.append((pid,out,v.get('_source')))
q=lambda s:"'"+s.replace("'","''")+"'"
with open('place_one_line_20261009.sql','w',encoding='utf-8',newline='\n') as f:
    f.write('-- S15P21E201-2029 장소 한 줄 소개 적재 (빈 칸만). 되돌리기: UPDATE gabolle.place SET one_line = NULL WHERE place_id IN (…이 파일의 번호…)\nBEGIN;\n')
    for pid,out,src in rows:
        f.write(f"UPDATE gabolle.place SET one_line = {q(json.dumps(out,ensure_ascii=False))}::jsonb WHERE place_id = {q(pid)} AND one_line IS NULL;\n")
    f.write('COMMIT;\n')
w=csv.writer(open('changes-one-line.csv','w',encoding='utf-8-sig',newline=''))
w.writerow(['place_id','name_ko','source']+LANG)
for pid,out,src in rows: w.writerow([pid,names.get(pid,''),src]+[out.get(l,'') for l in LANG])
print('rows',len(rows),'bad',len(bad))
for b in bad[:10]: print(b)
