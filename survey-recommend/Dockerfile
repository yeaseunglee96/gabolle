# 부산 추천 장소 설문 — 앱 컨테이너 (S15P21E201-754)
# 파일 넷(index.html · server.mjs · package.json + pg)이 전부다. 빌드 단계가 없다.
FROM node:22-alpine

WORKDIR /app

# 먼저 목록만 복사한다 — 코드만 고쳤을 때 npm 을 다시 안 돌게 하려고.
COPY package.json ./
RUN npm install --omit=dev --no-audit --no-fund

COPY server.mjs index.html ./

# 🔴 root 로 안 돌린다.
USER node

ENV PORT=3100 HOST=0.0.0.0
EXPOSE 3100

CMD ["node", "server.mjs"]
