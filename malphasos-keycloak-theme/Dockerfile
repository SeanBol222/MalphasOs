# ---------------------------------------------------------------------------
# Etapa de construccion del tema
# ---------------------------------------------------------------------------
# El JAR se construye AQUI y no en la maquina de quien desarrolla, a proposito.
# Keycloakify necesita Node y Maven para empaquetarlo -Maven porque el JAR se arma
# con el-, y exigir las dos cosas instaladas a mano convierte «levantar el proyecto»
# en una lista de requisitos. Dentro de Docker el JAR sale siempre, y sale igual.
#
# La alternativa era montar un JAR ya construido como volumen. Se descarto por como
# falla: si el archivo no existe, Docker CREA UN DIRECTORIO con ese nombre y Keycloak
# arranca sin tema y sin decir nada. Un fallo silencioso mas, y de los que este
# proyecto persigue.
FROM node:26-alpine AS build

# Maven, que es lo que Keycloakify usa para empaquetar el JAR, y un JDK para correrlo.
RUN apk add --no-cache maven openjdk21-jre-headless

WORKDIR /build

# Primero lo que define las dependencias: mientras no cambie, Docker reutiliza la capa
# y no vuelve a bajarse el arbol de npm aunque el tema si cambie.
COPY package.json package-lock.json ./
RUN npm ci

# Y ahora el tema.
COPY . .
RUN npm run build-keycloak-theme

# ---------------------------------------------------------------------------
# Imagen final: Keycloak con el tema dentro
# ---------------------------------------------------------------------------
# La version va fijada y tiene que coincidir con la del docker-compose: el JAR que se
# copia es el «all-other-versions», que es el que sirve para Keycloak 11-21 y 26+. Para
# Keycloak 22 a 25 haria falta el otro, y copiar el equivocado deja el tema sin cargar.
FROM quay.io/keycloak/keycloak:26.6.1

COPY --from=build /build/dist_keycloak/keycloak-theme-for-kc-all-other-versions.jar \
     /opt/keycloak/providers/malphasos-theme.jar

# Y el tema clasico de la consola de administracion, que no pasa por Keycloakify: son cuatro archivos
# -propiedades, CSS y dos logos- y heredan la plantilla de la consola real. Va al directorio de temas,
# no a providers/: los JAR van a providers, los temas en carpeta van a themes/.
#
# Comparte el nombre «malphasos» con el tema del JAR a proposito, y funciona porque Keycloak resuelve
# por (nombre, tipo): el JAR responde por el login y esta carpeta por la consola. Asi el realm nombra
# un solo tema en loginTheme y en adminTheme.
COPY admin-theme/ /opt/keycloak/themes/
