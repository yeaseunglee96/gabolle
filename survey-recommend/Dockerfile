# 부산 추천 장소 설문 — 앱 컨테이너 (S15P21E201-754)
# index.html · server.mjs · package.json(+ pg) 에 사진·글꼴 폴더(assets · fonts)가
# 더해졌다 — server.mjs 가 이제 그 둘을 허용 목록으로 직접 내주기 때문이다.
# 빌드 단계는 여전히 없다.
FROM node:22-alpine

WORKDIR /app

# 먼저 목록만 복사한다 — 코드만 고쳤을 때 npm 을 다시 안 돌게 하려고.
COPY package.json ./
RUN npm install --omit=dev --no-audit --no-fund

COPY server.mjs index.html ./
COPY assets ./assets
COPY fonts ./fonts

# 🔴 root 로 안 돌린다.
USER node

ENV PORT=3100 HOST=0.0.0.0
EXPOSE 3100

CMD ["node", "server.mjs"]
