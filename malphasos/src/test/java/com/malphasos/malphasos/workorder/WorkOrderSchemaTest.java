package com.malphasos.malphasos.workorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.CodigoIsoLibre;
import com.malphasos.malphasos.TestcontainersConfiguration;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

/**
 * Verifica que la migración V6 crea el esquema de órdenes de trabajo y que sus restricciones
 * rechazan los datos inválidos. Esta es la primera de cuatro tandas del módulo: aquí no hay
 * agregados, servicios ni controladores, solo el esquema.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(
        statements = {
            "DELETE FROM orden_trabajo_equipo",
            "DELETE FROM orden_trabajo",
            "DELETE FROM equipo_cliente",
            "DELETE FROM modelo",
            "DELETE FROM equipo",
            "DELETE FROM marca",
            "DELETE FROM punto_verificacion",
            "DELETE FROM verificacion_tipo_equipo",
            "DELETE FROM tipo_equipo",
            "DELETE FROM fabricante",
            "DELETE FROM area_servicio",
            "DELETE FROM sede",
            "DELETE FROM cliente",
            "DELETE FROM ciudad",
            "DELETE FROM pais",
            "DELETE FROM persona"
        },
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class WorkOrderSchemaTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    private String unico() {
        return String.format("%010d", Math.floorMod(System.nanoTime(), 10_000_000_000L));
    }

    // ---- Cliente, sede y area, reutilizando el patron de ClientSchemaTest ----

    private UUID insertClient() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO cliente (k_id_cliente, k_documento, n_tipo_identificacion, n_razon_social)
                VALUES (?, ?, 'NIT_juridico', 'Hospital Central')
                """,
                id, unico());

        return id;
    }

    private UUID insertCity() {
        UUID pais = UUID.randomUUID();
        UUID ciudad = UUID.randomUUID();
        long n = System.nanoTime();
        String iso = CodigoIsoLibre.en(jdbcTemplate);

        jdbcTemplate.update(
                "INSERT INTO pais (k_id_pais, k_codigo_iso, n_nombre_pais) VALUES (?, ?, ?)",
                pais, iso, "Pais " + n);
        jdbcTemplate.update(
                "INSERT INTO ciudad (k_id_ciudad, n_nombre_ciudad, k_id_pais) VALUES (?, ?, ?)",
                ciudad, "Ciudad " + n, pais);

        return ciudad;
    }

    private UUID insertHeadquarter(UUID cliente, UUID ciudad, String nombre) {
        UUID sede = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO sede (k_id_sede, n_nombre_sede, t_calle, t_carrera, t_numero,
                                  k_id_cliente, k_id_ciudad)
                VALUES (?, ?, '10', '20', '30-40', ?, ?)
                """,
                sede, nombre, cliente, ciudad);

        return sede;
    }

    private UUID insertHeadquarter(UUID cliente) {
        return insertHeadquarter(cliente, insertCity(), "Sede " + unico());
    }

    private UUID insertServiceArea(UUID sede, String nombre) {
        UUID area = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO area_servicio (k_id_area_servicio, n_nombre_area, k_id_sede) VALUES (?, ?, ?)",
                area, nombre, sede);

        return area;
    }

    private UUID insertPerson() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO persona (k_identificador, k_cedula, n_primer_nombre, n_primer_apellido,
                                     t_tipo_persona)
                VALUES (?, ?, 'Ada', 'Lovelace', 'ENGINEER')
                """,
                id, unico());

        return id;
    }

    // ---- Catalogo de equipos, reutilizando el patron de EquipmentCatalogSchemaTest ----

    private UUID insertBrand() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO marca (k_id_marca, n_nombre_marca) VALUES (?, ?)", id, "Marca " + unico());

        return id;
    }

    private UUID insertType() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO tipo_equipo (k_id_tipo_equipo, n_nombre_tipo_equipo, t_definicion_tecnica,
                                         t_recomendaciones_cuidado, t_tecnologia_predominante,
                                         m_valor_unitario_mantenimiento)
                VALUES (?, ?, 'Definicion', 'Cuidados', 'Electronica', 150000)
                """,
                id, "Tipo " + unico());

        return id;
    }

    private UUID insertEquipmentAssociation(UUID tipo, UUID marca) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO equipo (k_id_equipo, k_id_tipo_equipo, k_id_marca) VALUES (?, ?, ?)",
                id, tipo, marca);

        return id;
    }

    private UUID insertManufacturer() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO fabricante (k_id_fabricante, n_nombre_fabricante) VALUES (?, ?)",
                id, "Fabricante " + unico());

        return id;
    }

    private UUID insertModel(UUID fabricante, UUID equipo) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO modelo (k_id_modelo, n_nombre_modelo, k_id_fabricante, k_id_equipo)"
                        + " VALUES (?, ?, ?, ?)",
                id, "Modelo " + unico(), fabricante, equipo);

        return id;
    }

    /** Una unidad fisica de equipo, perteneciente a un area de servicio dada. */
    private UUID insertClientEquipment(UUID area) {
        UUID modelo = insertModel(insertManufacturer(), insertEquipmentAssociation(insertType(), insertBrand()));
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO equipo_cliente (k_id_equipo_cliente, k_serie, k_id_modelo, k_id_area_servicio)
                VALUES (?, ?, ?, ?)
                """,
                id, "Serie-" + unico(), modelo, area);

        return id;
    }

    // ---- Orden de trabajo ----

    /** Insercion minima: sin ingeniero, sin estado ni estado_activo explicitos -- prueba los defaults. */
    private UUID insertMinimalWorkOrder(UUID cliente, UUID sede) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO orden_trabajo (k_id_orden_trabajo, k_id_cliente, k_id_sede,
                                           f_fecha_mantenimiento, n_periodicidad, t_tipo_servicio)
                VALUES (?, ?, ?, ?, 'MENSUAL', 'PREVENTIVO')
                """,
                id, cliente, sede, LocalDate.now());

        return id;
    }

    private UUID insertWorkOrder(UUID cliente, UUID sede, String periodicidad, String tipoServicio) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO orden_trabajo (k_id_orden_trabajo, k_id_cliente, k_id_sede,
                                           f_fecha_mantenimiento, n_periodicidad, t_tipo_servicio,
                                           t_estado_ejecucion)
                VALUES (?, ?, ?, ?, ?, ?, 'CREADA')
                """,
                id, cliente, sede, LocalDate.now(), periodicidad, tipoServicio);

        return id;
    }

    private UUID insertWorkOrderWithState(UUID cliente, UUID sede, String estadoEjecucion) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO orden_trabajo (k_id_orden_trabajo, k_id_cliente, k_id_sede,
                                           f_fecha_mantenimiento, n_periodicidad, t_tipo_servicio,
                                           t_estado_ejecucion)
                VALUES (?, ?, ?, ?, 'MENSUAL', 'PREVENTIVO', ?)
                """,
                id, cliente, sede, LocalDate.now(), estadoEjecucion);

        return id;
    }

    private void insertWorkOrderEquipment(UUID orden, UUID equipoCliente, UUID area) {
        jdbcTemplate.update(
                """
                INSERT INTO orden_trabajo_equipo (k_id_orden_trabajo, k_id_equipo_cliente, k_id_area_servicio)
                VALUES (?, ?, ?)
                """,
                orden, equipoCliente, area);
    }

    @Test
    @DisplayName("las dos tablas del modulo existen tras la migracion")
    void migracionCreaLasTablas() {
        Integer tablas = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('orden_trabajo', 'orden_trabajo_equipo')
                """,
                Integer.class);

        assertThat(tablas).isEqualTo(2);
    }

    @Test
    @DisplayName("solo el ingeniero asignado es opcional; el resto de columnas de la orden es obligatorio")
    void columnasObligatoriasDeLaOrden() {
        Integer obligatorias = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM information_schema.columns
                WHERE table_name = 'orden_trabajo'
                  AND column_name IN ('k_id_orden_trabajo', 'k_id_cliente', 'k_id_sede',
                                      'f_fecha_mantenimiento', 'n_periodicidad', 't_tipo_servicio',
                                      't_estado_ejecucion', 'b_estado_activo')
                  AND is_nullable = 'NO'
                """,
                Integer.class);
        assertThat(obligatorias).isEqualTo(8);

        String nulabilidadIngeniero = jdbcTemplate.queryForObject(
                """
                SELECT is_nullable FROM information_schema.columns
                WHERE table_name = 'orden_trabajo' AND column_name = 'k_identificador'
                """,
                String.class);
        assertThat(nulabilidadIngeniero).isEqualTo("YES");
    }

    @Test
    @DisplayName("todas las columnas del puente equipo-orden son obligatorias")
    void columnasObligatoriasDelPuente() {
        Integer obligatorias = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM information_schema.columns
                WHERE table_name = 'orden_trabajo_equipo'
                  AND column_name IN ('k_id_orden_trabajo', 'k_id_equipo_cliente',
                                      'k_id_area_servicio', 'b_estado_activo')
                  AND is_nullable = 'NO'
                """,
                Integer.class);

        assertThat(obligatorias).isEqualTo(4);
    }

    @Test
    @DisplayName("una orden sin ingeniero asignado se crea sin error: asignar es una operacion aparte")
    void ordenSinIngenieroSeCreaSinError() {
        UUID cliente = insertClient();
        UUID sede = insertHeadquarter(cliente);

        UUID orden = insertMinimalWorkOrder(cliente, sede);

        UUID identificador = jdbcTemplate.queryForObject(
                "SELECT k_identificador FROM orden_trabajo WHERE k_id_orden_trabajo = ?",
                UUID.class, orden);
        assertThat(identificador).isNull();
    }

    @Test
    @DisplayName("omitir el estado de ejecucion deja la orden en CREADA")
    void estadoEjecucionPorDefectoEsCreada() {
        UUID cliente = insertClient();
        UUID sede = insertHeadquarter(cliente);

        UUID orden = insertMinimalWorkOrder(cliente, sede);

        String estado = jdbcTemplate.queryForObject(
                "SELECT t_estado_ejecucion FROM orden_trabajo WHERE k_id_orden_trabajo = ?",
                String.class, orden);
        assertThat(estado).isEqualTo("CREADA");
    }

    @Test
    @DisplayName("omitir el estado activo deja la orden activa")
    void estadoActivoPorDefectoEsVerdadero() {
        UUID cliente = insertClient();
        UUID sede = insertHeadquarter(cliente);

        UUID orden = insertMinimalWorkOrder(cliente, sede);

        Boolean activo = jdbcTemplate.queryForObject(
                "SELECT b_estado_activo FROM orden_trabajo WHERE k_id_orden_trabajo = ?",
                Boolean.class, orden);
        assertThat(activo).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"MENSUAL", "TRIMESTRAL", "SEMESTRAL", "ANUAL"})
    @DisplayName("la periodicidad admite los cuatro valores del catalogo")
    void periodicidadDelCatalogo(String periodicidad) {
        UUID cliente = insertClient();
        UUID sede = insertHeadquarter(cliente);

        assertThatCode(() -> insertWorkOrder(cliente, sede, periodicidad, "PREVENTIVO"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("BIANNUAL no es una periodicidad valida: es el error de traduccion del original")
    void biannualNoEsPeriodicidadValida() {
        UUID cliente = insertClient();
        UUID sede = insertHeadquarter(cliente);

        // El original tradujo "semestral" como BIANNUAL, palabra que en ingles significa a la vez
        // "dos veces al ano" y "cada dos anos". Aqui el vocabulario queda en espanol.
        assertThatThrownBy(() -> insertWorkOrder(cliente, sede, "BIANNUAL", "PREVENTIVO"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("la periodicidad en minuscula no pasa el CHECK: distingue mayusculas")
    void periodicidadEnMinusculaFalla() {
        UUID cliente = insertClient();
        UUID sede = insertHeadquarter(cliente);

        assertThatThrownBy(() -> insertWorkOrder(cliente, sede, "mensual", "PREVENTIVO"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("una periodicidad fuera del catalogo, aunque corta, falla por el CHECK")
    void periodicidadFueraDelCatalogo() {
        UUID cliente = insertClient();
        UUID sede = insertHeadquarter(cliente);

        // Corta a proposito: n_periodicidad es varchar(10) y un valor mas largo que el limite
        // fallaria por "value too long", no por el CHECK que aqui se quiere probar.
        assertThatThrownBy(() -> insertWorkOrder(cliente, sede, "SEMANAL", "PREVENTIVO"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PREVENTIVO", "CORRECTIVO", "CALIBRACION"})
    @DisplayName("el tipo de servicio admite los tres valores del catalogo")
    void tipoDeServicioDelCatalogo(String tipoServicio) {
        UUID cliente = insertClient();
        UUID sede = insertHeadquarter(cliente);

        assertThatCode(() -> insertWorkOrder(cliente, sede, "MENSUAL", tipoServicio))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("un tipo de servicio fuera del catalogo falla por el CHECK, no por longitud")
    void tipoDeServicioFueraDelCatalogo() {
        UUID cliente = insertClient();
        UUID sede = insertHeadquarter(cliente);

        // 'MIXTO' es corto a proposito: t_tipo_servicio es varchar(11) y un valor mas largo que
        // el limite fallaria por "value too long" en vez de por el CHECK.
        assertThatThrownBy(() -> insertWorkOrder(cliente, sede, "MENSUAL", "MIXTO"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREADA", "EN_EJECUCION", "EJECUTADA"})
    @DisplayName("el estado de ejecucion admite los tres valores vigentes")
    void estadoDeEjecucionDelCatalogo(String estado) {
        UUID cliente = insertClient();
        UUID sede = insertHeadquarter(cliente);

        assertThatCode(() -> insertWorkOrderWithState(cliente, sede, estado))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREADO", "EJECUCION", "EJECUTADO"})
    @DisplayName("los valores del CHECK original ya no son validos aqui")
    void estadosDelOriginalYaNoSonValidos(String estadoAntiguo) {
        UUID cliente = insertClient();
        UUID sede = insertHeadquarter(cliente);

        assertThatThrownBy(() -> insertWorkOrderWithState(cliente, sede, estadoAntiguo))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("una orden con una sede que existe pero es de otro cliente falla por la FK compuesta")
    void ordenConSedeDeOtroClienteFalla() {
        UUID clienteA = insertClient();
        insertHeadquarter(clienteA);
        UUID clienteB = insertClient();
        UUID sedeDeB = insertHeadquarter(clienteB);

        assertThatThrownBy(() -> insertMinimalWorkOrder(clienteA, sedeDeB))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("una orden con su propia sede y su propio cliente se crea sin error")
    void ordenConSuPropiaSedeSeCreaSinError() {
        UUID cliente = insertClient();
        UUID sede = insertHeadquarter(cliente);

        assertThatCode(() -> insertMinimalWorkOrder(cliente, sede)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("el mismo equipo no se lista dos veces en la misma orden")
    void mismoEquipoNoSeRepiteEnLaMismaOrden() {
        UUID cliente = insertClient();
        UUID sede = insertHeadquarter(cliente);
        UUID area = insertServiceArea(sede, "UCI");
        UUID orden = insertMinimalWorkOrder(cliente, sede);
        UUID equipo = insertClientEquipment(area);
        insertWorkOrderEquipment(orden, equipo, area);

        assertThatThrownBy(() -> insertWorkOrderEquipment(orden, equipo, area))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("el mismo equipo si puede aparecer en dos ordenes distintas")
    void mismoEquipoEnDosOrdenesDistintas() {
        UUID cliente = insertClient();
        UUID sede = insertHeadquarter(cliente);
        UUID area = insertServiceArea(sede, "UCI");
        UUID equipo = insertClientEquipment(area);
        UUID ordenUno = insertMinimalWorkOrder(cliente, sede);
        UUID ordenDos = insertMinimalWorkOrder(cliente, sede);
        insertWorkOrderEquipment(ordenUno, equipo, area);

        assertThatCode(() -> insertWorkOrderEquipment(ordenDos, equipo, area))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("trasladar el equipo a otra area no falla, y el puente conserva el area vieja")
    void trasladarElEquipoNoReescribeElAreaCongelada() {
        UUID cliente = insertClient();
        UUID sede = insertHeadquarter(cliente);
        UUID areaVieja = insertServiceArea(sede, "UCI");
        UUID areaNueva = insertServiceArea(sede, "Urgencias");
        UUID equipo = insertClientEquipment(areaVieja);
        UUID orden = insertMinimalWorkOrder(cliente, sede);
        insertWorkOrderEquipment(orden, equipo, areaVieja);

        // No hay clave foranea compuesta contra equipo_cliente a proposito: si la hubiera, este
        // traslado -- legitimo -- fallaria por culpa de una orden vieja que lo referencia.
        assertThatCode(() -> jdbcTemplate.update(
                        "UPDATE equipo_cliente SET k_id_area_servicio = ? WHERE k_id_equipo_cliente = ?",
                        areaNueva, equipo))
                .doesNotThrowAnyException();

        UUID areaEnElPuente = jdbcTemplate.queryForObject(
                """
                SELECT k_id_area_servicio FROM orden_trabajo_equipo
                WHERE k_id_orden_trabajo = ? AND k_id_equipo_cliente = ?
                """,
                UUID.class, orden, equipo);
        assertThat(areaEnElPuente).isEqualTo(areaVieja);
    }

    @Test
    @DisplayName("un cliente con ordenes de trabajo no se puede borrar fisicamente")
    void eliminarClienteConOrdenesFalla() {
        UUID cliente = insertClient();
        UUID sede = insertHeadquarter(cliente);
        insertMinimalWorkOrder(cliente, sede);

        // Ninguna FK del modulo lleva ON DELETE CASCADE: retirar es apagar b_estado_activo.
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM cliente WHERE k_id_cliente = ?", cliente))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("una orden con equipos asociados no se puede borrar fisicamente")
    void eliminarOrdenConEquiposFalla() {
        UUID cliente = insertClient();
        UUID sede = insertHeadquarter(cliente);
        UUID area = insertServiceArea(sede, "UCI");
        UUID orden = insertMinimalWorkOrder(cliente, sede);
        UUID equipo = insertClientEquipment(area);
        insertWorkOrderEquipment(orden, equipo, area);

        assertThatThrownBy(
                        () -> jdbcTemplate.update(
                                "DELETE FROM orden_trabajo WHERE k_id_orden_trabajo = ?", orden))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("el ingeniero asignado debe ser una persona existente")
    void ingenieroDebeExistir() {
        UUID cliente = insertClient();
        UUID sede = insertHeadquarter(cliente);
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        INSERT INTO orden_trabajo (k_id_orden_trabajo, k_id_cliente, k_id_sede,
                                                   f_fecha_mantenimiento, n_periodicidad, t_tipo_servicio,
                                                   k_identificador)
                        VALUES (?, ?, ?, ?, 'MENSUAL', 'PREVENTIVO', ?)
                        """,
                        id, cliente, sede, LocalDate.now(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("un ingeniero si puede asignarse a una orden existente")
    void ingenieroSeAsignaSinError() {
        UUID cliente = insertClient();
        UUID sede = insertHeadquarter(cliente);
        UUID ingeniero = insertPerson();
        UUID id = UUID.randomUUID();

        assertThatCode(() -> jdbcTemplate.update(
                        """
                        INSERT INTO orden_trabajo (k_id_orden_trabajo, k_id_cliente, k_id_sede,
                                                   f_fecha_mantenimiento, n_periodicidad, t_tipo_servicio,
                                                   k_identificador)
                        VALUES (?, ?, ?, ?, 'MENSUAL', 'PREVENTIVO', ?)
                        """,
                        id, cliente, sede, LocalDate.now(), ingeniero))
                .doesNotThrowAnyException();
    }
}
