package com.malphasos.malphasos.equipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.TestcontainersConfiguration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
 * Verifica que {@code V12} crea la tabla del historial de intervenciones y que sus restricciones
 * rechazan lo que deben. Primera tanda: aquí no hay agregado, servicio ni oyente, solo el esquema.
 *
 * <p><b>La prueba que vale más de esta clase</b> es {@link #corregirElReporteNoReescribeLaIntervencion()}.
 * Las tres columnas de datos son una <b>copia congelada</b> del momento del cierre —guardan lo que el
 * servicio fue, no lo que su reporte dice hoy—, y por eso el esquema <b>no</b> lleva una foránea
 * compuesta contra {@code reporte_servicio}. Esa prueba es la que fija que la restricción ausente siga
 * ausente: con ella, corregir un reporte quedaría bloqueado por la intervención que lo copió.
 *
 * <p>La segunda que importa es {@link #dosIntervencionesDelMismoReporteFallan()}: es lo que hace al
 * oyente idempotente <b>sin que el oyente tenga que saberlo</b>, y lo que impide que el historial
 * cuente dos veces el mismo mantenimiento.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(
        statements = {
            "DELETE FROM intervencion",
            "DELETE FROM dato_verificacion",
            "DELETE FROM reporte_servicio",
            "DELETE FROM orden_trabajo_equipo",
            "DELETE FROM orden_trabajo",
            "DELETE FROM equipo_cliente",
            "DELETE FROM modelo",
            "DELETE FROM equipo",
            "DELETE FROM fabricante",
            "DELETE FROM marca",
            "DELETE FROM punto_verificacion",
            "DELETE FROM verificacion_tipo_equipo",
            "DELETE FROM tipo_equipo",
            "DELETE FROM area_servicio",
            "DELETE FROM sede",
            "DELETE FROM cliente"
        },
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class InterventionSchemaTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    private String unico() {
        return String.format("%010d", Math.floorMod(System.nanoTime(), 10_000_000_000L));
    }

    private UUID insertCity() {
        UUID pais = UUID.randomUUID();
        UUID ciudad = UUID.randomUUID();
        long n = System.nanoTime();
        String letras = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String iso = "" + letras.charAt((int) (n % 26))
                + letras.charAt((int) ((n / 26) % 26))
                + letras.charAt((int) ((n / 676) % 26));
        jdbcTemplate.update(
                "INSERT INTO pais (k_id_pais, k_codigo_iso, n_nombre_pais) VALUES (?, ?, ?)",
                pais, iso, "Pais " + n);
        jdbcTemplate.update(
                "INSERT INTO ciudad (k_id_ciudad, n_nombre_ciudad, k_id_pais) VALUES (?, ?, ?)",
                ciudad, "Ciudad " + n, pais);

        return ciudad;
    }

    /** Toda la cadena hasta un reporte finalizado, que es lo mínimo para tener una intervención. */
    private Contexto unReporteFinalizado(String tipoServicio, String resultado) {
        UUID cliente = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO cliente (k_id_cliente, k_documento, n_tipo_identificacion, n_razon_social)
                VALUES (?, ?, 'NIT_juridico', 'Hospital Central')
                """,
                cliente, unico());

        UUID sede = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO sede (k_id_sede, n_nombre_sede, t_calle, t_carrera, t_numero,
                                  k_id_cliente, k_id_ciudad)
                VALUES (?, ?, '10', '20', '30-40', ?, ?)
                """,
                sede, "Sede " + unico(), cliente, insertCity());

        UUID area = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO area_servicio (k_id_area_servicio, n_nombre_area, k_id_sede) VALUES (?, ?, ?)",
                area, "Area " + unico(), sede);

        UUID tipo = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO tipo_equipo (k_id_tipo_equipo, n_nombre_tipo_equipo, t_definicion_tecnica,
                                         t_recomendaciones_cuidado, t_tecnologia_predominante,
                                         m_valor_unitario_mantenimiento)
                VALUES (?, ?, 'Definicion', 'Cuidados', 'Electronica', 150000)
                """,
                tipo, "Tipo " + unico());

        UUID marca = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO marca (k_id_marca, n_nombre_marca) VALUES (?, ?)", marca, "Marca " + unico());

        UUID equipoCatalogo = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO equipo (k_id_equipo, k_id_tipo_equipo, k_id_marca) VALUES (?, ?, ?)",
                equipoCatalogo, tipo, marca);

        UUID fabricante = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO fabricante (k_id_fabricante, n_nombre_fabricante) VALUES (?, ?)",
                fabricante, "Fabricante " + unico());

        UUID modelo = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO modelo (k_id_modelo, n_nombre_modelo, k_id_fabricante, k_id_equipo)"
                        + " VALUES (?, ?, ?, ?)",
                modelo, "Modelo " + unico(), fabricante, equipoCatalogo);

        UUID unidad = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO equipo_cliente (k_id_equipo_cliente, k_serie, k_id_modelo, k_id_area_servicio)
                VALUES (?, ?, ?, ?)
                """,
                unidad, "Serie-" + unico(), modelo, area);

        UUID orden = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO orden_trabajo (k_id_orden_trabajo, k_id_cliente, k_id_sede,
                                           f_fecha_mantenimiento, n_periodicidad, t_tipo_servicio)
                VALUES (?, ?, ?, ?, 'MENSUAL', ?)
                """,
                orden, cliente, sede, LocalDate.now(), tipoServicio);

        jdbcTemplate.update(
                """
                INSERT INTO orden_trabajo_equipo (k_id_orden_trabajo, k_id_equipo_cliente, k_id_area_servicio)
                VALUES (?, ?, ?)
                """,
                orden, unidad, area);

        UUID reporte = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO reporte_servicio (k_id_reporte_servicio, k_id_orden_trabajo, k_id_equipo_cliente,
                                              t_estado_reporte, t_procedimientos, t_resultado, t_finalizado)
                VALUES (?, ?, ?, 'FINALIZADO', 'Se hizo lo previsto', ?, ?)
                """,
                reporte, orden, unidad, resultado, LocalDateTime.now());

        return new Contexto(unidad, reporte);
    }

    private record Contexto(UUID equipo, UUID reporte) {
    }

    private UUID insertIntervention(Contexto contexto, String tipoServicio, String resultado) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO intervencion (k_id_intervencion, k_id_equipo_cliente, k_id_reporte_servicio,
                                          f_fecha_servicio, t_tipo_servicio, t_resultado)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                id, contexto.equipo(), contexto.reporte(), LocalDateTime.now(), tipoServicio, resultado);

        return id;
    }

    @Test
    @DisplayName("una intervencion de un reporte finalizado se guarda con sus tres datos")
    void intervencionValida() {
        Contexto contexto = unReporteFinalizado("PREVENTIVO", "OPERATIVO");

        UUID id = insertIntervention(contexto, "PREVENTIVO", "OPERATIVO");

        Map<String, Object> fila = jdbcTemplate.queryForMap(
                "SELECT * FROM intervencion WHERE k_id_intervencion = ?", id);
        assertThat(fila)
                .containsEntry("t_tipo_servicio", "PREVENTIVO")
                .containsEntry("t_resultado", "OPERATIVO")
                .containsEntry("b_estado_activo", true);
        assertThat(fila.get("f_fecha_servicio")).isNotNull();
    }

    @Test
    @DisplayName("corregir el reporte NO reescribe la intervencion, y no queda bloqueado por ella")
    void corregirElReporteNoReescribeLaIntervencion() {
        Contexto contexto = unReporteFinalizado("PREVENTIVO", "OPERATIVO");
        insertIntervention(contexto, "PREVENTIVO", "OPERATIVO");

        // No hay foranea compuesta contra reporte_servicio a proposito: si la hubiera, esta
        // correccion -- legitima -- fallaria por culpa de la intervencion que copio el valor viejo.
        // Es el mismo caso que el area congelada de orden_trabajo_equipo, y la misma razon: la
        // restriccion que protege el pasado impediria el futuro.
        assertThatCode(() -> jdbcTemplate.update(
                        "UPDATE reporte_servicio SET t_resultado = ? WHERE k_id_reporte_servicio = ?",
                        "FUERA_DE_SERVICIO", contexto.reporte()))
                .doesNotThrowAnyException();

        String enLaIntervencion = jdbcTemplate.queryForObject(
                "SELECT t_resultado FROM intervencion WHERE k_id_reporte_servicio = ?",
                String.class, contexto.reporte());
        assertThat(enLaIntervencion).isEqualTo("OPERATIVO");
    }

    @Test
    @DisplayName("retirar el reporte no borra la intervencion: el mantenimiento ocurrio")
    void retirarElReporteNoBorraLaIntervencion() {
        Contexto contexto = unReporteFinalizado("CORRECTIVO", "OPERATIVO_CON_RESTRICCIONES");
        insertIntervention(contexto, "CORRECTIVO", "OPERATIVO_CON_RESTRICCIONES");

        jdbcTemplate.update(
                "UPDATE reporte_servicio SET b_estado_activo = false WHERE k_id_reporte_servicio = ?",
                contexto.reporte());

        // Es la razon de que esta tabla exista en vez de ser una consulta sobre los reportes.
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM intervencion WHERE k_id_reporte_servicio = ? AND b_estado_activo",
                        Integer.class, contexto.reporte()))
                .isOne();
    }

    @Test
    @DisplayName("dos intervenciones del mismo reporte fallan: un mantenimiento se cuenta una vez")
    void dosIntervencionesDelMismoReporteFallan() {
        Contexto contexto = unReporteFinalizado("PREVENTIVO", "OPERATIVO");
        insertIntervention(contexto, "PREVENTIVO", "OPERATIVO");

        assertThatThrownBy(() -> insertIntervention(contexto, "PREVENTIVO", "OPERATIVO"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("la restriccion de un solo registro por reporte NO es parcial")
    void retirarUnaIntervencionNoDejaRegistrarOtra() {
        Contexto contexto = unReporteFinalizado("PREVENTIVO", "OPERATIVO");
        UUID primera = insertIntervention(contexto, "PREVENTIVO", "OPERATIVO");
        jdbcTemplate.update(
                "UPDATE intervencion SET b_estado_activo = false WHERE k_id_intervencion = ?", primera);

        // Si fuera parcial -- como los cinco indices unicos del resto del esquema -- retirar una
        // intervencion dejaria hueco para otra del mismo reporte, y el historial podria contar dos
        // veces el mismo mantenimiento. Aqui la excepcion a la costumbre es deliberada.
        assertThatThrownBy(() -> insertIntervention(contexto, "PREVENTIVO", "OPERATIVO"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PREVENTIVO", "CORRECTIVO", "CALIBRACION"})
    @DisplayName("admite los mismos tipos de servicio que la orden de la que se copian")
    void losTresTiposDeServicio(String tipo) {
        Contexto contexto = unReporteFinalizado(tipo, "OPERATIVO");

        assertThatCode(() -> insertIntervention(contexto, tipo, "OPERATIVO")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("un tipo de servicio que la orden no admite, aqui tampoco")
    void tipoDeServicioInventado() {
        Contexto contexto = unReporteFinalizado("PREVENTIVO", "OPERATIVO");

        // DIAGNOSTICO cabe en varchar(11) y no esta en la lista, y las dos cosas importan: la
        // primera version de esta prueba usaba MANTENIMIENTO, de trece caracteres, de modo que lo
        // rechazaba la LONGITUD y no el CHECK. Quitar el CHECK dejaba la prueba en verde, y lo
        // destapo mutarlo. Un valor demasiado largo prueba el varchar, no el vocabulario.
        assertThatThrownBy(() -> insertIntervention(contexto, "DIAGNOSTICO", "OPERATIVO"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("un resultado que el reporte no admite, aqui tampoco")
    void resultadoInventado() {
        Contexto contexto = unReporteFinalizado("PREVENTIVO", "OPERATIVO");

        assertThatThrownBy(() -> insertIntervention(contexto, "PREVENTIVO", "APTO"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("el historial se consulta por equipo y en orden cronologico, y tiene su indice")
    void elIndiceDelHistorialExiste() {
        // El segundo criterio de RF-27 pide que el historial sea consultable y ordenado
        // cronologicamente: el indice lleva la fecha para que ese orden no cueste una ordenacion.
        List<String> indices = jdbcTemplate.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE tablename = 'intervencion'", String.class);

        assertThat(indices)
                .anyMatch(d -> d.contains("k_id_equipo_cliente") && d.contains("f_fecha_servicio"));
    }
}
