FROM node:24-bookworm-slim AS build
WORKDIR /app
COPY package*.json ./
COPY server/package.json server/package.json
COPY web/package.json web/package.json
RUN npm ci
COPY web web
RUN npm run build --workspace web
FROM nginx:1.28-alpine
COPY web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/web/dist /usr/share/nginx/html
EXPOSE 80
