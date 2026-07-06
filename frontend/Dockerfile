# Stage 1 — build Vite
FROM node:20-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --prefer-offline

COPY . .

# VITE_API_URL='' significa mesma origem: o Nginx faz o proxy das chamadas à API.
# VITE_NOTIFICATIONS_URL='' idem para o serviço de notificações.
ARG VITE_API_URL=
ARG VITE_NOTIFICATIONS_URL=
ENV VITE_API_URL=$VITE_API_URL
ENV VITE_NOTIFICATIONS_URL=$VITE_NOTIFICATIONS_URL

RUN npm run build

# Stage 2 — Nginx (serve estático + reverse proxy para os backends)
FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
