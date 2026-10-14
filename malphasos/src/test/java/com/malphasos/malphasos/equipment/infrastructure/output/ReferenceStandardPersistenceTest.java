package com.malphasos.malphasos.equipment.infrastructure.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.equipment.domain.referenceStandard.ReferenceStandard;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.jdbc.Sql;

/** El almacén de equipos patrón, contra PostgreSQL real (V25). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(statements = "DELETE FROM equipo_patron", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ReferenceStandardPersistenceTest {

    private static final LocalDate VENCE = LocalDate.of(2027, 3, 31);

    @Autowired private ReferenceStandardPersistenceAdapter almacen;

    @Test
    @DisplayName("ida y vuelta con todos sus campos, marca nula incluida")
    void idaYVuelta() {
        ReferenceStandard patron = ReferenceStandard.create("Juego de pesas F1", null, "SN-1", "CAL-1", VENCE);
        almacen.save(patron);

        ReferenceStandard leido = almacen.findById(patron.getId()).orElseThrow();

        assertThat(leido.getNombre()).isEqualTo("Juego de pesas F1");
        assertThat(leido.getMarca()).isNull();
        assertThat(leido.getSerie()).isEqualTo("SN-1");
        assertThat(leido.getCertificado()).isEqualTo("CAL-1");
        assertThat(leido.getVencimientoCertificado()).isEqualTo(VENCE);
        assertThat(leido.isEstadoActivo()).isTrue();
    }

    @Test
    @DisplayName("la base impide dos vigentes con la misma serie, aunque el servicio se salte la comprobación")
    void serieUnica() {
        almacen.save(ReferenceStandard.create("Uno", null, "SN-1", "C", VENCE));

        assertThatThrownBy(() -> almacen.save(ReferenceStandard.create("Otro", null, "SN-1", "C", VENCE)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("uno retirado libera su serie, y la búsqueda solo encuentra al vigente")
    void retiradoLibera() {
        ReferenceStandard viejo = ReferenceStandard.create("Viejo", null, "SN-1", "C", VENCE);
        viejo.deactivate();
        almacen.save(viejo);
        ReferenceStandard nuevo = almacen.save(ReferenceStandard.create("Nuevo", null, "SN-1", "C", VENCE));

        assertThat(almacen.findActiveBySerie("SN-1")).get().extracting(ReferenceStandard::getId).isEqualTo(nuevo.getId());
    }

    @Test
    @DisplayName("se listan por nombre, sin distinguir mayúsculas")
    void porNombre() {
        almacen.save(ReferenceStandard.create("termómetro", null, "A", "C", VENCE));
        almacen.save(ReferenceStandard.create("Barómetro", null, "B", "C", VENCE));
        almacen.save(ReferenceStandard.create("Manómetro", null, "D", "C", VENCE));

        assertThat(almacen.findAll()).extracting(ReferenceStandard::getNombre)
                .containsExactly("Barómetro", "Manómetro", "termómetro");
    }
}
