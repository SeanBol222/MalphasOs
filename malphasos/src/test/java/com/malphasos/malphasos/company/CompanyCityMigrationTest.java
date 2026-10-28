package com.malphasos.malphasos.company;

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
 * Comprueba que `V44` enlaza con el catálogo la ciudad que la empresa tenía escrita (2026-10-27), y que
 * falla diciéndolo cuando no puede.
 *
 * <p>Migra un esquema aparte hasta `V43`, siembra la empresa, y aplica `V44`, como las pruebas de `V41` y
 * `V43`. Bogotá está en el catálogo que `V7` siembra, y por eso sirve de ejemplo sin inventar nada.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CompanyCityMigrationTest {

    private static final String ESQUEMA = "prueba_v44";

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

    private UUID empresaEn(String ciudad) {
        migrarHasta("43");
        UUID empresa = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO " + ESQUEMA + ".empresa (k_id_empresa, n_nombre, n_ciudad) VALUES (?, 'Ejemplo', ?)",
                empresa, ciudad);

        return empresa;
    }

    private UUID ciudadDe(UUID empresa) {
        return jdbcTemplate.queryForObject(
                "SELECT k_id_ciudad FROM " + ESQUEMA + ".empresa WHERE k_id_empresa = ?", UUID.class, empresa);
    }

    @Test
    @DisplayName("la escrita se enlaza con la del catálogo sin mayúsculas, tildes ni espacios alrededor")
    void enlazaPorNombre() {
        // Con tilde, en mayúsculas y con espacios: cada normalización de la migración tiene que hacer falta.
        UUID empresa = empresaEn("  BOGOTÁ ");

        migrarHasta("44");

        assertThat(ciudadDe(empresa)).isEqualTo(jdbcTemplate.queryForObject(
                "SELECT k_id_ciudad FROM " + ESQUEMA + ".ciudad WHERE n_nombre_ciudad = 'Bogotá'", UUID.class));
        assertThat(jdbcTemplate.queryForList(
                        "SELECT column_name FROM information_schema.columns WHERE table_schema = ?"
                                + " AND table_name = 'empresa' AND column_name = 'n_ciudad'",
                        String.class, ESQUEMA))
                .isEmpty();
    }

    @Test
    @DisplayName("sin ciudad escrita queda sin ciudad")
    void sinCiudad() {
        UUID empresa = empresaEn(null);

        migrarHasta("44");

        assertThat(ciudadDe(empresa)).isNull();
    }

    @Test
    @DisplayName("una que no está en el catálogo tumba la migración diciendo cuál, en vez de perderla")
    void noCasa() {
        empresaEn("Ciudad de Ejemplo");

        assertThatThrownBy(() -> migrarHasta("44"))
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("Ciudad de Ejemplo")
                .hasMessageContaining("casa con 0");
    }
}
