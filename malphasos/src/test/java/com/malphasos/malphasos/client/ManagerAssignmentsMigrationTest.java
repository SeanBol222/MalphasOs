package com.malphasos.malphasos.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.TestcontainersConfiguration;
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

/**
 * Comprueba que `V41` lleva a sus dos tablas los encargados que ya existían (2026-10-27).
 *
 * <p>Es la primera prueba de este proyecto que migra <b>hasta una versión</b>: las de esquema arrancan
 * con la base ya en la última, vacía, y no pueden ver lo que una migración hace con los datos que
 * encuentra. Antes eso se comprobaba a mano sobre la base de desarrollo —`V10`, `V13`, `V15`—, y la de
 * desarrollo no tiene hoy ningún encargado. Así que migra un esquema aparte hasta `V40`, siembra un
 * encargado de cada tipo, y aplica `V41`.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ManagerAssignmentsMigrationTest {

    private static final String ESQUEMA = "prueba_v41";

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
    @DisplayName("cada encargado pasa a la tabla de su tipo, con su estado, y encargado desaparece")
    void llevaLosDatos() {
        migrarHasta("40");

        UUID ciudad = jdbcTemplate.queryForObject(
                "SELECT k_id_ciudad FROM " + ESQUEMA + ".ciudad LIMIT 1", UUID.class);
        UUID cliente = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO " + ESQUEMA + ".cliente (k_id_cliente, k_documento, n_tipo_identificacion,"
                        + " n_razon_social, n_sigla) VALUES (?, '9001', 'NIT_juridico', 'Hospital', 'HOS')",
                cliente);
        UUID sede = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO " + ESQUEMA + ".sede (k_id_sede, n_nombre_sede, t_calle, t_carrera,"
                        + " t_numero, k_id_cliente, k_id_ciudad) VALUES (?, 'Norte', '1', '2', '3', ?, ?)",
                sede, cliente, ciudad);
        UUID area = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO " + ESQUEMA + ".area_servicio (k_id_area_servicio, n_nombre_area, k_id_sede)"
                        + " VALUES (?, 'UCI', ?)",
                area, sede);

        UUID deSede = persona("1001");
        UUID deArea = persona("1002");
        jdbcTemplate.update(
                "INSERT INTO " + ESQUEMA + ".encargado (k_identificador, t_tipo_encargado, k_id_sede)"
                        + " VALUES (?, 'HEADQUARTER', ?)",
                deSede, sede);
        // Inactivo: el relevo tiene que sobrevivir a la migración, no volver a la vida.
        jdbcTemplate.update(
                "INSERT INTO " + ESQUEMA + ".encargado (k_identificador, t_tipo_encargado,"
                        + " k_id_area_servicio, b_estado_activo) VALUES (?, 'SERVICE_AREA', ?, false)",
                deArea, area);

        migrarHasta("41");

        assertThat(jdbcTemplate.queryForList(
                        "SELECT k_identificador, k_id_sede, b_estado_activo FROM " + ESQUEMA
                                + ".encargado_sede"))
                .containsExactly(Map.of(
                        "k_identificador", deSede, "k_id_sede", sede, "b_estado_activo", true));
        assertThat(jdbcTemplate.queryForList(
                        "SELECT k_identificador, k_id_area_servicio, b_estado_activo FROM " + ESQUEMA
                                + ".encargado_area"))
                .containsExactly(Map.of(
                        "k_identificador", deArea, "k_id_area_servicio", area, "b_estado_activo", false));
        assertThat(jdbcTemplate.queryForList(
                        "SELECT table_name FROM information_schema.tables WHERE table_schema = ?"
                                + " AND table_name = 'encargado'",
                        String.class, ESQUEMA))
                .isEqualTo(List.of());
    }

    private UUID persona(String cedula) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO " + ESQUEMA + ".persona (k_identificador, k_cedula, n_primer_nombre,"
                        + " n_primer_apellido, t_tipo_persona) VALUES (?, ?, 'Ada', 'Lovelace', 'MANAGER')",
                id, cedula);

        return id;
    }
}
