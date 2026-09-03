package com.malphasos.malphasos.equipment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.equipment.domain.brand.Brand;
import com.malphasos.malphasos.equipment.domain.brand.events.BrandCreatedEvent;
import com.malphasos.malphasos.equipment.domain.clientEquipment.ClientEquipment;
import com.malphasos.malphasos.equipment.domain.clientEquipment.events.ClientEquipmentRegisteredEvent;
import com.malphasos.malphasos.equipment.domain.clientEquipment.events.ClientEquipmentRelocatedEvent;
import com.malphasos.malphasos.equipment.domain.equipment.Equipment;
import com.malphasos.malphasos.equipment.domain.equipmentType.EquipmentType;
import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationMode;
import com.malphasos.malphasos.equipment.domain.manufacturer.Manufacturer;
import com.malphasos.malphasos.equipment.domain.model.Model;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Los seis agregados del catálogo de equipos. */
class CatalogAggregatesTest {

    private static final UUID TIPO = UUID.randomUUID();
    private static final UUID MARCA = UUID.randomUUID();
    private static final UUID FABRICANTE = UUID.randomUUID();
    private static final UUID EQUIPO = UUID.randomUUID();
    private static final UUID MODELO = UUID.randomUUID();
    private static final UUID AREA = UUID.randomUUID();

    private EquipmentType unTipo(VerificationMode modalidad) {
        return EquipmentType.create("Monitor", "Definicion", "Cuidados", "Electronica",
                110, new BigDecimal("2.50"), modalidad, 150_000L);
    }

    @Nested
    @DisplayName("Marca y fabricante")
    class MarcaYFabricante {

        @Test
        @DisplayName("crear una marca la deja activa y registra el hecho")
        void crearMarca() {
            Brand marca = Brand.create("Philips");

            assertThat(marca.getNombre()).isEqualTo("Philips");
            assertThat(marca.isEstadoActivo()).isTrue();
            assertThat(marca.pullEvents()).singleElement().isInstanceOf(BrandCreatedEvent.class);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("una marca sin nombre se rechaza: es lo unico que una marca tiene")
        void marcaSinNombre(String nombre) {
            // En el esquema original la columna era anulable.
            assertThatThrownBy(() -> Brand.create(nombre))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nombre");
        }

        @Test
        @DisplayName("renombrar con el mismo nombre no anuncia un cambio que no ocurrio")
        void renombrarIgual() {
            Brand marca = Brand.create("Philips");
            marca.pullEvents();

            marca.rename("Philips");

            assertThat(marca.pullEvents()).isEmpty();
        }

        @Test
        @DisplayName("un fabricante puede no tener pais")
        void fabricanteSinPais() {
            assertThat(Manufacturer.create("Draeger", null).getIdPais()).isNull();
        }
    }

    @Nested
    @DisplayName("Tipo de equipo")
    class Tipo {

        @Test
        @DisplayName("es verificable exactamente cuando consta como verificarlo")
        void verificableEsDerivado() {
            // El original tenia un booleano suelto y ni siquiera modelaba la modalidad: cabia un
            // tipo marcado como verificable del que nadie sabia como se verifica.
            assertThat(unTipo(null).isVerificable()).isFalse();
            assertThat(unTipo(VerificationMode.PATRON_CONSTANTE).isVerificable()).isTrue();
        }

        @Test
        @DisplayName("declarar la modalidad vuelve verificable el tipo, y quitarla lo revierte")
        void cambiarModalidad() {
            EquipmentType tipo = unTipo(null);
            tipo.pullEvents();

            tipo.changeVerificationMode(VerificationMode.EQUIPO_CONSTANTE);
            assertThat(tipo.isVerificable()).isTrue();
            assertThat(tipo.pullEvents()).hasSize(1);

            tipo.changeVerificationMode(null);
            assertThat(tipo.isVerificable()).isFalse();
            assertThat(tipo.pullEvents()).hasSize(1);
        }

        @Test
        @DisplayName("declarar la modalidad que ya tenia no emite")
        void modalidadIgual() {
            EquipmentType tipo = unTipo(VerificationMode.PATRON_CONSTANTE);
            tipo.pullEvents();

            tipo.changeVerificationMode(VerificationMode.PATRON_CONSTANTE);

            assertThat(tipo.pullEvents()).isEmpty();
        }

        @Test
        @DisplayName("el amperaje conserva sus decimales")
        void amperajeConDecimales() {
            // En el esquema original numeric(2) redondeaba 2.5 a 3.
            assertThat(unTipo(null).getAmperaje()).isEqualByComparingTo("2.50");
        }

        @Test
        @DisplayName("voltaje y amperaje, si vienen, son positivos")
        void magnitudesPositivas() {
            assertThatThrownBy(() -> EquipmentType.create("M", "D", "C", "E",
                            0, null, null, 1000L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("voltaje");

            assertThatThrownBy(() -> EquipmentType.create("M", "D", "C", "E",
                            110, new BigDecimal("-1"), null, 1000L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("amperaje");
        }

        @Test
        @DisplayName("el valor del mantenimiento no puede ser negativo")
        void valorNoNegativo() {
            assertThatThrownBy(() -> EquipmentType.create("M", "D", "C", "E", null, null, null, -1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("negativo");
        }

        @Test
        @DisplayName("los cuatro textos descriptivos son obligatorios")
        void textosObligatorios() {
            assertThatThrownBy(() -> EquipmentType.create(" ", "D", "C", "E", null, null, null, 0L))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> EquipmentType.create("M", " ", "C", "E", null, null, null, 0L))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Equipo: la asociacion marca-tipo")
    class Asociacion {

        @Test
        @DisplayName("necesita sus dos referencias")
        void referenciasObligatorias() {
            assertThatThrownBy(() -> Equipment.create(null, MARCA))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tipo de equipo");
            assertThatThrownBy(() -> Equipment.create(TIPO, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("marca");
        }

        @Test
        @DisplayName("sus referencias no cambian: no hay forma de pedirlo")
        void referenciasInmutables() {
            Equipment equipo = Equipment.create(TIPO, MARCA);
            equipo.deactivate();

            // El original ofrecia updateEquipment y updateEquipmentPatch, que habrian convertido en
            // mentira todos los modelos colgados de la asociacion.
            assertThat(equipo.getIdTipoEquipo()).isEqualTo(TIPO);
            assertThat(equipo.getIdMarca()).isEqualTo(MARCA);
        }
    }

    @Nested
    @DisplayName("Modelo")
    class Modelo {

        @Test
        @DisplayName("necesita fabricante y equipo")
        void referenciasObligatorias() {
            assertThatThrownBy(() -> Model.create("INV-1", null, EQUIPO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("fabricante");
            assertThatThrownBy(() -> Model.create("INV-1", FABRICANTE, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("equipo");
        }

        @Test
        @DisplayName("un INVIMA en blanco es lo mismo que no tenerlo")
        void invimaEnBlanco() {
            assertThat(Model.create("   ", FABRICANTE, EQUIPO).getInvima()).isNull();
        }

        @Test
        @DisplayName("el INVIMA se puede anotar despues y corregir")
        void anotarInvima() {
            Model modelo = Model.create(null, FABRICANTE, EQUIPO);
            modelo.pullEvents();

            modelo.changeInvima("INVIMA-2024-001");
            assertThat(modelo.getInvima()).isEqualTo("INVIMA-2024-001");
            assertThat(modelo.pullEvents()).hasSize(1);

            modelo.changeInvima("INVIMA-2024-001");
            assertThat(modelo.pullEvents()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Unidad de un cliente")
    class Unidad {

        private ClientEquipment unaUnidad() {
            return ClientEquipment.register("SN-001", MODELO, AREA, "INV-42",
                    LocalDate.now().minusYears(1), 5_000_000L);
        }

        @Test
        @DisplayName("registrar deja la unidad activa y publica el hecho")
        void registrar() {
            ClientEquipment unidad = unaUnidad();

            assertThat(unidad.getSerie()).isEqualTo("SN-001");
            assertThat(unidad.isEstadoActivo()).isTrue();
            assertThat(unidad.pullEvents()).singleElement()
                    .isInstanceOf(ClientEquipmentRegisteredEvent.class);
        }

        @Test
        @DisplayName("una unidad sin serie, sin modelo o sin area se rechaza")
        void referenciasObligatorias() {
            assertThatThrownBy(() -> ClientEquipment.register(" ", MODELO, AREA, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ClientEquipment.register("SN", null, AREA, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ClientEquipment.register("SN", MODELO, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("un equipo no se compro en el futuro")
        void fechaDeCompraNoFutura() {
            assertThatThrownBy(() -> ClientEquipment.register("SN", MODELO, AREA, null,
                            LocalDate.now().plusDays(1), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("futuro");
        }

        @Test
        @DisplayName("trasladar de area publica el hecho; trasladar a la misma no")
        void trasladar() {
            ClientEquipment unidad = unaUnidad();
            unidad.pullEvents();

            unidad.relocateTo(UUID.randomUUID());
            assertThat(unidad.pullEvents()).singleElement()
                    .isInstanceOf(ClientEquipmentRelocatedEvent.class);

            unidad.relocateTo(unidad.getIdAreaServicio());
            assertThat(unidad.pullEvents()).isEmpty();
        }

        @Test
        @DisplayName("el modelo no cambia: una unidad no se convierte en otra cosa")
        void modeloInmutable() {
            ClientEquipment unidad = unaUnidad();
            unidad.relocateTo(UUID.randomUUID());

            assertThat(unidad.getIdModelo()).isEqualTo(MODELO);
        }

        @Test
        @DisplayName("dar de baja es idempotente")
        void darDeBaja() {
            ClientEquipment unidad = unaUnidad();
            unidad.pullEvents();

            unidad.decommission();
            assertThat(unidad.isEstadoActivo()).isFalse();
            assertThat(unidad.pullEvents()).hasSize(1);

            unidad.decommission();
            assertThat(unidad.pullEvents()).isEmpty();
        }
    }

    @Test
    @DisplayName("los seis agregados comparan por identidad, y rehidratar no emite")
    void identidadYRehidratacion() {
        UUID id = UUID.randomUUID();

        assertThat(Brand.rehydrate(id, "Uno", true)).isEqualTo(Brand.rehydrate(id, "Otro", false));
        assertThat(Manufacturer.rehydrate(id, "Uno", null, true).hasPendingEvents()).isFalse();
        assertThat(Equipment.rehydrate(id, TIPO, MARCA, true).hasPendingEvents()).isFalse();
        assertThat(Model.rehydrate(id, null, FABRICANTE, EQUIPO, true).hasPendingEvents()).isFalse();
        assertThat(ClientEquipment.rehydrate(id, "SN", MODELO, AREA, null, null, null, true)
                        .hasPendingEvents())
                .isFalse();
    }
}
