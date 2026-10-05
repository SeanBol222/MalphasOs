package com.malphasos.malphasos.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.client.domain.client.ClientAcronym;
import java.util.List;
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
 * La sigla en el esquema: su gemela en SQL, el trigger que la pone a lo que entra por fuera del dominio,
 * y las restricciones.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(statements = "DELETE FROM cliente WHERE n_razon_social LIKE 'Sigla %'",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ClientAcronymSchemaTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    /** La misma lista que ClientAcronymTest, y algunas con letras que el SQL tiene que traducir a mano. */
    private static final List<String> RAZONES_SOCIALES = List.of(
            "Clínica Dermatológica del Norte S.A.S.",
            "Hospital Universitario San Ignacio",
            "Bolívar Bioingeniería Ltda.",
            "Dermacenter S.A.S.",
            "Clínica del Dolor y Neurología S.A.",
            "Uñas & Spa Ltda",
            "IPS 24 Horas",
            "S.A.S.",
            "X",
            "Fundación Hospital Infantil de la Sabana de Bogotá Norte",
            "Óptica Ñuñoa Ü Ç",
            "  centro   médico   ");

    private String insertar(String razonSocial) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO cliente (k_id_cliente, k_documento, n_tipo_identificacion, n_razon_social)
                VALUES (?, ?, 'NIT_juridico', ?)
                """,
                id, String.valueOf(System.nanoTime() % 10_000_000_000L), razonSocial);

        return jdbcTemplate.queryForObject("SELECT n_sigla FROM cliente WHERE k_id_cliente = ?", String.class, id);
    }

    @Test
    @DisplayName("la regla en SQL y la regla en Java dan la misma sigla para cada razon social")
    void lasDosReglasCoinciden() {
        // V19 dio sigla a los clientes que ya existian con la de SQL, y el alta usa la de Java. Si se
        // separan, un cliente creado hoy y uno migrado ayer se numerarian con reglas distintas.
        for (String razonSocial : RAZONES_SOCIALES) {
            String enSql = jdbcTemplate.queryForObject("SELECT sigla_base(?)", String.class, razonSocial);

            assertThat(enSql).as(razonSocial).isEqualTo(ClientAcronym.base(razonSocial));
        }
    }

    @Test
    @DisplayName("un cliente que entra por SQL sin sigla la recibe, desempatada contra las que ya existen")
    void elTriggerLaPone() {
        String primera = insertar("Sigla Clinica Norte");
        String segunda = insertar("Sigla Centro Nuevo");

        assertThat(primera).isEqualTo(ClientAcronym.base("Sigla Clinica Norte"));
        // Las dos dan «SCN»: la segunda se desempata.
        assertThat(segunda).isEqualTo(ClientAcronym.conDesempate(primera, 2));
    }

    @Test
    @DisplayName("el esquema rechaza una sigla repetida o con otro formato")
    void restricciones() {
        String sigla = insertar("Sigla Unica Prueba");

        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        INSERT INTO cliente (k_id_cliente, k_documento, n_tipo_identificacion, n_razon_social, n_sigla)
                        VALUES (?, ?, 'NIT_juridico', 'Sigla Otra', ?)
                        """,
                        UUID.randomUUID(), String.valueOf(System.nanoTime() % 10_000_000_000L), sigla))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                        "UPDATE cliente SET n_sigla = 'ab' WHERE n_sigla = ?", sigla))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("CHK_cliente_sigla");
    }
}
