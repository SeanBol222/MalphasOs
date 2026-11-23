# Desplegar MalphasOS

Cómo poner MalphasOS en un servidor, crear el primer SuperUsuario, respaldar, restaurar y actualizar.
Escrita el 2026-10-17 con la versión 1.0.0 y **probada siguiéndola al pie de la letra** en una máquina
con el dominio `localhost`. **Desde la 1.1.0 cubre también la red local sin dominio**, que es como se
desplegó la 1.0.0 por primera vez: ver «En la red local, sin dominio».

## Qué queda en marcha

Seis servicios de `docker-compose.prod.yaml`, en una red interna. **Solo Caddy publica puertos**, el 80 y
el 443 —u otros, ver la red local—. Todo vive en un solo dominio:

| Ruta | Servicio |
|---|---|
| `https://<dominio>/` | El frontend |
| `https://<dominio>/v1/…` | El API |
| `https://<dominio>/auth/…` | Keycloak: el inicio de sesión y los tokens. **Su consola de administración no se publica** |

Además están **PostgreSQL**, con la base de la aplicación y la de Keycloak, **Gotenberg**, que convierte en PDF los
informes y las hojas de vida, y **el respaldo diario**. Swagger y pgAdmin no existen en producción.

## Lo que necesita el servidor

- **Linux con Docker** 24 o posterior, con el plugin `docker compose`.
- **4 GB de memoria** como mínimo: Keycloak, el backend y el Chromium de Gotenberg son lo que más pesa.
- **Unos 5 GB de disco** para las imágenes, más lo que ocupen los datos y sus respaldos.
- **Un dominio** con un registro DNS que apunte a la IP del servidor, **o una IP fija en la red local**:
  ver «En la red local, sin dominio».
- **Los puertos 80 y 443 abiertos** hacia internet. Caddy los necesita para pedir el certificado a Let's
  Encrypt y renovarlo solo. En la red local pueden ser otros.
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
| `MALPHASOS_DOMAIN` | El dominio, sin `https://` ni barra: `malphasos.ejemplo.com`. En la red local, la IP |
| `POSTGRES_PASSWORD` | La contraseña de la base de datos |
| `KC_ADMIN_USER`, `KC_ADMIN_PASSWORD` | El administrador de Keycloak. Solo se usa desde el servidor |
| `MALPHASOS_BACKEND_ADMIN_SECRET` | El secreto con que el backend da de alta personas en Keycloak |
| `RESPALDO_HORA`, `TZ`, `RESPALDO_DIAS`, `RESPALDO_DIR` | Cuándo, en qué zona horaria, cuántos días y dónde se guardan los respaldos |
| `APP_APPROVAL_SINCE` | Desde qué día una orden ejecutada espera la aprobación del supervisor, `AAAA-MM-DD`. Lo ejecutado antes —el historial que traiga una carga de datos— no la espera. **El día que el servidor se pone en marcha**, posterior a la última orden de la carga. Vacía, todo el historial saldría «por aprobar» |

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

El usuario se escribe **en minúsculas**, sin tildes ni espacios: Keycloak lo guarda así, y el guion lo
pasa a minúsculas y rechaza lo que no pueda pasar. (Hasta la 1.1.0 no lo hacía, y un nombre con
mayúsculas dejaba el usuario creado **sin contraseña y sin rol**.)

El guion imprime **una contraseña temporal**. Al iniciar sesión con ella por primera vez, Keycloak pide
cambiarla. A partir de ahí, **las demás personas se dan de alta desde MalphasOS**, en «Personas»: los
ingenieros y administradores reciben su cuenta de Keycloak en el mismo paso.

**Lo que el SuperUsuario no tiene**: una fila en `persona`. Ve y administra todo, pero **no puede guardar
una firma** —la ventana que abre su nombre, al pie de la barra, se lo explica— y no figura como ingeniero de ninguna orden. Quien vaya a firmar
informes necesita su propia cuenta de ingeniero o de administrador, creada desde «Personas».

## En la red local, sin dominio

Así se desplegó la 1.0.0 por primera vez, el 2026-11-03: un servidor Debian de la oficina, sin dominio, al
que se entra por su IP. **Tres cosas que la prueba con `localhost` no podía ver**, y que esta sección
resuelve: el certificado cuando se entra por IP, los puertos cuando el servidor ya tiene otro proxy, y el
SuperUsuario en minúsculas, que ya resuelve el guion.

**1. Una IP fija.** En el router, reservar una IP para el servidor —por ejemplo `192.168.1.50`—
**antes de instalar**: Keycloak guarda la dirección al importar el realm, y cambiarla después obliga a
empezar de cero.

**2. `.env.prod`.** Además de lo de la tabla de arriba:

```
MALPHASOS_DOMAIN=192.168.1.50
MALPHASOS_CADDYFILE=Caddyfile.red-local
MALPHASOS_IP=192.168.1.50
```

`Caddyfile.red-local` hace que el certificado lo emita **la autoridad propia de Caddy** —Let's Encrypt no
firma una IP privada— y le dice qué certificado presentar cuando el navegador entra por IP y no da ningún
nombre. Sin él, el navegador responde `SSL_ERROR_INTERNAL_ERROR_ALERT` y `curl`, `tlsv1 alert internal
error`. El porqué está en el propio archivo.

**3. Si el 80 y el 443 ya están ocupados** —otro proxy, como Nginx Proxy Manager—, MalphasOS va en otros
puertos, y **el de HTTPS va también en la dirección**:

```
MALPHASOS_DOMAIN=192.168.1.50:8443
MALPHASOS_HTTPS_PORT=8443
MALPHASOS_HTTP_PORT=8080
```

`MALPHASOS_IP` sigue siendo la IP sola, sin el puerto. Con el cortafuegos activo, abrir los dos:
`sudo ufw allow 8443 && sudo ufw allow 8080`.

**4. Levantar** como arriba, y crear el SuperUsuario.

**5. Que cada equipo confíe en la autoridad de Caddy, una vez.** Se saca del servidor:

```sh
docker compose -f docker-compose.prod.yaml --env-file .env.prod \
  cp caddy:/data/caddy/pki/authorities/local/root.crt ./malphasos-ca.crt
```

y se instala en cada equipo que vaya a entrar:

| Sistema | Cómo |
|---|---|
| Windows | Doble clic en el archivo › «Instalar certificado» › «Equipo local» › «Entidades de certificación raíz de confianza». Cerrar el navegador del todo |
| Debian, Ubuntu | `sudo cp malphasos-ca.crt /usr/local/share/ca-certificates/ && sudo update-ca-certificates` |
| Arch | `sudo trust anchor --store malphasos-ca.crt` |
| Firefox, en cualquier sistema | Ajustes › Privacidad y seguridad › Certificados › Ver certificados › Autoridades › Importar, con «Confiar en esta CA para identificar sitios web» |

Se entra en `https://192.168.1.50/`, o `https://192.168.1.50:8443/` con otro puerto. **El certificado de
la autoridad dura años, el del sitio lo renueva Caddy solo**: no hay que volver a instalar nada mientras
no se borre el volumen `caddy-data`.

> **Para entrar desde internet hace falta un dominio**, y con él se vuelve al caso de arriba: Caddy pide
> el certificado a Let's Encrypt y nadie instala nada. **Conviene decidirlo antes de cargar datos**: la
> dirección queda guardada en el realm al importarse, y un respaldo de Keycloak la trae consigo, así que
> cambiarla en una instalación con datos no es solo cambiar `.env.prod`. Esta guía no lo cubre todavía.

## Contraseñas

- **La contraseña que se pone al dar de alta a alguien es temporal**: désela, y al entrar por primera vez
  tendrá que elegir una propia. Quien la puso deja de saberla.
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
- **Por IP, `SSL_ERROR_INTERNAL_ERROR_ALERT`**: falta `MALPHASOS_CADDYFILE=Caddyfile.red-local` o
  `MALPHASOS_IP`. **`SEC_ERROR_UNKNOWN_ISSUER`** es otra cosa: ese equipo no confía todavía en la
  autoridad de Caddy.
- **`address already in use` al levantar Caddy**: otro servicio del servidor tiene el 80 o el 443. Ver
  el punto 3 de la red local.
- **«MalphasOS no pudo arrancar: falta su configuración»**: el frontend no pudo leer `/config.json`,
  que escribe su contenedor al arrancar desde `MALPHASOS_DOMAIN`.
- **El alta de una persona falla con un error del sistema de acceso**: el secreto del backend no coincide
  con el del realm. El realm lo tomó de `MALPHASOS_BACKEND_ADMIN_SECRET` **la primera vez**: si se cambió
  después en `.env.prod`, hay que cambiarlo también en Keycloak.
- **El PDF dice que el servicio que lo convierte no respondió**: Gotenberg está caído o reiniciándose.
