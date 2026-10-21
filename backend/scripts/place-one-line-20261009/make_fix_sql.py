# verdicts.json(Gemini 판정, 인기 상위) + rule-flags.json(규칙 검사, 전부) + 백업 → 고치는 SQL 과 바뀐 목록.
import json,re,csv
H=re.compile('[가-힣]'); GENERIC={'음식점','카페','문화·역사·사찰','도시 명소','자연·산책','바다·해변','축제·행사'}
STYLE=re.compile('평가|분위기|가성비|간결|단순화|명사형|40자'); WRONG=re.compile('오역|잘못|아니|오류|없음|없으므로|없는|다른|틀')
LANG=['ko','en','ja','zh-Hans','zh-Hant','vi']
cur={}
for l in open('one_line-backup-20261012.tsv',encoding='utf-8'):
    pid,j=l.rstrip('\n').split('\t',1); cur[pid]=json.loads(j)
V=json.load(open('verdicts.json',encoding='utf-8')); RF=json.load(open('rule-flags.json',encoding='utf-8'))
names={r['place_id']:r['name_ko'] for r in csv.DictReader(open('../changes-one-line.csv',encoding='utf-8-sig'))}
out=[];st={'checked_places':len(cur),'judged_places':len(V),'flagged_places':0,'fixed_lines':0,'dropped_lines':0,'nulled_places':0,'rejected_fix':0,'style_only_ignored':0}
for pid,old in cur.items():
    new=dict(old); notes=[]
    v=V.get(pid,{})
    k=v.get('ko',{})
    # 표현만 다듬은 판정(평가어·간결화)은 틀린 것이 아니다 — 그 장소 판정은 통째로 버린다
    if k.get('v')=='fix' and STYLE.search(k.get('why','')) and not WRONG.search(k.get('why','')): v={}; st['style_only_ignored']+=1
    kofix=v.get('ko',{}).get('v')=='fix' and (v['ko'].get('text') or '').strip().rstrip('.')!=old.get('ko')
    for l in LANG:
        x=v.get(l) or {}
        if l not in new: continue
        if x.get('v')=='drop' or (kofix and l!='ko' and x.get('v')!='fix'):
            new.pop(l); notes.append((l,'drop',x.get('why','ko 를 고쳐 뜻이 어긋남')))
        elif x.get('v')=='fix' and (l!='ko' or kofix):
            t=(x.get('text') or '').strip().rstrip('.。')
            ok=t and ((l=='ko' and H.search(t) and len(t)<=45) or (l!='ko' and not H.search(t) and len(t)<=120))
            if ok: new[l]=t; notes.append((l,'fix',x.get('why','')))
            else: st['rejected_fix']+=1; new.pop(l); notes.append((l,'drop','고친 문장이 형식 검사에 걸림'))
    for l,why in RF.get(pid,[]):
        if l in new and new[l]==old.get(l): new.pop(l); notes.append((l,'drop','규칙:'+why))
    if old.get('ko') in GENERIC: new={}; notes.append(('ko','drop','규칙:분류 이름뿐(GENERIC)'))
    if 'ko' not in new: new={}
    if new==old: continue
    st['flagged_places']+=1
    if not new: st['nulled_places']+=1
    for l in LANG:
        if l in old and l not in new: st['dropped_lines']+=1
        elif l in old and new.get(l)!=old[l]: st['fixed_lines']+=1
    out.append((pid,old,new,notes))
q=lambda s:"'"+s.replace("'","''")+"'"
with open('V_place_one_line_fix.sql','w',encoding='utf-8',newline='\n') as f:
    f.write('-- S15P21E201-2029 한 줄 소개 사실 점검 반영. 되돌리기: gabolle.place_one_line_backup_20261012 (place_id, one_line) 에서 되살린다.\n-- 원래 값이 그대로일 때만 바꾼다(AND one_line = 원래 값).\nBEGIN;\n')
    for pid,old,new,_ in out:
        val='NULL' if not new else q(json.dumps(new,ensure_ascii=False))+'::jsonb'
        f.write(f"UPDATE gabolle.place SET one_line = {val} WHERE place_id = {q(pid)} AND one_line = {q(json.dumps(old,ensure_ascii=False))}::jsonb;\n")
    f.write('COMMIT;\n')
w=csv.writer(open('fix-list.csv','w',encoding='utf-8-sig',newline=''))
w.writerow(['place_id','name_ko','lang','action','why','before','after'])
for pid,old,new,notes in out:
    for l,a,why in notes: w.writerow([pid,names.get(pid,''),l,a,why,old.get(l,''),new.get(l,'')])
print(json.dumps(st,ensure_ascii=False)); print('updates',len(out))
