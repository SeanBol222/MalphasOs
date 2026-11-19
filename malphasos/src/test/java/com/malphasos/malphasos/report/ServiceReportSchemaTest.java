package com.malphasos.malphasos.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.CodigoIsoLibre;
import com.malphasos.malphasos.TestcontainersConfiguration;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * Verifica que la migración V9 crea el esquema de reportes de servicio y que sus restricciones
 * rechazan los datos inválidos. Es la primera tanda del módulo: aquí no hay agregados, servicios ni
 * controladores, solo el esquema.
 *
 * <p>Dos pruebas de esta clase valen más que las demás, porque cubren las dos trampas que este
 * proyecto ya pagó una vez:
 *
 * <ul>
 *   <li>{@link #reporteDeUnaOrdenOEquipoInexistenteFalla()} ejercita las dos foráneas del reporte. Hasta
 *       {@code V50} era una sola, <b>compuesta</b> contra la tabla puente, que impedía reportar un equipo
 *       que la orden no incluía; sin puente es el reporte el que pone el equipo en la orden, y que ese
 *       equipo sea de la sede de la orden lo comprueba el servicio al añadirlo.
 *   <li>{@link #borradorConFechaDeFinalizacionFalla()} ejercita la rama {@code ELSE} del
 *       {@code CHECK} de cierre: un borrador no puede traer fecha de cierre.
 * </ul>
 *
 * <p><b>Las tres mutaciones con las que se comprobó esta clase</b>, porque una prueba de esquema que
 * nunca se ha visto fallar no dice nada:
 *
 * <ol>
 *   <li>Cambiar la foránea compuesta por dos sueltas —una a {@code orden_trabajo} y otra a
 *       {@code equipo_cliente}—: fallaba {@code reporteDeUnEquipoAjenoALaOrdenFalla}. Es lo que
 *       {@code V50} hizo a propósito, y esa prueba salió con ella.
 *   <li>Quitar {@code NULLS NOT DISTINCT} del índice único de lecturas: fallaba
 *       {@code secuenciaRepetidaSinPuntoFalla}. <b>Desde {@code V59} las lecturas no tienen tabla</b>: van en
 *       {@code t_contenido}, y no repetir una lectura lo cuida el agregado. Las pruebas de lecturas de esta
 *       clase salieron con la tabla; la forma del documento la prueba {@code ReportContentMigrationTest}.
 *   <li>Sustituir el {@code CASE} del cierre por dos ramas unidas por {@code OR}: <b>no falla
 *       ninguna</b>, y eso es un hallazgo, no un hueco. La trampa que V8 pagó —un {@code CHECK} que
 *       devuelve {@code NULL} se considera satisfecho— necesita que la columna que discrimina sea
 *       anulable, y {@code t_estado_reporte} es {@code NOT NULL} con solo dos valores. El
 *       {@code CASE} se conserva por legibilidad; el comentario de la migración decía que era por
 *       la trampa de V8 y se corrigió al comprobarlo.
 * </ol>
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(
        statements = {
            "DELETE FROM reporte_servicio",
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
            "DELETE FROM pais"
        },
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ServiceReportSchemaTest {

    /** El contenido de un reporte recién abierto, el mismo que pone el valor por defecto de V59. */
    private static final String CONTENIDO_VACIO = """
            {"version": 1, "falla": null, "diagnostico": null, "causa": null, "repuestos": null,
             "procedimientos": null, "observaciones": null, "ingreso": {"fisico": null, "operativo": null},
             "recibe": null, "pasos": [], "accesorios": [], "verificaciones": []}
            """;

    @Autowired private JdbcTemplate jdbcTemplate;

    private String unico() {
        return String.format("%010d", Math.floorMod(System.nanoTime(), 10_000_000_000L));
    }

    // ---- Todo lo que hace falta para que exista un equipo en una orden ----

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

    private UUID insertHeadquarter(UUID cliente) {
        UUID sede = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO sede (k_id_sede, n_nombre_sede, t_calle, t_carrera, t_numero,
                                  k_id_cliente, k_id_ciudad)
                VALUES (?, ?, '10', '20', '30-40', ?, ?)
                """,
                sede, "Sede " + unico(), cliente, insertCity());

        return sede;
    }

    private UUID insertServiceArea(UUID sede) {
        UUID area = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO area_servicio (k_id_area_servicio, n_nombre_area, k_id_sede) VALUES (?, ?, ?)",
                area, "Area " + unico(), sede);

        return area;
    }

    /** Un tipo de equipo sin modalidad de verificación: para los casos que no miden nada. */
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

    private UUID insertClientEquipment(UUID area) {
        UUID marca = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO marca (k_id_marca, n_nombre_marca) VALUES (?, ?)", marca, "Marca " + unico());

        UUID equipo = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO equipo (k_id_equipo, k_id_tipo_equipo, k_id_marca) VALUES (?, ?, ?)",
                equipo, insertType(), marca);

        UUID fabricante = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO fabricante (k_id_fabricante, n_nombre_fabricante) VALUES (?, ?)",
                fabricante, "Fabricante " + unico());

        UUID modelo = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO modelo (k_id_modelo, n_nombre_modelo, k_id_fabricante, k_id_equipo)"
                        + " VALUES (?, ?, ?, ?)",
                modelo, "Modelo " + unico(), fabricante, equipo);

        UUID unidad = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO equipo_cliente (k_id_equipo_cliente, k_serie, k_id_modelo, k_id_area_servicio)
                VALUES (?, ?, ?, ?)
                """,
                unidad, "Serie-" + unico(), modelo, area);

        return unidad;
    }

    private UUID insertWorkOrder(UUID cliente, UUID sede) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO orden_trabajo (k_id_orden_trabajo, k_id_sede,
                                           f_fecha_mantenimiento, n_periodicidad, t_tipo_servicio)
                VALUES (?, ?, ?, 'MENSUAL', 'PREVENTIVO')
                """,
                id, sede, LocalDate.now());

        return id;
    }

    /**
     * Una orden y un equipo de su sede, a los que se les puede abrir reporte.
     *
     * @return el par (orden, equipo cliente)
     */
    private UUID[] insertEquipmentInOrder() {
        UUID cliente = insertClient();
        UUID sede = insertHeadquarter(cliente);
        UUID area = insertServiceArea(sede);
        UUID equipo = insertClientEquipment(area);
        UUID orden = insertWorkOrder(cliente, sede);

        return new UUID[] {orden, equipo};
    }

    // ---- Reporte ----

    /** Inserción mínima: sin ninguno de los cinco campos, sin estado y sin estado activo. */
    private UUID insertDraft(UUID orden, UUID equipo) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO reporte_servicio (k_id_reporte_servicio, k_id_orden_trabajo, k_id_equipo_cliente)
                VALUES (?, ?, ?)
                """,
                id, orden, equipo);

        return id;
    }

    private UUID insertReport(
            UUID orden,
            UUID equipo,
            String estado,
            String procedimientos,
            String resultado,
            LocalDateTime finalizado) {

        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO reporte_servicio (k_id_reporte_servicio, k_id_orden_trabajo, k_id_equipo_cliente,
                                              t_estado_reporte, t_resultado, f_finalizado, t_contenido)
                VALUES (?, ?, ?, ?, ?, ?, jsonb_set(?::jsonb, '{procedimientos}',
                                                       coalesce(to_jsonb(?::text), 'null'::jsonb)))
                """,
                id, orden, equipo, estado, resultado, finalizado, CONTENIDO_VACIO, procedimientos);

        return id;
    }

    @Test
    @DisplayName("desde V59 el modulo es una sola tabla: lo que el reporte dice va en t_contenido")
    void unaSolaTabla() {
        // Las lecturas, los patrones, los pasos y los accesorios eran cuatro tablas hijas. Que no vuelvan: con
        // ellas habria otra vez dos sitios para lo mismo.
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND table_name IN ('dato_verificacion', 'patron_verificacion_reporte', 'paso_reporte',
                                             'accesorio_reporte')
                        """,
                        Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM information_schema.columns
                        WHERE table_schema = 'public' AND table_name = 'reporte_servicio'
                          AND column_name IN ('t_falla_reportada', 't_diagnostico', 't_procedimientos',
                                              't_observaciones', 't_causa', 't_repuestos', 't_ingreso_fisico',
                                              'n_recibe_nombre')
                        """,
                        Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("los cinco campos que enumera RF-15 siguen ahi: el resultado como columna, los textos en el documento")
    void losCincoCamposDeRf15() {
        // El resultado se queda como columna porque el listado de equipos lo filtra en SQL; los otros cuatro
        // nacen nulos dentro del documento de un reporte recien abierto.
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT is_nullable FROM information_schema.columns
                        WHERE table_name = 'reporte_servicio' AND column_name = 't_resultado'
                        """,
                        String.class))
                .isEqualTo("YES");

        UUID[] alcance = insertEquipmentInOrder();
        UUID reporte = insertDraft(alcance[0], alcance[1]);
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT jsonb_exists_all(t_contenido, ARRAY['falla', 'diagnostico', 'procedimientos', 'observaciones'])
                           AND t_contenido ->> 'falla' IS NULL AND t_contenido ->> 'procedimientos' IS NULL
                        FROM reporte_servicio WHERE k_id_reporte_servicio = ?
                        """,
                        Boolean.class, reporte))
                .isTrue();
    }

    @Test
    @DisplayName("el vinculo, el estado y el estado activo son obligatorios; la fecha de cierre no")
    void columnasObligatoriasDelReporte() {
        Integer obligatorias = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM information_schema.columns
                WHERE table_name = 'reporte_servicio'
                  AND column_name IN ('k_id_reporte_servicio', 'k_id_orden_trabajo',
                                      'k_id_equipo_cliente', 't_estado_reporte', 'b_estado_activo')
                  AND is_nullable = 'NO'
                """,
                Integer.class);
        assertThat(obligatorias).isEqualTo(5);

        String cierre = jdbcTemplate.queryForObject(
                """
                SELECT is_nullable FROM information_schema.columns
                WHERE table_name = 'reporte_servicio' AND column_name = 'f_finalizado'
                """,
                String.class);
        assertThat(cierre).isEqualTo("YES");
    }

    @Test
    @DisplayName("el reporte no copia cliente, sede, tipo de servicio ni ingeniero: los consulta de la orden")
    void elReporteNoCopiaLosDatosDeLaOrden() {
        // RF-11 pide autocompletar esos datos desde la orden de trabajo. Consultarlos es autocompletarlos;
        // copiarlos seria una tercera copia que mantener de acuerdo. El original si copiaba el cliente.
        Integer copiadas = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM information_schema.columns
                WHERE table_name = 'reporte_servicio'
                  AND column_name IN ('k_id_cliente', 'k_id_sede', 't_tipo_servicio', 'k_identificador')
                """,
                Integer.class);

        assertThat(copiadas).isZero();
    }

    @Test
    @DisplayName("un reporte recien abierto queda en BORRADOR y activo, sin ninguno de los cinco campos")
    void reporteMinimoQuedaEnBorrador() {
        UUID[] alcance = insertEquipmentInOrder();

        UUID reporte = insertDraft(alcance[0], alcance[1]);

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT t_estado_reporte FROM reporte_servicio WHERE k_id_reporte_servicio = ?",
                        String.class, reporte))
                .isEqualTo("BORRADOR");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT b_estado_activo FROM reporte_servicio WHERE k_id_reporte_servicio = ?",
                        Boolean.class, reporte))
                .isTrue();
    }

    @Test
    @DisplayName("un reporte de una orden o de un equipo que no existen falla: sus dos foraneas (V50)")
    void reporteDeUnaOrdenOEquipoInexistenteFalla() {
        UUID[] alcance = insertEquipmentInOrder();

        assertThatThrownBy(() -> insertDraft(UUID.randomUUID(), alcance[1]))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("FK_reporte_servicio_orden");
        assertThatThrownBy(() -> insertDraft(alcance[0], UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("FK_reporte_servicio_equipo_cliente");
    }

    @Test
    @DisplayName("el mismo equipo de la misma orden no tiene dos reportes activos")
    void unSoloReporteActivoPorEquipoDeLaOrden() {
        UUID[] alcance = insertEquipmentInOrder();
        insertDraft(alcance[0], alcance[1]);

        assertThatThrownBy(() -> insertDraft(alcance[0], alcance[1]))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("retirar un reporte deja abrir otro para el mismo equipo: el indice unico es parcial")
    void retirarElReporteDejaAbrirOtro() {
        UUID[] alcance = insertEquipmentInOrder();
        UUID primero = insertDraft(alcance[0], alcance[1]);
        jdbcTemplate.update(
                "UPDATE reporte_servicio SET b_estado_activo = false WHERE k_id_reporte_servicio = ?",
                primero);

        assertThatCode(() -> insertDraft(alcance[0], alcance[1])).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"borrador", "ABIERTO", "FIRMADO"})
    @DisplayName("un estado fuera del catalogo falla por el CHECK")
    void estadoFueraDelCatalogoFalla(String estado) {
        UUID[] alcance = insertEquipmentInOrder();

        assertThatThrownBy(() -> insertReport(alcance[0], alcance[1], estado, null, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"OPERATIVO", "OPERATIVO_CON_RESTRICCIONES", "FUERA_DE_SERVICIO"})
    @DisplayName("el resultado admite los tres valores del catalogo al finalizar")
    void resultadoDelCatalogo(String resultado) {
        UUID[] alcance = insertEquipmentInOrder();

        assertThatCode(() -> insertReport(
                        alcance[0], alcance[1], "FINALIZADO", "Limpieza y calibracion", resultado,
                        LocalDateTime.now()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("un resultado fuera del catalogo falla por el CHECK, no por longitud")
    void resultadoFueraDelCatalogoFalla() {
        UUID[] alcance = insertEquipmentInOrder();

        // Corto a proposito: t_resultado es varchar(27) y un valor mas largo fallaria por longitud, no
        // por el CHECK que aqui se quiere probar.
        assertThatThrownBy(() -> insertReport(
                        alcance[0], alcance[1], "FINALIZADO", "Procedimientos", "BUENO",
                        LocalDateTime.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("desde V22 la base deja cerrar sin procedimientos: con pasos del protocolo son opcionales")
    void finalizarSinProcedimientosLoDecideElDominio() {
        // Hasta V22 esta prueba exigia el rechazo. Ahora un reporte con pasos se cierra con todos
        // marcados y el texto -«trabajo adicional»- es opcional; uno sin pasos lo sigue exigiendo. Eso
        // mira otra tabla y un CHECK no puede, asi que la regla vive en ServiceReport.finish y la prueba
        // que la sostiene esta en el dominio. Aqui se fija que la base ya no la impone.
        UUID[] alcance = insertEquipmentInOrder();

        assertThatCode(() -> insertReport(
                        alcance[0], alcance[1], "FINALIZADO", null, "OPERATIVO", LocalDateTime.now()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("finalizar sin resultado falla: la hoja de vida y las alertas cuelgan de ese dato")
    void finalizarSinResultadoFalla() {
        UUID[] alcance = insertEquipmentInOrder();

        assertThatThrownBy(() -> insertReport(
                        alcance[0], alcance[1], "FINALIZADO", "Limpieza", null, LocalDateTime.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("finalizar sin fecha de cierre falla")
    void finalizarSinFechaFalla() {
        UUID[] alcance = insertEquipmentInOrder();

        assertThatThrownBy(() -> insertReport(
                        alcance[0], alcance[1], "FINALIZADO", "Limpieza", "OPERATIVO", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("un borrador con fecha de cierre falla: es la rama ELSE, la que el OR dejaba pasar")
    void borradorConFechaDeFinalizacionFalla() {
        UUID[] alcance = insertEquipmentInOrder();

        // Una fecha de cierre en un borrador es un reporte que dice a la vez que esta abierto y cuando se
        // cerro. No distingue el CASE del OR -- se comprobo y las dos formas lo rechazan, ver el javadoc.
        assertThatThrownBy(() -> insertReport(
                        alcance[0], alcance[1], "BORRADOR", "Limpieza", "OPERATIVO", LocalDateTime.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("un borrador si admite procedimientos y resultado: se llena antes de cerrarse")
    void borradorAdmiteLoQueYaSeSabe() {
        UUID[] alcance = insertEquipmentInOrder();

        assertThatCode(() -> insertReport(
                        alcance[0], alcance[1], "BORRADOR", "Limpieza", "OPERATIVO", null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("el historial ya no tiene tabla: sale de los reportes, con un indice para el ultimo cierre (V52)")
    void sinTablaDeIntervenciones() {
        // intervencion copiaba de cada reporte cerrado lo que una linea de la hoja de vida dice. Que no vuelva:
        // con ella habria otra vez dos fuentes del mismo hecho.
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'"
                                + " AND table_name = 'intervencion'",
                        Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT indexdef FROM pg_indexes WHERE schemaname = 'public'"
                                + " AND indexname = 'IX_reporte_servicio_equipo_cierre'",
                        String.class))
                .contains("k_id_equipo_cliente, f_finalizado DESC")
                .contains("WHERE");
    }
}
