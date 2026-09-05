---
name: rf-usuarios-seguridad
description: RF-49 a RF-53, Usuarios y seguridad (ERS 3.2.9). Los cinco implementados, con criterios de aceptacion incumplidos que hay que citar junto con la marca
tags: [requisitos, rf, usuarios, seguridad, keycloak]
fuente: "Documentation/IEEE830/IEEE830.tex, apartado 3.2.9"
estado: vigente
updated: 2026-09-05
---

# 3.2.9. Usuarios y seguridad (RF-49 a RF-53)

Los cinco requisitos de esta categoría están `[IMPLEMENTADO]` — es el único bloque de la ERS donde el 100 % del dominio tiene código detrás — y, a la vez, **ninguno de los siete objetivos de la constitución lo menciona** (ver [[defectos-conocidos-de-la-ers]]). Todos Must Have.

## Reparto de responsabilidades: MalphasOS no comprueba credenciales

Punto de partida para leer las cinco fichas: MalphasOS es un *resource server* de OAuth2 que valida JWT emitidos por Keycloak. La pantalla de usuario/contraseña, el mensaje de error genérico y la política de contraseñas viven en Keycloak, no en el backend del proyecto.

## RF-49 Login de usuario

**Implementado**, con una anomalía de la propia especificación: **su apartado de dependencias declara únicamente "RF-49"** — depende de sí mismo. Ver [[defectos-conocidos-de-la-ers]].

Evidencia: `SecurityConfig` configura el *resource server* OAuth2; hay pruebas de integración que fijan 401 sin token y 403 con un token sin el permiso exigido. No existe todavía una aplicación cliente desde la que iniciar sesión — el repositorio contiene el backend, no una interfaz de usuario.

## RF-50 Identificación de rol

**Implementado, con una salvedad que pesa.** `KeycloakRoleConverter` traduce los roles del token en autoridades, descartando a propósito los de otros *clients* del mismo realm (hay una prueba que fija ese descarte). La autorización se declara operación por operación, no solo por ruta.

**El segundo criterio de aceptación —"cada rol accede solo a sus funcionalidades"— no se cumple todavía.** Las 83 operaciones de la API exigen la misma autoridad, `admin.full`, que el realm solo concede al grupo `admins`. Los grupos `engineers` y `clients` reciben cuatro roles de solo lectura (`service-area.read`, `client.read`, `work-order.read`, `equipment.read`) que **ninguna operación REST comprueba todavía**: un ingeniero o un representante de cliente se autentica sin problema pero recibe 403 en toda llamada. El rol de SuperUsuario existe en el realm, pero ningún grupo lo otorga ni ninguna operación lo exige. Ver [[correspondencia-terminologica]] para cómo se llama cada cosa en cada capa.

## RF-51 Creación de usuarios

**Implementado.** Tres altas, una por rol: `POST /v1/api/persons/engineers`, `/admins`, `/ceo-clients`. Cada una crea el usuario en Keycloak con su contraseña, lo asigna al grupo correspondiente y luego guarda la persona en `persona`; si el guardado falla, el usuario de Keycloak se retira para no dejarlo huérfano. Existe además `POST /v1/api/persons`, que registra a alguien sin credenciales — no toda persona necesita acceder al sistema (por ejemplo, un `Manager`/encargado que solo es contacto de una sede).

No hay alta genérica por parámetro de rol, ni alta de SuperUsuario: el enumerado de roles de Keycloak (`RoleType`) tiene tres valores y ninguno lo cubre.

## RF-52 Modificación de usuarios

**Implementado, y se detiene en `persona`.** `PUT /v1/api/persons/{id}` actualiza cédula, nombres, apellidos y tipo, y valida la combinación de tipos. **No se propaga nada a Keycloak**: ni nombre de usuario, ni correo, ni contraseña. La persona y su cuenta pueden divergir; cambiar una contraseña exige entrar a Keycloak directamente.

## RF-53 Eliminación de usuarios

**Implementado, pero no revoca el acceso.** `DELETE /v1/api/persons/{id}` responde 204 y marca la persona inactiva — el requisito habla de "eliminar" y el sistema, coherente con su regla de baja lógica universal, "retira". El tercer criterio de aceptación, "el acceso se revoca inmediatamente", **no se cumple**: la baja no toca la cuenta de Keycloak, que solo se elimina para deshacer un alta fallida a medias. Una persona retirada conserva credenciales válidas y sigue pudiendo autenticarse.

## Tabla resumen

| Código | Requisito | Estado | Criterio incumplido |
|---|---|---|---|
| RF-49 | Login | Implementado | — (pero depende de sí mismo) |
| RF-50 | Identificación de rol | Implementado | "Cada rol accede solo a lo suyo" — no se cumple |
| RF-51 | Creación de usuarios | Implementado | Sin alta de SuperUsuario |
| RF-52 | Modificación de usuarios | Implementado | Cambios no llegan a Keycloak |
| RF-53 | Eliminación de usuarios | Implementado | Acceso no se revoca |

## Notas relacionadas

[[correspondencia-terminologica]] ⭐ · [[defectos-conocidos-de-la-ers]] · [[estado-de-implementacion]] · [[requisitos-no-funcionales]] (RNF-23, JWT)
