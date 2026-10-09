-- S15P21E201-2038 — 부산 행정 경계 밖이면서 김해·양산도 아닌 장소 67곳(창원 51·밀양 12·울산 울주 4)을 지운다.
--
-- 🔴 왜
--    V20261009140000 은 김해·양산만 지웠다. 나머지 부산 밖 OSM 점도 지우기로 사용자가 정했다.
--    판정: 부산 행정 경계(geo/busan-boundary.json) 밖 + 카카오 행정구역 조회가 경상남도 창원시·밀양시, 울산광역시 울주군.
--    이 가운데 6곳은 이름(교회·성당·나무)으로 V20261009140000 에서 이미 지워졌다.
--
-- 🔴 어떻게 — V20261009140000 과 같은 방식. 일정 항목은 빼고 순번을 다시 매기며, 끊긴 자리는 직선거리 어림 구간(ESTIMATED)으로 잇는다.
--    추천 노출 이벤트(event_outbox)는 기록이라 건드리지 않는다.
--    운영 DB 에는 2026-10-09 에 먼저 적용했다(백업 ~/backups/gabolle-full-before-S15P21E201-2038-delete3-202610090909.dump). 여기서는 0건. 멱등.
--    다시 들어오지 않게 하는 것은 OSM 적재의 부산 경계 필터(OsmPlaceLoader)다.

create temp table del (place_id uuid primary key);
insert into del (place_id) select v.id::uuid from (values
     ('00383d84-58e4-3c2b-bc52-655d1ff2bbcb')  -- 금호횟집 (부산 밖)
    ,('131dbbe3-0458-3867-9005-7743b9b94271')  -- 청아대 횟집 (부산 밖)
    ,('1687026d-8170-3a49-b5d2-f0ce3c164921')  -- 온정횟집 (부산 밖)
    ,('194f31b1-3905-3197-8df1-5a5ce07b79a7')  -- 앗커피다 (부산 밖)
    ,('1b6202a8-fb3a-3b03-afe4-462497bd712e')  -- 새부산횟집 (부산 밖)
    ,('1c715669-26c0-3c75-af53-574b87962eb7')  -- 자연산횟집 (부산 밖)
    ,('200c0119-33f3-3d71-8622-6135be93b0d9')  -- 밀레니엄횟집 (부산 밖)
    ,('21139fff-d69e-3a51-a976-9d7b6777b4fa')  -- 용원어시장 (부산 밖)
    ,('2d54c2f8-5f36-341f-8d3f-7822b48cc787')  -- 안성금호횟집 (부산 밖)
    ,('38e692d0-f9d4-31e5-b91f-1f678aa6c4b2')  -- 어부가횟집 (부산 밖)
    ,('39c7bb4c-3d7c-33b6-a9b2-73b17631ef5d')  -- 삼랑진 생태문화공원 (부산 밖)
    ,('3f8fd2b9-28c0-3c28-8e13-0e8baad1a0e0')  -- 해금강횟집 (부산 밖)
    ,('3fa95173-28f5-3933-928d-50cc57179f81')  -- 태평양횟집 (부산 밖)
    ,('44613cfb-de3b-3dbb-a3d4-821efd90f3de')  -- 삼랑진소머리곰탕 (부산 밖)
    ,('47b32147-6409-354f-893b-40abde988cd2')  -- Q-motel (부산 밖)
    ,('48d66cdc-3814-32a7-af16-1f5dfe629868')  -- 수목원 (부산 밖)
    ,('4b5e0140-fda8-31d1-833d-88ec8e54a473')  -- 갈밭골산장 (부산 밖)
    ,('4f2f3fe0-cc44-33d3-80b3-33fbb433790c')  -- 아지매횟집 (부산 밖)
    ,('5191b110-5da5-36a7-85fe-9380a2bfbdcb')  -- 등나무횟집 (부산 밖)
    ,('51ab85df-89de-3402-831c-989fc839861a')  -- 바다횟집 (부산 밖)
    ,('5372caf2-09e6-309e-ad4a-49655cce7470')  -- 골목횟집 (부산 밖)
    ,('53d0c1fd-b85a-303c-b236-22b577b532d8')  -- 가덕도횟집 (부산 밖)
    ,('57607237-6559-33a8-9b94-fd2dd50f0ee9')  -- 용원성당 (부산 밖)
    ,('5855213e-9b44-36a4-8059-da6567027bdb')  -- 용원보배산참숯찜질방 (부산 밖)
    ,('5a94bac6-402a-366f-9c00-76289adea9e8')  -- 웅천왜성 (부산 밖)
    ,('637fc514-d910-3ed6-a199-83a4c12a2023')  -- 윤정횟집 (부산 밖)
    ,('665acd41-d397-339c-9019-23dc12336f1d')  -- 도선장횟집 (부산 밖)
    ,('66c84afc-44a4-3d6c-8662-b938e3711322')  -- 천주교부산교구삼랑진성당 (부산 밖)
    ,('6d94f02c-5df0-3c44-9e57-fa992e478545')  -- 비봉삼계탕 (부산 밖)
    ,('6df2e59b-a3c0-3953-bee9-fc4511d46c32')  -- 웅천읍성 (부산 밖)
    ,('7386112f-940e-33cd-9b25-d12108aaa5e6')  -- 돌고래횟집 (부산 밖)
    ,('763abb8b-b527-3463-bdb8-491631aed5af')  -- 유토피아관광호텔 (부산 밖)
    ,('77f5e1d6-185c-32ba-b26c-dfc71411fad1')  -- 제덕동 팽나무 (부산 밖)
    ,('7d0b0982-8e2d-3ad8-8c9c-be387f7da27f')  -- 신항만횟집 (부산 밖)
    ,('816fd48d-dc57-3564-9164-6f77729d2ff7')  -- Assemble Coffee (부산 밖)
    ,('82237b7a-19b0-3dc0-953c-1a8cc8774092')  -- 김해식당 (부산 밖)
    ,('822fc404-1eb4-3c06-a3bf-a94b4d3aa816')  -- 들머리쌈밥 (부산 밖)
    ,('82464bf7-5ce0-3af8-8148-6055eeaf6cda')  -- 안골왜성 (부산 밖)
    ,('88bdc360-cc0b-38c9-a9f4-3ad37ed64d16')  -- 스카이모텔 (부산 밖)
    ,('8bc8b563-58a6-302b-a071-082d7f48cb33')  -- 고리횟집 (부산 밖)
    ,('8c918553-9e67-3f68-ab27-5fc9e0a22e60')  -- CF모텔 (부산 밖)
    ,('8fe66e55-a7ae-3f96-b9f3-8342c9842d33')  -- 등대횟집 (부산 밖)
    ,('90ad69ff-c5de-3d17-a358-690ddba4ddc9')  -- 춘천옥산가찜질방 (부산 밖)
    ,('92740396-a1bf-3094-84ec-5462d86e8f52')  -- 수담 (부산 밖)
    ,('94511496-b0ff-318b-970d-356ee61cc6a0')  -- 동원횟집 (부산 밖)
    ,('94f278f3-219f-324b-b562-e843f1240871')  -- 성내동 느티나무 (부산 밖)
    ,('9770608a-5c2f-3d98-b1d7-b5237dc69d4b')  -- 거제도횟집 (부산 밖)
    ,('9e420607-ddc6-3c13-8346-91ae01eebe70')  -- 장어와횟집 (부산 밖)
    ,('a27e8040-169f-3823-beeb-0fc0f2d504de')  -- 대장동 느티나무 (부산 밖)
    ,('a5594326-16ca-309c-8c04-4cacfdf153dd')  -- 흰둘메공원 남문휴게소 (부산 밖)
    ,('ae6860c4-3fa7-3183-a088-e12f78bf09e9')  -- 성해횟집 (부산 밖)
    ,('b1bc9714-d89b-3c0e-837b-e8f614d4c93b')  -- 내원암 (부산 밖)
    ,('c21fb9f1-5db4-35dc-b74a-9c7f714524c7')  -- 태화반점 (부산 밖)
    ,('c4daa5d5-9cf9-38da-b083-e05dd971987e')  -- 제포성지 (부산 밖)
    ,('c58a5fae-c7eb-3776-9fe2-042c9589a1f9')  -- 솔섬횟집 (부산 밖)
    ,('c70ac180-359d-3747-9359-ed0167f8c098')  -- 북부동 느티나무 (부산 밖)
    ,('c8298e37-c584-3434-9f6d-9e52771c7320')  -- 봉해횟집 (부산 밖)
    ,('ca21b1e5-442c-32ea-bdd0-3ab49939a88f')  -- sosong (부산 밖)
    ,('cbe22b06-128a-3f7f-9e58-7a1fbd8517dc')  -- 마천산장 (부산 밖)
    ,('ce095338-051a-3b01-9819-21cb4439dedb')  -- 코리아나관광호텔 (부산 밖)
    ,('d010cc68-b93a-3479-9e7d-47437713c84a')  -- 용원장어구이 (부산 밖)
    ,('d42e4dba-16f8-3c3e-bdf0-23ad66ff4892')  -- 용원사우나찜질방 (부산 밖)
    ,('d729bc2d-e2e4-3a4f-9e95-ba06644d9486')  -- 골매횟집 (부산 밖)
    ,('d81c1565-996c-3ba6-a0b2-2f4420f87df1')  -- Daesan Art Museum (부산 밖)
    ,('ddeb7e47-789d-3266-8fb6-85b2703fe52f')  -- 청솔횟집 (부산 밖)
    ,('f10fe6df-e46d-340b-9f9f-3783de6f68f6')  -- 나이스관광호텔 (부산 밖)
    ,('fd89ff66-4699-30d9-9c13-402d519ec400')  -- 그린준호텔 (부산 밖)
) as v(id)
where exists (select 1 from place p where p.place_id = v.id::uuid);
-- 지울 장소로 합쳐진(MERGED) 사본도 같이 지운다 — 가리키는 정본이 사라지므로
insert into del select p.place_id from place p join del d on p.merged_into=d.place_id where p.place_id not in (select place_id from del);
create temp table gone as
 select i.itinerary_item_id, i.itinerary_version_id, i.day_index, i.sequence, i.item_key, i.place_id
 from itinerary_item i join del d using (place_id);
create temp table aff as select distinct itinerary_version_id, day_index from gone;
-- 지워질 구간에서 쓰던 이동수단(새 구간에 물려준다)
create temp table oldmode as
 select l.itinerary_version_id, l.day_index, l.sequence, l.travel_mode from itinerary_leg l join aff using (itinerary_version_id, day_index);
-- 1) 장소에 닿는 구간과 항목 삭제
delete from itinerary_leg l using del d where l.from_place_id=d.place_id or l.to_place_id=d.place_id;
delete from itinerary_item i using gone g where i.itinerary_item_id=g.itinerary_item_id;
-- 2) 남은 항목 순번 다시 매기기
create temp table newseq as
 select i.itinerary_item_id, i.itinerary_version_id, i.day_index, i.sequence old_seq,
        row_number() over (partition by i.itinerary_version_id, i.day_index order by i.sequence)::int new_seq, i.place_id
 from itinerary_item i join aff using (itinerary_version_id, day_index);
update itinerary_item i set sequence = i.sequence + 100000 from newseq n where n.itinerary_item_id=i.itinerary_item_id;
update itinerary_item i set sequence = n.new_seq from newseq n where n.itinerary_item_id=i.itinerary_item_id;
-- 3) 남은 구간: 새 연속쌍과 맞는 것은 번호만 옮기고, 맞지 않는 것은 지운다
create temp table pairs as
 select n.itinerary_version_id, n.day_index, n.new_seq, n.old_seq, p.place_id from_pid, n.place_id to_pid
 from newseq n left join newseq p on p.itinerary_version_id=n.itinerary_version_id and p.day_index=n.day_index and p.new_seq=n.new_seq-1;
update itinerary_leg l set sequence = l.sequence + 100000 from aff a where a.itinerary_version_id=l.itinerary_version_id and a.day_index=l.day_index;
update itinerary_leg l set sequence = pr.new_seq from pairs pr
 where pr.itinerary_version_id=l.itinerary_version_id and pr.day_index=l.day_index and l.sequence=pr.old_seq+100000
   and l.to_place_id=pr.to_pid and l.from_place_id is not distinct from pr.from_pid;
create temp table stale as select l.itinerary_leg_id from itinerary_leg l join aff using (itinerary_version_id, day_index) where l.sequence > 100000;
delete from itinerary_leg l using stale s where s.itinerary_leg_id=l.itinerary_leg_id;
-- 4) 빠진 쌍은 직선거리 어림 구간으로 채운다 (선형·소요시간 없음, ESTIMATED)
create temp table inserted as
 select gen_random_uuid() itinerary_leg_id, pr.itinerary_version_id, pr.day_index, pr.new_seq, pr.from_pid, pr.to_pid,
   coalesce((select o.travel_mode from oldmode o where o.itinerary_version_id=pr.itinerary_version_id and o.day_index=pr.day_index and o.sequence=pr.old_seq),'WALK') travel_mode,
   case when f.lat is not null and t.lat is not null then round(6371000*2*asin(sqrt(power(sin(radians(t.lat-f.lat)/2),2)+cos(radians(f.lat))*cos(radians(t.lat))*power(sin(radians(t.lng-f.lng)/2),2))))::int end dist
 from pairs pr left join place f on f.place_id=pr.from_pid left join place t on t.place_id=pr.to_pid
 where not exists (select 1 from itinerary_leg l where l.itinerary_version_id=pr.itinerary_version_id and l.day_index=pr.day_index and l.sequence=pr.new_seq)
   and (pr.from_pid is null or pr.from_pid<>pr.to_pid);
insert into itinerary_leg(itinerary_leg_id,itinerary_version_id,day_index,sequence,from_place_id,to_place_id,travel_mode,distance_m,duration_min,walking_meters,created_at,data_status)
 select itinerary_leg_id,itinerary_version_id,day_index,new_seq,from_pid,to_pid,travel_mode,dist,null,case when travel_mode='WALK' then dist end,now(),'ESTIMATED' from inserted;
-- 5) 일정 바깥 참조
delete from itinerary_item_actual a using gone g where a.item_key=g.item_key and not exists (select 1 from itinerary_item i where i.item_key=g.item_key);
delete from itinerary_stop_event a using gone g where a.item_key=g.item_key and not exists (select 1 from itinerary_item i where i.item_key=g.item_key);
update trip t set accommodation_place_id=null from del d where t.accommodation_place_id=d.place_id;
update place p set merged_into=null from del d where p.merged_into=d.place_id and p.place_id not in (select place_id from del);
update story s set place_id=null from del d where s.place_id=d.place_id;
delete from itinerary_excluded_place x using del d where x.place_id=d.place_id;
delete from trip_seed_place x using del d where x.place_id=d.place_id;
delete from editorial_pick_place x using del d where x.place_id=d.place_id;
delete from place_feature x using del d where x.place_id=d.place_id;
delete from place_photo x using del d where x.place_id=d.place_id;
delete from recommendation_candidate x using del d where x.place_id=d.place_id;
delete from user_place_taste_state x using del d where x.place_id=d.place_id;
delete from place_merge_log x using del d where x.kept_place_id=d.place_id or x.merged_place_id=d.place_id;
-- CASCADE 표는 place 삭제가 지운다 (saved_place, collection_item, review, visit_verification, event_period, place_action)
delete from place p using del d where p.place_id=d.place_id;
drop table del, gone, aff, oldmode, newseq, pairs, stale, inserted;
