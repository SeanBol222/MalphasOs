package com.malphasos.malphasos.company;

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
 * Comprueba que `V43` lleva a su tabla los teléfonos que la empresa ya tenía en su arreglo (2026-10-27).
 *
 * <p>Migra un esquema aparte hasta `V42`, siembra una empresa con teléfonos, y aplica `V43`, como la
 * prueba de `V41`: las de esquema arrancan con la base ya en la última y no ven lo que una migración hace
 * con los datos que encuentra.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CompanyPhonesMigrationTest {

    private static final String ESQUEMA = "prueba_v43";

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
    @DisplayName("los teléfonos pasan a su tabla en su orden, sin los blancos, y el arreglo desaparece")
    void llevaLosTelefonos() {
        migrarHasta("42");
        UUID empresa = UUID.randomUUID();
        // El arreglo admitía blancos y espacios alrededor, que el dominio nunca guardó pero nada impedía.
        jdbcTemplate.update(
                "INSERT INTO " + ESQUEMA + ".empresa (k_id_empresa, n_nombre, t_telefonos)"
                        + " VALUES (?, 'Ejemplo', ARRAY[' 601 1 ', '', '601 2'])",
                empresa);

        migrarHasta("43");

        assertThat(jdbcTemplate.queryForList(
                        "SELECT i_orden, n_telefono_empresa FROM " + ESQUEMA
                                + ".telefono_empresa WHERE k_id_empresa = ? ORDER BY i_orden",
                        empresa))
                .containsExactly(
                        Map.of("i_orden", 0, "n_telefono_empresa", "601 1"),
                        Map.of("i_orden", 1, "n_telefono_empresa", "601 2"));
        assertThat(jdbcTemplate.queryForList(
                        "SELECT column_name FROM information_schema.columns WHERE table_schema = ?"
                                + " AND table_name = 'empresa' AND column_name = 't_telefonos'",
                        String.class, ESQUEMA))
                .isEqualTo(List.of());
    }
}
