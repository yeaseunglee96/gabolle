# Expo(React Native) 앱의 웹 export를 정적 파일로 빌드해 Nginx로 서빙한다.
# 실제 iOS/Android 배포는 EAS Build(Expo 클라우드)를 쓴다 — 이 Dockerfile은
# 팀원들이 물리 디바이스 없이 브라우저로 미리보기 위한 것이다 (S15P21E201-583).
FROM node:20-alpine AS build

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npx expo export --platform web

FROM nginx:alpine

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
