-- ══════════════════════════════════════════════════════════════════════════════
-- 관광공사 제3유형 사진 나머지를 넣는다 — 수집본에는 있는데 DB 가 비어 있던 것
-- ══════════════════════════════════════════════════════════════════════════════
--
-- V20260916290000 이 제3유형 247장을 넣어 「Type3 을 제3자 저작물로 잘못 읽던」 문제를
-- 풀었다. 이 파일은 그 뒤에 남은 것을 마저 넣는다.
--
-- ── 무엇이 남았나 (2026-09-17 실측) ────────────────────────────────────────
--
--   관광공사 수집본(data/raw/tourapi/tourapi-busan.ndjson)  656곳
--     대표사진(firstimage)이 있는 곳                        543곳
--     이미 어느 마이그레이션에든 photo_url 이 적힌 것        342곳
--     🔴 아직 안 적힌 것                                    257곳  ← 이 파일
--
--   저작권 유형은 **257곳 전부 Type3** 이다. Type1 은 남은 것이 없다.
--
-- 🔴 이 숫자는 **수집본 기준**이지 운영 DB 기준이 아니다. 수집본 656곳 중 운영 DB 에
--    적재된 관광공사 장소는 그보다 적다(다른 문서가 328곳이라고 적는다). 그래서 실제로
--    채워지는 행은 257보다 적을 수 있다 — 아래 WHERE 절이 그것을 알아서 거른다.
--    **DB 를 직접 세지 않았으므로 「257곳이 채워진다」고 쓰지 않는다.**
--
-- ── 🔴 안전한 이유 셋 ─────────────────────────────────────────────────────
--
--   1. `place.photo_url IS NULL` 인 행만 고친다. **이미 있는 사진을 덮지 않는다**
--   2. `place.source_type = 'TOURAPI'` 로 제한한다. 상가 장소는 안 건드린다
--   3. DB 에 없는 contentid 는 조인이 안 되어 **그냥 지나간다**. 오류가 아니다
--
--   그래서 이 파일은 **여러 번 돌려도 같은 결과**이고, 앞 파일과 순서가 바뀌어도 안전하다.
--
-- ── 따라붙는 의무 둘 — V20260916290000 이 적은 그대로다 ───────────────────
--
--   공공누리 **제3유형 = 출처표시 + 변경금지**.
--
--   1. **출처를 화면에 표시해야 한다.** 「한국관광공사의 공공누리 제3유형 공공저작물을
--      이용하였습니다」 같은 문구가 사용자에게 보여야 한다. photo_source 칸에 적히는
--      것만으로는 의무가 안 채워진다 — S15P21E201-1120 이 그 자리를 만드는 중이다
--   2. **이미지를 변경하면 안 된다.** 지금은 관광공사 서버 주소를 가리키기만 하고
--      파일을 내려받지도 가공하지도 않으므로 만족한다. 🔴 나중에 썸네일을 서버에서
--      만들어 저장하는 방식으로 바꾸면 **그 순간 위반**이 된다. 화면에서 잘라 보이는
--      것(CSS)은 배포되는 원본을 바꾸는 것이 아니라 괜찮다
--
--   그래서 photo_source 를 제1유형과 **다른 문자열**로 적는다. 화면이 문구를 가려 쓸 수
--   있어야 하고, 되돌릴 때도 이 값으로 골라낼 수 있다.
--
-- ── 어느 칸을 쓰나 ────────────────────────────────────────────────────────
--
--   `firstimage`(큰 사진)를 쓴다. V20260916290000 과 같은 칸이다 — 확인했다:
--     129156 가덕도 등대  firstimage …3342781_image2_1.jpg   ← 앞 파일이 넣은 값과 같다
--                         firstimage2 …3342781_image3_1.jpg  (작은 사진. 안 쓴다)
--
--   257곳 전부 `http://` 다. https 는 한 곳도 없다 — 원천이 그렇게 준다.
--   🔴 앱이 평문 http 를 막으면 사진이 안 보인다. 앞 파일이 넣은 247장도 같은 조건이라
--      이 파일이 새로 만드는 문제는 아니지만, **같이 걸린다.**
--
-- 🔴 이 파일은 데이터만 고친다. 앞으로 들어오는 적재는 TourApiPlaceLoader 가 여전히
--    Type1 만 받으므로 새 장소는 또 비워진다 — 그 자바 상수를 고치는 것은 별도 작업이다.

UPDATE place
   SET photo_url = seed.photo_url,
       photo_source = '한국관광공사 공공누리 제3유형'
  FROM (VALUES
    ('2845011', 'http://tong.visitkorea.or.kr/cms/resource/05/2845005_image2_1.jpg'),
    ('2869158', 'http://tong.visitkorea.or.kr/cms/resource/32/2869132_image2_1.jpg'),
    ('2872626', 'http://tong.visitkorea.or.kr/cms/resource/15/2872615_image2_1.JPG'),
    ('2784321', 'http://tong.visitkorea.or.kr/cms/resource/26/2787626_image2_1.jpg'),
    ('2760771', 'http://tong.visitkorea.or.kr/cms/resource/56/2761256_image2_1.jpg'),
    ('2867215', 'http://tong.visitkorea.or.kr/cms/resource/07/2867207_image2_1.JPG'),
    ('2838907', 'http://tong.visitkorea.or.kr/cms/resource/97/2838897_image2_1.jpg'),
    ('2785272', 'http://tong.visitkorea.or.kr/cms/resource/82/2785282_image2_1.jpg'),
    ('2841481', 'http://tong.visitkorea.or.kr/cms/resource/76/2841476_image2_1.jpg'),
    ('2869241', 'http://tong.visitkorea.or.kr/cms/resource/33/2869233_image2_1.JPG'),
    ('2870277', 'http://tong.visitkorea.or.kr/cms/resource/73/2870273_image2_1.JPG'),
    ('2859073', 'http://tong.visitkorea.or.kr/cms/resource/70/2859070_image2_1.jpg'),
    ('2832700', 'http://tong.visitkorea.or.kr/cms/resource/94/2832694_image2_1.jpg'),
    ('2873498', 'http://tong.visitkorea.or.kr/cms/resource/87/2873487_image2_1.jpg'),
    ('2869454', 'http://tong.visitkorea.or.kr/cms/resource/45/2869445_image2_1.jpg'),
    ('2782628', 'http://tong.visitkorea.or.kr/cms/resource/53/2800253_image2_1.jpg'),
    ('2833257', 'http://tong.visitkorea.or.kr/cms/resource/43/2833243_image2_1.jpg'),
    ('2852685', 'http://tong.visitkorea.or.kr/cms/resource/76/2852676_image2_1.jpeg'),
    ('2616370', 'http://tong.visitkorea.or.kr/cms/resource/10/2616410_image2_1.jpg'),
    ('2872474', 'http://tong.visitkorea.or.kr/cms/resource/72/2872472_image2_1.JPG'),
    ('850121', 'http://tong.visitkorea.or.kr/cms/resource/75/1902175_image2_1.jpg'),
    ('2783360', 'http://tong.visitkorea.or.kr/cms/resource/27/2795627_image2_1.jpg'),
    ('2859101', 'http://tong.visitkorea.or.kr/cms/resource/85/2859085_image2_1.jpg'),
    ('574250', 'http://tong.visitkorea.or.kr/cms/resource/82/1982682_image2_1.jpg'),
    ('2868507', 'http://tong.visitkorea.or.kr/cms/resource/99/2868499_image2_1.jpg'),
    ('2853012', 'http://tong.visitkorea.or.kr/cms/resource/02/2853002_image2_1.jpg'),
    ('2836431', 'http://tong.visitkorea.or.kr/cms/resource/30/2836430_image2_1.jpg'),
    ('2840883', 'http://tong.visitkorea.or.kr/cms/resource/60/2840860_image2_1.jpg'),
    ('2785275', 'http://tong.visitkorea.or.kr/cms/resource/03/2797503_image2_1.JPG'),
    ('2786008', 'http://tong.visitkorea.or.kr/cms/resource/23/2792523_image2_1.jpg'),
    ('2867692', 'http://tong.visitkorea.or.kr/cms/resource/89/2867689_image2_1.JPG'),
    ('2822838', 'http://tong.visitkorea.or.kr/cms/resource/98/2822898_image2_1.jpg'),
    ('2845041', 'http://tong.visitkorea.or.kr/cms/resource/33/2845033_image2_1.jpg'),
    ('2838872', 'http://tong.visitkorea.or.kr/cms/resource/63/2838863_image2_1.jpg'),
    ('2841064', 'http://tong.visitkorea.or.kr/cms/resource/58/2841058_image2_1.jpg'),
    ('2781799', 'http://tong.visitkorea.or.kr/cms/resource/28/2782128_image2_1.jpg'),
    ('2852312', 'http://tong.visitkorea.or.kr/cms/resource/08/2852308_image2_1.jpg'),
    ('2778476', 'http://tong.visitkorea.or.kr/cms/resource/65/2778665_image2_1.jpg'),
    ('2870868', 'http://tong.visitkorea.or.kr/cms/resource/55/2870855_image2_1.jpg'),
    ('2891745', 'http://tong.visitkorea.or.kr/cms/resource/40/2891740_image2_1.jpg'),
    ('2869159', 'http://tong.visitkorea.or.kr/cms/resource/44/2869144_image2_1.jpg'),
    ('2759629', 'http://tong.visitkorea.or.kr/cms/resource/10/2759810_image2_1.jpg'),
    ('2891789', 'http://tong.visitkorea.or.kr/cms/resource/81/2891781_image2_1.jpg'),
    ('2783646', 'http://tong.visitkorea.or.kr/cms/resource/15/2795715_image2_1.jpg'),
    ('2840901', 'http://tong.visitkorea.or.kr/cms/resource/87/2840887_image2_1.jpg'),
    ('2869129', 'http://tong.visitkorea.or.kr/cms/resource/15/2869115_image2_1.jpg'),
    ('2832712', 'http://tong.visitkorea.or.kr/cms/resource/06/2832706_image2_1.jpg'),
    ('2891814', 'http://tong.visitkorea.or.kr/cms/resource/03/2891803_image2_1.jpg'),
    ('2782695', 'http://tong.visitkorea.or.kr/cms/resource/29/2795729_image2_1.png'),
    ('2841417', 'http://tong.visitkorea.or.kr/cms/resource/98/2841398_image2_1.jpg'),
    ('2868609', 'http://tong.visitkorea.or.kr/cms/resource/71/2868571_image2_1.jpg'),
    ('2845069', 'http://tong.visitkorea.or.kr/cms/resource/60/2845060_image2_1.jpg'),
    ('2845094', 'http://tong.visitkorea.or.kr/cms/resource/78/2845078_image2_1.jpg'),
    ('2869196', 'http://tong.visitkorea.or.kr/cms/resource/89/2869189_image2_1.jpg'),
    ('2872442', 'http://tong.visitkorea.or.kr/cms/resource/10/2872410_image2_1.JPG'),
    ('3415203', 'http://tong.visitkorea.or.kr/cms/resource/00/3415200_image2_1.jpg'),
    ('2867715', 'http://tong.visitkorea.or.kr/cms/resource/02/2867702_image2_1.JPG'),
    ('2853079', 'http://tong.visitkorea.or.kr/cms/resource/67/2853067_image2_1.jpg'),
    ('2872611', 'http://tong.visitkorea.or.kr/cms/resource/03/2872603_image2_1.JPG'),
    ('2891826', 'http://tong.visitkorea.or.kr/cms/resource/20/2891820_image2_1.jpg'),
    ('2891849', 'http://tong.visitkorea.or.kr/cms/resource/38/2891838_image2_1.jpg'),
    ('2841499', 'http://tong.visitkorea.or.kr/cms/resource/93/2841493_image2_1.jpg'),
    ('2847910', 'http://tong.visitkorea.or.kr/cms/resource/99/2847899_image2_1.jpg'),
    ('2847893', 'http://tong.visitkorea.or.kr/cms/resource/82/2847882_image2_1.jpg'),
    ('2752976', 'http://tong.visitkorea.or.kr/cms/resource/82/2753482_image2_1.jpg'),
    ('2832729', 'http://tong.visitkorea.or.kr/cms/resource/27/2832727_image2_1.jpg'),
    ('2852419', 'http://tong.visitkorea.or.kr/cms/resource/18/2852418_image2_1.jpg'),
    ('2776396', 'http://tong.visitkorea.or.kr/cms/resource/86/2790886_image2_1.JPG'),
    ('2891868', 'http://tong.visitkorea.or.kr/cms/resource/57/2891857_image2_1.jpg'),
    ('2872395', 'http://tong.visitkorea.or.kr/cms/resource/86/2872386_image2_1.JPG'),
    ('2869595', 'http://tong.visitkorea.or.kr/cms/resource/80/2869580_image2_1.JPG'),
    ('2868655', 'http://tong.visitkorea.or.kr/cms/resource/30/2868630_image2_1.jpg'),
    ('2872574', 'http://tong.visitkorea.or.kr/cms/resource/60/2872560_image2_1.JPG'),
    ('2756690', 'http://tong.visitkorea.or.kr/cms/resource/09/2756909_image2_1.jpg'),
    ('2787408', 'http://tong.visitkorea.or.kr/cms/resource/53/2792153_image2_1.jpg'),
    ('2865941', 'http://tong.visitkorea.or.kr/cms/resource/16/2865916_image2_1.jpg'),
    ('2840921', 'http://tong.visitkorea.or.kr/cms/resource/13/2840913_image2_1.jpg'),
    ('2869113', 'http://tong.visitkorea.or.kr/cms/resource/07/2869107_image2_1.JPG'),
    ('2840956', 'http://tong.visitkorea.or.kr/cms/resource/33/2840933_image2_1.jpg'),
    ('2785270', 'http://tong.visitkorea.or.kr/cms/resource/55/2790955_image2_1.JPG'),
    ('2845123', 'http://tong.visitkorea.or.kr/cms/resource/06/2845106_image2_1.jpg'),
    ('2853131', 'http://tong.visitkorea.or.kr/cms/resource/21/2853121_image2_1.jpg'),
    ('2752996', 'http://tong.visitkorea.or.kr/cms/resource/15/2753515_image2_1.jpg'),
    ('2853145', 'http://tong.visitkorea.or.kr/cms/resource/40/2853140_image2_1.jpg'),
    ('2847969', 'http://tong.visitkorea.or.kr/cms/resource/60/2847960_image2_1.jpg'),
    ('2868824', 'http://tong.visitkorea.or.kr/cms/resource/40/2868740_image2_1.jpg'),
    ('2861291', 'http://tong.visitkorea.or.kr/cms/resource/90/2861290_image2_1.jpg'),
    ('841730', 'http://tong.visitkorea.or.kr/cms/resource/78/3583678_image2_1.jpg'),
    ('2787109', 'http://tong.visitkorea.or.kr/cms/resource/18/2798218_image2_1.JPG'),
    ('2785277', 'http://tong.visitkorea.or.kr/cms/resource/93/2790693_image2_1.JPG'),
    ('2860819', 'http://tong.visitkorea.or.kr/cms/resource/16/2860816_image2_1.jpg'),
    ('2860823', 'http://tong.visitkorea.or.kr/cms/resource/22/2860822_image2_1.jpg'),
    ('2867598', 'http://tong.visitkorea.or.kr/cms/resource/90/2867590_image2_1.JPG'),
    ('2872553', 'http://tong.visitkorea.or.kr/cms/resource/45/2872545_image2_1.JPG'),
    ('3373600', 'http://tong.visitkorea.or.kr/cms/resource/21/3373621_image2_1.jpg'),
    ('2952019', 'http://tong.visitkorea.or.kr/cms/resource/16/2952016_image2_1.jpg'),
    ('2784320', 'http://tong.visitkorea.or.kr/cms/resource/25/2787525_image2_1.jpg'),
    ('1805861', 'http://tong.visitkorea.or.kr/cms/resource/88/3049888_image2_1.JPG'),
    ('2793181', 'http://tong.visitkorea.or.kr/cms/resource/23/2794823_image2_1.jpg'),
    ('2860829', 'http://tong.visitkorea.or.kr/cms/resource/26/2860826_image2_1.jpg'),
    ('2868850', 'http://tong.visitkorea.or.kr/cms/resource/37/2868837_image2_1.jpg'),
    ('2860833', 'http://tong.visitkorea.or.kr/cms/resource/31/2860831_image2_1.jpg'),
    ('2785965', 'http://tong.visitkorea.or.kr/cms/resource/49/2800449_image2_1.JPG'),
    ('2838489', 'http://tong.visitkorea.or.kr/cms/resource/79/2838479_image2_1.jpg'),
    ('2852696', 'http://tong.visitkorea.or.kr/cms/resource/89/2852689_image2_1.jpg'),
    ('2869572', 'http://tong.visitkorea.or.kr/cms/resource/56/2869556_image2_1.JPG'),
    ('2785269', 'http://tong.visitkorea.or.kr/cms/resource/03/2785303_image2_1.jpg'),
    ('2860839', 'http://tong.visitkorea.or.kr/cms/resource/37/2860837_image2_1.jpg'),
    ('2870354', 'http://tong.visitkorea.or.kr/cms/resource/26/2870326_image2_1.JPG'),
    ('2865975', 'http://tong.visitkorea.or.kr/cms/resource/50/2865950_image2_1.jpg'),
    ('2872534', 'http://tong.visitkorea.or.kr/cms/resource/20/2872520_image2_1.JPG'),
    ('2869183', 'http://tong.visitkorea.or.kr/cms/resource/69/2869169_image2_1.JPG'),
    ('2869439', 'http://tong.visitkorea.or.kr/cms/resource/30/2869430_image2_1.jpg'),
    ('2832671', 'http://tong.visitkorea.or.kr/cms/resource/67/2832667_image2_1.jpg'),
    ('851832', 'http://tong.visitkorea.or.kr/cms/resource/65/1867165_image2_1.jpg'),
    ('2838382', 'http://tong.visitkorea.or.kr/cms/resource/72/2838372_image2_1.jpg'),
    ('2860851', 'http://tong.visitkorea.or.kr/cms/resource/50/2860850_image2_1.jpg'),
    ('2759609', 'http://tong.visitkorea.or.kr/cms/resource/76/2759676_image2_1.jpg'),
    ('2845149', 'http://tong.visitkorea.or.kr/cms/resource/47/2845147_image2_1.jpg'),
    ('2865384', 'http://tong.visitkorea.or.kr/cms/resource/71/2865371_image2_1.jpg'),
    ('3412613', 'http://tong.visitkorea.or.kr/cms/resource/63/3412563_image2_1.jpg'),
    ('2781598', 'http://tong.visitkorea.or.kr/cms/resource/37/2781837_image2_1.jpg'),
    ('2773623', 'http://tong.visitkorea.or.kr/cms/resource/14/2773614_image2_1.jpg'),
    ('2867167', 'http://tong.visitkorea.or.kr/cms/resource/60/2867160_image2_1.JPG'),
    ('2786742', 'http://tong.visitkorea.or.kr/cms/resource/14/2797514_image2_1.JPG'),
    ('2867517', 'http://tong.visitkorea.or.kr/cms/resource/11/2867511_image2_1.JPG'),
    ('2753006', 'http://tong.visitkorea.or.kr/cms/resource/18/2753518_image2_1.jpg'),
    ('2832717', 'http://tong.visitkorea.or.kr/cms/resource/15/2832715_image2_1.jpg'),
    ('2777936', 'http://tong.visitkorea.or.kr/cms/resource/34/2794834_image2_1.jpg'),
    ('2844638', 'http://tong.visitkorea.or.kr/cms/resource/32/2844632_image2_1.jpg'),
    ('2875727', 'http://tong.visitkorea.or.kr/cms/resource/15/2875715_image2_1.JPG'),
    ('2838432', 'http://tong.visitkorea.or.kr/cms/resource/22/2838422_image2_1.jpg'),
    ('2836412', 'http://tong.visitkorea.or.kr/cms/resource/08/2836408_image2_1.jpg'),
    ('2868912', 'http://tong.visitkorea.or.kr/cms/resource/03/2868903_image2_1.jpg'),
    ('2872353', 'http://tong.visitkorea.or.kr/cms/resource/45/2872345_image2_1.JPG'),
    ('2869896', 'http://tong.visitkorea.or.kr/cms/resource/82/2869882_image2_1.jpg'),
    ('2867674', 'http://tong.visitkorea.or.kr/cms/resource/70/2867670_image2_1.JPG'),
    ('2867626', 'http://tong.visitkorea.or.kr/cms/resource/07/2867607_image2_1.JPG'),
    ('2875744', 'http://tong.visitkorea.or.kr/cms/resource/36/2875736_image2_1.JPG'),
    ('2860755', 'http://tong.visitkorea.or.kr/cms/resource/43/2860743_image2_1.jpg'),
    ('2869405', 'http://tong.visitkorea.or.kr/cms/resource/88/2869388_image2_1.jpg'),
    ('2785266', 'http://tong.visitkorea.or.kr/cms/resource/72/2790972_image2_1.JPG'),
    ('2836424', 'http://tong.visitkorea.or.kr/cms/resource/16/2836416_image2_1.jpg'),
    ('2840998', 'http://tong.visitkorea.or.kr/cms/resource/84/2840984_image2_1.jpg'),
    ('2860782', 'http://tong.visitkorea.or.kr/cms/resource/67/2860767_image2_1.jpg'),
    ('2838973', 'http://tong.visitkorea.or.kr/cms/resource/58/2838958_image2_1.jpg'),
    ('2844259', 'http://tong.visitkorea.or.kr/cms/resource/55/2844255_image2_1.jpg'),
    ('2659244', 'http://tong.visitkorea.or.kr/cms/resource/48/2659248_image2_1.jpg'),
    ('2868940', 'http://tong.visitkorea.or.kr/cms/resource/37/2868937_image2_1.jpg'),
    ('2784325', 'http://tong.visitkorea.or.kr/cms/resource/98/2787498_image2_1.jpg'),
    ('2872583', 'http://tong.visitkorea.or.kr/cms/resource/73/2872573_image2_1.JPG'),
    ('2870841', 'http://tong.visitkorea.or.kr/cms/resource/19/2870819_image2_1.jpg'),
    ('2869936', 'http://tong.visitkorea.or.kr/cms/resource/20/2869920_image2_1.jpg'),
    ('2869323', 'http://tong.visitkorea.or.kr/cms/resource/95/2869295_image2_1.JPG'),
    ('2867662', 'http://tong.visitkorea.or.kr/cms/resource/35/2867635_image2_1.JPG'),
    ('2875761', 'http://tong.visitkorea.or.kr/cms/resource/54/2875754_image2_1.JPG'),
    ('2841018', 'http://tong.visitkorea.or.kr/cms/resource/07/2841007_image2_1.jpg'),
    ('2872610', 'http://tong.visitkorea.or.kr/cms/resource/95/2872595_image2_1.JPG'),
    ('2852185', 'http://tong.visitkorea.or.kr/cms/resource/74/2852174_image2_1.jpg'),
    ('2870394', 'http://tong.visitkorea.or.kr/cms/resource/79/2870379_image2_1.JPG'),
    ('2726193', 'http://tong.visitkorea.or.kr/cms/resource/61/3582861_image2_1.jpg'),
    ('2875783', 'http://tong.visitkorea.or.kr/cms/resource/67/2875767_image2_1.JPG'),
    ('2838418', 'http://tong.visitkorea.or.kr/cms/resource/04/2838404_image2_1.jpg'),
    ('2836450', 'http://tong.visitkorea.or.kr/cms/resource/38/2836438_image2_1.jpg'),
    ('2841029', 'http://tong.visitkorea.or.kr/cms/resource/24/2841024_image2_1.jpg'),
    ('2852402', 'http://tong.visitkorea.or.kr/cms/resource/00/2852400_image2_1.jpg'),
    ('2832723', 'http://tong.visitkorea.or.kr/cms/resource/22/2832722_image2_1.jpg'),
    ('2845027', 'http://tong.visitkorea.or.kr/cms/resource/18/2845018_image2_1.jpg'),
    ('2847877', 'http://tong.visitkorea.or.kr/cms/resource/70/2847870_image2_1.jpg'),
    ('2844677', 'http://tong.visitkorea.or.kr/cms/resource/74/2844674_image2_1.jpg'),
    ('2872634', 'http://tong.visitkorea.or.kr/cms/resource/23/2872623_image2_1.JPG'),
    ('2848952', 'http://tong.visitkorea.or.kr/cms/resource/63/2848663_image2_1.jpg'),
    ('2608181', 'http://tong.visitkorea.or.kr/cms/resource/89/2608289_image2_1.JPG'),
    ('2872517', 'http://tong.visitkorea.or.kr/cms/resource/10/2872510_image2_1.JPG'),
    ('2844687', 'http://tong.visitkorea.or.kr/cms/resource/84/2844684_image2_1.jpg'),
    ('2872496', 'http://tong.visitkorea.or.kr/cms/resource/90/2872490_image2_1.JPG'),
    ('2785970', 'http://tong.visitkorea.or.kr/cms/resource/62/2800462_image2_1.jpg'),
    ('2759593', 'http://tong.visitkorea.or.kr/cms/resource/56/2759656_image2_1.jpg'),
    ('851104', 'http://tong.visitkorea.or.kr/cms/resource/60/3048860_image2_1.JPG'),
    ('2873468', 'http://tong.visitkorea.or.kr/cms/resource/63/2873463_image2_1.jpg'),
    ('2844698', 'http://tong.visitkorea.or.kr/cms/resource/96/2844696_image2_1.jpg'),
    ('2853231', 'http://tong.visitkorea.or.kr/cms/resource/27/2853227_image2_1.jpg'),
    ('2838820', 'http://tong.visitkorea.or.kr/cms/resource/00/2838800_image2_1.jpg'),
    ('2608459', 'http://tong.visitkorea.or.kr/cms/resource/93/2608593_image2_1.JPG'),
    ('2844643', 'http://tong.visitkorea.or.kr/cms/resource/41/2844641_image2_1.jpg'),
    ('2872258', 'http://tong.visitkorea.or.kr/cms/resource/57/2872257_image2_1.JPG'),
    ('2852238', 'http://tong.visitkorea.or.kr/cms/resource/30/2852230_image2_1.jpg'),
    ('2875311', 'http://tong.visitkorea.or.kr/cms/resource/96/2875296_image2_1.JPG'),
    ('2852250', 'http://tong.visitkorea.or.kr/cms/resource/42/2852242_image2_1.jpg'),
    ('2838849', 'http://tong.visitkorea.or.kr/cms/resource/47/2838847_image2_1.jpg'),
    ('2870751', 'http://tong.visitkorea.or.kr/cms/resource/46/2870746_image2_1.jpg'),
    ('2785956', 'http://tong.visitkorea.or.kr/cms/resource/24/2800524_image2_1.jpg'),
    ('2870703', 'http://tong.visitkorea.or.kr/cms/resource/96/2870696_image2_1.jpg'),
    ('2852263', 'http://tong.visitkorea.or.kr/cms/resource/56/2852256_image2_1.jpg'),
    ('2865403', 'http://tong.visitkorea.or.kr/cms/resource/88/2865388_image2_1.jpg'),
    ('2794743', 'http://tong.visitkorea.or.kr/cms/resource/11/2794811_image2_1.jpg'),
    ('2848949', 'http://tong.visitkorea.or.kr/cms/resource/92/2848692_image2_1.jpg'),
    ('2822840', 'http://tong.visitkorea.or.kr/cms/resource/00/2822900_image2_1.jpg'),
    ('2642681', 'http://tong.visitkorea.or.kr/cms/resource/90/3423190_image2_1.png'),
    ('2853060', 'http://tong.visitkorea.or.kr/cms/resource/42/2853042_image2_1.jpg'),
    ('2852408', 'http://tong.visitkorea.or.kr/cms/resource/06/2852406_image2_1.jpg'),
    ('2875345', 'http://tong.visitkorea.or.kr/cms/resource/38/2875338_image2_1.JPG'),
    ('2952936', 'http://tong.visitkorea.or.kr/cms/resource/22/2952922_image2_1.jpg'),
    ('2832636', 'http://tong.visitkorea.or.kr/cms/resource/33/2832633_image2_1.jpg'),
    ('2852620', 'http://tong.visitkorea.or.kr/cms/resource/13/2852613_image2_1.jpg'),
    ('2785267', 'http://tong.visitkorea.or.kr/cms/resource/90/2785290_image2_1.jpg'),
    ('2870780', 'http://tong.visitkorea.or.kr/cms/resource/67/2870767_image2_1.jpg'),
    ('2859188', 'http://tong.visitkorea.or.kr/cms/resource/85/2859185_image2_1.jpg'),
    ('2858974', 'http://tong.visitkorea.or.kr/cms/resource/63/2858963_image2_1.jpg'),
    ('2869532', 'http://tong.visitkorea.or.kr/cms/resource/18/2869518_image2_1.JPG'),
    ('2868448', 'http://tong.visitkorea.or.kr/cms/resource/48/2868348_image2_1.jpg'),
    ('2852602', 'http://tong.visitkorea.or.kr/cms/resource/93/2852593_image2_1.jpg'),
    ('2869186', 'http://tong.visitkorea.or.kr/cms/resource/78/2869178_image2_1.jpg'),
    ('2841442', 'http://tong.visitkorea.or.kr/cms/resource/30/2841430_image2_1.jpg'),
    ('2838889', 'http://tong.visitkorea.or.kr/cms/resource/78/2838878_image2_1.jpg'),
    ('2853081', 'http://tong.visitkorea.or.kr/cms/resource/78/2853078_image2_1.jpg'),
    ('2844738', 'http://tong.visitkorea.or.kr/cms/resource/32/2844732_image2_1.jpg'),
    ('2756678', 'http://tong.visitkorea.or.kr/cms/resource/96/2756896_image2_1.jpg'),
    ('2844748', 'http://tong.visitkorea.or.kr/cms/resource/40/2844740_image2_1.jpg'),
    ('2869249', 'http://tong.visitkorea.or.kr/cms/resource/06/2869206_image2_1.jpg'),
    ('2616371', 'http://tong.visitkorea.or.kr/cms/resource/16/2616416_image2_1.jpg'),
    ('2832642', 'http://tong.visitkorea.or.kr/cms/resource/39/2832639_image2_1.jpg'),
    ('2891887', 'http://tong.visitkorea.or.kr/cms/resource/83/2891883_image2_1.jpg'),
    ('2832649', 'http://tong.visitkorea.or.kr/cms/resource/46/2832646_image2_1.jpg'),
    ('2875819', 'http://tong.visitkorea.or.kr/cms/resource/17/2875817_image2_1.JPG'),
    ('2785276', 'http://tong.visitkorea.or.kr/cms/resource/01/2798101_image2_1.JPG'),
    ('2787137', 'http://tong.visitkorea.or.kr/cms/resource/79/2791979_image2_1.jpg'),
    ('2852277', 'http://tong.visitkorea.or.kr/cms/resource/69/2852269_image2_1.jpg'),
    ('2848930', 'http://tong.visitkorea.or.kr/cms/resource/93/2848793_image2_1.jpg'),
    ('2858955', 'http://tong.visitkorea.or.kr/cms/resource/44/2858944_image2_1.jpg'),
    ('2841052', 'http://tong.visitkorea.or.kr/cms/resource/43/2841043_image2_1.jpg'),
    ('2867734', 'http://tong.visitkorea.or.kr/cms/resource/29/2867729_image2_1.JPG'),
    ('2905141', 'http://tong.visitkorea.or.kr/cms/resource/35/2905135_image2_1.jpg'),
    ('1805095', 'http://tong.visitkorea.or.kr/cms/resource/93/1804793_image2_1.jpg'),
    ('2869618', 'http://tong.visitkorea.or.kr/cms/resource/03/2869603_image2_1.JPG'),
    ('2872271', 'http://tong.visitkorea.or.kr/cms/resource/67/2872267_image2_1.JPG'),
    ('2875364', 'http://tong.visitkorea.or.kr/cms/resource/53/2875353_image2_1.JPG'),
    ('2786766', 'http://tong.visitkorea.or.kr/cms/resource/45/2797545_image2_1.JPG'),
    ('2853156', 'http://tong.visitkorea.or.kr/cms/resource/43/2853143_image2_1.jpg'),
    ('2865435', 'http://tong.visitkorea.or.kr/cms/resource/17/2865417_image2_1.jpg'),
    ('2760773', 'http://tong.visitkorea.or.kr/cms/resource/66/2761266_image2_1.jpg'),
    ('2858936', 'http://tong.visitkorea.or.kr/cms/resource/34/2858934_image2_1.jpg'),
    ('2852289', 'http://tong.visitkorea.or.kr/cms/resource/85/2852285_image2_1.jpg'),
    ('2759611', 'http://tong.visitkorea.or.kr/cms/resource/64/2759664_image2_1.jpg'),
    ('2841524', 'http://tong.visitkorea.or.kr/cms/resource/09/2841509_image2_1.jpg'),
    ('2832655', 'http://tong.visitkorea.or.kr/cms/resource/54/2832654_image2_1.jpg'),
    ('2852301', 'http://tong.visitkorea.or.kr/cms/resource/97/2852297_image2_1.jpg'),
    ('2869343', 'http://tong.visitkorea.or.kr/cms/resource/29/2869329_image2_1.JPG'),
    ('2869875', 'http://tong.visitkorea.or.kr/cms/resource/70/2869870_image2_1.jpg'),
    ('2848924', 'http://tong.visitkorea.or.kr/cms/resource/20/2848820_image2_1.jpg'),
    ('2853031', 'http://tong.visitkorea.or.kr/cms/resource/21/2853021_image2_1.jpg'),
    ('2859132', 'http://tong.visitkorea.or.kr/cms/resource/00/2859100_image2_1.jpg'),
    ('2865236', 'http://tong.visitkorea.or.kr/cms/resource/31/2865231_image2_1.jpg'),
    ('2870666', 'http://tong.visitkorea.or.kr/cms/resource/58/2870658_image2_1.jpg'),
    ('2853243', 'http://tong.visitkorea.or.kr/cms/resource/41/2853241_image2_1.jpg'),
    ('2870800', 'http://tong.visitkorea.or.kr/cms/resource/92/2870792_image2_1.jpg'),
    ('2608470', 'http://tong.visitkorea.or.kr/cms/resource/11/2608611_image2_1.JPG')
) AS seed(source_id, photo_url)
 WHERE place.source_type = 'TOURAPI'
   AND place.source_id = seed.source_id
   AND place.photo_url IS NULL;
