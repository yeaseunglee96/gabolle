-- S15P21E201-1882 — OSM 장소 이름이 러시아어·중국어·일본어로 들어가 있던 것을 한국어 이름(name:ko)으로.
--
-- 🔴 왜: 적재기(OsmPoiReader.nameOf)가 기본 이름(name)을 먼저 보고 한국어 이름(name:ko)은 기본 이름이 없을 때만 봤다.
--    기본 이름에 그 나라 글자를 넣은 곳 — 문화 갈래의 「Храм святой Богородицы」(name:ko 「정교회」), 식당의
--    「巨人炸雞」(「거인통닭」) — 이 한국어 화면에 외국어로 떴다. 적재기는 같은 MR 에서 고쳤고, 이미 들어간 곳을 여기서 고친다.
--
-- 대상: bigData/data/raw/pbf/poi.ndjson 에서 기본 이름에 한글이 없고 한국어 이름에 한글이 있는 51곳(후보 — 갈래가
--    없어 적재되지 않은 곳도 섞여 있고, 그런 행은 아래 UPDATE 가 아무것도 안 건드린다).
-- 장소 번호는 OsmPlaceLoader.placeIdOf 와 같은 규칙(UUID.nameUUIDFromBytes("gabolle:place:OSM:" + osmId))으로 계산했다.
-- 이름은 PlaceNames.primary(세미콜론이면 한 이름) 를 거친 값이다.
-- 🔴 영어 이름 칸도 채운다. OSM 장소는 name_en 이 비어 있어 외국어 화면이 한국어 칸의 「Starbucks」를 보여 주고 있었다 —
--    한국어로만 바꾸면 「스타벅스 (Seutabeokseu)」로 나빠진다. name:en, 없으면 라틴 문자 원래 이름(러시아어·한자는 안 넣는다).
--    영어 이름이 이미 있으면 그대로 둔다.
-- 🔴 지금 이름이 옛 외국어 이름 «그대로» 일 때만 바꾼다 — 그 사이 사람이 고친 이름은 덮지 않는다.
UPDATE place p
SET name_ko = v.new_name,
    name_en = COALESCE(p.name_en, v.name_en)
FROM (VALUES
    ('ae768817-7095-365c-a561-247d59b545c8'::uuid, 'Viewpoint New Harbour', '흰돌메공원', 'Viewpoint New Harbour'),  -- osm node 400908020
    ('47ce32d0-c3af-3097-b2d0-e11d09405752'::uuid, 'E-Mart', '이마트', 'E-Mart'),  -- osm node 2395780684
    ('c260b810-deee-3cf4-8b12-b641de21ca14'::uuid, 'McDonald', '맥도날드', 'McDonald'),  -- osm node 2513845941
    ('1c638a87-ea3c-3ab2-9a17-a7d91be51017'::uuid, 'Starbucks', '스타벅스', 'Starbucks'),  -- osm node 3785217646
    ('5b20e2e4-4172-3261-883d-23cc45ce2216'::uuid, 'Tom N Toms', '탐앤탐스', 'Tom N Toms'),  -- osm node 3785255115
    ('1b8c690e-d8b5-330e-9a7b-1c32594d1178'::uuid, 'Starbucks', '스타벅스', 'Starbucks'),  -- osm node 3785356880
    ('b062b7a5-1c77-336c-984e-bdc17023fab6'::uuid, '市場(吃)', '부산부평시장', NULL),  -- osm node 4180081314
    ('7f596abb-05eb-3910-a908-9d330f96363a'::uuid, '巨人炸雞', '거인통닭', NULL),  -- osm node 4180081315
    ('3ba8e5c9-0d79-309c-a2a4-92703e8ed82f'::uuid, 'Starbucks', '스타벅스', 'Starbucks'),  -- osm node 4526476190
    ('ba674e69-e08f-344d-8627-fe8daf79be8f'::uuid, '南浦蔘雞湯', '남포삼계탕', NULL),  -- osm node 4535673594
    ('915edbff-7b2c-3156-adea-4e9b45c9551d'::uuid, 'Starbucks', '스타벅스', 'Starbucks'),  -- osm node 4537711648
    ('d7201cd6-2e41-3298-801f-1f409d43e5fd'::uuid, 'Starbucks', '스타벅스', 'Starbucks'),  -- osm node 4539659287
    ('2d613135-c6df-3289-a826-1489a5911033'::uuid, 'Songdo Marine Sports Club', '송도 마린 스포츠 클럽', 'Songdo Marine Sports Club'),  -- osm node 4638812697
    ('4ad5849e-1a71-3858-85a4-9d3602575c3b'::uuid, 'Homeplus', '홈플러스', 'Homeplus'),  -- osm node 4658697968
    ('84a6d8f4-86eb-33bf-9071-ebb5f5a7cb22'::uuid, '3', '3번 구역', '3'),  -- osm node 4739375136
    ('7bbcfe98-14a8-3c4d-893c-a55c2cda28bd'::uuid, '4', '4번 표지', '4'),  -- osm node 4739375156
    ('836b152e-302e-3550-a68d-1cd3db66fc23'::uuid, 'Subway', '서브웨이 중앙점', 'SUBWAY Busan Jungang'),  -- osm node 4813530022
    ('52742d72-e50d-38a2-b958-705ba0ead59e'::uuid, 'CGV', '씨제이 씨지브이)㈜', 'CGV Haeundae'),  -- osm node 4865549021
    ('94c11522-3216-3b73-91af-a12fda59a536'::uuid, 'CU 24hour convenient store', 'CU 편의점', 'CU 24hour convenient store'),  -- osm node 4941378923
    ('a23fc6e1-710f-3162-96a5-7bc76266cc1b'::uuid, 'Homeplus', '홈플러스', 'Homeplus'),  -- osm node 4988186721
    ('a7e4964c-e961-34bc-b395-fc3e0bbfaf30'::uuid, 'Subway', '서브웨이', 'Subway'),  -- osm node 5043795225
    ('fa313cf2-3870-3ab5-bd48-8f891dfd4ac6'::uuid, 'Vips', '빕스 온천장역점', 'Vips'),  -- osm node 5289370213
    ('b4cccaec-d779-3cd9-bab3-bab16ed95343'::uuid, 'INUKI', '이누키', 'INUKI'),  -- osm node 5596694232
    ('7d19bbe6-b962-3fae-b4ca-1f1ecff4e254'::uuid, 'Olive Young', '올리브영', 'Olive Young'),  -- osm node 5599855921
    ('118b1d70-faa1-3309-b3d0-210e7c9df5cd'::uuid, '海雲台電影大道', '해운대 영화의 거리', NULL),  -- osm node 5750034495
    ('2ed4586f-629f-3a6a-85df-f8d245c78f93'::uuid, 'Cleantopia CoinWash', '크린토피아 CoinWash', 'Cleantopia CoinWash'),  -- osm node 5836333185
    ('0f117192-4183-3e49-820b-dd84f486afd5'::uuid, 'Olive Young', '올리브영', 'Olive Young'),  -- osm node 5884911287
    ('0b29bed5-e860-3c23-b850-9b7e8daab101'::uuid, '富平コプチャン', '부평양곱창', NULL),  -- osm node 5918295585
    ('c95e9c03-db72-3e8e-aeb2-a17c83edecdc'::uuid, 'Starbucks', '스타벅스', 'Starbucks'),  -- osm node 6122180521
    ('4344dd04-0b1b-36f8-aa78-a4117f09e233'::uuid, 'Olleh', '올레', 'Olleh'),  -- osm node 6183270579
    ('aa4eaf07-b78c-377c-86b7-a5d6c28cf1fd'::uuid, '7-Eleven', '세븐일레븐', '7-Eleven'),  -- osm node 6433202075
    ('aafa8de3-b21d-319b-a6d4-5106c824d388'::uuid, 'Hollys', '할리스', 'Hollys'),  -- osm node 6476885385
    ('1a376b5f-305e-3010-ae46-705931219ecf'::uuid, 'Храм святой Богородицы', '정교회', 'Orthodox Church'),  -- osm node 6680782685
    ('dfc99cc1-1b56-3966-8274-a4167f4c3cf9'::uuid, '中央両替', '중앙환전', 'JoongAng Money Exchange'),  -- osm node 6836322354
    ('8c8db7d5-4c8e-3036-9999-5a5c5e093ab9'::uuid, 'GS25', 'GS25 아스티오피스텔점', 'GS25'),  -- osm node 8321776220
    ('3754812b-70a9-3776-88de-b55511a7d742'::uuid, 'el olive', '엘올리브', 'el olive'),  -- osm node 9926488292
    ('bf924e79-f567-36f4-8c6b-6000b3f5b9b9'::uuid, 'Bosudongga', '정원이 있는 카페', 'Bosudongga'),  -- osm node 10841330772
    ('1fe5a227-9729-370e-9e25-542ddfaf0fb9'::uuid, 'Hard And Heavy', '하드앤헤비', 'Hard And Heavy'),  -- osm node 11124191736
    ('1b48163b-d5e2-32dd-a528-f935ca70ab9a'::uuid, 'Olive Young', '올리브영', 'Olive Young'),  -- osm node 12020307577
    ('888f5de0-d03f-33de-b1a5-c08a55fa2d88'::uuid, 'Olive Young', '올리브영', 'Olive Young'),  -- osm node 12163193957
    ('3a503d33-c4e7-3090-9cea-0d73da7dc97c'::uuid, 'Baskin-Robbins', '배스킨라빈스', 'Baskin-Robbins'),  -- osm node 12491975031
    ('15189cad-f088-3633-88b9-c26f1245121b'::uuid, 'Travelodge Suites Busan Centum', '트레블로지 스위트 부산 센텀 호텔', 'Travelodge Suites Busan Centum'),  -- osm node 12498002701
    ('af71ec44-9802-3dfa-b73c-b101fb586f60'::uuid, 'Wood Side Bar', '우드사이드', 'Wood Side Bar'),  -- osm node 12500657502
    ('31466d70-920c-3a45-b3b5-5a02161f1c13'::uuid, 'MAK APA', '막카파', 'MAK APA'),  -- osm node 12961247004
    ('2ad99113-46d7-3c13-8854-4e352a4c1b74'::uuid, 'Hollys', '할리스', 'Hollys'),  -- osm node 12963629524
    ('4e453aa6-c442-3632-9efa-0a90bd64674c'::uuid, 'E-Mart', '이마트', 'E-Mart'),  -- osm node 12963691592
    ('b882a037-cbc2-3861-b335-4859c77b1c6d'::uuid, 'Hours coffee', '아월스커피', 'Hours coffee'),  -- osm node 13018535354
    ('52205bec-b80e-3fec-bb1a-264ec8c9421e'::uuid, 'Burgers Almighty', '버거스 올마이티 서면점', 'Burgers Almighty'),  -- osm node 13382215501
    ('0f6b2d85-3b7e-3349-95ee-bccbe3df89d5'::uuid, 'Coralani', '코랄라니', 'Coralani'),  -- osm node 13384617801
    ('6147968e-c49b-3550-9d98-fbdcdfd44a37'::uuid, 'EDIYA COFFEE', '이디야커피', 'EDIYA COFFEE'),  -- osm node 13760264672
    ('3ba11d36-2552-368a-bdc8-480d8aa1aa84'::uuid, 'Auntie Annels', '앤티앤스', 'Auntie Annels')  -- osm node 13760264674
) AS v(place_id, old_name, new_name, name_en)
WHERE p.place_id = v.place_id
  AND p.source_type = 'OSM'
  AND p.name_ko = v.old_name;
