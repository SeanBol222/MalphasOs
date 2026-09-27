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
import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationPoint;
import com.malphasos.malphasos.equipment.domain.manufacturer.Manufacturer;
import com.malphasos.malphasos.equipment.domain.model.Model;
import java.math.BigDecimal;
import java.util.List;
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
        // Las modalidades constantes exigen cantidad y al menos un punto; la variable no los admite.
        boolean constante = modalidad == VerificationMode.PATRON_CONSTANTE
                || modalidad == VerificationMode.EQUIPO_CONSTANTE;

        return EquipmentType.create("Monitor", "Definicion", "Cuidados", "Electronica",
                110, new BigDecimal("2.50"), modalidad,
                constante ? 3 : null,
                constante ? List.of(VerificationPoint.of(new BigDecimal("100"), "mmHg")) : List.of(),
                150_000L);
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

            tipo.changeVerificationMode(VerificationMode.EQUIPO_CONSTANTE, 3, List.of(VerificationPoint.of(new BigDecimal("100"), "mmHg")));
            assertThat(tipo.isVerificable()).isTrue();
            assertThat(tipo.pullEvents()).hasSize(1);

            tipo.changeVerificationMode(null, null, List.of());
            assertThat(tipo.isVerificable()).isFalse();
            assertThat(tipo.pullEvents()).hasSize(1);
        }

        @Test
        @DisplayName("declarar la modalidad que ya tenia no emite")
        void modalidadIgual() {
            EquipmentType tipo = unTipo(VerificationMode.PATRON_CONSTANTE);
            tipo.pullEvents();

            tipo.changeVerificationMode(VerificationMode.PATRON_CONSTANTE, 3, List.of(VerificationPoint.of(new BigDecimal("100"), "mmHg")));

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
                            0, null, null, null, List.of(), 1000L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("voltaje");

            assertThatThrownBy(() -> EquipmentType.create("M", "D", "C", "E",
                            110, new BigDecimal("-1"), null, null, List.of(), 1000L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("amperaje");
        }

        @Test
        @DisplayName("el valor del mantenimiento no puede ser negativo")
        void valorNoNegativo() {
            assertThatThrownBy(() -> EquipmentType.create("M", "D", "C", "E", null, null, null, null, List.of(), -1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("negativo");
        }

        @Test
        @DisplayName("los cuatro textos descriptivos son obligatorios")
        void textosObligatorios() {
            assertThatThrownBy(() -> EquipmentType.create(" ", "D", "C", "E", null, null, null, null, List.of(), 0L))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> EquipmentType.create("M", " ", "C", "E", null, null, null, null, List.of(), 0L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Nested
        @DisplayName("Con que y cuantas veces se verifica")
        class Verificacion {

            private EquipmentType conModalidad(
                    VerificationMode modalidad, Integer cantidad, List<VerificationPoint> puntos) {

                return EquipmentType.create("Monitor", "D", "C", "E", null, null, modalidad,
                        cantidad, puntos, 1000L);
            }

            private VerificationPoint punto(String valor) {
                return VerificationPoint.of(new BigDecimal(valor), "mmHg");
            }

            @Test
            @DisplayName("una modalidad constante exige cuantas lecturas y al menos un punto")
            void constanteExigeAmbos() {
                // Son los dos datos que hacen falta para llenar el reporte: sin ellos, el tipo dice
                // como se verifica y no con que.
                assertThatThrownBy(() -> conModalidad(
                                VerificationMode.PATRON_CONSTANTE, null, List.of(punto("100"))))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("lecturas por punto");

                assertThatThrownBy(() -> conModalidad(
                                VerificationMode.EQUIPO_CONSTANTE, 3, List.of()))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("al menos un punto");
            }

            @Test
            @DisplayName("la cantidad de lecturas va de 1 a 100")
            void cantidadAcotada() {
                for (Integer cantidad : new Integer[] {0, 101}) {
                    assertThatThrownBy(() -> conModalidad(
                                    VerificationMode.PATRON_CONSTANTE, cantidad, List.of(punto("100"))))
                            .describedAs("cantidad " + cantidad)
                            .isInstanceOf(IllegalArgumentException.class);
                }
            }

            @Test
            @DisplayName("la modalidad variable no admite ni cantidad ni puntos")
            void variableNoAdmiteNinguno() {
                // Cuantas lecturas tomar lo decide el ingeniero en campo, y no hay nada constante que
                // declarar: los dos campos serian un dato que nadie puede cumplir.
                assertThatThrownBy(() -> conModalidad(
                                VerificationMode.PATRON_EQUIPO_VARIABLE, 3, List.of()))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("modalidades constantes");

                assertThatThrownBy(() -> conModalidad(
                                VerificationMode.PATRON_EQUIPO_VARIABLE, null, List.of(punto("100"))))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("puntos de verificacion");
            }

            @Test
            @DisplayName("un tipo que no se verifica tampoco los admite")
            void sinModalidadNoAdmiteNinguno() {
                assertThatThrownBy(() -> conModalidad(null, 3, List.of()))
                        .isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> conModalidad(null, null, List.of(punto("100"))))
                        .isInstanceOf(IllegalArgumentException.class);
            }

            @Test
            @DisplayName("el mismo punto escrito distinto sigue siendo el mismo")
            void puntoRepetido() {
                // 100 y 100.0000 son el mismo punto, y equals de BigDecimal diria que no. Sin esto,
                // el reporte tendria dos veces la misma medicion.
                assertThatThrownBy(() -> conModalidad(VerificationMode.PATRON_CONSTANTE, 3,
                                List.of(punto("100"), punto("100.0000"))))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("dos veces");
            }

            @Test
            @DisplayName("un punto negativo vale: un congelador se verifica a -20 grados")
            void puntoNegativo() {
                EquipmentType tipo = conModalidad(VerificationMode.EQUIPO_CONSTANTE, 5,
                        List.of(VerificationPoint.of(new BigDecimal("-20"), "°C")));

                assertThat(tipo.puntosActivos()).hasSize(1);
                assertThat(tipo.puntosActivos().getFirst().valor()).isEqualByComparingTo("-20");
            }

            @Test
            @DisplayName("un punto sin unidad no dice nada, y se rechaza")
            void puntoSinUnidad() {
                assertThatThrownBy(() -> VerificationPoint.of(new BigDecimal("100"), "  "))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("unidad");
            }

            @Test
            @DisplayName("los puntos se recorren ordenados por valor")
            void puntosOrdenados() {
                EquipmentType tipo = conModalidad(VerificationMode.PATRON_CONSTANTE, 3,
                        List.of(punto("150"), punto("50"), punto("100")));

                assertThat(tipo.puntosActivos().stream().map(VerificationPoint::valor))
                        .containsExactly(
                                new BigDecimal("50.0000"),
                                new BigDecimal("100.0000"),
                                new BigDecimal("150.0000"));
            }

            @Test
            @DisplayName("reconfigurar retira los puntos anteriores, no los borra")
            void reconfigurarRetira() {
                // Con los de antes se hicieron los reportes anteriores: tienen que seguir existiendo.
                EquipmentType tipo = conModalidad(
                        VerificationMode.PATRON_CONSTANTE, 3, List.of(punto("100")));

                tipo.changeVerificationMode(
                        VerificationMode.EQUIPO_CONSTANTE, 5, List.of(punto("200")));

                assertThat(tipo.puntosActivos()).hasSize(1);
                assertThat(tipo.puntosActivos().getFirst().valor()).isEqualByComparingTo("200");
                assertThat(tipo.getPuntosVerificacion()).hasSize(2);
                assertThat(tipo.getPuntosVerificacion().stream().filter(p -> !p.estadoActivo()))
                        .hasSize(1);
                assertThat(tipo.getCantidadDatos()).isEqualTo(5);
            }

            @Test
            @DisplayName("dejar de verificarse retira la cantidad y los puntos")
            void dejarDeVerificarse() {
                EquipmentType tipo = conModalidad(
                        VerificationMode.PATRON_CONSTANTE, 3, List.of(punto("100")));

                tipo.changeVerificationMode(null, null, List.of());

                assertThat(tipo.isVerificable()).isFalse();
                assertThat(tipo.getCantidadDatos()).isNull();
                assertThat(tipo.puntosActivos()).isEmpty();
                // Pero el punto sigue en la lista, retirado.
                assertThat(tipo.getPuntosVerificacion()).hasSize(1);
            }

            @Test
            @DisplayName("una configuracion identica no emite evento")
            void configuracionIdentica() {
                // La convencion del proyecto: un cambio que no cambia nada no es un hecho del dominio.
                EquipmentType tipo = conModalidad(
                        VerificationMode.PATRON_CONSTANTE, 3, List.of(punto("100")));
                tipo.pullEvents();

                tipo.changeVerificationMode(
                        VerificationMode.PATRON_CONSTANTE, 3, List.of(punto("100.0000")));

                assertThat(tipo.pullEvents()).isEmpty();
            }

            @Test
            @DisplayName("los puntos se entregan como copia inmutable")
            void copiaInmutable() {
                EquipmentType tipo = conModalidad(
                        VerificationMode.PATRON_CONSTANTE, 3, List.of(punto("100")));

                assertThatThrownBy(() -> tipo.getPuntosVerificacion().clear())
                        .isInstanceOf(UnsupportedOperationException.class);
            }
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
