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

/**
 * Comprueba que `V47` quita el cliente de la orden sin perder de quién es (2026-10-28): después de
 * migrar, el cliente de cada orden sigue llegando por su sede, y es el mismo que tenía guardado.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class WorkOrderClientMigrationTest {

    private static final String ESQUEMA = "prueba_v47";

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void borrarEsquema() {
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

    @Test
    @DisplayName("la orden pierde la columna y su cliente sigue llegando por la sede, el mismo que tenía")
    void elClienteLlegaPorLaSede() {
        migrarHasta("46");
        UUID ciudad = jdbcTemplate.queryForObject("SELECT k_id_ciudad FROM " + ESQUEMA + ".ciudad LIMIT 1", UUID.class);
        UUID cliente = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO " + ESQUEMA + ".cliente (k_id_cliente, k_documento, n_tipo_identificacion,"
                + " n_razon_social, n_sigla) VALUES (?, '9001', 'NIT_juridico', 'Hospital', 'HOS')", cliente);
        UUID sede = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO " + ESQUEMA + ".sede (k_id_sede, n_nombre_sede, t_calle, t_carrera,"
                + " t_numero, k_id_cliente, k_id_ciudad) VALUES (?, 'Norte', '1', '2', '3', ?, ?)", sede, cliente, ciudad);
        UUID orden = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO " + ESQUEMA + ".orden_trabajo (k_id_orden_trabajo, k_id_cliente, k_id_sede,"
                + " f_fecha_mantenimiento, n_periodicidad, t_tipo_servicio) VALUES (?, ?, ?, ?, 'MENSUAL', 'PREVENTIVO')",
                orden, cliente, sede, LocalDate.now());

        migrarHasta("47");

        assertThat(jdbcTemplate.queryForObject("SELECT s.k_id_cliente FROM " + ESQUEMA + ".orden_trabajo o JOIN "
                + ESQUEMA + ".sede s USING (k_id_sede) WHERE o.k_id_orden_trabajo = ?", UUID.class, orden))
                .isEqualTo(cliente);
        assertThat(jdbcTemplate.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema = ?"
                        + " AND table_name = 'orden_trabajo' AND column_name = 'k_id_cliente'", String.class, ESQUEMA))
                .isEmpty();
        assertThat(jdbcTemplate.queryForList("SELECT conname FROM pg_constraint c JOIN pg_namespace n"
                        + " ON n.oid = c.connamespace WHERE n.nspname = ? AND conname = 'UQ_sede_identidad_con_cliente'",
                        String.class, ESQUEMA))
                .isEmpty();
    }
}
