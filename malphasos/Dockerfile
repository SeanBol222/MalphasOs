# ---------------------------------------------------------------------------
# Etapa de construccion
# ---------------------------------------------------------------------------
# Se usa la imagen con JDK completo porque aqui si hace falta compilar. El tamano
# de esta etapa no importa: se descarta y no llega a la imagen final.
FROM eclipse-temurin:21-jdk AS build

WORKDIR /build

# Primero solo los archivos que definen las dependencias. Docker cachea esta capa
# y no vuelve a descargar el arbol completo de Maven mientras el pom no cambie,
# aunque el codigo fuente si cambie.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline

# Ahora si el codigo, que es lo que cambia en cada build
COPY src src
RUN ./mvnw -B -q clean package -DskipTests

# ---------------------------------------------------------------------------
# Etapa de ejecucion
# ---------------------------------------------------------------------------
# JRE en lugar de JDK y sobre Alpine: la imagen final pesa una fraccion de lo que
# pesaria arrastrando el JDK, y expone menos superficie de ataque.
FROM eclipse-temurin:21-jre-alpine

# La aplicacion no necesita privilegios de root. Si alguien lograra ejecutar codigo
# dentro del contenedor, lo haria como un usuario sin permisos.
RUN addgroup -S malphasos && adduser -S -G malphasos malphasos

WORKDIR /app
COPY --from=build --chown=malphasos:malphasos /build/target/*.jar app.jar
USER malphasos

EXPOSE 8081

# ENTRYPOINT y no CMD: el contenedor es la aplicacion, no un shell donde
# ejecutarla. Asi los argumentos extra de docker run se pasan a la aplicacion.
ENTRYPOINT ["java", "-jar", "app.jar"]
