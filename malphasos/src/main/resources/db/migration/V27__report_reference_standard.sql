-- V27: contra qué patrón se tomaron las lecturas de un reporte, y con qué tolerancia (2026-10-14).
--
-- Decidido por el usuario: todo reporte con lecturas dice contra qué patrón se midió, el patrón sale
-- del catálogo de V25 y, si su certificado estaba vencido el día del servicio, el reporte no se cierra.
--
-- El reporte guarda el identificador del patrón Y una copia de lo que el patrón decía ese día: nombre,
-- marca, serie, certificado y vencimiento. Renovar el certificado en el catálogo cambia la fila del
-- patrón, y un reporte ya firmado tiene que seguir imprimiendo el certificado contra el que se midió.
-- Es lo mismo que hace la lectura con su unidad desde V9. La foránea es simple y no compuesta: un
-- patrón no se borra nunca, solo se retira, así que no puede bloquear nada. Ver la nota
-- congelar-una-referencia-historica.
--
-- La copia es de todo o nada: sin patrón no hay ninguna columna, y con patrón están todas salvo la
-- marca, que en el catálogo también es opcional. Con CASE y no con OR, por la trampa de V9: un CHECK se
-- satisface con NULL.

ALTER TABLE reporte_servicio
    ADD COLUMN k_id_equipo_patron   uuid         NULL,
    ADD COLUMN n_patron_nombre      varchar(120) NULL,
    ADD COLUMN n_patron_marca       varchar(80)  NULL,
    ADD COLUMN k_patron_serie       varchar(60)  NULL,
    ADD COLUMN n_patron_certificado varchar(80)  NULL,
    ADD COLUMN f_patron_vencimiento date         NULL,
    ADD CONSTRAINT "FK_reporte_servicio_equipo_patron"
        FOREIGN KEY (k_id_equipo_patron) REFERENCES equipo_patron (k_id_equipo_patron),
    ADD CONSTRAINT "CHK_reporte_servicio_patron_completo"
        CHECK (CASE
                   WHEN k_id_equipo_patron IS NULL
                       THEN n_patron_nombre IS NULL
                       AND n_patron_marca IS NULL
                       AND k_patron_serie IS NULL
                       AND n_patron_certificado IS NULL
                       AND f_patron_vencimiento IS NULL
                   ELSE n_patron_nombre IS NOT NULL
                       AND k_patron_serie IS NOT NULL
                       AND n_patron_certificado IS NOT NULL
                       AND f_patron_vencimiento IS NOT NULL
               END);

COMMENT ON COLUMN reporte_servicio.k_id_equipo_patron IS
    'El patrón contra el que se tomaron las lecturas. Las columnas n_patron_* y f_patron_vencimiento copian lo que decía ese día.';

-- La tolerancia de la verificación, copiada en cada lectura al registrarla, por la misma razón que la
-- unidad: si el tipo de equipo cambia su tolerancia mañana, un reporte cerrado no puede pasar de
-- conforme a no conforme. Nula si la verificación no tenía tolerancia, y en todas las lecturas
-- anteriores a esta migración: no se rellena con la de hoy, porque no es la que valía entonces.

ALTER TABLE dato_verificacion
    ADD COLUMN d_tolerancia numeric(12, 4) NULL,
    ADD CONSTRAINT "CHK_dato_verificacion_tolerancia" CHECK (d_tolerancia IS NULL OR d_tolerancia > 0);

COMMENT ON COLUMN dato_verificacion.d_tolerancia IS
    'La tolerancia de la verificación cuando se tomó la lectura. Conforme si |equipo − patrón| ≤ tolerancia.';
