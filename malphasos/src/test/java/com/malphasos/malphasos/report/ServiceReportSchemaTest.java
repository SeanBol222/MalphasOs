package com.malphasos.malphasos.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.CodigoIsoLibre;
import com.malphasos.malphasos.TestcontainersConfiguration;
import java.math.BigDecimal;
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
 *   <li>{@link #reporteDeUnEquipoAjenoALaOrdenFalla()} ejercita la llave foránea <b>compuesta</b>
 *       contra el puente. Con dos foráneas sueltas esa inserción pasaría, y el reporte hablaría de un
 *       equipo que la orden nunca incluyó.
 *   <li>{@link #borradorConFechaDeFinalizacionFalla()} ejercita la rama {@code ELSE} del
 *       {@code CHECK} de cierre: un borrador no puede traer fecha de cierre.
 * </ul>
 *
 * <p><b>Las tres mutaciones con las que se comprobó esta clase</b>, porque una prueba de esquema que
 * nunca se ha visto fallar no dice nada:
 *
 * <ol>
 *   <li>Cambiar la foránea compuesta por dos sueltas —una a {@code orden_trabajo} y otra a
 *       {@code equipo_cliente}—: falla {@code reporteDeUnEquipoAjenoALaOrdenFalla}.
 *   <li>Quitar {@code NULLS NOT DISTINCT} del índice único de lecturas: falla
 *       {@code secuenciaRepetidaSinPuntoFalla}.
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
            "DELETE FROM dato_verificacion",
            "DELETE FROM reporte_servicio",
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
            "DELETE FROM pais"
        },
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ServiceReportSchemaTest {

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

    /**
     * Una verificación con su tipo de equipo detrás, y el punto que le cuelga.
     *
     * <p>La magnitud y la unidad se <b>leen del catálogo sembrado por {@code V10}</b>: no se pueden
     * inventar, porque el esquema ata el par (magnitud, unidad) con una foránea compuesta.
     */
    private Verificacion insertVerification(String codigoMagnitud, String simbolo, String valor) {
        UUID tipo = insertType();
        UUID verificacion = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO verificacion_tipo_equipo (k_id_verificacion, k_id_tipo_equipo,
                                                      k_id_magnitud, k_id_unidad_medida,
                                                      n_modalidad_verificacion, i_cantidad_datos)
                SELECT ?, ?, m.k_id_magnitud, u.k_id_unidad_medida, 'patron_constante', 3
                FROM magnitud m JOIN unidad_medida u USING (k_id_magnitud)
                WHERE m.n_codigo_magnitud = ? AND u.n_simbolo_unidad = ?
                """,
                verificacion, tipo, codigoMagnitud, simbolo);

        UUID punto = null;

        if (valor != null) {
            punto = UUID.randomUUID();
            jdbcTemplate.update(
                    """
                    INSERT INTO punto_verificacion (k_id_punto_verificacion, k_id_verificacion, d_valor)
                    VALUES (?, ?, CAST(? AS numeric))
                    """,
                    punto, verificacion, valor);
        }

        return new Verificacion(verificacion, punto);
    }

    /** Una verificación de presión con un punto, que es lo que piden casi todas las pruebas. */
    private Verificacion unaVerificacion() {
        return insertVerification("presion", "mmHg", "50.0000");
    }

    /** Una verificación sin puntos, para las lecturas que no los llevan. */
    private Verificacion unaVerificacionSinPuntos() {
        return insertVerification("presion", "mmHg", null);
    }

    /** Lo que hace falta nombrar para escribir una lectura: su verificación y, si hay, su punto. */
    private record Verificacion(UUID id, UUID punto) {
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
                INSERT INTO orden_trabajo (k_id_orden_trabajo, k_id_cliente, k_id_sede,
                                           f_fecha_mantenimiento, n_periodicidad, t_tipo_servicio)
                VALUES (?, ?, ?, ?, 'MENSUAL', 'PREVENTIVO')
                """,
                id, cliente, sede, LocalDate.now());

        return id;
    }

    /**
     * Un equipo dentro del alcance de una orden, que es el único destino que el reporte admite.
     *
     * @return el par (orden, equipo cliente) al que se le puede abrir reporte
     */
    private UUID[] insertEquipmentInOrder() {
        UUID cliente = insertClient();
        UUID sede = insertHeadquarter(cliente);
        UUID area = insertServiceArea(sede);
        UUID equipo = insertClientEquipment(area);
        UUID orden = insertWorkOrder(cliente, sede);

        jdbcTemplate.update(
                """
                INSERT INTO orden_trabajo_equipo (k_id_orden_trabajo, k_id_equipo_cliente, k_id_area_servicio)
                VALUES (?, ?, ?)
                """,
                orden, equipo, area);

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
                                              t_estado_reporte, t_procedimientos, t_resultado, t_finalizado)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                id, orden, equipo, estado, procedimientos, resultado, finalizado);

        return id;
    }

    /**
     * Una lectura. <b>Lleva su verificación desde {@code V10}</b>, y es obligatoria: con dos magnitudes
     * variables el punto nulo no distingue cuál se midió.
     */
    private UUID insertReading(
            UUID reporte, UUID verificacion, UUID punto, int secuencia, String valor, String unidad) {

        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO dato_verificacion (k_id_dato_verificacion, k_id_reporte_servicio,
                                               k_id_verificacion, k_id_punto_verificacion, i_secuencia,
                                               d_valor_patron, d_valor_equipo, n_unidad)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, reporte, verificacion, punto, secuencia, new BigDecimal(valor),
                new BigDecimal(valor), unidad);

        return id;
    }

    @Test
    @DisplayName("las dos tablas del modulo existen tras la migracion")
    void migracionCreaLasTablas() {
        Integer tablas = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('reporte_servicio', 'dato_verificacion')
                """,
                Integer.class);

        assertThat(tablas).isEqualTo(2);
    }

    @Test
    @DisplayName("los cinco campos que enumera RF-15 tienen columna, y todos son anulables")
    void losCincoCamposDeRf15TienenColumnaAnulable() {
        // La nota de requisitos decia "ninguno de esos cinco campos tiene columna en el esquema". Esta
        // prueba es lo que hace falsa esa frase, y por eso nombra los cinco uno a uno.
        Integer anulables = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM information_schema.columns
                WHERE table_name = 'reporte_servicio'
                  AND column_name IN ('t_falla_reportada', 't_diagnostico', 't_procedimientos',
                                      't_observaciones', 't_resultado')
                  AND is_nullable = 'YES'
                """,
                Integer.class);

        assertThat(anulables).isEqualTo(5);
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
                WHERE table_name = 'reporte_servicio' AND column_name = 't_finalizado'
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
    @DisplayName("un reporte de un equipo que la orden no incluye falla: la foranea es compuesta")
    void reporteDeUnEquipoAjenoALaOrdenFalla() {
        UUID[] alcance = insertEquipmentInOrder();
        // Un equipo real, de otro cliente, que no esta en el alcance de esta orden. Con dos foraneas
        // sueltas -- una a orden_trabajo y otra a equipo_cliente -- esta insercion pasaria.
        UUID ajeno = insertClientEquipment(insertServiceArea(insertHeadquarter(insertClient())));

        assertThatThrownBy(() -> insertDraft(alcance[0], ajeno))
                .isInstanceOf(DataIntegrityViolationException.class);
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
    @DisplayName("finalizar sin procedimientos falla: es la mitad de lo que el reporte existe para contar")
    void finalizarSinProcedimientosFalla() {
        UUID[] alcance = insertEquipmentInOrder();

        assertThatThrownBy(() -> insertReport(
                        alcance[0], alcance[1], "FINALIZADO", null, "OPERATIVO", LocalDateTime.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("unos procedimientos en blanco no cierran el reporte: el CHECK recorta")
    void finalizarConProcedimientosEnBlancoFalla() {
        UUID[] alcance = insertEquipmentInOrder();

        assertThatThrownBy(() -> insertReport(
                        alcance[0], alcance[1], "FINALIZADO", "   ", "OPERATIVO", LocalDateTime.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
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
    @DisplayName("un campo de texto en blanco falla: o trae algo o esta nulo")
    void textoEnBlancoFalla() {
        UUID[] alcance = insertEquipmentInOrder();
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        INSERT INTO reporte_servicio (k_id_reporte_servicio, k_id_orden_trabajo,
                                                      k_id_equipo_cliente, t_falla_reportada)
                        VALUES (?, ?, ?, '   ')
                        """,
                        id, alcance[0], alcance[1]))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- Datos de verificacion ----

    @Test
    @DisplayName("una lectura se guarda con su punto, y tambien sin punto: la modalidad variable no tiene")
    void lecturaConPuntoYSinPunto() {
        UUID[] alcance = insertEquipmentInOrder();
        UUID reporte = insertDraft(alcance[0], alcance[1]);
        Verificacion verificacion = unaVerificacion();
        Verificacion sinPuntos = unaVerificacionSinPuntos();

        assertThatCode(() -> insertReading(
                        reporte, verificacion.id(), verificacion.punto(), 1, "50.2000", "mmHg"))
                .doesNotThrowAnyException();
        assertThatCode(() -> insertReading(reporte, sinPuntos.id(), null, 1, "12.3400", "mmHg"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("una lectura sin verificacion falla: es lo que la distingue cuando no hay punto")
    void lecturaSinVerificacionFalla() {
        // La columna entro en V10 para tapar el agujero que el cambio de ese dia abria: con dos
        // magnitudes variables, dos lecturas sin punto eran indistinguibles y el reporte no sabia en
        // que columna imprimirlas.
        UUID[] alcance = insertEquipmentInOrder();
        UUID reporte = insertDraft(alcance[0], alcance[1]);

        assertThatThrownBy(() -> insertReading(reporte, null, null, 1, "1.0000", "mA"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("el punto tiene que ser de SU verificacion, y eso si lo puede comprobar el esquema")
    void puntoDeOtraVerificacionFalla() {
        // V9 decia que el esquema no puede exigir que el punto pertenezca al tipo del equipo
        // reportado, porque son cuatro saltos hasta tipo_equipo. Eso sigue siendo verdad para el
        // equipo; pero que el punto sea de SU verificacion es un solo salto, y lo impone una foranea
        // compuesta.
        UUID[] alcance = insertEquipmentInOrder();
        UUID reporte = insertDraft(alcance[0], alcance[1]);
        Verificacion una = unaVerificacion();
        Verificacion otra = insertVerification("temperatura", "°C", "37.0000");

        assertThatThrownBy(() -> insertReading(
                        reporte, una.id(), otra.punto(), 1, "50.1000", "mmHg"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("un valor negativo se admite: un congelador se verifica a -20 grados")
    void valorNegativoSeAdmite() {
        UUID[] alcance = insertEquipmentInOrder();
        UUID reporte = insertDraft(alcance[0], alcance[1]);
        Verificacion sinPuntos = unaVerificacionSinPuntos();

        assertThatCode(() -> insertReading(reporte, sinPuntos.id(), null, 1, "-20.5000", "C"))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 101})
    @DisplayName("la secuencia esta entre 1 y 100, el mismo tope que la cantidad de datos")
    void secuenciaFueraDeRangoFalla(int secuencia) {
        UUID[] alcance = insertEquipmentInOrder();
        UUID reporte = insertDraft(alcance[0], alcance[1]);
        Verificacion sinPuntos = unaVerificacionSinPuntos();

        assertThatThrownBy(() -> insertReading(
                        reporte, sinPuntos.id(), null, secuencia, "1.0000", "mA"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("una unidad en blanco falla: un numero pelado no se puede imprimir")
    void unidadEnBlancoFalla() {
        // La unidad sigue congelada en la lectura, aunque ahora la ponga el servidor desde la
        // verificacion: reconfigurar un tipo no debe cambiar un reporte ya firmado.
        UUID[] alcance = insertEquipmentInOrder();
        UUID reporte = insertDraft(alcance[0], alcance[1]);
        Verificacion sinPuntos = unaVerificacionSinPuntos();

        assertThatThrownBy(() -> insertReading(reporte, sinPuntos.id(), null, 1, "1.0000", "  "))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("dos lecturas con la misma secuencia en el mismo punto son la misma lectura dos veces")
    void secuenciaRepetidaEnElMismoPuntoFalla() {
        UUID[] alcance = insertEquipmentInOrder();
        UUID reporte = insertDraft(alcance[0], alcance[1]);
        Verificacion verificacion = unaVerificacion();
        insertReading(reporte, verificacion.id(), verificacion.punto(), 1, "50.1000", "mmHg");

        assertThatThrownBy(() -> insertReading(
                        reporte, verificacion.id(), verificacion.punto(), 1, "50.9000", "mmHg"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("sin punto la secuencia tampoco se repite: es lo que hace NULLS NOT DISTINCT")
    void secuenciaRepetidaSinPuntoFalla() {
        UUID[] alcance = insertEquipmentInOrder();
        UUID reporte = insertDraft(alcance[0], alcance[1]);
        Verificacion sinPuntos = unaVerificacionSinPuntos();
        insertReading(reporte, sinPuntos.id(), null, 1, "1.0000", "mA");

        // Por defecto PostgreSQL considera dos NULL distintos, y sin la clausula la modalidad variable
        // -- la unica que deja el punto nulo -- seria la unica que admitiria duplicados.
        assertThatThrownBy(() -> insertReading(reporte, sinPuntos.id(), null, 1, "2.0000", "mA"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("sin punto, dos VERIFICACIONES distintas si pueden repetir la secuencia")
    void mismaSecuenciaSinPuntoEnVerificacionesDistintas() {
        // ESTE ES EL CASO QUE V10 ARREGLA. El indice de V9 era (reporte, punto, secuencia) con NULLS
        // NOT DISTINCT: correcto entonces, porque habia una sola modalidad por tipo y una lectura sin
        // punto solo podia ser de la unica verificacion que existia. Con dos magnitudes variables, la
        // lectura 1 de temperatura y la 1 de humedad tenian las dos el punto nulo y el mismo numero, de
        // modo que la segunda chocaba contra la primera aunque midieran cosas distintas.
        UUID[] alcance = insertEquipmentInOrder();
        UUID reporte = insertDraft(alcance[0], alcance[1]);
        Verificacion una = unaVerificacionSinPuntos();
        Verificacion otra = insertVerification("temperatura", "°C", null);
        insertReading(reporte, una.id(), null, 1, "1.0000", "mmHg");

        assertThatCode(() -> insertReading(reporte, otra.id(), null, 1, "37.0000", "°C"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("la misma secuencia en dos puntos distintos si se admite: son dos medidas")
    void mismaSecuenciaEnPuntosDistintos() {
        UUID[] alcance = insertEquipmentInOrder();
        UUID reporte = insertDraft(alcance[0], alcance[1]);
        Verificacion una = unaVerificacion();
        Verificacion otra = insertVerification("presion", "kPa", "150.0000");
        insertReading(reporte, una.id(), una.punto(), 1, "50.1000", "mmHg");

        assertThatCode(() -> insertReading(
                        reporte, otra.id(), otra.punto(), 1, "150.4000", "kPa"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("retirar una lectura deja volver a tomarla: el indice unico tambien es parcial")
    void retirarLaLecturaDejaVolverATomarla() {
        UUID[] alcance = insertEquipmentInOrder();
        UUID reporte = insertDraft(alcance[0], alcance[1]);
        Verificacion verificacion = unaVerificacion();
        UUID primera = insertReading(
                reporte, verificacion.id(), verificacion.punto(), 1, "50.1000", "mmHg");
        jdbcTemplate.update(
                "UPDATE dato_verificacion SET b_estado_activo = false WHERE k_id_dato_verificacion = ?",
                primera);

        assertThatCode(() -> insertReading(
                        reporte, verificacion.id(), verificacion.punto(), 1, "50.2000", "mmHg"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("un punto retirado sigue siendo referenciable: con el se hicieron los reportes viejos")
    void puntoRetiradoSigueSiendoReferenciable() {
        UUID[] alcance = insertEquipmentInOrder();
        UUID reporte = insertDraft(alcance[0], alcance[1]);
        Verificacion verificacion = unaVerificacion();
        jdbcTemplate.update(
                "UPDATE punto_verificacion SET b_estado_activo = false WHERE k_id_punto_verificacion = ?",
                verificacion.punto());

        assertThatCode(() -> insertReading(
                        reporte, verificacion.id(), verificacion.punto(), 1, "50.1000", "mmHg"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("una verificacion retirada sigue siendo referenciable, por lo mismo")
    void verificacionRetiradaSigueSiendoReferenciable() {
        UUID[] alcance = insertEquipmentInOrder();
        UUID reporte = insertDraft(alcance[0], alcance[1]);
        Verificacion verificacion = unaVerificacion();
        jdbcTemplate.update(
                "UPDATE verificacion_tipo_equipo SET b_estado_activo = false WHERE k_id_verificacion = ?",
                verificacion.id());

        assertThatCode(() -> insertReading(
                        reporte, verificacion.id(), verificacion.punto(), 1, "50.1000", "mmHg"))
                .doesNotThrowAnyException();
    }
}
