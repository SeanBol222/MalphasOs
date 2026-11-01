-- El supervisor del cliente (2026-11-01): la única persona del cliente con cuenta. Ve lo de su cliente y
-- aprueba sus órdenes, en todas sus sedes.
--
-- Sustituye a dos papeles que el usuario no reconoce en su negocio:
--   * el representante legal, que solo daba cuenta al cliente y acotaba lo que veía —no salía en ningún
--     documento ni firmaba nada—. Su tabla se renombra: las filas no se copian a ninguna parte;
--   * los encargados de sede y de área (V41), que se borran sin pasar a ningún sitio, decidido así.
--
-- Y un solo tipo de persona para la gente del cliente: SUPERVISOR, en lugar de CEO_CLIENT y MANAGER.

ALTER TABLE representante_legal RENAME TO supervisor_cliente;
ALTER TABLE supervisor_cliente RENAME CONSTRAINT "PK_representante_legal" TO "PK_supervisor_cliente";
ALTER TABLE supervisor_cliente
    RENAME CONSTRAINT "FK_representante_legal_persona" TO "FK_supervisor_cliente_persona";
ALTER TABLE supervisor_cliente
    RENAME CONSTRAINT "FK_representante_legal_cliente" TO "FK_supervisor_cliente_cliente";
ALTER INDEX "IX_representante_legal_cliente" RENAME TO "IX_supervisor_cliente_cliente";

-- El cargo depende de la persona y del cliente a la vez: alguien puede supervisar a dos clientes con
-- cargos distintos. Por eso va aquí y no en persona.
ALTER TABLE supervisor_cliente
    ADD COLUMN n_cargo varchar(80) NULL,
    ADD CONSTRAINT "CHK_supervisor_cliente_cargo" CHECK (n_cargo IS NULL OR btrim(n_cargo) <> '');

COMMENT ON TABLE supervisor_cliente IS
    'Una persona del cliente con cuenta: ve lo de ese cliente y aprueba sus órdenes, en todas sus sedes. '
    'Una persona puede supervisar a varios clientes.';
COMMENT ON COLUMN supervisor_cliente.n_cargo IS
    'Cómo se le nombra en los documentos: «Ingeniero biomédico», «Médico propietario». Nulo: sin cargo.';

ALTER TABLE persona DROP CONSTRAINT "CHK_tipo_persona";
UPDATE persona SET t_tipo_persona = 'SUPERVISOR' WHERE t_tipo_persona IN ('MANAGER', 'CEO_CLIENT');
ALTER TABLE persona ADD CONSTRAINT "CHK_tipo_persona"
    CHECK (t_tipo_persona IN ('ENGINEER', 'SUPERVISOR', 'ADMIN', 'SUPER_ADMIN'));

DROP TABLE encargado_area;
DROP TABLE encargado_sede;
