---
name: rf-clientes
description: RF-08, Gestion de Clientes (ERS 3.2.2). El unico de su categoria, implementado, con tres precisiones terminologicas importantes
tags: [requisitos, rf, clientes]
fuente: "Documentation/IEEE830/IEEE830.tex, apartado 3.2.2"
estado: vigente
updated: 2026-09-05
---

# 3.2.2. Gestión de Clientes (RF-08)

**RF-08 Crear cliente** — Must Have — **Implementado**.

> El sistema debe permitir crear por medio de un formulario a un cliente con Nombre, dirección, sede, NIT, ciudad, Profesional Responsable, correo, teléfono fijo, celular, sedes y áreas, y guardarlo en la base de datos.

Depende de RF-49, RF-50, RF-52 (autenticación, rol, modificación de usuarios).

## Evidencia

`POST /v1/api/clients` persiste en la tabla `cliente` a través del agregado `Client`. Correos y teléfonos son entidades internas del agregado (`POST /v1/api/clients/{id}/emails`, `/phones`). Sedes: `POST /v1/api/clients/{idCliente}/headquarters`. Áreas de servicio: `POST /v1/api/headquarters/{idSede}/service-areas`. Edición: `PATCH /v1/api/clients/{id}`. Baja: `DELETE /v1/api/clients/{id}` — responde 204 y desactiva, no borra.

## Tres precisiones terminológicas que importan para escribir sobre este requisito

1. **Dirección y ciudad no son del cliente, son de la sede.** Viven descompuestas en `sede` (`t_calle`, `t_carrera`, `t_numero`) más la referencia a la ciudad. Del cliente solo se guarda el país.
2. **Teléfono fijo y celular no se distinguen.** `telefono_cliente` es una lista de teléfonos sin tipo — el requisito pide dos campos, el esquema tiene uno.
3. **"Profesional responsable" corresponde a dos figuras distintas del código**, ninguna con ese nombre: el representante legal del cliente (`representante_legal`, relación de muchos a muchos entre `Client` y personas) y el encargado de una sede o de un área (`Manager`/`encargado`, identidad compartida 1:1 con una persona). Ver [[correspondencia-terminologica]] para el detalle completo.

## Otras notas de la evidencia

- La unicidad del NIT la impone la restricción `UQ_cliente_documento` del esquema, **no** una comprobación del servicio: un documento repetido se rechaza con un conflicto genérico de base de datos, no con un error de dominio propio. Es una desviación de la convención del proyecto de comprobar las referencias en el servicio (ver `SecondBrain/wiki/malphasos/deuda-tecnica-y-riesgos.md`).
- El tercer criterio de aceptación ("el cliente no representa un rol de acceso al sistema") se cumple: el cliente no tiene credenciales, solo las personas se dan de alta en Keycloak.

## Diagrama de casos de uso

No hay diagrama propio para esta categoría en `use_cases/` — el listado de diez diagramas de la ERS no incluye uno de "clientes" con ese nombre exacto entre los incluidos en el `.tex`; existe `use_cases/clientes/` compilado pero sin `\includegraphics` en el documento. Ver [[diagramas-de-casos-de-uso]].

## Notas relacionadas

[[correspondencia-terminologica]] · [[glosario-dominio]] · [[estado-de-implementacion]] · [[priorizacion-moscow]]
