package com.malphasos.malphasos.equipment.infrastructure.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.equipment.domain.referenceStandard.ReferenceStandard;
import com.malphasos.malphasos.equipment.domain.referenceStandard.ReferenceStandardSheet;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

/** El almacén de equipos patrón, contra PostgreSQL real (V25). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(statements = "DELETE FROM equipo_patron", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ReferenceStandardPersistenceTest {

    private static final LocalDate VENCE = LocalDate.of(2027, 3, 31);
    private static final ReferenceStandardSheet SIN_FICHA = ReferenceStandardSheet.VACIA;

    @Autowired private ReferenceStandardPersistenceAdapter almacen;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("ida y vuelta con todos sus campos, marca nula incluida")
    void idaYVuelta() {
        ReferenceStandard patron =
                ReferenceStandard.create("Juego de pesas F1", null, "SN-1", "CAL-1", VENCE, SIN_FICHA);
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
    @DisplayName("la ficha entera va y vuelve (V29)")
    void laFicha() {
        ReferenceStandardSheet ficha = new ReferenceStandardSheet("MS-200", "BB-PAT-003", "1 g a 2 kg", "0,1 mg",
                LocalDate.of(2026, 3, 31), "Metrología Andina S.A.S.");
        ReferenceStandard patron = ReferenceStandard.create("Juego de pesas F1", "Kern", "SN-1", "CAL-1", VENCE, ficha);
        almacen.save(patron);

        assertThat(almacen.findById(patron.getId()).orElseThrow().getFicha()).isEqualTo(ficha);
    }

    @Test
    @DisplayName("la base impide una calibración posterior al vencimiento, aunque se salte el dominio")
    void calibracionPosterior() {
        ReferenceStandard patron = ReferenceStandard.create("Pesas", null, "SN-1", "C", VENCE, SIN_FICHA);
        almacen.save(patron);

        assertThatThrownBy(() -> jdbc.update(
                        "UPDATE equipo_patron SET f_calibracion = ? WHERE k_id_equipo_patron = ?",
                        VENCE.plusDays(1), patron.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        // El mismo día sí: es el borde que el dominio también admite.
        assertThat(jdbc.update("UPDATE equipo_patron SET f_calibracion = ? WHERE k_id_equipo_patron = ?",
                VENCE, patron.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("la base impide dos vigentes con la misma serie, aunque el servicio se salte la comprobación")
    void serieUnica() {
        almacen.save(ReferenceStandard.create("Uno", null, "SN-1", "C", VENCE, SIN_FICHA));

        assertThatThrownBy(() -> almacen.save(ReferenceStandard.create("Otro", null, "SN-1", "C", VENCE, SIN_FICHA)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("uno retirado libera su serie, y la búsqueda solo encuentra al vigente")
    void retiradoLibera() {
        ReferenceStandard viejo = ReferenceStandard.create("Viejo", null, "SN-1", "C", VENCE, SIN_FICHA);
        viejo.deactivate();
        almacen.save(viejo);
        ReferenceStandard nuevo = almacen.save(ReferenceStandard.create("Nuevo", null, "SN-1", "C", VENCE, SIN_FICHA));

        assertThat(almacen.findActiveBySerie("SN-1")).get().extracting(ReferenceStandard::getId).isEqualTo(nuevo.getId());
    }

    @Test
    @DisplayName("se listan por nombre, sin distinguir mayúsculas")
    void porNombre() {
        almacen.save(ReferenceStandard.create("termómetro", null, "A", "C", VENCE, SIN_FICHA));
        almacen.save(ReferenceStandard.create("Barómetro", null, "B", "C", VENCE, SIN_FICHA));
        almacen.save(ReferenceStandard.create("Manómetro", null, "D", "C", VENCE, SIN_FICHA));

        assertThat(almacen.findAll()).extracting(ReferenceStandard::getNombre)
                .containsExactly("Barómetro", "Manómetro", "termómetro");
    }
}
