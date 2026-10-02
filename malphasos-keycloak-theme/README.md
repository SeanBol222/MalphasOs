# Tema de Keycloak de MalphasOS

Lo que se ve de Keycloak con la marca del producto: la **pantalla de inicio de sesión** y la **consola
de administración**. Se construye con [Keycloakify](https://keycloakify.dev) y produce un **JAR** que
Keycloak carga como tema.

## Por qué existe un proyecto aparte, y por qué es React

**Keycloakify no se puede instalar dentro de un proyecto Angular existente** —lo dice su
documentación—: el tema es un proyecto independiente o un subproyecto de un monorepo. Así que iba a
vivir fuera de `malphasos-frontend/` de todas formas.

Y dentro es **React** porque la consola de administración solo se puede tematizar con React:
*«only React supports custom Admin UIs»*. Tematizarla es justamente lo que se pidió — que quien entra a
Keycloak no vea el logo de Keycloak—, de modo que la alternativa en Angular habría cubierto el login y
dejado fuera la mitad del encargo. El detalle de la decisión, con la corrección de un argumento que se
dio por bueno antes de comprobarlo, está en `SecondBrain/wiki/malphasos/decisiones-tecnicas-malphasos.md`.

**Esto no mete React en la aplicación.** Este directorio no comparte nada con el Angular de
`malphasos-frontend/`: ni dependencias, ni build, ni pruebas. Lo único que sale de aquí es un JAR.

## Cómo se trabaja

```bash
npm install
npm run storybook            # las pantallas en aislamiento, sin Keycloak
npx keycloakify start-keycloak   # un Keycloak real con el tema puesto
npm run build-keycloak-theme     # el JAR, en dist_keycloak/
```

**Keycloak 26 usa `keycloak-theme-for-kc-all-other-versions.jar`** —el otro, `-22-to-25`, es para
versiones intermedias—. El `docker-compose.yaml` de la raíz monta ese archivo en
`/opt/keycloak/providers/`.

## El tema se aplica por realm, y eso incluye el realm `master`

El `adminTheme` hay que ponerlo en **los dos** realms, y el segundo no está en ningún archivo:

- **`malphasos-realm`** lo lleva el JSON versionado de `docker/keycloak/import/`.
- **`master`** lo crea Keycloak solo y no se importa de ninguna parte. **Es la consola por la que entra
  quien desarrolla**, así que sin este paso se inicia sesión y se sigue viendo el logo de Keycloak:

  ```bash
  docker exec malphasos-keycloak /opt/keycloak/bin/kcadm.sh update realms/master -s adminTheme=malphasos
  ```

Y el JSON versionado tampoco se aplica solo: `--import-realm` usa **`IGNORE_EXISTING`**, de modo que si
el realm ya está en la base, el archivo **se salta en silencio**. Las dos recetas están en el
`docker-compose.yaml`, junto al volumen de importación.

## Lo que hay que saber antes de tocarlo

- **El nombre del tema es `malphasos`** y lo fija `vite.config.ts`. El realm versionado lo nombra en
  `loginTheme` y `adminTheme`: cambiarlo aquí sin cambiarlo allí deja a Keycloak con su tema por
  defecto **sin decir nada**.
- **La autoridad del sistema visual es el manual de marca**, no este proyecto: radio cero, escala de 8,
  una sola familia tipográfica (Archivo) y el acento `#EC3013` **solo para lo que no es texto** — para
  texto y rellenos, `#AE1800`, que es el que alcanza AA. Está medido, no estimado.
- **El tema de la consola de cuenta del usuario no está implementado** (`accountThemeImplementation:
  "none"`). Añadirlo es `npx keycloakify initialize-account-theme`, y la documentación avisa de que la
  interfaz de partida no es la de Keycloak, así que exige ajustes.
- El proyecto salió del starter oficial `keycloakify/keycloakify-starter` (MIT). Se le quitaron su
  `.git`, sus flujos de GitHub Actions y su licencia; lo que queda se publica con la del proyecto.
