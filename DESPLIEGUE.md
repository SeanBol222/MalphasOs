# Desplegar MalphasOS

Cómo poner MalphasOS en un servidor, crear el primer SuperUsuario, respaldar, restaurar y actualizar.
Escrita el 2026-10-17 con la versión 1.0.0 y **probada siguiéndola al pie de la letra** en una máquina
con el dominio `localhost`.

## Qué queda en marcha

Seis servicios de `docker-compose.prod.yaml`, en una red interna. **Solo Caddy publica puertos**, el 80 y
el 443. Todo vive en un solo dominio:

| Ruta | Servicio |
|---|---|
| `https://<dominio>/` | El frontend |
| `https://<dominio>/v1/…` | El API |
| `https://<dominio>/auth/…` | Keycloak: el inicio de sesión y los tokens. **Su consola de administración no se publica** |

Además están **PostgreSQL**, con la base de la aplicación y la de Keycloak, **Gotenberg**, que convierte los
informes en PDF, y **el respaldo diario**. Swagger y pgAdmin no existen en producción.

## Lo que necesita el servidor

- **Linux con Docker** 24 o posterior, con el plugin `docker compose`.
- **4 GB de memoria** como mínimo: Keycloak, el backend y el Chromium de Gotenberg son lo que más pesa.
- **Unos 5 GB de disco** para las imágenes, más lo que ocupen los datos y sus respaldos.
- **Un dominio** con un registro DNS que apunte a la IP del servidor.
- **Los puertos 80 y 443 abiertos** hacia internet. Caddy los necesita para pedir el certificado a Let's
  Encrypt y renovarlo solo.
- `git` y `openssl`.

## Instalar

```sh
git clone <repositorio> malphasos && cd malphasos
cp .env.prod.example .env.prod
```

Llenar `.env.prod`. **Cada contraseña y cada secreto se genera para este servidor**, por ejemplo con
`openssl rand -base64 32`, y no se copia de desarrollo:

| Variable | Qué es |
|---|---|
| `MALPHASOS_DOMAIN` | El dominio, sin `https://` ni barra: `malphasos.ejemplo.com` |
| `POSTGRES_PASSWORD` | La contraseña de la base de datos |
| `KC_ADMIN_USER`, `KC_ADMIN_PASSWORD` | El administrador de Keycloak. Solo se usa desde el servidor |
| `MALPHASOS_BACKEND_ADMIN_SECRET` | El secreto con que el backend da de alta personas en Keycloak |
| `RESPALDO_HORA`, `TZ`, `RESPALDO_DIAS`, `RESPALDO_DIR` | Cuándo, en qué zona horaria, cuántos días y dónde se guardan los respaldos |

Opcional: los datos de la empresa que presta el servicio, para el membrete de los documentos. Si no se
ponen, se llenan después desde la pantalla «Empresa»:

```sh
cp config/empresa.example.yaml config/empresa.yaml   # y editarlo; el logo va en config/
```

Levantar:

```sh
docker compose -f docker-compose.prod.yaml --env-file .env.prod up -d --build
```

La primera vez tarda varios minutos: construye las imágenes, Keycloak importa el realm y Caddy pide el
certificado. Está lista cuando todos los servicios con comprobación de salud dicen `healthy`:

```sh
docker compose -f docker-compose.prod.yaml --env-file .env.prod ps
```

`https://<dominio>/` debe responder con la página de inicio de sesión.

> **El realm se importa una sola vez.** Si ya existe en la base, Keycloak no vuelve a leer el archivo, así
> que cambiar `docker/keycloak/produccion/` no cambia una instalación en marcha. Ese archivo se deriva del
> de desarrollo con `python3 docker/keycloak/derivar-realm-produccion.py`, y no se edita a mano.

## El primer SuperUsuario

El realm de producción **no trae ninguna persona**. El SuperUsuario se crea a mano, por diseño: ningún
grupo concede `super.admin.full`. Como la consola de Keycloak no se publica, se crea desde el servidor:

```sh
./docker/keycloak/crear-superusuario.sh <usuario> <correo> <nombre> <apellido>
```

El guion imprime **una contraseña temporal**. Al iniciar sesión con ella por primera vez, Keycloak pide
cambiarla. A partir de ahí, **las demás personas se dan de alta desde MalphasOS**, en «Personas»: los
ingenieros y administradores reciben su cuenta de Keycloak en el mismo paso.

**Lo que el SuperUsuario no tiene**: una fila en `persona`. Ve y administra todo, pero **no puede guardar
una firma** —la ventana que abre su nombre, al pie de la barra, se lo explica— y no figura como ingeniero de ninguna orden. Quien vaya a firmar
informes necesita su propia cuenta de ingeniero o de administrador, creada desde «Personas».

## Contraseñas

- **Cada quien cambia la suya** desde la ventana que abre su nombre, al pie de la barra lateral:
  «Cambiar contraseña» lleva a la página de Keycloak y vuelve.
- **Si alguien la olvida**, quien puede editar a esa persona en «Personas» le pone una **contraseña
  temporal** en la sección «Acceso» de su ficha y se la da; al entrar con ella, Keycloak obliga a
  elegir una nueva. La política es de **12 caracteres** como mínimo, distinta del usuario y del correo.
- **El SuperUsuario no tiene ficha**, así que si olvida la suya se le pone una temporal desde el
  servidor:

  ```sh
  docker compose -f docker-compose.prod.yaml --env-file .env.prod exec keycloak sh -c \
    '/opt/keycloak/bin/kcadm.sh config credentials --server http://localhost:8080/auth --realm master \
       --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" &&
     /opt/keycloak/bin/kcadm.sh set-password -r malphasos-realm --username <usuario> \
       --new-password <temporal> --temporary'
  ```

- **No hay «¿Olvidó su contraseña?» por correo**: exige un servidor de correo, que esta versión no
  configura.

## Respaldos

El servicio `respaldo` saca **uno al día** de las dos bases, la de la aplicación y la de Keycloak, a la
hora `RESPALDO_HORA` en la zona `TZ`, y borra los de más de `RESPALDO_DIAS` días. Cada respaldo es una
carpeta con su fecha y hora en `RESPALDO_DIR`, `./respaldos` por defecto, con dos archivos de `pg_dump`.
Uno que se cortó a medias queda como `.parcial` y no cuenta.

Para sacar uno en el momento, por ejemplo antes de actualizar:

```sh
docker compose -f docker-compose.prod.yaml --env-file .env.prod exec respaldo respaldo.sh ahora
```

> ⚠️ **Los respaldos quedan en el mismo servidor.** Eso protege de un error —un borrado, una
> actualización que sale mal—, no de perder el disco o el servidor. **Copiarlos fuera de él es
> responsabilidad de quien despliega**, por ejemplo con `rsync` o `rclone` programado.

## Restaurar

```sh
./docker/respaldo/restaurar.sh 2026-10-17_020000
```

Para la aplicación y Keycloak, devuelve **las dos bases** a ese respaldo y los vuelve a arrancar. **Lo
que se hizo después del respaldo se pierde**, incluidas las cuentas creadas en Keycloak: por eso conviene
sacar uno con `respaldo.sh ahora` antes de restaurar.

Probado así el 2026-10-17: se restauró un respaldo con otros datos, las 37 tablas quedaron idénticas a
las del origen y se generó un PDF; se volvió al respaldo anterior y las 37 tablas volvieron a ser las de
ese respaldo, con el SuperUsuario entrando como antes.

## Actualizar

```sh
docker compose -f docker-compose.prod.yaml --env-file .env.prod exec respaldo respaldo.sh ahora
git pull
docker compose -f docker-compose.prod.yaml --env-file .env.prod up -d --build
```

El backend aplica sus migraciones al arrancar. Una migración no se deshace: si la versión nueva sale mal,
se vuelve a la anterior con `git checkout` y se restaura el respaldo de antes de actualizar.

## Si algo no anda

```sh
docker compose -f docker-compose.prod.yaml --env-file .env.prod logs -f <servicio>
```

- **La página no carga, o el certificado no sale**: el DNS no apunta todavía al servidor, o los puertos
  80 y 443 están cerrados. Caddy lo dice en sus registros.
- **«MalphasOS no pudo arrancar: falta su configuración»**: el frontend no pudo leer `/config.json`,
  que escribe su contenedor al arrancar desde `MALPHASOS_DOMAIN`.
- **El alta de una persona falla con un error del sistema de acceso**: el secreto del backend no coincide
  con el del realm. El realm lo tomó de `MALPHASOS_BACKEND_ADMIN_SECRET` **la primera vez**: si se cambió
  después en `.env.prod`, hay que cambiarlo también en Keycloak.
- **El PDF dice que el servicio que lo convierte no respondió**: Gotenberg está caído o reiniciándose.
