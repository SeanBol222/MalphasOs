-- V42: índices de foráneas, dueños obligatorios y tres nombres que engañaban (2026-10-27).
--
-- La primera de las siete migraciones de la refactorización del esquema, decidida por el usuario sobre
-- Documentation/DatabaseDesign/analisis-2026-10-27.md (grupos C3, C4 y C5). No cambia el modelo: ninguna
-- tabla entra ni sale, y ninguna fila cambia de valor.

-- C4. LAS FORÁNEAS SIN ÍNDICE. Aquí nunca se borra una fila, así que el índice de una foránea no está
-- para que borrar el padre sea barato, sino para leer: «los accesorios de este reporte», «los equipos de
-- esta marca». Por eso cuenta como cubierta la que ya encabeza un índice parcial sobre las filas activas,
-- que son las que se leen. Quedaban trece sin ninguno. Tres desaparecen o se sustituyen en las
-- migraciones siguientes —la autorreferencia de intervencion, la compuesta de la orden con su cliente y
-- la de la unidad con su magnitud—, y no se indexa lo que se va a quitar. Las diez que quedan llevan
-- nueve índices: el de dato_verificacion encabeza con la verificación y cubre sus dos foráneas.
CREATE INDEX "IX_accesorio_reporte_reporte" ON accesorio_reporte (k_id_reporte_servicio);
CREATE INDEX "IX_cliente_pais" ON cliente (k_id_pais);
CREATE INDEX "IX_dato_verificacion_punto" ON dato_verificacion (k_id_verificacion, k_id_punto_verificacion);
CREATE INDEX "IX_equipo_marca" ON equipo (k_id_marca);
CREATE INDEX "IX_fabricante_pais" ON fabricante (k_id_pais);
CREATE INDEX "IX_paso_reporte_protocolo" ON paso_reporte (k_id_protocolo);
CREATE INDEX "IX_patron_verificacion_reporte_patron" ON patron_verificacion_reporte (k_id_equipo_patron);
CREATE INDEX "IX_patron_verificacion_reporte_verificacion"
    ON patron_verificacion_reporte (k_id_verificacion);
CREATE INDEX "IX_reporte_servicio_firma_recibe" ON reporte_servicio (k_huella_firma_recibe);

-- C3. EL DUEÑO DE UN CORREO O UN TELÉFONO DE PERSONA ES OBLIGATORIO, como el de los contactos del cliente.
-- Era anulable desde V2, heredado del original, y anotado como deuda desde entonces: un correo sin dueño
-- no lo encuentra nadie. Si quedara alguno, esta migración falla en lugar de inventarle uno: no hay
-- forma de saber de quién era.
ALTER TABLE correo_persona ALTER COLUMN k_identificador SET NOT NULL;
ALTER TABLE telefono_persona ALTER COLUMN k_identificador SET NOT NULL;

-- C5. LOS NOMBRES QUE ENGAÑABAN. En orden_trabajo, k_identificador es el ingeniero y no lo decía: en el
-- resto del esquema ese nombre es la llave de una persona cualquiera. t_finalizado es una fecha con
-- prefijo de texto. Y firma_persona llamaba k_id_persona a lo que el resto del esquema llama
-- k_identificador. Renombrar conserva los datos, las restricciones y los comentarios.
ALTER TABLE orden_trabajo RENAME COLUMN k_identificador TO k_id_ingeniero;
ALTER TABLE orden_trabajo RENAME CONSTRAINT "FK_orden_trabajo_persona" TO "FK_orden_trabajo_ingeniero";
ALTER TABLE reporte_servicio RENAME COLUMN t_finalizado TO f_finalizado;
ALTER TABLE firma_persona RENAME COLUMN k_id_persona TO k_identificador;
