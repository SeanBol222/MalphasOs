-- V14: una intervencion puede quedar reemplazada por la del reporte que la corrigio.
--
-- EL PROBLEMA. Un reporte cerrado no se edita: para corregirlo se retira y se abre otro sobre la misma
-- orden y el mismo equipo. Y desde V12 la intervencion sobrevive al reporte retirado, porque un
-- mantenimiento hecho no se borra. Las dos decisiones son buenas por separado, y juntas hacian que
-- corregir un reporte dejara DOS lineas en la hoja de vida para un solo mantenimiento: la del reporte
-- equivocado y la del bueno.
--
-- LA DECISION DEL USUARIO, del 2026-10-04: el sustituto reemplaza al anterior. La linea vieja deja de
-- contar en la hoja de vida pero NO se borra: queda el rastro de que hubo una correccion, apuntando a
-- la linea que la sustituyo.
--
-- CUANDO SE REEMPLAZA: al CERRARSE el sustituto, no al abrirlo. Si fuera al abrirlo y el sustituto no
-- llegara a cerrarse, la hoja de vida perderia un mantenimiento que si ocurrio.

ALTER TABLE intervencion
    ADD COLUMN k_id_reemplazada_por uuid NULL;

COMMENT ON COLUMN intervencion.k_id_reemplazada_por IS
    'La intervencion que sustituyo a esta, cuando su reporte se retiro para corregirlo y se cerro otro '
    'sobre la misma orden y el mismo equipo. Nula en una intervencion vigente.';

ALTER TABLE intervencion
    ADD CONSTRAINT "FK_intervencion_reemplazada_por"
        FOREIGN KEY (k_id_reemplazada_por) REFERENCES intervencion (k_id_intervencion);

-- Reemplazada es retirada: una linea con sustituta y todavia activa contaria dos veces el mismo
-- mantenimiento, que es justo el defecto que esto corrige. Con CASE y no con OR, porque un CHECK se
-- satisface con NULL y tres ramas unidas por OR ya dejaron pasar una vez el estado que querian prohibir.
ALTER TABLE intervencion
    ADD CONSTRAINT "CHK_intervencion_reemplazada_inactiva"
        CHECK (CASE WHEN k_id_reemplazada_por IS NULL THEN true ELSE NOT b_estado_activo END);

ALTER TABLE intervencion
    ADD CONSTRAINT "CHK_intervencion_no_se_reemplaza_a_si_misma"
        CHECK (k_id_reemplazada_por IS NULL OR k_id_reemplazada_por <> k_id_intervencion);

-- RECONCILIA LO QUE YA EXISTE. V13 relleno una linea por reporte cerrado, retirados incluidos, de modo
-- que una correccion anterior a esta migracion ya tiene sus dos lineas. Dentro de cada par (orden,
-- equipo) queda vigente la del reporte ACTIVO, y si todos estan retirados la del cierre mas reciente;
-- las demas pasan a reemplazadas por ella. Es la misma regla que aplica el oyente desde ahora, de modo
-- que el resultado no depende de si una correccion ocurrio antes o despues de esta migracion.
WITH grupo AS (
    SELECT i.k_id_intervencion,
           r.k_id_orden_trabajo,
           r.k_id_equipo_cliente,
           row_number() OVER (
               PARTITION BY r.k_id_orden_trabajo, r.k_id_equipo_cliente
               ORDER BY r.b_estado_activo DESC, r.t_finalizado DESC
               ) AS puesto
    FROM intervencion i
             JOIN reporte_servicio r ON r.k_id_reporte_servicio = i.k_id_reporte_servicio
    WHERE i.k_id_reemplazada_por IS NULL
),
vigente AS (
    SELECT * FROM grupo WHERE puesto = 1
)
UPDATE intervencion i
SET k_id_reemplazada_por = v.k_id_intervencion,
    b_estado_activo      = false
FROM grupo g
         JOIN vigente v
              ON v.k_id_orden_trabajo = g.k_id_orden_trabajo
                  AND v.k_id_equipo_cliente = g.k_id_equipo_cliente
WHERE i.k_id_intervencion = g.k_id_intervencion
  AND g.puesto > 1;
