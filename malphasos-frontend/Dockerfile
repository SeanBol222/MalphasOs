# ---------------------------------------------------------------------------
# Etapa de construccion
# ---------------------------------------------------------------------------
# Node solo hace falta para compilar: la imagen final sirve ficheros estaticos y no lleva Node.
FROM node:24-alpine AS build

WORKDIR /build

# Primero solo lo que define las dependencias, para que Docker cachee esta capa y no vuelva a
# descargarlas mientras el lockfile no cambie, aunque el codigo si cambie.
COPY package.json package-lock.json ./
RUN npm ci

# El cliente del API generado desde OpenAPI esta versionado en src/app/core/api, de modo que la
# imagen NO necesita ../contracts: se compila con lo que hay en el repositorio del frontend.
COPY . .
RUN npx ng build --configuration production

# ---------------------------------------------------------------------------
# Etapa de ejecucion
# ---------------------------------------------------------------------------
# nginx sin privilegios de root, por la misma razon que el backend corre con un usuario propio: si
# alguien lograra ejecutar codigo dentro del contenedor, no lo haria como root. Esta variante escucha
# en el 8080 porque un usuario sin privilegios no puede abrir puertos por debajo del 1024.
FROM nginxinc/nginx-unprivileged:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist/malphasos-frontend/browser /usr/share/nginx/html

EXPOSE 8080
