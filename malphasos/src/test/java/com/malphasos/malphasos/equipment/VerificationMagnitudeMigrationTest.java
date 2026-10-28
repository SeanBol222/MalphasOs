package com.malphasos.malphasos.equipment;

import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.TestcontainersConfiguration;
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
 * Comprueba que `V48` quita la magnitud de la verificación sin perder qué mide (2026-10-28): después de
 * migrar, la magnitud de cada verificación sigue llegando por su unidad, y es la que tenía guardada.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class VerificationMagnitudeMigrationTest {

    private static final String ESQUEMA = "prueba_v48";

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
    @DisplayName("la verificacion pierde la columna y su magnitud sigue llegando por la unidad, la misma")
    void laMagnitudLlegaPorLaUnidad() {
        migrarHasta("47");
        UUID tipo = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO " + ESQUEMA + ".tipo_equipo (k_id_tipo_equipo, n_nombre_tipo_equipo,"
                + " t_definicion_tecnica, t_recomendaciones_cuidado, t_tecnologia_predominante,"
                + " m_valor_unitario_mantenimiento) VALUES (?, 'Termohigrometro', 'D', 'C', 'T', 1000)", tipo);
        // Dos magnitudes en el mismo tipo, y una de ellas con un simbolo que existe en dos magnitudes:
        // '%' es humedad relativa y concentracion, asi que solo la unidad dice cual era.
        UUID humedad = insertarVerificacion(tipo, "humedad_relativa", "%");
        UUID temperatura = insertarVerificacion(tipo, "temperatura", "°C");

        migrarHasta("48");

        assertThat(magnitudDe(humedad)).isEqualTo("humedad_relativa");
        assertThat(magnitudDe(temperatura)).isEqualTo("temperatura");
        assertThat(jdbcTemplate.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema = ?"
                        + " AND table_name = 'verificacion_tipo_equipo' AND column_name = 'k_id_magnitud'",
                        String.class, ESQUEMA))
                .isEmpty();
        assertThat(jdbcTemplate.queryForList("SELECT conname FROM pg_constraint c JOIN pg_namespace n"
                        + " ON n.oid = c.connamespace WHERE n.nspname = ? AND conname = 'UQ_unidad_medida_por_magnitud'",
                        String.class, ESQUEMA))
                .isEmpty();
    }

    private UUID insertarVerificacion(UUID tipo, String magnitud, String simbolo) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO " + ESQUEMA + ".verificacion_tipo_equipo (k_id_verificacion, k_id_tipo_equipo,"
                + " k_id_magnitud, k_id_unidad_medida, n_modalidad_verificacion, i_cantidad_datos)"
                + " SELECT ?, ?, m.k_id_magnitud, u.k_id_unidad_medida, 'patron_constante', 3"
                + " FROM " + ESQUEMA + ".magnitud m JOIN " + ESQUEMA + ".unidad_medida u USING (k_id_magnitud)"
                + " WHERE m.n_codigo_magnitud = ? AND u.n_simbolo_unidad = ?", id, tipo, magnitud, simbolo);
        return id;
    }

    private String magnitudDe(UUID verificacion) {
        return jdbcTemplate.queryForObject("SELECT m.n_codigo_magnitud FROM " + ESQUEMA + ".verificacion_tipo_equipo v"
                + " JOIN " + ESQUEMA + ".unidad_medida u USING (k_id_unidad_medida)"
                + " JOIN " + ESQUEMA + ".magnitud m USING (k_id_magnitud) WHERE v.k_id_verificacion = ?",
                String.class, verificacion);
    }
}
