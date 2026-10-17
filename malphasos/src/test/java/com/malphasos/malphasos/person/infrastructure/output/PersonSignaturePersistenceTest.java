package com.malphasos.malphasos.person.infrastructure.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.person.domain.person.PersonSignature;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

/** El almacén de firmas, contra PostgreSQL real (V38). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(statements = {"DELETE FROM firma_persona", "DELETE FROM persona WHERE n_primer_nombre = 'Firma'"},
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class PersonSignaturePersistenceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 7};
    private static final LocalDateTime HOY = LocalDateTime.of(2026, 10, 17, 9, 0);

    @Autowired private PersonSignaturePersistenceAdapter almacen;
    @Autowired private JdbcTemplate jdbc;

    private UUID unaPersona() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO persona (k_identificador, k_cedula, n_primer_nombre, n_primer_apellido, t_tipo_persona)
                VALUES (?, ?, 'Firma', 'Prueba', 'ENGINEER')
                """, id, String.valueOf(Math.floorMod(id.getMostSignificantBits(), 1_000_000_000L)));
        return id;
    }

    @Test
    @DisplayName("ida y vuelta, y guardar otra la sustituye: una por persona")
    void idaYVuelta() {
        UUID persona = unaPersona();
        almacen.save(persona, new PersonSignature(PNG, HOY));
        byte[] otra = PNG.clone();
        otra[8] = 8;
        almacen.save(persona, new PersonSignature(otra, HOY.plusDays(1)));

        assertThat(almacen.find(persona)).contains(new PersonSignature(otra, HOY.plusDays(1)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM firma_persona WHERE k_id_persona = ?", Integer.class,
                persona)).isEqualTo(1);
    }

    @Test
    @DisplayName("sin firma no hay firma")
    void sinFirma() {
        assertThat(almacen.find(unaPersona())).isEmpty();
    }

    @Test
    @DisplayName("la base impide la firma de alguien que no existe, aunque se salte el servicio")
    void personaInexistente() {
        assertThatThrownBy(() -> almacen.save(UUID.randomUUID(), new PersonSignature(PNG, HOY)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
