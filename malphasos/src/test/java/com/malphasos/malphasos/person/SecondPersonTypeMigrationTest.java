package com.malphasos.malphasos.person;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.TestcontainersConfiguration;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Comprueba que `V46` quita el segundo tipo de persona, y que se detiene si alguien que no es encargado
 * ni representante tiene una sede o un área a cargo (2026-10-27).
 *
 * <p>Migra un esquema aparte hasta `V45`, siembra y aplica `V46`, como las demás pruebas de la
 * refactorización del esquema.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SecondPersonTypeMigrationTest {

    private static final String ESQUEMA = "prueba_v46";

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

    private UUID persona(String cedula, String tipo, String segundoTipo) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO " + ESQUEMA + ".persona (k_identificador, k_cedula, n_primer_nombre,"
                        + " n_primer_apellido, t_tipo_persona, t_segundo_tipo_persona)"
                        + " VALUES (?, ?, 'Ada', 'Lovelace', ?, ?)",
                id, cedula, tipo, segundoTipo);

        return id;
    }

    private void aCargoDeUnaSede(UUID persona) {
        UUID ciudad = jdbcTemplate.queryForObject(
                "SELECT k_id_ciudad FROM " + ESQUEMA + ".ciudad LIMIT 1", UUID.class);
        UUID cliente = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO " + ESQUEMA + ".cliente (k_id_cliente, k_documento, n_tipo_identificacion,"
                + " n_razon_social, n_sigla) VALUES (?, '9001', 'NIT_juridico', 'Hospital', 'HOS')", cliente);
        UUID sede = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO " + ESQUEMA + ".sede (k_id_sede, n_nombre_sede, t_calle, t_carrera,"
                + " t_numero, k_id_cliente, k_id_ciudad) VALUES (?, 'Norte', '1', '2', '3', ?, ?)", sede, cliente, ciudad);
        jdbcTemplate.update("INSERT INTO " + ESQUEMA + ".encargado_sede (k_identificador, k_id_sede) VALUES (?, ?)",
                persona, sede);
    }

    @Test
    @DisplayName("quita la columna, y un representante marcado como encargado sigue siendo representante")
    void quitaLaColumna() {
        migrarHasta("45");
        UUID representante = persona("1001", "CEO_CLIENT", "MANAGER");
        aCargoDeUnaSede(persona("1002", "MANAGER", null));

        migrarHasta("46");

        assertThat(jdbcTemplate.queryForObject("SELECT t_tipo_persona FROM " + ESQUEMA
                + ".persona WHERE k_identificador = ?", String.class, representante)).isEqualTo("CEO_CLIENT");
        assertThat(jdbcTemplate.queryForList("SELECT column_name FROM information_schema.columns"
                        + " WHERE table_schema = ? AND table_name = 'persona' AND column_name = 't_segundo_tipo_persona'",
                        String.class, ESQUEMA))
                .isEmpty();
    }

    @Test
    @DisplayName("un ingeniero a cargo de una sede tumba la migración diciendo cuántos, en vez de relevarlo en silencio")
    void ingenieroACargo() {
        migrarHasta("45");
        aCargoDeUnaSede(persona("1003", "ENGINEER", "MANAGER"));

        assertThatThrownBy(() -> migrarHasta("46"))
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("1 personas que no son encargados ni representantes");
    }
}
