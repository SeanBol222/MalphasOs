-- V46: sin segundo tipo de persona (2026-10-27).
--
-- Grupo A2 del análisis del esquema (Documentation/DatabaseDesign/analisis-2026-10-27.md), decidido por
-- el usuario. persona tenía tipo y segundo tipo, que es un grupo repetido —primera forma normal—, y el
-- segundo solo podía ser MANAGER: decía «además es encargado». Desde V41 eso ya lo dicen encargado_sede
-- y encargado_area, y las dos fuentes podían contradecirse —en desarrollo, el 2026-10-27, había un
-- representante marcado como encargado sin ninguna sede ni área a cargo—. Queda una: es encargado quien
-- tiene asignaciones.
--
-- QUIÉN PUEDE TENERLAS, decidido por el usuario el mismo día: los encargados y los representantes del
-- cliente. Hasta aquí el servicio aceptaba a cualquier persona, y lo comprueba desde esta versión. El
-- esquema no puede: el tipo está en persona y las asignaciones en otras dos tablas.
--
-- Si alguien de otro tipo ya tuviera asignaciones activas, la migración FALLA diciendo cuántas: retirarlas
-- en silencio dejaría una sede sin quien responda, y aceptarlas dejaría el dato contra la regla nueva.

DO
$$
    DECLARE
        fuera integer;
    BEGIN
        SELECT count(*)
        INTO fuera
        FROM persona p
        WHERE p.t_tipo_persona NOT IN ('MANAGER', 'CEO_CLIENT')
          AND (EXISTS (SELECT 1 FROM encargado_sede s WHERE s.k_identificador = p.k_identificador AND s.b_estado_activo)
            OR EXISTS (SELECT 1 FROM encargado_area a WHERE a.k_identificador = p.k_identificador AND a.b_estado_activo));

        IF fuera > 0 THEN
            RAISE EXCEPTION 'V46: % personas que no son encargados ni representantes tienen sedes o áreas a cargo. Reléveles de ellas, o cámbieles el tipo, antes de actualizar.',
                fuera;
        END IF;
    END
$$;

ALTER TABLE persona DROP CONSTRAINT "CHK_segundo_tipo_persona";
ALTER TABLE persona DROP COLUMN t_segundo_tipo_persona;
