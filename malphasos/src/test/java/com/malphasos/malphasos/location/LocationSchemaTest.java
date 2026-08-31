package com.malphasos.malphasos.location;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

/**
 * Verifica que la migración V3 crea el esquema de ubicaciones y que sus restricciones rechazan de
 * verdad los datos inválidos.
 *
 * <p>Contra un PostgreSQL real: una restricción solo se puede comprobar en el motor que la aplica.
 * Ninguna de estas reglas existía en el esquema original, así que las pruebas fijan lo que se
 * decidió añadir.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(
        statements = {"DELETE FROM ciudad", "DELETE FROM pais"},
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class LocationSchemaTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** El codigo ISO es unico: cada prueba necesita el suyo. */
    private String uniqueIsoCode() {
        String letras = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        long n = System.nanoTime();

        return "" + letras.charAt((int) (n % 26))
                + letras.charAt((int) ((n / 26) % 26))
                + letras.charAt((int) ((n / 676) % 26));
    }

    private UUID insertCountry(String isoCode, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO pais (k_id_pais, k_codigo_iso, n_nombre_pais) VALUES (?, ?, ?)",
                id, isoCode, name);

        return id;
    }

    private void insertCity(UUID countryId, String name) {
        jdbcTemplate.update(
                "INSERT INTO ciudad (k_id_ciudad, n_nombre_ciudad, k_id_pais) VALUES (?, ?, ?)",
                UUID.randomUUID(), name, countryId);
    }

    @Test
    @DisplayName("las dos tablas del modulo existen tras la migracion")
    void migracionCreaLasTablas() {
        Integer tablas = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name IN ('pais', 'ciudad')
                """,
                Integer.class);

        assertThat(tablas).isEqualTo(2);
    }

    @Test
    @DisplayName("un pais con codigo ISO y nombre validos se inserta")
    void paisValido() {
        assertThatCode(() -> insertCountry(uniqueIsoCode(), "Pais " + System.nanoTime()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("el codigo ISO no se puede repetir")
    void codigoIsoUnico() {
        String iso = uniqueIsoCode();
        insertCountry(iso, "Primero " + System.nanoTime());

        assertThatThrownBy(() -> insertCountry(iso, "Segundo " + System.nanoTime()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("el nombre del pais tampoco se puede repetir")
    void nombreDePaisUnico() {
        String nombre = "Duplicado " + System.nanoTime();
        insertCountry(uniqueIsoCode(), nombre);

        assertThatThrownBy(() -> insertCountry(uniqueIsoCode(), nombre))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("el codigo ISO exige tres letras mayusculas")
    void formatoDelCodigoIso() {
        assertThatThrownBy(() -> insertCountry("co1", "Minusculas " + System.nanoTime()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertCountry("c o", "Con espacio " + System.nanoTime()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("una ciudad necesita un pais que exista")
    void ciudadExigePaisExistente() {
        assertThatThrownBy(() -> insertCity(UUID.randomUUID(), "Huerfana"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("dos ciudades del mismo pais no pueden llamarse igual")
    void nombreDeCiudadUnicoEnSuPais() {
        UUID pais = insertCountry(uniqueIsoCode(), "Pais " + System.nanoTime());
        insertCity(pais, "Cordoba");

        assertThatThrownBy(() -> insertCity(pais, "Cordoba"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("el mismo nombre de ciudad si vale en dos paises distintos")
    void mismoNombreEnPaisesDistintos() {
        UUID espana = insertCountry(uniqueIsoCode(), "Espana " + System.nanoTime());
        UUID argentina = insertCountry(uniqueIsoCode(), "Argentina " + System.nanoTime());

        insertCity(espana, "Cordoba");

        assertThatCode(() -> insertCity(argentina, "Cordoba")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("las dos tablas nacen activas: el borrado es logico")
    void bornActive() {
        UUID pais = insertCountry(uniqueIsoCode(), "Pais " + System.nanoTime());
        insertCity(pais, "Capital");

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT b_estado_activo FROM pais WHERE k_id_pais = ?", Boolean.class, pais))
                .isTrue();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM ciudad WHERE k_id_pais = ? AND b_estado_activo", Integer.class, pais))
                .isEqualTo(1);
    }
}
