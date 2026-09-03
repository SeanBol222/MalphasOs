package com.malphasos.malphasos.equipment.infrastructure.output;

import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.equipment.domain.brand.Brand;
import com.malphasos.malphasos.equipment.domain.equipmentType.EquipmentType;
import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationMode;
import com.malphasos.malphasos.equipment.domain.manufacturer.Manufacturer;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

/**
 * Ejercita los adaptadores del catálogo base contra un PostgreSQL real. Lo que más importa aquí es
 * el tipo de equipo: el agregado tiene una modalidad y la tabla tiene dos columnas.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(
        statements = {"DELETE FROM marca", "DELETE FROM tipo_equipo", "DELETE FROM fabricante"},
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class EquipmentCatalogPersistenceTest {

    @Autowired private ManufacturerPersistenceAdapter manufacturerAdapter;
    @Autowired private BrandPersistenceAdapter brandAdapter;
    @Autowired private EquipmentTypePersistenceAdapter equipmentTypeAdapter;
    @Autowired private JdbcTemplate jdbcTemplate;

    private String unico() {
        return String.valueOf(System.nanoTime());
    }

    private EquipmentType unTipo(VerificationMode modalidad) {
        return EquipmentType.create("Tipo " + unico(), "Definicion", "Cuidados", "Electronica",
                110, new BigDecimal("2.50"), modalidad, 150_000L);
    }

    @Test
    @DisplayName("un fabricante y una marca se guardan y se recuperan enteros")
    void idaYVuelta() {
        Manufacturer fabricante = manufacturerAdapter.save(
                Manufacturer.create("Draeger " + unico(), null));
        Brand marca = brandAdapter.save(Brand.create("Philips " + unico()));

        assertThat(manufacturerAdapter.findById(fabricante.getId()).orElseThrow().getNombre())
                .isEqualTo(fabricante.getNombre());
        assertThat(brandAdapter.findById(marca.getId()).orElseThrow().isEstadoActivo()).isTrue();
    }

    @Test
    @DisplayName("un tipo verificable guarda su modalidad y el booleano derivado en verdadero")
    void tipoVerificable() {
        EquipmentType tipo = equipmentTypeAdapter.save(unTipo(VerificationMode.PATRON_CONSTANTE));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT b_verificable FROM tipo_equipo WHERE k_id_tipo_equipo = ?",
                        Boolean.class, tipo.getId()))
                .isTrue();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT n_tipo_verificacion FROM tipo_equipo WHERE k_id_tipo_equipo = ?",
                        String.class, tipo.getId()))
                .isEqualTo("patron_constante");
    }

    @Test
    @DisplayName("un tipo no verificable guarda el booleano en falso y la modalidad nula")
    void tipoNoVerificable() {
        EquipmentType tipo = equipmentTypeAdapter.save(unTipo(null));

        // La restriccion de la tabla rechazaria cualquier otra combinacion: el mapper deriva el
        // booleano, de modo que las dos columnas no pueden contradecirse.
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT b_verificable FROM tipo_equipo WHERE k_id_tipo_equipo = ?",
                        Boolean.class, tipo.getId()))
                .isFalse();
        assertThat(equipmentTypeAdapter.findById(tipo.getId()).orElseThrow().isVerificable())
                .isFalse();
    }

    @Test
    @DisplayName("declarar la modalidad y quitarla despues sobrevive al viaje de ida y vuelta")
    void cambiarModalidadPersiste() {
        EquipmentType tipo = equipmentTypeAdapter.save(unTipo(null));

        EquipmentType recuperado = equipmentTypeAdapter.findById(tipo.getId()).orElseThrow();
        recuperado.changeVerificationMode(VerificationMode.PATRON_EQUIPO_VARIABLE);
        equipmentTypeAdapter.save(recuperado);

        EquipmentType conModalidad = equipmentTypeAdapter.findById(tipo.getId()).orElseThrow();
        assertThat(conModalidad.isVerificable()).isTrue();
        assertThat(conModalidad.getModalidadVerificacion())
                .isEqualTo(VerificationMode.PATRON_EQUIPO_VARIABLE);

        conModalidad.changeVerificationMode(null);
        equipmentTypeAdapter.save(conModalidad);

        assertThat(equipmentTypeAdapter.findById(tipo.getId()).orElseThrow().isVerificable())
                .isFalse();
    }

    @Test
    @DisplayName("el amperaje conserva sus decimales al ir y volver de la base")
    void amperajePersiste() {
        EquipmentType tipo = equipmentTypeAdapter.save(unTipo(null));

        // En el esquema original numeric(2) lo habria redondeado a 3.
        assertThat(equipmentTypeAdapter.findById(tipo.getId()).orElseThrow().getAmperaje())
                .isEqualByComparingTo("2.50");
    }

    @Test
    @DisplayName("lo recuperado no trae eventos")
    void sinEventosAlLeer() {
        Brand marca = brandAdapter.save(Brand.create("Philips " + unico()));

        assertThat(brandAdapter.findById(marca.getId()).orElseThrow().hasPendingEvents()).isFalse();
    }

    @Test
    @DisplayName("retirar conserva la fila con el estado en falso")
    void retirarConservaLaFila() {
        Brand marca = brandAdapter.save(Brand.create("Philips " + unico()));

        Brand recuperada = brandAdapter.findById(marca.getId()).orElseThrow();
        recuperada.deactivate();
        brandAdapter.save(recuperada);

        assertThat(brandAdapter.findById(marca.getId()).orElseThrow().isEstadoActivo()).isFalse();
    }
}
