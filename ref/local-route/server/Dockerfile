FROM node:24-bookworm-slim AS build
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends python3 python3-pip && rm -rf /var/lib/apt/lists/*
COPY package*.json ./
COPY server/package.json server/package.json
COPY web/package.json web/package.json
RUN npm ci
COPY server server
RUN pip3 install --break-system-packages -r server/requirements-optimizer.txt && npm run prisma:generate --workspace server && npm run build --workspace server
ENV NODE_ENV=production PORT=4000 PYTHON_EXECUTABLE=python3
EXPOSE 4000
CMD ["sh", "-c", "cd server && npx prisma migrate deploy && node dist/index.js"]
