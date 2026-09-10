# 부산 추천 장소 설문 — 앱 컨테이너 (S15P21E201-754)
# index.html · server.mjs · package.json(+ pg) 에 사진·글꼴 폴더(assets · fonts)가
# 더해졌다 — server.mjs 가 이제 그 둘을 허용 목록으로 직접 내주기 때문이다.
# 빌드 단계는 여전히 없다.
FROM node:22-alpine

WORKDIR /app

# 먼저 목록만 복사한다 — 코드만 고쳤을 때 npm 을 다시 안 돌게 하려고.
COPY package.json ./
RUN npm install --omit=dev --no-audit --no-fund

# 🔴 places.json 과 design.mjs 를 빠뜨리면 앱이 시작하자마자 죽는다.
#    server.mjs 가 뜨는 자리에서 둘 다 읽는다 — places.json 은 가게 이름 색인(53,716곳),
#    design.mjs 는 짝 비교 문항 정의다. 없으면 ENOENT 로 죽고 컨테이너가 재시작을
#    반복하며 502 가 된다. 2026-09-10 배포에서 실제로 그렇게 죽었다 (S15P21E201-754).
#    화면 파일만 바뀌는 게 아니라 **읽는 파일이 늘면 이 줄도 같이 늘어야 한다.**
COPY server.mjs index.html places.json design.mjs ./
COPY assets ./assets
COPY fonts ./fonts

# 🔴 root 로 안 돌린다.
USER node

ENV PORT=3100 HOST=0.0.0.0
EXPOSE 3100

CMD ["node", "server.mjs"]
