# 부산 추천 장소 설문 — 앱 컨테이너 (S15P21E201-754)
# index.html · server.mjs · package.json(+ pg) 에 사진·글꼴 폴더(assets · fonts)가
# 더해졌다 — server.mjs 가 이제 그 둘을 허용 목록으로 직접 내주기 때문이다.
# 빌드 단계는 여전히 없다.
#
# 🔴 **server.mjs 가 시작할 때 읽는 파일은 전부 아래 COPY 에 있어야 한다.**
#    빠지면 컨테이너가 뜨자마자 죽고, 앞의 nginx 는 502 만 보여 준다. 화면에는
#    "왜" 가 안 나오고 `docker logs` 를 봐야만 보인다.
#    2026-09-10 배포가 정확히 이것으로 죽었다 — `places.json`(가게 이름 자동완성
#    색인)이 COPY 에 없었다. 코드는 멀쩡했고 빠진 것은 이 한 줄이었다.
#    🔴 server.mjs 에 파일을 하나 더 읽는 줄을 넣으면 **여기도 같이 고친다.**
#    지금 읽는 것: index.html · places.json · assets/* · fonts/*
#    (assets · fonts 의 정확한 목록은 server.mjs 의 STATIC_FILES 에 있다.)
FROM node:22-alpine

WORKDIR /app

# 먼저 목록만 복사한다 — 코드만 고쳤을 때 npm 을 다시 안 돌게 하려고.
COPY package.json ./
RUN npm install --omit=dev --no-audit --no-fund

# 🔴 places.json 을 빠뜨리면 앱이 시작하자마자 죽는다 — 가게 이름 색인(53,716곳)을
#    server.mjs 가 뜨는 자리에서 읽는다. 없으면 ENOENT 로 죽고 컨테이너가 재시작을
#    반복하며 502 가 된다. 2026-09-10 배포에서 실제로 그렇게 죽었다 (S15P21E201-754).
#    **읽는 파일이 늘면 이 줄도 같이 늘어야 한다.**
#
# 🔴 2026-09-12 (S15P21E201-884) 정정 — design.mjs 를 뺐다. 위 주석이 *"design.mjs
#    는 짝 비교 문항 정의다. 없으면 ENOENT 로 죽는다"* 라고 적고 있었는데 **사실이
#    아니었다.** server.mjs 는 그 파일을 열지 않는다 — 문항은 index.html 안의
#    <script id="design"> 블록에 들어 있고 거기서 읽는다. 생성기는 개발할 때만
#    돌리는 도구라 이미지에 있을 이유가 없다.
COPY server.mjs index.html places.json ./
COPY assets ./assets
COPY fonts ./fonts

# 🔴 root 로 안 돌린다.
USER node

ENV PORT=3100 HOST=0.0.0.0
EXPOSE 3100

CMD ["node", "server.mjs"]
