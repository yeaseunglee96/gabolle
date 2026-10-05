# Expo(React Native) 앱의 웹 export를 정적 파일로 빌드해 Nginx로 서빙한다.
# 실제 iOS/Android 배포는 EAS Build(Expo 클라우드)를 쓴다 — 이 Dockerfile은
# 팀원들이 물리 디바이스 없이 브라우저로 미리보기 위한 것이다 (S15P21E201-583).
FROM node:20-alpine AS build

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

# EXPO_PUBLIC_ 로 시작하는 값은 Expo 가 export 하는 순간 번들 안에 문자열로 박아 넣는다.
# 그래서 컨테이너를 띄울 때 docker run -e 로 주는 것은 아무 효과가 없고, 반드시 이
# build 단계의 환경에 들어있어야 한다.
#
# 이 ARG(**docker build 에 --build-arg 로 넘기는 값**) 들이 없던 동안 배포된 웹은 API
# 주소가 코드 기본값인 http://localhost:8080 으로, 소셜 client ID 세 개가 undefined 로
# 굳어 있었다. 그 상태에서 소셜 버튼을 누르면 "GOOGLE 로그인 설정이 필요해요" 가 뜨고
# 서버를 부르지도 못한다 (S15P21E201-609).
#
# 여기 들어가는 client ID 는 브라우저에 그대로 드러나는 공개 식별자다. 토큰 교환에 쓰는
# client secret 은 백엔드에만 있고 이 경로로 나가지 않는다.
#
# npm ci 뒤에 두는 이유는 값이 바뀌어도 의존성 설치 레이어를 다시 받지 않게 하려는 것이다.
ARG EXPO_PUBLIC_API_BASE_URL
ARG EXPO_PUBLIC_OAUTH_CALLBACK_BASE_URL
ARG EXPO_PUBLIC_GOOGLE_CLIENT_ID
ARG EXPO_PUBLIC_NAVER_CLIENT_ID
ARG EXPO_PUBLIC_KAKAO_CLIENT_ID
# 애플 로그인용 Service ID. 값이 없으면 버튼이 "APPLE 로그인 설정이 필요해요" 로 떨어진다
# — 이 값은 빌드하는 순간 번들에 박히므로 컨테이너에 넣어 주는 것으로는 안 된다.
ARG EXPO_PUBLIC_APPLE_CLIENT_ID
# 카카오 지도용 JavaScript 키. 지금은 Jenkins 에 해당 Credential 이 없어서
# frontend/Jenkinsfile 이 넘기지 않는다. 배선만 미리 만들어 둔다.
ARG EXPO_PUBLIC_KAKAO_MAP_JS_KEY
ARG EXPO_PUBLIC_BUILD_SHA
ARG EXPO_PUBLIC_BUILD_REF
ARG EXPO_PUBLIC_SHOW_BUILD_INFO=0

ENV EXPO_PUBLIC_API_BASE_URL=$EXPO_PUBLIC_API_BASE_URL \
    EXPO_PUBLIC_OAUTH_CALLBACK_BASE_URL=$EXPO_PUBLIC_OAUTH_CALLBACK_BASE_URL \
    EXPO_PUBLIC_GOOGLE_CLIENT_ID=$EXPO_PUBLIC_GOOGLE_CLIENT_ID \
    EXPO_PUBLIC_NAVER_CLIENT_ID=$EXPO_PUBLIC_NAVER_CLIENT_ID \
    EXPO_PUBLIC_KAKAO_CLIENT_ID=$EXPO_PUBLIC_KAKAO_CLIENT_ID \
    EXPO_PUBLIC_APPLE_CLIENT_ID=$EXPO_PUBLIC_APPLE_CLIENT_ID \
    EXPO_PUBLIC_KAKAO_MAP_JS_KEY=$EXPO_PUBLIC_KAKAO_MAP_JS_KEY \
    EXPO_PUBLIC_BUILD_SHA=$EXPO_PUBLIC_BUILD_SHA \
    EXPO_PUBLIC_BUILD_REF=$EXPO_PUBLIC_BUILD_REF \
    EXPO_PUBLIC_SHOW_BUILD_INFO=$EXPO_PUBLIC_SHOW_BUILD_INFO

COPY . .
# 🔴 위 ARG 들이 비어 있으면 여기서 **소리 내어 알린다.** 막지는 않는다 (종료 코드 0).
#    2026-09-07 배포본은 지도 키가 빈 채로 구워졌고, 그 값이 빌드 때 정해지는 값이라
#    번들러가 지도 코드를 죽은 코드로 보고 통째로 지웠다 — 그런데 빌드 로그에는
#    아무 흔적도 안 남았다 (S15P21E201-163).
RUN node tools/check-public-env.mjs
RUN npx expo export --platform web && node tools/set-html-lang.cjs dist/index.html

FROM nginx:alpine

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
