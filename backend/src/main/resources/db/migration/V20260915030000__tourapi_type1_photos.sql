-- ══════════════════════════════════════════════════════════════════════════════
-- 관광공사 사진(Type1, 공공누리 제1유형)을 이미 적재된 장소에 채운다 — S15P21E201-146
-- ══════════════════════════════════════════════════════════════════════════════
--
-- TourApiPlaceLoader 는 이미 있는 장소를 건너뛴다(고치지 않는다) — 그래서 코드 변경만으로는
-- 운영에 먼저 들어간 장소의 photo_url 이 안 채워진다. 저작권 유형이 Type1(자유 이용)인
-- 78곳만, 그때 받아 둔 원본 파일(bigData/data/raw/tourapi/tourapi-busan.ndjson)에서
-- 그대로 뽑아 채운다. Type3(제3자 저작물, 468곳)는 재사용 전 저작권자 허락이 필요해
-- 넣지 않는다 — TourApiPlaceLoader 코드 변경과 같은 기준이다.
--
-- 🔴 이 UPDATE 가 실제로 몇 행을 건드리는지는 확인 못 함 — 운영 DB 읽기가 도구 권한에
--    막혀 있어 재지 못했다. TourApiPlaceLoader 가 이미 돌았으면 채워지고, 그 장소가 아직
--    없으면 0행을 건드리고 지나간다(그래도 안전하다 — 앞으로 도는 적재는 이미 채워 넣는다).
WITH photos (source_id, photo_url) AS (
  VALUES
    ('1935608', 'http://tong.visitkorea.or.kr/cms/resource/44/3552344_image2_1.jpg'),
    ('2744597', 'http://tong.visitkorea.or.kr/cms/resource/40/3369140_image2_1.jpg'),
    ('298099', 'http://tong.visitkorea.or.kr/cms/resource/82/3552382_image2_1.jpg'),
    ('3421909', 'http://tong.visitkorea.or.kr/cms/resource/96/3421696_image2_1.jpg'),
    ('1607440', 'http://tong.visitkorea.or.kr/cms/resource/14/3561514_image2_1.jpg'),
    ('129140', 'http://tong.visitkorea.or.kr/cms/resource/43/3589743_image2_1.jpg'),
    ('298111', 'http://tong.visitkorea.or.kr/cms/resource/04/3561504_image2_1.jpg'),
    ('126028', 'http://tong.visitkorea.or.kr/cms/resource/44/3575744_image2_1.jpg'),
    ('2643873', 'http://tong.visitkorea.or.kr/cms/resource/82/3561482_image2_1.jpg'),
    ('2614714', 'http://tong.visitkorea.or.kr/cms/resource/37/3029337_image2_1.jpg'),
    ('2944336', 'http://tong.visitkorea.or.kr/cms/resource/00/2947000_image2_1.jpg'),
    ('769761', 'http://tong.visitkorea.or.kr/cms/resource/08/3506208_image2_1.jpg'),
    ('2822334', 'http://tong.visitkorea.or.kr/cms/resource/33/2822333_image2_1.png'),
    ('128810', 'http://tong.visitkorea.or.kr/cms/resource/62/3575962_image2_1.jpg'),
    ('2614716', 'http://tong.visitkorea.or.kr/cms/resource/28/3589728_image2_1.jpg'),
    ('2675011', 'http://tong.visitkorea.or.kr/cms/resource/30/3552430_image2_1.jpg'),
    ('2617724', 'http://tong.visitkorea.or.kr/cms/resource/89/3575989_image2_1.jpg'),
    ('985921', 'http://tong.visitkorea.or.kr/cms/resource/89/3552989_image2_1.jpg'),
    ('127149', 'http://tong.visitkorea.or.kr/cms/resource/47/3498447_image2_1.jpg'),
    ('2677748', 'http://tong.visitkorea.or.kr/cms/resource/04/3575804_image2_1.jpg'),
    ('2708108', 'http://tong.visitkorea.or.kr/cms/resource/71/3552571_image2_1.jpg'),
    ('1607242', 'http://tong.visitkorea.or.kr/cms/resource/04/3552504_image2_1.jpg'),
    ('2946508', 'http://tong.visitkorea.or.kr/cms/resource/43/2947043_image2_1.jpg'),
    ('128164', 'http://tong.visitkorea.or.kr/cms/resource/77/3368477_image2_1.jpg'),
    ('3442423', 'http://tong.visitkorea.or.kr/cms/resource/07/3442507_image2_1.jpeg'),
    ('2785762', 'http://tong.visitkorea.or.kr/cms/resource/07/3370707_image2_1.jpg'),
    ('2782626', 'http://tong.visitkorea.or.kr/cms/resource/38/3575838_image2_1.jpg'),
    ('128053', 'http://tong.visitkorea.or.kr/cms/resource/36/3552636_image2_1.jpg'),
    ('1607335', 'http://tong.visitkorea.or.kr/cms/resource/09/3561509_image2_1.jpg'),
    ('128108', 'http://tong.visitkorea.or.kr/cms/resource/25/3409925_image2_1.jpg'),
    ('2674974', 'http://tong.visitkorea.or.kr/cms/resource/46/3029246_image2_1.jpg'),
    ('2385686', 'http://tong.visitkorea.or.kr/cms/resource/91/3309791_image2_1.jpg'),
    ('337415', 'http://tong.visitkorea.or.kr/cms/resource/81/3561181_image2_1.jpg'),
    ('3454074', 'http://tong.visitkorea.or.kr/cms/resource/99/3453999_image2_1.jpg'),
    ('128134', 'http://tong.visitkorea.or.kr/cms/resource/58/3589758_image2_1.jpg'),
    ('2776276', 'http://tong.visitkorea.or.kr/cms/resource/02/3561302_image2_1.jpg'),
    ('2822240', 'http://tong.visitkorea.or.kr/cms/resource/33/2822233_image2_1.jpg'),
    ('347229', 'http://tong.visitkorea.or.kr/cms/resource/89/3561189_image2_1.jpg'),
    ('2944033', 'http://tong.visitkorea.or.kr/cms/resource/22/2947122_image2_1.jpg'),
    ('3017435', 'http://tong.visitkorea.or.kr/cms/resource/53/3455153_image2_1.jpg'),
    ('3452166', 'http://tong.visitkorea.or.kr/cms/resource/06/3451506_image2_1.jpg'),
    ('127752', 'http://tong.visitkorea.or.kr/cms/resource/85/3547085_image2_1.jpg'),
    ('1608751', 'http://tong.visitkorea.or.kr/cms/resource/87/3552687_image2_1.jpg'),
    ('128055', 'http://tong.visitkorea.or.kr/cms/resource/63/3370163_image2_1.jpg'),
    ('2775559', 'http://tong.visitkorea.or.kr/cms/resource/91/3561291_image2_1.jpg'),
    ('2775495', 'http://tong.visitkorea.or.kr/cms/resource/73/3561273_image2_1.jpg'),
    ('2822343', 'http://tong.visitkorea.or.kr/cms/resource/37/2822337_image2_1.png'),
    ('128886', 'http://tong.visitkorea.or.kr/cms/resource/87/3561487_image2_1.jpg'),
    ('2756696', 'http://tong.visitkorea.or.kr/cms/resource/41/3497041_image2_1.jpg'),
    ('2823059', 'http://tong.visitkorea.or.kr/cms/resource/56/2823056_image2_1.jpg'),
    ('3083767', 'http://tong.visitkorea.or.kr/cms/resource/38/3428638_image2_1.jpg'),
    ('3451794', 'http://tong.visitkorea.or.kr/cms/resource/71/3459571_image2_1.jpg'),
    ('2381567', 'http://tong.visitkorea.or.kr/cms/resource/35/3575935_image2_1.jpg'),
    ('131087', 'http://tong.visitkorea.or.kr/cms/resource/86/3496786_image2_1.jpg'),
    ('1878262', 'http://tong.visitkorea.or.kr/cms/resource/12/3575912_image2_1.jpg'),
    ('3044634', 'http://tong.visitkorea.or.kr/cms/resource/30/3054130_image2_1.jpg'),
    ('132574', 'http://tong.visitkorea.or.kr/cms/resource/55/3561855_image2_1.jpg'),
    ('2788830', 'http://tong.visitkorea.or.kr/cms/resource/11/3562011_image2_1.jpg'),
    ('132187', 'http://tong.visitkorea.or.kr/cms/resource/25/3561825_image2_1.jpg'),
    ('986063', 'http://tong.visitkorea.or.kr/cms/resource/14/3561914_image2_1.jpg'),
    ('1013694', 'http://tong.visitkorea.or.kr/cms/resource/19/3561919_image2_1.jpg'),
    ('2763881', 'http://tong.visitkorea.or.kr/cms/resource/95/3561995_image2_1.jpg'),
    ('132190', 'http://tong.visitkorea.or.kr/cms/resource/13/2941313_image2_1.bmp'),
    ('132499', 'http://tong.visitkorea.or.kr/cms/resource/44/3561844_image2_1.jpg'),
    ('132189', 'http://tong.visitkorea.or.kr/cms/resource/33/3561833_image2_1.jpg'),
    ('2763981', 'http://tong.visitkorea.or.kr/cms/resource/76/3561476_image2_1.jpg'),
    ('132576', 'http://tong.visitkorea.or.kr/cms/resource/63/3561863_image2_1.jpg'),
    ('1013709', 'http://tong.visitkorea.or.kr/cms/resource/26/3561926_image2_1.jpg'),
    ('2788444', 'http://tong.visitkorea.or.kr/cms/resource/25/3561525_image2_1.jpg'),
    ('2788451', 'http://tong.visitkorea.or.kr/cms/resource/20/3561520_image2_1.jpg'),
    ('2776286', 'http://tong.visitkorea.or.kr/cms/resource/98/3561498_image2_1.jpg'),
    ('1013721', 'http://tong.visitkorea.or.kr/cms/resource/33/3561933_image2_1.jpg'),
    ('1304515', 'http://tong.visitkorea.or.kr/cms/resource/13/3552613_image2_1.jpg'),
    ('3444416', 'http://tong.visitkorea.or.kr/cms/resource/23/3444423_image2_1.JPG'),
    ('3452014', 'http://tong.visitkorea.or.kr/cms/resource/97/3451897_image2_1.jpg'),
    ('3442923', 'http://tong.visitkorea.or.kr/cms/resource/54/3442954_image2_1.jpg'),
    ('3443614', 'http://tong.visitkorea.or.kr/cms/resource/89/3443589_image2_1.jpeg'),
    ('843489', 'http://tong.visitkorea.or.kr/cms/resource/35/3585635_image2_1.jpg')
)
UPDATE place
   SET photo_url = photos.photo_url,
       photo_source = '한국관광공사 공공누리 제1유형'
  FROM photos
 WHERE place.source_type = 'TOURAPI'
   AND place.source_id = photos.source_id
   AND place.photo_url IS NULL;
