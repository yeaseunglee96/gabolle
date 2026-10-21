-- S15P21E201-2038 — 실제로 갈 수 없는 장소 53곳을 추천·검색에서 뺀다. 지우지 않고 「숨김」(HIDDEN)으로.
--
-- 🔴 왜
--    여행 추천 1일차에 「세계를 바라보다(남자)」(해운대)가 명소로 나왔다. 이 줄은 OSM(오픈스트리트맵 — 누구나 고치는
--    공개 지도)의 tourism=artwork(거리 조각품) 점이다. 들러서 시간을 보낼 「장소」가 아니라 길가의 조형물 하나다.
--    추천 가능한 부산 장소 6,094곳을 카카오 장소 검색(같은 이름이 150m 안에 있나)과 OSM 원자료로 대조했다.
--
-- 🔴 숨긴 기준 — 두 신호가 같은 말을 할 때만
--    ① OSM 이 조각품·동상·벽화·기념물(tourism=artwork, historic=memorial)이라 하고 카카오에 150m 안 같은 이름이 없다
--    ② OSM 에서 그 점이 지워졌거나(visible=false) 폐업 표시가 있고, 카카오에도 150m 안 같은 이름이 없다
--    카카오에서만 못 찾은 곳(약 2,300곳 — 대부분 OSM 이름 표기 차이)은 숨기지 않았다. 사람 검토 목록으로 따로 남겼다.
--
-- 🔴 왜 이 방식
--    찾아 주는 조회(추천 후보·검색·근처·둘러보기)는 모두 CURATED 만 본다(PlaceRepository). 번호로 여는 조회(상세·옛
--    일정)는 그대로 열린다 — 이미 만든 일정·저장한 장소는 안 깨진다. 운영 DB 에는 2026-10-09 에 같은 UPDATE 를 먼저
--    적용했다(백업 ~/backups/place-before-S15P21E201-2038-202610090034.sql.gz). 여기서는 0건으로 지나간다.
--
-- 되돌리기:
--    UPDATE place SET curation_status = 'CURATED' WHERE curation_status = 'HIDDEN' AND place_id IN (아래 번호들);

UPDATE place
   SET curation_status = 'HIDDEN'
 WHERE curation_status = 'CURATED'
   AND place_id IN (
       'd4ccfe22-33c0-3edb-ae5f-eb231f860d8e',  -- 산동완탕교자관 (OSM 10559300309, OSM_DELETED)
       '729b4e17-b68a-3d58-9dcc-dc14d9f588cc',  -- 하모횟집 (OSM 368973198, OSM_DELETED)
       'f6bdc737-4982-36f5-b196-307ee8ab30ab',  -- 커피스미스 (OSM 4530197391, OSM_DELETED)
       '048f3374-b533-3240-a1c2-9a1cef6cfc7d',  -- 만 (OSM 4786134223, tourism=artwork)
       'e6112112-1d65-3d3d-bb46-ac8296649513',  -- 빕스 (OSM 5589948921, OSM_DELETED)
       '182394b3-ee30-38f4-b3b8-e0884279a8ae',  -- 어머니의 상 (OSM 6689629724, tourism=artwork)
       'e7d0d227-6a71-378a-98c4-d9bd0901c600',  -- 광음 (OSM 7092632966, tourism=artwork)
       '915131d9-26b0-3dcf-a92d-f6201a7fd3b1',  -- 그리스도상 (OSM 7117036453, tourism=artwork)
       'c5b90ff0-279b-3e4f-b393-ad362623d350',  -- 곰돌이조형물 (OSM 8282453821, tourism=artwork)
       '0767995a-fc93-3582-b966-3d3072d7f79d',  -- 고요한 강 (OSM 9006015086, tourism=artwork)
       '89e5964d-e86b-3722-b35a-1c733a976612',  -- 안용군 (OSM 9644873099, tourism=artwork)
       'dcf5ada4-13f3-3a50-897e-e396f39f443f',  -- 모자상 (OSM 10635512224, tourism=artwork)
       'f30e3846-b3bd-3ffc-82ab-b917f1bb2134',  -- 불상 (OSM 10636116389, tourism=artwork)
       'ecadea88-c731-327c-bbf2-549ed3daf834',  -- 뚜레쥬르 (OSM 10940848648, OSM_DELETED)
       'ed4a0473-e32e-3aee-a386-5d7d0e45eee8',  -- Well (OSM 12345747202, tourism=artwork)
       '2e6f7aea-b3aa-3f93-9265-222420a47c73',  -- 불상 (OSM 12581571039, tourism=artwork)
       '979d2a1c-b979-3378-aa6a-5bd01df0fc93',  -- 세계를 바라보다(여자) (OSM 13146981336, tourism=artwork)
       '84c6fbe1-599d-3ddf-8d43-f5b6bb96734b',  -- 세계를 바라보다(남자) (OSM 13146981337, tourism=artwork)
       '6c37eed8-45f4-3198-a69c-2b08ffe2cfb4',  -- 임실치즈피자미사랑 (OSM 4530209991, OSM_DELETED)
       'c2726eff-8d48-3f56-8dfc-690ca1723b8b',  -- 풍미횟집 (OSM 368956700, OSM_DELETED)
       '964beb0d-0297-3f2f-9afe-ceab2f09bf88',  -- BEANSBINS CAFFE (OSM 2516760531, OSM_DELETED)
       'd638b488-5a15-37aa-9f47-bf9e84e0f7ea',  -- 득남불 (OSM 3778890849, tourism=artwork)
       '7fa97d77-be81-349b-8cbe-ced099cc8856',  -- 해수관음상 (OSM 3778890851, tourism=artwork)
       '0c239e44-c38f-316a-bc00-9d5eea0e5187',  -- 개신상 (OSM 3859225867, tourism=artwork)
       'd31a7bef-c911-344e-896d-f03b1d087854',  -- 용신상 (OSM 3859225868, tourism=artwork)
       '6908b84b-ed27-3dd6-82d5-d1f24239d8aa',  -- 양신상 (OSM 3859225869, tourism=artwork)
       '347c7b8e-2cee-3444-85b4-a0141456092d',  -- 말신상 (OSM 3859225870, tourism=artwork)
       'aec43bd8-bf5d-30ae-a87a-957655e166bb',  -- 잔나비신상 (OSM 3859225871, tourism=artwork)
       '1bb4b698-2cf8-344d-b308-ff1f87ca67b2',  -- 소신상 (OSM 3859225872, tourism=artwork)
       'fe4ceacc-5028-327e-9dde-2a8ffb578c4e',  -- 돼지신상 (OSM 3859225873, tourism=artwork)
       '5f1484c5-c552-3ed1-b5df-ba8272f6b1ca',  -- 토끼신상 (OSM 3859225874, tourism=artwork)
       'ca308409-fff4-39d5-831f-77fea3f237f3',  -- 쥐신상 (OSM 3859225875, tourism=artwork)
       '13949572-e498-31d5-9999-5e21525586a9',  -- 닭신상 (OSM 3859225876, tourism=artwork)
       '7f3c3a73-6ea1-344d-bf5d-f01e077a24ab',  -- 뱀신상 (OSM 3859225877, tourism=artwork)
       'd6f279dc-1e92-351a-9fb3-16f993b2e39b',  -- 범신상 (OSM 3859225878, tourism=artwork)
       'e7881406-d39e-364b-917f-361451bde08c',  -- Busan (OSM 4168546316, tourism=artwork)
       'c6a64e2c-71ed-3bbe-89ca-96e99deda319',  -- Bob's Burger (OSM 4401927991, OSM_DELETED)
       'b44ff7a8-d0e5-3d19-a38e-ddd51cd6cf4b',  -- 세종대왕 동상 (OSM 4639685538, tourism=artwork)
       '3ec31280-5af1-37fa-9a4d-a423b2db3b2a',  -- 이순신동상 (OSM 4639685539, tourism=artwork)
       '93c97fc3-7fec-3b44-8a97-d6eb4d07f4b0',  -- pt재활용 설치미술 (OSM 4725115052, tourism=artwork)
       '0dd79f5f-c922-3f11-9105-66a129614b60',  -- 호미든관음상 (OSM 5371310297, tourism=artwork)
       'd1ac6efc-104c-3bbf-b0ff-9c1200c7fd35',  -- 종가대박집 (OSM 6191374449, OSM_DISUSED)
       '33c4f3f0-f016-3a3e-b80c-c9c1b793d0d2',  -- 바람과 달리는 말 (OSM 6355666503, tourism=artwork)
       'bcda7c65-860e-39dd-94df-2e7e309762e4',  -- 항구의 표정들 (OSM 6479928687, tourism=artwork)
       '1351fcdf-3dcf-30e7-a30b-001f3483afe5',  -- 솨뜨기 (OSM 6479946685, tourism=artwork)
       'b729e1ae-32f0-3716-ae0e-ce1423fd1110',  -- 던킨 도너츠 (OSM 6853078877, OSM_DELETED)
       'f7e55be0-b2a8-3c30-bbdb-301e57c01193',  -- 베 짜는 옥녀상 (OSM 9784789501, tourism=artwork)
       'fa83abb4-acc4-360d-b82d-a84e073d7d38',  -- 사해의 전설 (OSM 10821125109, tourism=artwork)
       '8181918d-7c86-3953-952d-de9a6cfd2dc7',  -- 상향수불 (OSM 11074698564, tourism=artwork)
       '6aa0dec8-4ca4-344d-88f1-177e2f7be95f',  -- 학업성취를 (OSM 11074698567, tourism=artwork)
       '3e8ca8d3-7fb0-3872-9c34-bdf3d9017c04',  -- Starbucks (OSM 13886870215, OSM_DELETED)
       '87c48d47-02dd-3831-9e1a-f53582c80671',  -- 경남고등학교 교훈석 (OSM 5857261786, tourism=artwork)
       'bac64710-4779-322f-ab80-8fa9d72272c1'   -- 어린왕자상 (OSM 10679830337, tourism=artwork)
   );
