# LOCAL ROUTE 배포·운영

## 로컬 검증

1. `npm ci`
2. `cd server && npx prisma migrate deploy && npm run seed && npm run seed:translations && npm run seed:commerce && npm run seed:v2 && npm run seed:discover-tags`
3. 루트에서 `npm run dev:server`, 다른 터미널에서 `npm run dev:web`
4. 서버가 실행된 상태에서 `npm run load:test --workspace server`

## 컨테이너

`docker compose up --build` 후 웹은 `http://localhost:8080`, API는 `http://localhost:4000`에서 확인한다. 운영 전 `BOOKING_WEBHOOK_SECRET`, `ADMIN_TOKEN`, `CORS_ORIGINS`, 외부 API 키를 비밀 저장소로 주입하고 기본값을 사용하지 않는다. 스토리 이미지는 MVP Data URL 저장을 오브젝트 스토리지·리사이즈·CDN으로 교체하고, 기상청 단기예보 배치가 1시간 캐시를 채우도록 구성한다.

Kafka/Flink 데모는 기본 서비스와 분리되어 있다. `docker compose -f docker-compose.yml -f docker-compose.streaming.yml up --build`로 기동하며, API는 Kafka 장애 중에도 DB Outbox에 이벤트를 보존한다. `events:dispatch`가 멱등 producer로 재전송하고 `/api/analytics/kpis`는 현재 저장된 이벤트의 핵심 지표를 제공한다.

## 정기 작업

- 매일 `npm run privacy:cleanup --workspace server`: 위치 격자화 확인, 만료 세션 토큰 폐기, 토픽별 보존기간 적용
- 배포 전 `npm audit --audit-level=high`, 서버 테스트, 서버·웹 빌드
- 배포 후 `/health`, 이벤트 멱등성, 예약 웹훅 서명, 광고 화이트리스트/자연추천 분리, 공유본 개인정보 제외, 협업 403/409, 스토리 EXIF 제거·신고 큐 테스트
