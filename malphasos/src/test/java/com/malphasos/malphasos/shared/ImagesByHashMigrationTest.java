package com.malphasos.malphasos.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.shared.domain.image.DocumentImage;
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
 * Comprueba que `V45` lleva al almacén común el logo de la empresa y las firmas, por su huella
 * (2026-10-27).
 *
 * <p>Migra un esquema aparte hasta `V44`, siembra y aplica `V45`, como las de `V41`, `V43` y `V44`. Lo que
 * más importa es que <b>la huella que calcula la migración en SQL sea la misma que calcula
 * {@link DocumentImage} en Java</b>: si no, un logo que un informe ya congeló se guardaría dos veces, y
 * la empresa apuntaría a una imagen que el código nunca encontraría por su huella.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ImagesByHashMigrationTest {

    private static final String ESQUEMA = "prueba_v45";
    private static final byte[] LOGO = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1};
    private static final byte[] FIRMA = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 2};

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
    @DisplayName("el logo y la firma pasan al almacén por su huella, una vez cada uno, y sus bytes salen de sus tablas")
    void llevaLasImagenes() {
        migrarHasta("44");
        String huellaLogo = new DocumentImage(LOGO, "image/png").huella();
        String huellaFirma = new DocumentImage(FIRMA, "image/png").huella();
        // Un informe ya congeló este logo antes de la migración: no se puede repetir.
        jdbcTemplate.update("INSERT INTO " + ESQUEMA + ".imagen_documento (k_huella, b_contenido, t_tipo)"
                + " VALUES (?, ?, 'image/png')", huellaLogo, LOGO);
        jdbcTemplate.update("INSERT INTO " + ESQUEMA + ".empresa (k_id_empresa, n_nombre, b_logo, t_logo_tipo)"
                + " VALUES (?, 'Ejemplo', ?, 'image/png')", UUID.randomUUID(), LOGO);
        UUID persona = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO " + ESQUEMA + ".persona (k_identificador, k_cedula, n_primer_nombre,"
                + " n_primer_apellido, t_tipo_persona) VALUES (?, '1001', 'Ada', 'Lovelace', 'ENGINEER')", persona);
        jdbcTemplate.update("INSERT INTO " + ESQUEMA + ".firma_persona (k_identificador, b_imagen, f_actualizada)"
                + " VALUES (?, ?, now())", persona, FIRMA);

        migrarHasta("45");

        assertThat(jdbcTemplate.queryForObject("SELECT k_huella_logo FROM " + ESQUEMA + ".empresa", String.class))
                .isEqualTo(huellaLogo);
        assertThat(jdbcTemplate.queryForObject("SELECT k_huella FROM " + ESQUEMA + ".firma_persona", String.class))
                .isEqualTo(huellaFirma);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM " + ESQUEMA + ".imagen_documento",
                        Integer.class))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT b_contenido FROM " + ESQUEMA
                        + ".imagen_documento WHERE k_huella = ?", byte[].class, huellaFirma))
                .isEqualTo(FIRMA);
        assertThat(jdbcTemplate.queryForList("SELECT table_name || '.' || column_name FROM information_schema.columns"
                        + " WHERE table_schema = ? AND column_name IN ('b_logo', 't_logo_tipo', 'b_imagen')",
                        String.class, ESQUEMA))
                .isEmpty();
    }
}
