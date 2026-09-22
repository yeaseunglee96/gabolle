-- 취향 무게를 근거별로 나눠 적는다 — 합치는 것은 읽는 쪽으로 옮긴다 (S15P21E201-1499).
--
-- 🔴 왜 키를 바꾸나
--
-- 지금 기본키가 (taste_vector_id, dimension, code) 셋뿐이라 한 성분에 줄이 하나다.
-- 그래서 설문 몫과 행동 몫이 같은 성분을 가리키면 BLENDED 한 줄로 합쳐지고,
-- **그 줄에서 행동 몫이 얼마였는지는 사라진다.**
--
-- 배치는 이것이 문제가 안 된다 — 접을 때마다 전 이력을 처음부터 다시 계산해서
-- 새 판(user_taste_vector 한 줄)을 통째로 만들기 때문이다. 두 몫을 그 자리에서
-- 다시 구한다.
--
-- 카프카 소비자는 다르다. 이벤트 하나만 들고 와서 그 줄을 고쳐야 하는데, 지금
-- 숫자에서 행동 몫이 얼마인지 모르면 얼마를 더해야 할지 계산할 수가 없다.
-- 그 일을 하는 것이 S15P21E201-1500 이고, 이 마이그레이션이 그 앞자리다.
--
-- 🔴 결과는 안 바뀐다 — 합치는 규칙이 원래 «합» 이기 때문이다
--
-- TasteVectorFoldService.merge() 가 겹친 자리를 clamp(설문 + 행동) 으로 적는다.
-- 평균이 아니라 합이라, 나눠서 적고 읽을 때 더하는 것이 수학적으로 같다.
-- 평균이었다면 이 변경은 불가능했다 — 몇 개를 평균 냈는지가 값에 녹아 있어서다.
--
-- 🔴 기존 줄은 안 건드린다
--
-- 지금 (벡터, 차원, 코드) 마다 줄이 정확히 하나씩이라, 근거를 키에 더해도 중복이
-- 생기지 않는다. 남아 있는 BLENDED 줄은 그대로 둔다 — 그 줄의 설문 몫과 행동 몫은
-- 이미 섞여서 되돌릴 수 없고, 되돌릴 필요도 없다. 다음 배치가 그 사용자를 접을 때
-- **새 판**을 만들면서 설문 줄과 행동 줄로 나뉘어 적힌다.
--
-- 🔴 evidence 는 이미 NOT NULL 이라 키로 올려도 빈 값이 생기지 않는다
--    (ck_user_taste_weight_evidence 가 SURVEY·INTERACTION·BLENDED 로 막고 있다).

ALTER TABLE user_taste_weight
    DROP CONSTRAINT pk_user_taste_weight;

ALTER TABLE user_taste_weight
    ADD CONSTRAINT pk_user_taste_weight
        PRIMARY KEY (taste_vector_id, dimension, code, evidence);
