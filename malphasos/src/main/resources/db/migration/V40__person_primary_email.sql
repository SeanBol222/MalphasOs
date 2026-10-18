-- El correo de la cuenta (2026-10-17). Una persona con acceso tiene cuenta en Keycloak, y Keycloak le
-- guarda UN correo: con él se puede entrar —el realm admite el correo como nombre de usuario— y a él
-- iría cualquier aviso. Hasta ahora ninguno de los correos de la ficha estaba marcado, así que editar o
-- retirar un correo en la aplicación no se podía llevar a Keycloak: no había forma de saber cuál era.
--
-- Esta migración solo añade la marca, en falso para todas las filas. Cuál es el de cada cuenta no se
-- puede deducir desde aquí —la tabla no guarda fecha de alta, y la respuesta está en Keycloak—, así que
-- lo rellena al arrancar el conciliador del módulo, que le pregunta a Keycloak por cada cuenta.

ALTER TABLE correo_persona
    ADD COLUMN b_principal boolean NOT NULL DEFAULT false;

-- Un correo retirado no puede ser el de la cuenta: la cuenta seguiría usando una dirección que la
-- ficha dice que ya no vale.
ALTER TABLE correo_persona
    ADD CONSTRAINT "CHK_correo_principal_activo" CHECK (NOT b_principal OR b_estado_activo);

-- Uno solo por persona. Va como restricción de exclusión y no como índice único parcial, que es la
-- forma habitual en este esquema, por una razón concreta: cambiar de principal son dos UPDATE en el
-- mismo flush —el viejo a falso, el nuevo a verdadero— y Hibernate no garantiza su orden. Un índice
-- parcial no se puede diferir; una restricción de exclusión sí, y admite el mismo WHERE. Así la regla se
-- comprueba al confirmar la transacción, cuando las dos filas ya están en su sitio.
ALTER TABLE correo_persona
    ADD CONSTRAINT "EX_correo_principal_por_persona"
        EXCLUDE USING btree (k_identificador WITH =) WHERE (b_principal)
        DEFERRABLE INITIALLY DEFERRED;
