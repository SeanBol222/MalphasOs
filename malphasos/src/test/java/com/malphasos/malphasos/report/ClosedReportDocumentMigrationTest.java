package com.malphasos.malphasos.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.TestcontainersConfiguration;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * Lo que {@code V51} hace además de rellenar los documentos (2026-10-28), que lo prueba
 * {@link ClosedReportDocumentTest}: <b>se detiene</b> si un reporte cerrado no tiene documento —sus copias no
 * tendrían dónde ir—, y deja a un borrador sin documento, con sus filas y sin las copias.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ClosedReportDocumentMigrationTest {

    private static final String ESQUEMA = "prueba_v51";

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbcTemplate;

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

    /** Un esquema en V50 con una orden en ejecución y un equipo en ella; devuelve (orden, equipo). */
    private UUID[] unaOrdenConSuEquipo() {
        migrarHasta("50");
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
        UUID marca = UUID.randomUUID();
        sql("INSERT INTO marca (k_id_marca, n_nombre_marca) VALUES (?, 'Kern')", marca);
        UUID equipo = UUID.randomUUID();
        sql("INSERT INTO equipo (k_id_equipo, k_id_tipo_equipo, k_id_marca) VALUES (?, ?, ?)", equipo, tipo, marca);
        UUID fabricante = UUID.randomUUID();
        sql("INSERT INTO fabricante (k_id_fabricante, n_nombre_fabricante) VALUES (?, 'Kern GmbH')", fabricante);
        UUID modelo = UUID.randomUUID();
        sql("INSERT INTO modelo (k_id_modelo, n_nombre_modelo, k_id_fabricante, k_id_equipo) VALUES (?, 'PCB', ?, ?)",
                modelo, fabricante, equipo);
        UUID unidad = UUID.randomUUID();
        sql("INSERT INTO equipo_cliente (k_id_equipo_cliente, k_serie, k_id_modelo, k_id_area_servicio)"
                + " VALUES (?, 'S-1', ?, ?)", unidad, modelo, area);
        UUID orden = UUID.randomUUID();
        sql("INSERT INTO orden_trabajo (k_id_orden_trabajo, k_id_sede, f_fecha_mantenimiento, n_periodicidad,"
                + " t_tipo_servicio, t_estado_ejecucion) VALUES (?, ?, ?, 'MENSUAL', 'PREVENTIVO', 'EN_EJECUCION')",
                orden, sede, LocalDate.of(2026, 9, 1));

        return new UUID[] {orden, unidad};
    }

    @Test
    @DisplayName("un reporte cerrado sin documento detiene la migracion, y dice cuantos y que hacer")
    void cerradoSinDocumentoLaDetiene() {
        UUID[] orden = unaOrdenConSuEquipo();
        sql("INSERT INTO reporte_servicio (k_id_reporte_servicio, k_id_orden_trabajo, k_id_equipo_cliente,"
                        + " t_estado_reporte, t_resultado, f_finalizado) VALUES (?, ?, ?, 'FINALIZADO', 'OPERATIVO', ?)",
                UUID.randomUUID(), orden[0], orden[1], LocalDateTime.now());

        assertThatThrownBy(() -> migrarHasta("51"))
                .hasStackTraceContaining("1 no lo tienen")
                .hasStackTraceContaining("Arranque una vez la versión anterior");
        // Y no quitó nada: las copias siguen donde estaban.
        assertThat(enElEsquema.queryForObject(
                        "SELECT count(*) FROM information_schema.columns WHERE table_schema = ?"
                                + " AND table_name = 'paso_reporte' AND column_name = 'n_nombre'",
                        Integer.class, ESQUEMA))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("con lecturas, la tolerancia del documento es la que se copio en ellas, no la que el tipo tiene hoy")
    void laToleranciaDeLasLecturas() {
        // Es la verdad de aquel día: la que juzgó cada lectura. Si el tipo cambió después, el documento no.
        UUID[] orden = unaOrdenConSuEquipo();
        UUID tipo = enElEsquema.queryForObject("SELECT k_id_tipo_equipo FROM tipo_equipo LIMIT 1", UUID.class);
        UUID verificacion = UUID.randomUUID();
        sql("INSERT INTO verificacion_tipo_equipo (k_id_verificacion, k_id_tipo_equipo, k_id_unidad_medida,"
                + " n_modalidad_verificacion, d_tolerancia) SELECT ?, ?, k_id_unidad_medida, 'patron_equipo_variable',"
                + " 0.7 FROM unidad_medida WHERE n_simbolo_unidad = 'mmHg'", verificacion, tipo);
        UUID reporte = UUID.randomUUID();
        sql("INSERT INTO reporte_servicio (k_id_reporte_servicio, k_id_orden_trabajo, k_id_equipo_cliente,"
                        + " t_estado_reporte, t_resultado, f_finalizado, t_documento)"
                        + " VALUES (?, ?, ?, 'FINALIZADO', 'OPERATIVO', ?, ?::jsonb)",
                reporte, orden[0], orden[1], LocalDateTime.now(),
                "{\"version\": 1, \"verificaciones\": [{\"id\": \"" + verificacion + "\", \"unidad\": \"mmHg\"}]}");
        sql("INSERT INTO dato_verificacion (k_id_dato_verificacion, k_id_reporte_servicio, k_id_verificacion,"
                + " i_secuencia, d_valor_patron, d_valor_equipo, n_unidad, d_tolerancia)"
                + " VALUES (?, ?, ?, 1, 100, 100.4, 'mmHg', 0.5)", UUID.randomUUID(), reporte, verificacion);

        migrarHasta("51");

        assertThat(enElEsquema.queryForObject(
                        "SELECT (t_documento -> 'verificaciones' -> 0 ->> 'tolerancia')::numeric FROM reporte_servicio"
                                + " WHERE k_id_reporte_servicio = ?",
                        java.math.BigDecimal.class, reporte))
                .isEqualByComparingTo("0.5");
    }

    @Test
    @DisplayName("un borrador sigue sin documento, con su paso y su lectura, y sin las copias")
    void unBorradorSeQuedaComoEsta() {
        UUID[] orden = unaOrdenConSuEquipo();
        UUID reporte = UUID.randomUUID();
        sql("INSERT INTO reporte_servicio (k_id_reporte_servicio, k_id_orden_trabajo, k_id_equipo_cliente)"
                + " VALUES (?, ?, ?)", reporte, orden[0], orden[1]);
        UUID protocolo = UUID.randomUUID();
        sql("INSERT INTO protocolo (k_id_protocolo, n_nombre, n_clave, t_descripcion) VALUES (?, 'Limpieza',"
                + " 'limpieza', 'Paño')", protocolo);
        sql("INSERT INTO paso_reporte (k_id_paso_reporte, k_id_reporte_servicio, i_orden, k_id_protocolo, n_nombre,"
                + " t_descripcion, t_estado_paso) VALUES (?, ?, 1, ?, 'Limpieza', 'Paño', 'HECHO')",
                UUID.randomUUID(), reporte, protocolo);

        migrarHasta("51");

        assertThat(enElEsquema.queryForObject(
                        "SELECT t_documento IS NULL FROM reporte_servicio WHERE k_id_reporte_servicio = ?",
                        Boolean.class, reporte))
                .isTrue();
        assertThat(enElEsquema.queryForObject(
                        "SELECT t_estado_paso FROM paso_reporte WHERE k_id_reporte_servicio = ? AND k_id_protocolo = ?",
                        String.class, reporte, protocolo))
                .isEqualTo("HECHO");
    }
}
