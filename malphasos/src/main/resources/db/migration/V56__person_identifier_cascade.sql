-- Dar acceso a una ficha sin cuenta (2026-11-02). El identificador de una persona ES el de su usuario
-- en Keycloak, y la Admin API no deja elegirlo al crear el usuario: lo genera ella. Una ficha que nació
-- sin cuenta —los encargados hasta V53— solo puede tenerla cambiando su identificador por el que
-- Keycloak le dé, en una sola sentencia que arrastre a todo lo que la nombra.
--
-- Por eso las ocho foráneas hacia persona pasan a ON UPDATE CASCADE. No cambia nada más: el borrado
-- sigue sin acción, porque aquí nada se borra. La importación parcial del realm sí respeta el
-- identificador, pero exige que el backend administre el realm entero, y eso es mucho más poder del
-- que esta operación necesita.

ALTER TABLE correo_persona
    DROP CONSTRAINT "FK_correo_persona",
    ADD CONSTRAINT "FK_correo_persona"
        FOREIGN KEY (k_identificador) REFERENCES persona (k_identificador) ON UPDATE CASCADE;

ALTER TABLE telefono_persona
    DROP CONSTRAINT "FK_telefono_persona",
    ADD CONSTRAINT "FK_telefono_persona"
        FOREIGN KEY (k_identificador) REFERENCES persona (k_identificador) ON UPDATE CASCADE;

ALTER TABLE supervisor_cliente
    DROP CONSTRAINT "FK_supervisor_cliente_persona",
    ADD CONSTRAINT "FK_supervisor_cliente_persona"
        FOREIGN KEY (k_identificador) REFERENCES persona (k_identificador) ON UPDATE CASCADE;

ALTER TABLE orden_trabajo
    DROP CONSTRAINT "FK_orden_trabajo_ingeniero",
    ADD CONSTRAINT "FK_orden_trabajo_ingeniero"
        FOREIGN KEY (k_id_ingeniero) REFERENCES persona (k_identificador) ON UPDATE CASCADE;

ALTER TABLE reporte_servicio
    DROP CONSTRAINT "FK_reporte_servicio_recibe",
    ADD CONSTRAINT "FK_reporte_servicio_recibe"
        FOREIGN KEY (k_id_recibe) REFERENCES persona (k_identificador) ON UPDATE CASCADE;

ALTER TABLE firma_persona
    DROP CONSTRAINT "FK_firma_persona_persona",
    ADD CONSTRAINT "FK_firma_persona_persona"
        FOREIGN KEY (k_identificador) REFERENCES persona (k_identificador) ON UPDATE CASCADE;

ALTER TABLE aprobacion_orden
    DROP CONSTRAINT "FK_aprobacion_orden_supervisor",
    ADD CONSTRAINT "FK_aprobacion_orden_supervisor"
        FOREIGN KEY (k_id_supervisor) REFERENCES persona (k_identificador) ON UPDATE CASCADE,
    DROP CONSTRAINT "FK_aprobacion_orden_operador",
    ADD CONSTRAINT "FK_aprobacion_orden_operador"
        FOREIGN KEY (k_id_operador) REFERENCES persona (k_identificador) ON UPDATE CASCADE;
