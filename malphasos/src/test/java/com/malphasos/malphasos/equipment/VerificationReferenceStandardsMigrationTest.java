package com.malphasos.malphasos.equipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.TestcontainersConfiguration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * {@code V60}, los patrones de cada verificación y de cada orden (2026-11-19), sobre un esquema aparte en
 * {@code V59} con dos reportes que usaron patrones: lo que se prueba es el relleno del catálogo, que sale del
 * documento de cada reporte, y que las llaves impiden repetir y apuntar a lo que no existe.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class VerificationReferenceStandardsMigrationTest {

    private static final String ESQUEMA = "prueba_v60";

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbcTemplate;

    private SingleConnectionDataSource conexion;
    private JdbcTemplate enElEsquema;

    private UUID orden;
    private UUID unidad;
    private UUID tipo;
    private UUID presion;
    private UUID frecuencia;
    private UUID simulador;
    private UUID manometro;

    @AfterEach
    void borrarEsquema() {
        if (conexion != null) {
            enElEsquema.execute("RESET search_path");
            conexion.destroy();
        }
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + ESQUEMA + " CASCADE");
    }

    private void migrarHasta(String version) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(ESQUEMA)
                .locations("classpath:db/migration")
                .target(version)
                .load()
                .migrate();
    }

    private void sql(String sentencia, Object... argumentos) {
        enElEsquema.update(sentencia, argumentos);
    }

    /** Un esquema en V59 con un equipo en una orden, dos verificaciones de su tipo y dos patrones. */
    private void unEsquemaEnV59() {
        migrarHasta("59");
        conexion = new SingleConnectionDataSource(DataSourceUtils.getConnection(dataSource), true);
        enElEsquema = new JdbcTemplate(conexion);
        enElEsquema.execute("SET search_path TO " + ESQUEMA);

        UUID ciudad = enElEsquema.queryForObject("SELECT k_id_ciudad FROM ciudad LIMIT 1", UUID.class);
        UUID cliente = UUID.randomUUID();
        sql("INSERT INTO cliente (k_id_cliente, k_documento, n_tipo_identificacion, n_razon_social, n_sigla)"
                + " VALUES (?, '9001', 'NIT_juridico', 'Hospital', 'HOS')", cliente);
        UUID sede = UUID.randomUUID();
        sql("INSERT INTO sede (k_id_sede, n_nombre_sede, t_calle, t_carrera, t_numero, k_id_cliente, k_id_ciudad)"
                + " VALUES (?, 'Norte', '1', '2', '3', ?, ?)", sede, cliente, ciudad);
        UUID area = UUID.randomUUID();
        sql("INSERT INTO area_servicio (k_id_area_servicio, n_nombre_area, k_id_sede) VALUES (?, 'Urgencias', ?)",
                area, sede);
        tipo = UUID.randomUUID();
        sql("INSERT INTO tipo_equipo (k_id_tipo_equipo, n_nombre_tipo_equipo, t_definicion_tecnica,"
                + " t_recomendaciones_cuidado, t_tecnologia_predominante, m_valor_unitario_mantenimiento)"
                + " VALUES (?, 'Monitor', 'D', 'C', 'T', 1000)", tipo);
        UUID marca = UUID.randomUUID();
        sql("INSERT INTO marca (k_id_marca, n_nombre_marca) VALUES (?, 'Mindray')", marca);
        UUID equipo = UUID.randomUUID();
        sql("INSERT INTO equipo (k_id_equipo, k_id_tipo_equipo, k_id_marca) VALUES (?, ?, ?)", equipo, tipo, marca);
        UUID fabricante = UUID.randomUUID();
        sql("INSERT INTO fabricante (k_id_fabricante, n_nombre_fabricante) VALUES (?, 'Mindray Co')", fabricante);
        UUID modelo = UUID.randomUUID();
        sql("INSERT INTO modelo (k_id_modelo, n_nombre_modelo, k_id_fabricante, k_id_equipo) VALUES (?, 'M1', ?, ?)",
                modelo, fabricante, equipo);
        unidad = UUID.randomUUID();
        sql("INSERT INTO equipo_cliente (k_id_equipo_cliente, k_serie, k_id_modelo, k_id_area_servicio)"
                + " VALUES (?, 'S-1', ?, ?)", unidad, modelo, area);
        orden = UUID.randomUUID();
        sql("INSERT INTO orden_trabajo (k_id_orden_trabajo, k_id_sede, f_fecha_mantenimiento, n_periodicidad,"
                + " t_tipo_servicio, t_estado_ejecucion) VALUES (?, ?, ?, 'MENSUAL', 'PREVENTIVO', 'EN_EJECUCION')",
                orden, sede, LocalDate.of(2026, 9, 1));

        presion = UUID.randomUUID();
        frecuencia = UUID.randomUUID();
        sql("INSERT INTO verificacion_tipo_equipo (k_id_verificacion, k_id_tipo_equipo, k_id_unidad_medida,"
                + " n_modalidad_verificacion) SELECT ?, ?, k_id_unidad_medida, 'patron_equipo_variable'"
                + " FROM unidad_medida WHERE n_simbolo_unidad = 'mmHg'", presion, tipo);
        sql("INSERT INTO verificacion_tipo_equipo (k_id_verificacion, k_id_tipo_equipo, k_id_unidad_medida,"
                + " n_modalidad_verificacion) SELECT ?, ?, k_id_unidad_medida, 'patron_equipo_variable'"
                + " FROM unidad_medida WHERE n_simbolo_unidad = 'lpm'", frecuencia, tipo);
        simulador = UUID.randomUUID();
        manometro = UUID.randomUUID();
        sql("INSERT INTO equipo_patron (k_id_equipo_patron, n_nombre, k_serie, n_certificado,"
                + " f_vencimiento_certificado) VALUES (?, 'Simulador', 'P-1', 'C-1', ?), (?, 'Manometro', 'P-2',"
                + " 'C-2', ?)", simulador, LocalDate.of(2027, 1, 1), manometro, LocalDate.of(2027, 1, 1));
    }

    /** Un reporte con estos patrones por verificación, escritos en su documento como los escribe V59. */
    private void unReporte(boolean activo, Map<UUID, List<UUID>> patronesPorVerificacion) {
        StringBuilder verificaciones = new StringBuilder("[");
        patronesPorVerificacion.forEach((verificacion, patrones) -> {
            if (verificaciones.length() > 1) {
                verificaciones.append(',');
            }
            verificaciones.append("{\"id\": \"").append(verificacion).append("\", \"lecturas\": [], \"patrones\": [");
            for (int i = 0; i < patrones.size(); i++) {
                verificaciones.append(i == 0 ? "" : ",")
                        .append("{\"id\": \"").append(patrones.get(i)).append("\", \"desdeOrden\": false}");
            }
            verificaciones.append("]}");
        });
        verificaciones.append(']');

        sql("INSERT INTO reporte_servicio (k_id_reporte_servicio, k_id_orden_trabajo, k_id_equipo_cliente,"
                        + " b_estado_activo, t_contenido)"
                        + " VALUES (?, ?, ?, ?, jsonb_build_object('version', 1, 'pasos', '[]'::jsonb,"
                        + " 'accesorios', '[]'::jsonb, 'verificaciones', ?::jsonb))",
                UUID.randomUUID(), orden, unidad, activo, verificaciones.toString());
    }

    private List<Map<String, Object>> pares() {
        return enElEsquema.queryForList(
                "SELECT k_id_verificacion, k_id_equipo_patron FROM patron_verificacion"
                        + " ORDER BY k_id_verificacion::text, k_id_equipo_patron::text");
    }

    @Test
    @DisplayName("cada verificacion recibe, una vez, los patrones con que la midio el historial, retirados incluidos")
    void elRellenoSaleDelDocumento() {
        unEsquemaEnV59();
        // El simulador en las dos verificaciones, y el manometro en la de presion de un reporte retirado.
        unReporte(true, Map.of(presion, List.of(simulador), frecuencia, List.of(simulador)));
        unReporte(false, Map.of(presion, List.of(simulador, manometro)));

        migrarHasta("60");

        assertThat(pares()).hasSize(3)
                .contains(Map.of("k_id_verificacion", presion, "k_id_equipo_patron", simulador),
                        Map.of("k_id_verificacion", presion, "k_id_equipo_patron", manometro),
                        Map.of("k_id_verificacion", frecuencia, "k_id_equipo_patron", simulador));
        assertThat(enElEsquema.queryForObject("SELECT count(*) FROM patron_orden_trabajo", Integer.class)).isZero();
    }

    @Test
    @DisplayName("un patron del documento que no existe detiene la migracion en vez de dejar el par roto")
    void unPatronQueNoExisteLaDetiene() {
        unEsquemaEnV59();
        unReporte(true, Map.of(presion, List.of(UUID.randomUUID())));

        assertThatThrownBy(() -> migrarHasta("60")).hasStackTraceContaining("FK_patron_verificacion_patron");
    }

    @Test
    @DisplayName("las llaves no dejan repetir un patron en una verificacion, ni en el tipo de una orden")
    void lasLlaves() {
        unEsquemaEnV59();
        migrarHasta("60");

        sql("INSERT INTO patron_verificacion VALUES (?, ?)", presion, simulador);
        assertThatThrownBy(() -> sql("INSERT INTO patron_verificacion VALUES (?, ?)", presion, simulador))
                .hasMessageContaining("PK_patron_verificacion");

        sql("INSERT INTO patron_orden_trabajo VALUES (?, ?, ?)", orden, tipo, simulador);
        assertThatThrownBy(() -> sql("INSERT INTO patron_orden_trabajo VALUES (?, ?, ?)", orden, tipo, simulador))
                .hasMessageContaining("PK_patron_orden_trabajo");
        // El mismo patron para otro tipo de la misma orden si cabe: la orden reparte por tipo.
        UUID otroTipo = UUID.randomUUID();
        sql("INSERT INTO tipo_equipo (k_id_tipo_equipo, n_nombre_tipo_equipo, t_definicion_tecnica,"
                + " t_recomendaciones_cuidado, t_tecnologia_predominante, m_valor_unitario_mantenimiento)"
                + " VALUES (?, 'Oximetro', 'D', 'C', 'T', 1000)", otroTipo);
        sql("INSERT INTO patron_orden_trabajo VALUES (?, ?, ?)", orden, otroTipo, simulador);
    }

    @Test
    @DisplayName("una verificacion, un tipo o una orden que no existen no entran")
    void lasForaneas() {
        unEsquemaEnV59();
        migrarHasta("60");

        assertThatThrownBy(() -> sql("INSERT INTO patron_verificacion VALUES (?, ?)", UUID.randomUUID(), simulador))
                .hasMessageContaining("FK_patron_verificacion_verificacion");
        assertThatThrownBy(() -> sql("INSERT INTO patron_orden_trabajo VALUES (?, ?, ?)",
                        UUID.randomUUID(), tipo, simulador))
                .hasMessageContaining("FK_patron_orden_trabajo_orden");
        assertThatThrownBy(() -> sql("INSERT INTO patron_orden_trabajo VALUES (?, ?, ?)",
                        orden, UUID.randomUUID(), simulador))
                .hasMessageContaining("FK_patron_orden_trabajo_tipo");
    }
}
