package com.malphasos.malphasos.workorder;

import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.TestcontainersConfiguration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
 * Comprueba lo que {@code V50} rellena al quitar la tabla puente (2026-10-28), con un caso por cada regla:
 * un borrador para cada equipo del puente sin reporte —con los pasos del protocolo si la orden es
 * preventiva—, ninguno en una orden ejecutada, uno también en una cancelada, el borrador de un equipo que
 * ya se había sacado de la orden retirado, y el cerrado de uno sacado, conservado.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class WorkOrderWithoutBridgeMigrationTest {

    private static final String ESQUEMA = "prueba_v50";

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbcTemplate;

    /** Una conexión con el esquema en su {@code search_path}: ver {@link WorkOrderFrozenDocumentMigrationTest}. */
    private SingleConnectionDataSource conexion;
    private JdbcTemplate enElEsquema;

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

    private UUID orden(UUID sede, String servicio, String estado, boolean activa) {
        UUID id = UUID.randomUUID();
        sql("INSERT INTO orden_trabajo (k_id_orden_trabajo, k_id_sede, f_fecha_mantenimiento, n_periodicidad,"
                        + " t_tipo_servicio, t_estado_ejecucion, b_estado_activo, t_documento)"
                        + " VALUES (?, ?, ?, 'MENSUAL', ?, ?, ?, ?::jsonb)",
                id, sede, LocalDate.of(2026, 9, 1), servicio, estado, activa, "EJECUTADA".equals(estado) ? "{}" : null);
        return id;
    }

    private void enElPuente(UUID orden, UUID equipo, UUID area, boolean activo) {
        sql("INSERT INTO orden_trabajo_equipo (k_id_orden_trabajo, k_id_equipo_cliente, k_id_area_servicio,"
                + " b_estado_activo) VALUES (?, ?, ?, ?)", orden, equipo, area, activo);
    }

    private void unReporte(UUID orden, UUID equipo, boolean cerrado) {
        sql("INSERT INTO reporte_servicio (k_id_reporte_servicio, k_id_orden_trabajo, k_id_equipo_cliente,"
                        + " t_estado_reporte, t_resultado, f_finalizado) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), orden, equipo, cerrado ? "FINALIZADO" : "BORRADOR",
                cerrado ? "OPERATIVO" : null, cerrado ? LocalDateTime.now() : null);
    }

    /** Los reportes activos de un equipo en una orden, por estado. */
    private List<String> activos(UUID orden, UUID equipo) {
        return enElEsquema.queryForList(
                "SELECT t_estado_reporte FROM reporte_servicio WHERE k_id_orden_trabajo = ? AND k_id_equipo_cliente = ?"
                        + " AND b_estado_activo",
                String.class, orden, equipo);
    }

    private List<Integer> pasos(UUID orden, UUID equipo) {
        return enElEsquema.queryForList(
                """
                SELECT p.i_orden FROM paso_reporte p JOIN reporte_servicio r USING (k_id_reporte_servicio)
                WHERE r.k_id_orden_trabajo = ? AND r.k_id_equipo_cliente = ? AND r.b_estado_activo
                ORDER BY p.i_orden
                """,
                Integer.class, orden, equipo);
    }

    @Test
    @DisplayName("cada equipo del puente queda con su reporte, y el puente desaparece con sus foraneas")
    void rellenaYQuitaElPuente() {
        migrarHasta("49");
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
        UUID tipo = UUID.randomUUID();
        sql("INSERT INTO tipo_equipo (k_id_tipo_equipo, n_nombre_tipo_equipo, t_definicion_tecnica,"
                + " t_recomendaciones_cuidado, t_tecnologia_predominante, m_valor_unitario_mantenimiento)"
                + " VALUES (?, 'Balanza', 'D', 'C', 'T', 1000)", tipo);
        // Dos protocolos activos y uno retirado del tipo: el retirado no se copia.
        for (int prioridad : new int[] {3, 1, 2}) {
            UUID protocolo = UUID.randomUUID();
            sql("INSERT INTO protocolo (k_id_protocolo, n_nombre, n_clave, t_descripcion) VALUES (?, ?, ?, 'Hacerlo')",
                    protocolo, "Paso " + prioridad, "paso-" + prioridad);
            sql("INSERT INTO protocolo_tipo_equipo (k_id_tipo_equipo, k_id_protocolo, i_prioridad, t_indicacion,"
                    + " b_estado_activo) VALUES (?, ?, ?, ?, ?)", tipo, protocolo, prioridad,
                    prioridad == 1 ? "Con guantes" : null, prioridad != 2);
        }
        UUID marca = UUID.randomUUID();
        sql("INSERT INTO marca (k_id_marca, n_nombre_marca) VALUES (?, 'Kern')", marca);
        UUID equipo = UUID.randomUUID();
        sql("INSERT INTO equipo (k_id_equipo, k_id_tipo_equipo, k_id_marca) VALUES (?, ?, ?)", equipo, tipo, marca);
        UUID fabricante = UUID.randomUUID();
        sql("INSERT INTO fabricante (k_id_fabricante, n_nombre_fabricante) VALUES (?, 'Kern GmbH')", fabricante);
        UUID modelo = UUID.randomUUID();
        sql("INSERT INTO modelo (k_id_modelo, n_nombre_modelo, k_id_fabricante, k_id_equipo) VALUES (?, 'PCB', ?, ?)",
                modelo, fabricante, equipo);
        UUID[] u = new UUID[7];
        for (int i = 0; i < u.length; i++) {
            u[i] = UUID.randomUUID();
            sql("INSERT INTO equipo_cliente (k_id_equipo_cliente, k_serie, k_id_modelo, k_id_area_servicio)"
                    + " VALUES (?, ?, ?, ?)", u[i], "S-" + i, modelo, area);
        }

        UUID creada = orden(sede, "PREVENTIVO", "CREADA", true);
        enElPuente(creada, u[0], area, true);
        UUID enEjecucion = orden(sede, "CORRECTIVO", "EN_EJECUCION", true);
        enElPuente(enEjecucion, u[1], area, true);
        enElPuente(enEjecucion, u[2], area, true);
        unReporte(enEjecucion, u[2], false);
        enElPuente(enEjecucion, u[3], area, false);
        unReporte(enEjecucion, u[3], false);
        enElPuente(enEjecucion, u[4], area, false);
        unReporte(enEjecucion, u[4], true);
        UUID ejecutada = orden(sede, "PREVENTIVO", "EJECUTADA", true);
        enElPuente(ejecutada, u[5], area, true);
        UUID cancelada = orden(sede, "PREVENTIVO", "CREADA", false);
        enElPuente(cancelada, u[6], area, true);

        migrarHasta("50");

        // 1. El que estaba sin reporte recibe su borrador, con los pasos activos del tipo por prioridad.
        assertThat(activos(creada, u[0])).containsExactly("BORRADOR");
        assertThat(pasos(creada, u[0])).containsExactly(1, 3);
        assertThat(enElEsquema.queryForObject(
                        """
                        SELECT p.t_indicacion FROM paso_reporte p JOIN reporte_servicio r USING (k_id_reporte_servicio)
                        WHERE r.k_id_orden_trabajo = ? AND p.i_orden = 1
                        """,
                        String.class, creada))
                .isEqualTo("Con guantes");
        // En un correctivo, sin pasos; y el que ya tenia borrador no recibe otro.
        assertThat(activos(enEjecucion, u[1])).containsExactly("BORRADOR");
        assertThat(pasos(enEjecucion, u[1])).isEmpty();
        assertThat(activos(enEjecucion, u[2])).containsExactly("BORRADOR");
        // 2. Sacado de la orden: su borrador se retira, su cerrado se queda.
        assertThat(activos(enEjecucion, u[3])).isEmpty();
        assertThat(activos(enEjecucion, u[4])).containsExactly("FINALIZADO");
        // Una ejecutada no recibe borradores; una cancelada si, para seguir diciendo que equipos tenia.
        assertThat(activos(ejecutada, u[5])).isEmpty();
        assertThat(activos(cancelada, u[6])).containsExactly("BORRADOR");

        assertThat(enElEsquema.queryForObject(
                        "SELECT count(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = 'orden_trabajo_equipo'",
                        Integer.class, ESQUEMA))
                .isZero();
        assertThat(enElEsquema.queryForList(
                        """
                        SELECT c.conname FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                        WHERE n.nspname = ? AND c.conrelid = (? || '.reporte_servicio')::regclass AND c.contype = 'f'
                          AND c.conname IN ('FK_reporte_servicio_orden', 'FK_reporte_servicio_equipo_cliente',
                                            'FK_reporte_servicio_equipo_de_la_orden')
                        ORDER BY c.conname
                        """,
                        String.class, ESQUEMA, ESQUEMA))
                .containsExactly("FK_reporte_servicio_equipo_cliente", "FK_reporte_servicio_orden");
    }
}
