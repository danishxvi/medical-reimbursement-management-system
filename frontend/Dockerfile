# ---------------------------------------------------------------------
# Build stage: install exact dependency versions and build static files.
# ---------------------------------------------------------------------
FROM node:24-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# ---------------------------------------------------------------------
# Runtime stage: unprivileged nginx serving the app and proxying /api.
# ---------------------------------------------------------------------
FROM nginxinc/nginx-unprivileged:1.29-alpine
# Rendered to /etc/nginx/conf.d/default.conf at start up (envsubst on defined variables only)
ENV MRMS_ESP_ORIGIN=""
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 8080
