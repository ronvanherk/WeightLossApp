FROM node:22-alpine AS build
RUN apk add --no-cache openssl && corepack enable
WORKDIR /app
COPY package.json pnpm-lock.yaml .npmrc ./
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm build

FROM node:22-alpine
RUN apk add --no-cache openssl && npm install -g prisma@6.19.3
WORKDIR /app
ENV NODE_ENV=production PORT=3002 HOSTNAME=0.0.0.0
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
COPY --from=build /app/prisma ./prisma
EXPOSE 3002
# This app owns its database, so it applies its own migrations on start.
CMD ["sh", "-c", "prisma migrate deploy && node server.js"]
