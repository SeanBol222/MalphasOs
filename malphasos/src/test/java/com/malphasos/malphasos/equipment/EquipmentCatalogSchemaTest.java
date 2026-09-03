package com.malphasos.malphasos.equipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.TestcontainersConfiguration;
import java.math.BigDecimal;
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
 * Verifica que la migración V5 crea el catálogo de equipos y que sus restricciones rechazan los
 * datos inválidos. Casi ninguna existía en el esquema original.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(
        statements = {
            "DELETE FROM equipo_cliente",
            "DELETE FROM modelo",
            "DELETE FROM equipo",
            "DELETE FROM marca",
            "DELETE FROM tipo_equipo",
            "DELETE FROM fabricante"
        },
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class EquipmentCatalogSchemaTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    private String unico() {
        return String.valueOf(System.nanoTime());
    }

    private UUID insertBrand() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO marca (k_id_marca, n_nombre_marca) VALUES (?, ?)", id, "Marca " + unico());

        return id;
    }

    private UUID insertType(boolean verificable, String modalidad, BigDecimal amperaje) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO tipo_equipo (k_id_tipo_equipo, n_nombre_tipo_equipo, t_definicion_tecnica,
                                         t_recomendaciones_cuidado, t_tecnologia_predominante,
                                         d_amperaje, b_verificable, n_tipo_verificacion,
                                         m_valor_unitario_mantenimiento)
                VALUES (?, ?, 'Definicion', 'Cuidados', 'Electronica', ?, ?, ?, 150000)
                """,
                id, "Tipo " + unico(), amperaje, verificable, modalidad);

        return id;
    }

    private UUID insertEquipment(UUID tipo, UUID marca) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO equipo (k_id_equipo, k_id_tipo_equipo, k_id_marca) VALUES (?, ?, ?)",
                id, tipo, marca);

        return id;
    }

    @Test
    @DisplayName("las seis tablas del catalogo existen tras la migracion")
    void migracionCreaLasTablas() {
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND table_name IN ('fabricante', 'marca', 'tipo_equipo', 'equipo',
                                             'modelo', 'equipo_cliente')
                        """,
                        Integer.class))
                .isEqualTo(6);
    }

    @Test
    @DisplayName("una marca no puede quedarse sin nombre")
    void marcaSinNombre() {
        // En el original la columna era anulable, y el nombre es lo unico que una marca tiene.
        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO marca (k_id_marca, n_nombre_marca) VALUES (?, NULL)",
                        UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("el amperaje admite decimales y valores por encima de 99")
    void amperajeConDecimales() {
        // El original lo declaraba numeric(2): maximo 99 y sin decimales, de modo que 2.5 A se
        // redondeaba a 3.
        UUID tipo = insertType(false, null, new BigDecimal("2.50"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT d_amperaje FROM tipo_equipo WHERE k_id_tipo_equipo = ?",
                        BigDecimal.class, tipo))
                .isEqualByComparingTo("2.50");

        assertThatCode(() -> insertType(false, null, new BigDecimal("120.75")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("un tipo verificable exige decir como se verifica")
    void verificableExigeModalidad() {
        assertThatCode(() -> insertType(true, "patron_constante", null)).doesNotThrowAnyException();

        // El original dejaba las dos columnas sueltas.
        assertThatThrownBy(() -> insertType(true, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("un tipo no verificable no puede traer modalidad")
    void noVerificableSinModalidad() {
        assertThatThrownBy(() -> insertType(false, "patron_constante", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("la modalidad de verificacion solo admite los tres valores del catalogo")
    void modalidadDelCatalogo() {
        assertThatThrownBy(() -> insertType(true, "a_ojo", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("el valor del mantenimiento no puede ser negativo")
    void valorNoNegativo() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        INSERT INTO tipo_equipo (k_id_tipo_equipo, n_nombre_tipo_equipo,
                                                 t_definicion_tecnica, t_recomendaciones_cuidado,
                                                 t_tecnologia_predominante,
                                                 m_valor_unitario_mantenimiento)
                        VALUES (?, ?, 'D', 'C', 'E', -1)
                        """,
                        UUID.randomUUID(), "Tipo " + unico()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("la misma marca no fabrica dos veces el mismo tipo de equipo")
    void asociacionUnica() {
        UUID tipo = insertType(false, null, null);
        UUID marca = insertBrand();
        insertEquipment(tipo, marca);

        // equipo es una asociacion: repetir el par no significa nada.
        assertThatThrownBy(() -> insertEquipment(tipo, marca))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("un modelo necesita fabricante y equipo")
    void modeloNecesitaSusReferencias() {
        UUID equipo = insertEquipment(insertType(false, null, null), insertBrand());

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO modelo (k_id_modelo, k_id_fabricante, k_id_equipo) VALUES (?, NULL, ?)",
                        UUID.randomUUID(), equipo))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("el registro INVIMA no se repite, pero varios modelos pueden no tenerlo")
    void invimaUnicoPeroOpcional() {
        UUID fabricante = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO fabricante (k_id_fabricante, n_nombre_fabricante) VALUES (?, ?)",
                fabricante, "Fabricante " + unico());

        UUID equipo = insertEquipment(insertType(false, null, null), insertBrand());
        String invima = "INVIMA-" + unico();

        jdbcTemplate.update(
                "INSERT INTO modelo (k_id_modelo, n_invima, k_id_fabricante, k_id_equipo) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), invima, fabricante, equipo);

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO modelo (k_id_modelo, n_invima, k_id_fabricante, k_id_equipo) VALUES (?, ?, ?, ?)",
                        UUID.randomUUID(), invima, fabricante, equipo))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Varios modelos sin registro conviven: Postgres admite nulos repetidos bajo UNIQUE.
        assertThatCode(() -> {
                    jdbcTemplate.update(
                            "INSERT INTO modelo (k_id_modelo, k_id_fabricante, k_id_equipo) VALUES (?, ?, ?)",
                            UUID.randomUUID(), fabricante, equipo);
                    jdbcTemplate.update(
                            "INSERT INTO modelo (k_id_modelo, k_id_fabricante, k_id_equipo) VALUES (?, ?, ?)",
                            UUID.randomUUID(), fabricante, equipo);
                })
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("equipo_cliente ya existe: la pieza que V4__client dejo pendiente")
    void equipoClienteExiste() {
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM information_schema.columns
                        WHERE table_name = 'equipo_cliente'
                          AND column_name IN ('k_id_modelo', 'k_id_area_servicio')
                          AND is_nullable = 'NO'
                        """,
                        Integer.class))
                .isEqualTo(2);
    }
}
