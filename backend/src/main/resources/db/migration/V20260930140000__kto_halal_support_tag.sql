-- S15P21E201-1873 — 관광공사 무슬림 친화 식당 목록에 있는 부산 식당에 할랄 지원 표식을 붙인다.
--
-- 목록(정보 기준 2021-12)은 식당을 할랄 인증·자가 인증·무슬림 프렌들리·포크프리 넷으로 나누지만 여기서는 나누지 않는다.
-- 목록에 오른 곳은 전부 HALAL 로 본다 — 무슬림 여행자가 고를 수 있는 식당을 우선 채우고, 등급은 필요해지면 그때 나눈다.
-- 운영 정본에 있는 25곳이 대상이다. 11곳은 원래 있었고 14곳은 V20260930010000 이 넣었다. 목록의 나머지 셋(106·110·246)은
-- 영업을 확인하지 못해 정본에 없다.
--
-- evidence_status 는 VERIFIED 다. 안전 표식 넷은 ESTIMATED 저장을 DB 가 막는다(V20260907003000). 확인한 것은 「관광공사 목록에
-- 올라 있다」는 사실이고, observed_at 에 목록의 기준일을 적어 언제의 사실인지 남긴다.
--
-- 장소는 목록 좌표 150m 안에서 가게 이름의 핵심 낱말(한글·영문)로 찾는다. 운영에는 같은 가게가 한글 이름 행과 영문 이름 행으로
-- 따로 있는 경우가 있어(펀자브·헬로인디아·발리우드) 둘 다 붙는다. 빈 시험 DB 에서는 0행이다.
INSERT INTO place_feature (
    place_feature_id, place_id, feature_type, feature_key, value,
    evidence_status, source_type, source_id, observed_at, source_version, created_at
)
SELECT DISTINCT ON (p.place_id)
    gen_random_uuid(), p.place_id, 'DIETARY_SUPPORT_TAG', 'HALAL', 'true'::jsonb,
    'VERIFIED', 'KTO_MUSLIM_FRIENDLY', seed.no, TIMESTAMPTZ '2021-12-01 00:00:00+09',
    'kto-muslim-friendly-2021-12', TIMESTAMPTZ '2026-09-30 03:00:00+09'
FROM (VALUES
    ('015', 35.260091, 129.092224, '카파도키아', 'Cappadocia'),
    ('092', 35.113881, 129.037966, '사마르칸트', 'Samarkand'),
    ('095', 35.1537211, 129.0618762, '라마앤바바나', 'Rama'),
    ('096', 35.160954, 129.171545, '할매복국', 'Halmae Bokguk'),
    ('097', 35.161387, 129.161030, '펀자브', 'Punjab'),
    ('098', 35.161967, 129.160598, '헬로인디아', 'Hello India'),
    ('099', 35.1463313, 129.0661222, '봄베이', 'Bombay'),
    ('100', 35.115711, 129.039587, '리틀인디아', 'Little India'),
    ('102', 35.1565585, 129.0556933, '무궁화', 'Mugunghwa'),
    ('103', 35.153065, 129.117085, '발리우드', 'Bollywood'),
    ('104', 35.146338, 129.114280, '고마대구탕', 'Goma'),
    ('105', 35.173717, 129.108834, '옥미아구찜', 'Okmi'),
    ('107', 35.0986627, 129.0339748, '봄베이', 'Bombay'),
    ('108', 35.098984, 129.028925, '리틀인디아', 'Little India'),
    ('109', 35.161482, 129.160453, '봄베이', 'Bombay'),
    ('111', 35.1181886, 128.8919971, '용장어', 'Yongjangeo'),
    ('112', 35.160221, 129.160627, '나마스테', 'Namaste'),
    ('113', 35.156632, 129.134305, '베지나랑', 'Vege Narang'),
    ('289', 35.053567, 128.970582, '수향촌', 'Suhyang'),
    ('322', 35.257068, 129.216415, '흙시루', 'Hurgsiru'),
    ('323', 35.0982708, 129.0326242, '서울삼계탕', 'Seoul Samgyetang'),
    ('324', 35.145081, 129.028805, '서가네', 'Seogane'),
    ('325', 35.079767, 128.903796, '산골애', 'Sangole'),
    ('326', 35.169521, 129.119584, '장수삼', 'Jangsusam'),
    ('327', 35.139004, 129.105022, '옹기촌', 'Onggichon')
) AS seed(no, lat, lng, token_ko, token_en)
JOIN place p
  ON abs(p.lat - seed.lat) < 0.00135
 AND abs(p.lng - seed.lng) < 0.00165
 AND p.category = 'FOOD'
 AND (REPLACE(p.name_ko, ' ', '') LIKE '%' || REPLACE(seed.token_ko, ' ', '') || '%'
      OR p.name_ko ILIKE '%' || seed.token_en || '%'
      OR p.name_en ILIKE '%' || seed.token_en || '%')
WHERE NOT EXISTS (
    SELECT 1 FROM place_feature f
    WHERE f.place_id = p.place_id
      AND f.feature_type = 'DIETARY_SUPPORT_TAG'
      AND f.feature_key = 'HALAL'
)
ORDER BY p.place_id, seed.no;

-- 되돌리기
--   DELETE FROM place_feature WHERE feature_type = 'DIETARY_SUPPORT_TAG' AND source_version = 'kto-muslim-friendly-2021-12';
