package com.malphasos.malphasos.equipment.infrastructure.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.equipment.domain.brand.Brand;
import com.malphasos.malphasos.equipment.domain.equipment.Equipment;
import com.malphasos.malphasos.equipment.domain.equipmentType.EquipmentType;
import com.malphasos.malphasos.equipment.domain.manufacturer.Manufacturer;
import com.malphasos.malphasos.equipment.domain.model.Model;
import com.malphasos.malphasos.equipment.domain.model.RiskClass;
import com.malphasos.malphasos.equipment.domain.model.TechnicalSheet;
import java.math.BigDecimal;
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
 * La ficha técnica de un modelo contra PostgreSQL real: que va y vuelve entera, y que el esquema
 * sostiene sus reglas aunque alguien escriba sin pasar por el dominio.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(
        statements = {
            "DELETE FROM modelo",
            "DELETE FROM equipo",
            "DELETE FROM punto_verificacion",
            "DELETE FROM verificacion_tipo_equipo",
            "DELETE FROM tipo_equipo",
            "DELETE FROM marca",
            "DELETE FROM fabricante"
        },
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ModelTechnicalSheetPersistenceTest {

    @Autowired private ManufacturerPersistenceAdapter manufacturerAdapter;
    @Autowired private BrandPersistenceAdapter brandAdapter;
    @Autowired private EquipmentTypePersistenceAdapter equipmentTypeAdapter;
    @Autowired private EquipmentPersistenceAdapter equipmentAdapter;
    @Autowired private ModelPersistenceAdapter modelAdapter;
    @Autowired private JdbcTemplate jdbcTemplate;

    private String unico() {
        return String.valueOf(System.nanoTime());
    }

    private Model guardar(TechnicalSheet ficha) {
        Manufacturer fabricante = manufacturerAdapter.save(Manufacturer.create("Beurer " + unico(), null));
        Brand marca = brandAdapter.save(Brand.create("Beurer " + unico()));
        EquipmentType tipo = equipmentTypeAdapter.save(EquipmentType.create(
                "Balanza " + unico(), "Definicion", "Cuidados", "Electronica", null, null, List.of(), 1000L));
        Equipment asociacion = equipmentAdapter.save(Equipment.create(tipo.getId(), marca.getId()));

        return modelAdapter.save(
                Model.create("GS14 " + unico(), null, fabricante.getId(), asociacion.getId(), ficha));
    }

    @Test
    @DisplayName("la ficha va y vuelve entera, con el riesgo y los dos decimales del amperaje")
    void vaYVuelve() {
        TechnicalSheet ficha = TechnicalSheet.of(
                RiskClass.IIB, "Pantalla LCD", "Red electrica", 110, 45, new BigDecimal("2.5"), 60);

        Model guardado = guardar(ficha);

        // Igualdad de la ficha entera, y no campo a campo: el amperaje vuelve de numeric(8,2) como
        // 2.50, y la ficha lo normalizo a 2.50 al crearse, de modo que las dos son iguales.
        assertThat(modelAdapter.findById(guardado.getId()).orElseThrow().getFichaTecnica()).isEqualTo(ficha);
    }

    @Test
    @DisplayName("un modelo sin ficha vuelve con la ficha vacia, no con nulos sueltos")
    void sinFicha() {
        Model guardado = guardar(TechnicalSheet.EMPTY);

        assertThat(modelAdapter.findById(guardado.getId()).orElseThrow().getFichaTecnica())
                .isEqualTo(TechnicalSheet.EMPTY);
    }

    @Test
    @DisplayName("el esquema rechaza una clase de riesgo que no existe y un voltaje en cero")
    void elEsquemaSostieneLasReglas() {
        Model guardado = guardar(TechnicalSheet.EMPTY);
        UUID id = guardado.getId();

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "UPDATE modelo SET n_clase_riesgo = 'IV' WHERE k_id_modelo = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("CHK_modelo_clase_riesgo");
        assertThatThrownBy(() -> jdbcTemplate.update(
                        "UPDATE modelo SET i_voltaje = 0 WHERE k_id_modelo = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("CHK_modelo_voltaje");
    }

    @Test
    @DisplayName("el tipo de equipo ya no tiene voltaje ni amperaje")
    void elTipoLosPerdio() {
        // V15 los movio al modelo. Si volvieran a aparecer en el tipo, habria dos fuentes del mismo dato.
        Integer columnas = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM information_schema.columns
                WHERE table_name = 'tipo_equipo' AND column_name IN ('i_voltage', 'd_amperaje')
                """,
                Integer.class);

        assertThat(columnas).isZero();
    }
}
