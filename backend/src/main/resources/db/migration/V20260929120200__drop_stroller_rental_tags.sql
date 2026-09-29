-- 유아차 「대여」 안내로 붙은 「유아차로 갈 수 있음(확인됨)」 표식을 걷어 낸다.
--
-- 🔴 무엇이 틀렸나. 관광공사 무장애 여행 정보의 유모차 칸(stroller)은 「유아차를 빌려주는가」를 적는
--    자리다. 부산 수집본 181곳 중 값이 있는 15곳이 전부 「대여가능」·「유모차 무료 대여(2대,1층안내데스크)」·
--    「대여 가능(10대/안내소)」 같은 대여 안내였다. 그런데 적재기(BarrierFreeAccessibility)는 값이 있기만 하면
--    ACCESSIBILITY_TAG:STROLLER 를 VERIFIED 로 붙였다.
--
--    확인된 표식은 경사 추정보다 앞선다(BaselineCandidateScorer.evaluateMobility — 확인된 「갈 수 있음」이면
--    경사 검사를 건너뛴다). 그래서 유아차를 빌려주는 가파른 곳이 유아차 「반드시」 조건을 그대로 통과했다.
--    빌려준다는 것은 들어갈 수 있다는 말이 아니다.
--
-- 적재기는 이제 그 칸으로 표식을 안 붙인다. 이미 들어간 행은 여기서 지운다 — 이 표식을 만드는 곳은 그 적재기
-- 하나뿐이다(source_type = 'TOURAPI'). 다른 출처(BF_FACILITY 등)가 붙인 표식은 건드리지 않는다.
-- 지운 곳은 「모른다」로 돌아가 미확인 경고와 경사 검사를 받는다 — 행이 없는 것은 「못 간다」가 아니다.
DELETE FROM place_feature
 WHERE feature_type = 'ACCESSIBILITY_TAG'
   AND feature_key = 'STROLLER'
   AND source_type = 'TOURAPI';
