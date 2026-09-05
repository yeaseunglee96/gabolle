# BIMS 실시간 버스 수집기 — 서버에서 6개 키를 동시에 돌린다 (S15P21E201-632)
#
# 🔴 이미지는 하나이고 컨테이너가 여섯이다. 다른 것은 환경변수 둘(이름·키)뿐이다.
#    사람마다 다른 이미지를 만들면 여섯 벌을 따로 고쳐야 하고, 그 중 하나가 뒤처지는
#    날이 반드시 온다.
FROM node:22-alpine

# 수집기는 바깥 꾸러미를 안 쓴다 (fetch 는 Node 내장). 그래서 npm install 이 없다.
WORKDIR /app

# 🔴 수집기가 실제로 읽는 것만 넣는다. 처리·분석 코드와 온톨로지·경계 데이터는
#    안 넣는다 — 수집만 하는 컨테이너에 그것들을 같이 넣으면, 분석을 고칠 때마다
#    "지금 수집하는 중인" 컨테이너를 다시 만들 이유가 생긴다. 그러면 수집이 끊긴다.
#    실시간 데이터는 끊긴 만큼 영영 없다.
COPY collect/bims-poll.mjs   ./collect/
COPY config/bims-assign.json ./config/
COPY config/routes.json      ./config/

# 데이터는 이미지가 아니라 볼륨에 쌓인다. 컨테이너를 다시 만들어도 남아야 한다.
VOLUME ["/app/data"]

# 🔴 --dry-run 이 아니라 실제 수집이 기본이다. 서버에 올린 컨테이너가 조용히
#    아무것도 안 하는 것이 가장 나쁜 실패다.
CMD ["node", "collect/bims-poll.mjs"]
