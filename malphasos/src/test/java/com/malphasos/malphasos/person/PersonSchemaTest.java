package com.malphasos.malphasos.person;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.person.domain.person.PersonType;
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
 * Verifica que la migración V2 crea el esquema del módulo de personas y que sus restricciones
 * rechazan de verdad los datos inválidos.
 *
 * <p>Se ejecuta contra un PostgreSQL real levantado por Testcontainers: las restricciones de
 * integridad solo se pueden comprobar en el motor que las aplica.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(statements = "DELETE FROM persona", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class PersonSchemaTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Las cédulas son únicas y la columna admite 10 caracteres. */
    private String cedulaUnica() {
        return String.valueOf(System.nanoTime() % 10_000_000_000L);
    }

    private void insertarPersona(String cedula, String tipo, String segundoTipo) {
        jdbcTemplate.update(
                """
                INSERT INTO persona (k_identificador, k_cedula, n_primer_nombre, n_primer_apellido,
                                     t_tipo_persona, t_segundo_tipo_persona)
                VALUES (?, ?, 'Ada', 'Lovelace', ?, ?)
                """,
                UUID.randomUUID(), cedula, tipo, segundoTipo);
    }

    @Test
    @DisplayName("las tres tablas del modulo existen tras la migracion")
    void laMigracionCreaLasTablas() {
        Integer tablas = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('persona', 'correo_persona', 'telefono_persona')
                """,
                Integer.class);

        assertThat(tablas).isEqualTo(3);
    }

    @Test
    @DisplayName("b_estado_activo es boolean en las tres tablas")
    void estadoActivoEsBooleanoEnTodasLasTablas() {
        // En el esquema original la columna de persona estaba declarada como varchar(50)
        // pese a ser booleana, a diferencia de las otras dos tablas.
        var tipos = jdbcTemplate.queryForList(
                """
                SELECT table_name, data_type FROM information_schema.columns
                WHERE table_schema = 'public' AND column_name = 'b_estado_activo'
                  AND table_name IN ('persona', 'correo_persona', 'telefono_persona')
                """);

        assertThat(tipos)
                .hasSize(3)
                .allSatisfy(fila -> assertThat(fila.get("data_type")).isEqualTo("boolean"));
    }

    @Test
    @DisplayName("el enum PersonType y el catalogo de la base no se desincronizan")
    void elEnumCubreTodosLosValoresQueAceptaLaBase() {
        // El original tenia justo este problema: el enum de Java conocia tres valores mientras la
        // restriccion de la base aceptaba cinco. Si alguien agrega un valor en un solo lado, este
        // test lo detecta.
        for (PersonType tipo : PersonType.values()) {
            assertThatCode(() -> insertarPersona(cedulaUnica(), tipo.name(), null))
                    .withFailMessage("La base rechazo el tipo %s, presente en el enum", tipo)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("la cedula es unica")
    void laCedulaEsUnica() {
        insertarPersona("1111111111", "ENGINEER", null);

        assertThatThrownBy(() -> insertarPersona("1111111111", "ADMIN", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("se rechaza un tipo de persona fuera del catalogo")
    void tipoDePersonaInvalidoEsRechazado() {
        assertThatThrownBy(() -> insertarPersona("2222222222", "PRESIDENTE", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("el segundo rol solo admite MANAGER o nulo")
    void segundoTipoDePersonaSoloAdmiteManager() {
        assertThatCode(() -> insertarPersona("3333333333", "CEO_CLIENT", "MANAGER"))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> insertarPersona("4444444444", "CEO_CLIENT", "ENGINEER"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
