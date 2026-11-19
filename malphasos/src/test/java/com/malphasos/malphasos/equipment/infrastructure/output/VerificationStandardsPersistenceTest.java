package com.malphasos.malphasos.equipment.infrastructure.output;

import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.equipment.domain.verificationStandards.VerificationStandards;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

/** Los patrones de cada verificación contra PostgreSQL real (V60): ida y vuelta, y reemplazar el conjunto. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(
        statements = {
            "DELETE FROM patron_verificacion",
            "DELETE FROM equipo_patron",
            "DELETE FROM verificacion_tipo_equipo",
            "DELETE FROM tipo_equipo"
        },
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class VerificationStandardsPersistenceTest {

    @Autowired private VerificationStandardsPersistenceAdapter adapter;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UUID unaVerificacion(String simbolo) {
        UUID tipo = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO tipo_equipo (k_id_tipo_equipo, n_nombre_tipo_equipo, t_definicion_tecnica,
                                         t_recomendaciones_cuidado, t_tecnologia_predominante,
                                         m_valor_unitario_mantenimiento)
                VALUES (?, ?, 'Definicion', 'Cuidados', 'Electronica', 150000)
                """,
                tipo, "Tipo " + System.nanoTime());
        UUID verificacion = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO verificacion_tipo_equipo (k_id_verificacion, k_id_tipo_equipo, k_id_unidad_medida,
                                                      n_modalidad_verificacion)
                SELECT ?, ?, k_id_unidad_medida, 'patron_equipo_variable'
                FROM unidad_medida WHERE n_simbolo_unidad = ?
                """,
                verificacion, tipo, simbolo);

        return verificacion;
    }

    private UUID unPatron() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO equipo_patron (k_id_equipo_patron, n_nombre, k_serie, n_certificado,
                                           f_vencimiento_certificado)
                VALUES (?, 'Simulador', ?, 'C-1', ?)
                """,
                id, "S-" + System.nanoTime(), LocalDate.of(2027, 1, 1));

        return id;
    }

    @Test
    @DisplayName("una verificacion sin patrones devuelve el conjunto vacio, no nada")
    void sinPatrones() {
        UUID verificacion = unaVerificacion("mmHg");

        assertThat(adapter.findByVerification(verificacion).getPatrones()).isEmpty();
    }

    @Test
    @DisplayName("guardar reemplaza el conjunto: entra el nuevo, sale el que falta, se queda el que sigue")
    void reemplaza() {
        UUID verificacion = unaVerificacion("mmHg");
        UUID simulador = unPatron();
        UUID manometro = unPatron();
        UUID tercero = unPatron();

        VerificationStandards patrones = VerificationStandards.empty(verificacion);
        patrones.assign(List.of(simulador, manometro));
        assertThat(adapter.save(patrones).getPatrones()).containsExactlyInAnyOrder(simulador, manometro);

        patrones.assign(List.of(manometro, tercero));
        assertThat(adapter.save(patrones).getPatrones()).containsExactlyInAnyOrder(manometro, tercero);

        patrones.assign(List.of());
        assertThat(adapter.save(patrones).getPatrones()).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM patron_verificacion", Integer.class)).isZero();
    }

    @Test
    @DisplayName("varias verificaciones en una consulta, en el orden pedido, sin mezclar sus patrones")
    void varias() {
        UUID presion = unaVerificacion("mmHg");
        UUID frecuencia = unaVerificacion("lpm");
        UUID simulador = unPatron();
        UUID manometro = unPatron();
        jdbcTemplate.update("INSERT INTO patron_verificacion VALUES (?, ?), (?, ?), (?, ?)",
                presion, simulador, presion, manometro, frecuencia, simulador);

        List<VerificationStandards> leidos = adapter.findByVerifications(List.of(frecuencia, presion));

        assertThat(leidos).extracting(VerificationStandards::getIdVerificacion).containsExactly(frecuencia, presion);
        assertThat(leidos.get(0).getPatrones()).containsExactly(simulador);
        assertThat(leidos.get(1).getPatrones()).containsExactlyInAnyOrder(simulador, manometro);
        assertThat(adapter.findByVerifications(List.of())).isEmpty();
    }
}
