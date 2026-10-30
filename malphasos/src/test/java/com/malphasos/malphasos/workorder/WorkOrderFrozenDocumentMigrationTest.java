package com.malphasos.malphasos.workorder;

import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.TestcontainersConfiguration;
import java.time.LocalDate;
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
 * Comprueba que `V49` congela las órdenes ya ejecutadas al aplicarse (2026-10-28): con el documento
 * marcado como tardío, sin fecha de ejecución, y con el área que la orden guardó al seleccionar cada equipo
 * —no la de hoy, si el equipo se trasladó después—. Las demás quedan sin documento. Que el contenido sea el
 * mismo que compone Java lo dice {@link WorkOrderFrozenDocumentTest}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class WorkOrderFrozenDocumentMigrationTest {

    private static final String ESQUEMA = "prueba_v49";

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbcTemplate;

    /**
     * Una conexión propia, con el esquema de prueba en su {@code search_path}: los triggers de {@code V19} y
     * {@code V20} buscan sus tablas sin esquema, y con la del pool encontrarían las de la base principal.
     */
    private SingleConnectionDataSource conexion;
    private JdbcTemplate enElEsquema;

    @AfterEach
    void borrarEsquema() {
        if (conexion != null) {
            // Vuelve al pool: sin esto, la siguiente prueba que la reciba buscaria sus tablas en un esquema
            // que ya no existe.
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
        enElEsquema.update(sentencia.replace("{e}", ""), argumentos);
    }

    private <T> T uno(String consulta, Class<T> tipo, Object... argumentos) {
        return enElEsquema.queryForObject(consulta.replace("{e}", ""), tipo, argumentos);
    }

    private UUID orden(UUID sede, String estado) {
        UUID id = UUID.randomUUID();
        sql("INSERT INTO {e}orden_trabajo (k_id_orden_trabajo, k_id_sede, f_fecha_mantenimiento, n_periodicidad,"
                + " t_tipo_servicio, t_estado_ejecucion) VALUES (?, ?, ?, 'MENSUAL', 'PREVENTIVO', ?)",
                id, sede, LocalDate.of(2026, 9, 1), estado);
        return id;
    }

    @Test
    @DisplayName("la ejecutada se congela tarde con el area que guardo; las demas quedan sin documento")
    void congelaLasEjecutadas() {
        migrarHasta("48");
        conexion = new SingleConnectionDataSource(DataSourceUtils.getConnection(dataSource), true);
        enElEsquema = new JdbcTemplate(conexion);
        enElEsquema.execute("SET search_path TO " + ESQUEMA);
        UUID ciudad = uno("SELECT k_id_ciudad FROM {e}ciudad LIMIT 1", UUID.class);
        UUID cliente = UUID.randomUUID();
        sql("INSERT INTO {e}cliente (k_id_cliente, k_documento, n_tipo_identificacion, n_razon_social, n_sigla)"
                + " VALUES (?, '9001', 'NIT_juridico', 'Hospital', 'HOS')", cliente);
        UUID sede = UUID.randomUUID();
        sql("INSERT INTO {e}sede (k_id_sede, n_nombre_sede, t_calle, t_carrera, t_numero, k_id_cliente, k_id_ciudad)"
                + " VALUES (?, 'Norte', '1', '2', '3', ?, ?)", sede, cliente, ciudad);
        UUID dondeSeAtendio = UUID.randomUUID();
        UUID dondeEstaHoy = UUID.randomUUID();
        sql("INSERT INTO {e}area_servicio (k_id_area_servicio, n_nombre_area, k_id_sede) VALUES (?, 'Urgencias', ?)",
                dondeSeAtendio, sede);
        sql("INSERT INTO {e}area_servicio (k_id_area_servicio, n_nombre_area, k_id_sede) VALUES (?, 'Bodega', ?)",
                dondeEstaHoy, sede);
        UUID tipo = UUID.randomUUID();
        sql("INSERT INTO {e}tipo_equipo (k_id_tipo_equipo, n_nombre_tipo_equipo, t_definicion_tecnica,"
                + " t_recomendaciones_cuidado, t_tecnologia_predominante, m_valor_unitario_mantenimiento)"
                + " VALUES (?, 'Balanza', 'D', 'C', 'T', 1000)", tipo);
        UUID marca = UUID.randomUUID();
        sql("INSERT INTO {e}marca (k_id_marca, n_nombre_marca) VALUES (?, 'Kern')", marca);
        UUID equipo = UUID.randomUUID();
        sql("INSERT INTO {e}equipo (k_id_equipo, k_id_tipo_equipo, k_id_marca) VALUES (?, ?, ?)", equipo, tipo, marca);
        UUID fabricante = UUID.randomUUID();
        sql("INSERT INTO {e}fabricante (k_id_fabricante, n_nombre_fabricante) VALUES (?, 'Kern GmbH')", fabricante);
        UUID modelo = UUID.randomUUID();
        sql("INSERT INTO {e}modelo (k_id_modelo, n_nombre_modelo, k_id_fabricante, k_id_equipo) VALUES (?, 'PCB', ?, ?)",
                modelo, fabricante, equipo);
        // Hoy esta en la bodega: se traslado despues de la orden.
        UUID unidad = UUID.randomUUID();
        sql("INSERT INTO {e}equipo_cliente (k_id_equipo_cliente, k_serie, k_id_modelo, k_id_area_servicio)"
                + " VALUES (?, 'S-1', ?, ?)", unidad, modelo, dondeEstaHoy);

        UUID ejecutada = orden(sede, "EJECUTADA");
        sql("INSERT INTO {e}orden_trabajo_equipo (k_id_orden_trabajo, k_id_equipo_cliente, k_id_area_servicio)"
                + " VALUES (?, ?, ?)", ejecutada, unidad, dondeSeAtendio);
        UUID vacia = orden(sede, "EJECUTADA");
        UUID enEjecucion = orden(sede, "EN_EJECUCION");

        migrarHasta("49");

        assertThat(uno("SELECT b_documento_tardio FROM {e}orden_trabajo WHERE k_id_orden_trabajo = ?",
                Boolean.class, ejecutada)).isTrue();
        assertThat(uno("SELECT t_documento #>> '{equipos,0,area}' FROM {e}orden_trabajo WHERE k_id_orden_trabajo = ?",
                String.class, ejecutada)).isEqualTo("Urgencias");
        assertThat(uno("SELECT t_documento #>> '{equipos,0,serie}' FROM {e}orden_trabajo WHERE k_id_orden_trabajo = ?",
                String.class, ejecutada)).isEqualTo("S-1");
        assertThat(uno("SELECT t_documento -> 'fechaEjecucion' = 'null'::jsonb FROM {e}orden_trabajo"
                + " WHERE k_id_orden_trabajo = ?", Boolean.class, ejecutada)).isTrue();
        assertThat(uno("SELECT t_documento #>> '{cliente,nombre}' FROM {e}orden_trabajo WHERE k_id_orden_trabajo = ?",
                String.class, ejecutada)).isEqualTo("Hospital");
        assertThat(uno("SELECT t_documento -> 'equipos' FROM {e}orden_trabajo WHERE k_id_orden_trabajo = ?",
                String.class, vacia)).isEqualTo("[]");
        assertThat(uno("SELECT t_documento IS NULL AND NOT b_documento_tardio FROM {e}orden_trabajo"
                + " WHERE k_id_orden_trabajo = ?", Boolean.class, enEjecucion)).isTrue();
    }
}
