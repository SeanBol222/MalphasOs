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
import com.malphasos.malphasos.equipment.domain.equipmentType.TypeVerification;
import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationPoint;
import com.malphasos.malphasos.equipment.domain.magnitude.Magnitude;
import com.malphasos.malphasos.equipment.domain.magnitude.MeasurementUnit;
import com.malphasos.malphasos.equipment.domain.manufacturer.Manufacturer;
import com.malphasos.malphasos.equipment.domain.model.Model;
import com.malphasos.malphasos.equipment.domain.model.TechnicalSheet;
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

    private static final UUID ID_TEMPERATURA = UUID.randomUUID();
    private static final UUID ID_HUMEDAD = UUID.randomUUID();

    private static final Magnitude TEMPERATURA =
            new Magnitude(ID_TEMPERATURA, "temperatura", "Temperatura", true);
    private static final MeasurementUnit GRADOS =
            new MeasurementUnit(UUID.randomUUID(), ID_TEMPERATURA, "°C", "grado Celsius", true);
    private static final Magnitude HUMEDAD =
            new Magnitude(ID_HUMEDAD, "humedad_relativa", "Humedad relativa", true);
    private static final MeasurementUnit PORCIENTO =
            new MeasurementUnit(UUID.randomUUID(), ID_HUMEDAD, "%HR", "por ciento", true);

    /** Un tipo con la modalidad indicada en temperatura, o sin verificaciones si llega nula. */
    private EquipmentType unTipo(VerificationMode modalidad) {
        // Las modalidades constantes exigen cantidad y al menos un punto; la variable no los admite.
        boolean constante = modalidad == VerificationMode.PATRON_CONSTANTE
                || modalidad == VerificationMode.EQUIPO_CONSTANTE;

        List<TypeVerification> verificaciones = modalidad == null
                ? List.of()
                : List.of(TypeVerification.of(TEMPERATURA, GRADOS, modalidad,
                        constante ? 3 : null,
                        constante ? List.of(VerificationPoint.of(new BigDecimal("100"))) : List.of()));

        return EquipmentType.create("Monitor", "Definicion", "Cuidados", "Electronica",
                "Pesaje de pacientes", "Paño con alcohol al 70 %", verificaciones, 150_000L);
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
        @DisplayName("declarar una verificacion vuelve verificable el tipo, y la lista vacia lo revierte")
        void cambiarVerificaciones() {
            EquipmentType tipo = unTipo(null);
            tipo.pullEvents();

            tipo.declareVerifications(List.of(TypeVerification.of(TEMPERATURA, GRADOS,
                    VerificationMode.EQUIPO_CONSTANTE, 3,
                    List.of(VerificationPoint.of(new BigDecimal("100"))))));
            assertThat(tipo.isVerificable()).isTrue();
            assertThat(tipo.pullEvents()).hasSize(1);

            tipo.declareVerifications(List.of());
            assertThat(tipo.isVerificable()).isFalse();
            assertThat(tipo.pullEvents()).hasSize(1);
        }

        @Test
        @DisplayName("declarar lo que ya tenia no emite")
        void verificacionIgual() {
            EquipmentType tipo = unTipo(VerificationMode.PATRON_CONSTANTE);
            tipo.pullEvents();

            tipo.declareVerifications(List.of(TypeVerification.of(TEMPERATURA, GRADOS,
                    VerificationMode.PATRON_CONSTANTE, 3,
                    List.of(VerificationPoint.of(new BigDecimal("100"))))));

            assertThat(tipo.pullEvents()).isEmpty();
        }

        // Voltaje y amperaje se probaban aqui hasta V15: bajaron al modelo, ver ModelTechnicalSheetTest.

        @Test
        @DisplayName("el uso y la limpieza cotidiana son opcionales, y un texto en blanco es no tenerlos")
        void usoYLimpiezaOpcionales() {
            EquipmentType tipo = EquipmentType.create("M", "D", "C", "E", "  ", null, List.of(), 1000L);

            assertThat(tipo.getUso()).isNull();
            assertThat(tipo.getLimpiezaCotidiana()).isNull();
            assertThat(unTipo(null).getUso()).isEqualTo("Pesaje de pacientes");
        }

        @Test
        @DisplayName("al corregir, un nulo deja el uso como esta y un blanco lo vacia")
        void corregirUso() {
            EquipmentType tipo = unTipo(null);
            tipo.pullEvents();

            tipo.update(null, null, null, null, null, null, null);
            assertThat(tipo.getUso()).isEqualTo("Pesaje de pacientes");
            assertThat(tipo.pullEvents()).isEmpty();

            tipo.update(null, null, null, null, " ", "Paño seco", null);
            assertThat(tipo.getUso()).isNull();
            assertThat(tipo.getLimpiezaCotidiana()).isEqualTo("Paño seco");
            assertThat(tipo.pullEvents()).hasSize(1);
        }

        @Test
        @DisplayName("el valor del mantenimiento no puede ser negativo")
        void valorNoNegativo() {
            assertThatThrownBy(() -> EquipmentType.create("M", "D", "C", "E", null, null, List.of(), -1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("negativo");
        }

        @Test
        @DisplayName("los cuatro textos descriptivos son obligatorios")
        void textosObligatorios() {
            assertThatThrownBy(() -> EquipmentType.create(" ", "D", "C", "E", null, null, List.of(), 0L))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> EquipmentType.create("M", " ", "C", "E", null, null, List.of(), 0L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Nested
        @DisplayName("Que, con que y cuantas veces se verifica")
        class Verificacion {

            private EquipmentType conVerificaciones(List<TypeVerification> verificaciones) {
                return EquipmentType.create("Monitor", "D", "C", "E", null, null,
                        verificaciones, 1000L);
            }

            private TypeVerification temperatura(
                    VerificationMode modalidad, Integer cantidad, List<VerificationPoint> puntos) {

                return TypeVerification.of(TEMPERATURA, GRADOS, modalidad, cantidad, puntos);
            }

            private VerificationPoint punto(String valor) {
                return VerificationPoint.of(new BigDecimal(valor));
            }

            @Test
            @DisplayName("un tipo se verifica en varias magnitudes a la vez")
            void variasMagnitudes() {
                // Es el caso que forzo el cambio del 2026-10-03: un termohigrometro mide temperatura y
                // humedad, y con el modelo anterior habia que registrarlo como dos tipos de equipo.
                EquipmentType tipo = conVerificaciones(List.of(
                        temperatura(VerificationMode.PATRON_CONSTANTE, 3, List.of(punto("-20"))),
                        TypeVerification.of(HUMEDAD, PORCIENTO,
                                VerificationMode.PATRON_EQUIPO_VARIABLE, null, List.of())));

                assertThat(tipo.verificacionesActivas()).hasSize(2);
                assertThat(tipo.verificacionesActivas().stream()
                                .map(v -> v.magnitud().nombre()))
                        .containsExactly("Temperatura", "Humedad relativa");
                // Y cada una lleva su propia modalidad: con el modelo anterior tenian que coincidir.
                assertThat(tipo.verificacionesActivas().stream().map(TypeVerification::modalidad))
                        .containsExactly(VerificationMode.PATRON_CONSTANTE,
                                VerificationMode.PATRON_EQUIPO_VARIABLE);
            }

            @Test
            @DisplayName("la misma magnitud dos veces se rechaza")
            void magnitudRepetida() {
                // Dos verificaciones de temperatura en el mismo aparato son la misma escrita dos veces:
                // la segunda contradice a la primera sin que nada diga cual vale.
                assertThatThrownBy(() -> conVerificaciones(List.of(
                                temperatura(VerificationMode.PATRON_CONSTANTE, 3, List.of(punto("100"))),
                                temperatura(VerificationMode.EQUIPO_CONSTANTE, 1, List.of(punto("200"))))))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("dos veces");
            }

            @Test
            @DisplayName("una unidad de otra magnitud no se puede ni construir")
            void unidadDeOtraMagnitud() {
                // Ultima defensa: el servicio lo comprueba antes con codigo de error propio y el esquema
                // lo impone con una foranea compuesta. Aqui es para que el objeto no exista ni en una
                // prueba.
                assertThatThrownBy(() -> TypeVerification.of(TEMPERATURA, PORCIENTO,
                                VerificationMode.PATRON_CONSTANTE, 3, List.of(punto("100"))))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("no es de la magnitud");
            }

            @Test
            @DisplayName("una verificacion sin modalidad no es una verificacion")
            void sinModalidad() {
                // Al contrario que antes: la modalidad del TIPO podia ser nula y significaba "no se
                // verifica". Ahora eso se dice con la lista vacia, y una verificacion sin modalidad no
                // tiene sentido.
                assertThatThrownBy(() -> TypeVerification.of(TEMPERATURA, GRADOS, null, 3,
                                List.of(punto("100"))))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("modalidad");
            }

            @Test
            @DisplayName("una modalidad constante exige cuantas lecturas y al menos un punto")
            void constanteExigeAmbos() {
                // Son los dos datos que hacen falta para llenar el reporte: sin ellos, la verificacion
                // dice como se hace y no con que.
                assertThatThrownBy(() -> temperatura(
                                VerificationMode.PATRON_CONSTANTE, null, List.of(punto("100"))))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("lecturas por punto");

                assertThatThrownBy(() -> temperatura(
                                VerificationMode.EQUIPO_CONSTANTE, 3, List.of()))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("al menos un punto");
            }

            @Test
            @DisplayName("la cantidad de lecturas va de 1 a 100")
            void cantidadAcotada() {
                for (Integer cantidad : new Integer[] {0, 101}) {
                    assertThatThrownBy(() -> temperatura(
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
                assertThatThrownBy(() -> temperatura(
                                VerificationMode.PATRON_EQUIPO_VARIABLE, 3, List.of()))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("lo decide el ingeniero");

                assertThatThrownBy(() -> temperatura(
                                VerificationMode.PATRON_EQUIPO_VARIABLE, null, List.of(punto("100"))))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("no hay puntos");
            }

            @Test
            @DisplayName("un tipo sin verificaciones no es verificable y no falla")
            void sinVerificaciones() {
                // Antes esto era "modalidad nula no admite cantidad ni puntos". Ya no hace falta: sin
                // modalidad no hay verificacion donde ponerlos, de modo que el estado es inexpresable.
                EquipmentType tipo = conVerificaciones(List.of());

                assertThat(tipo.isVerificable()).isFalse();
                assertThat(tipo.lecturasEsperadas()).isZero();
            }

            @Test
            @DisplayName("el mismo punto escrito distinto sigue siendo el mismo")
            void puntoRepetido() {
                // 100 y 100.0000 son el mismo punto, y equals de BigDecimal diria que no. Sin esto, el
                // reporte tendria dos veces la misma medicion.
                assertThatThrownBy(() -> temperatura(VerificationMode.PATRON_CONSTANTE, 3,
                                List.of(punto("100"), punto("100.0000"))))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("dos veces");
            }

            @Test
            @DisplayName("el mismo valor en dos magnitudes distintas vale")
            void mismoValorEnDosMagnitudes() {
                // 40 grados y 40 por ciento no son el mismo punto. Con el modelo anterior los puntos
                // colgaban del aparato entero y solo se distinguian por la unidad escrita a mano.
                EquipmentType tipo = conVerificaciones(List.of(
                        temperatura(VerificationMode.PATRON_CONSTANTE, 1, List.of(punto("40"))),
                        TypeVerification.of(HUMEDAD, PORCIENTO, VerificationMode.EQUIPO_CONSTANTE, 1,
                                List.of(punto("40")))));

                assertThat(tipo.verificacionesActivas()).hasSize(2);
                assertThat(tipo.lecturasEsperadas()).isEqualTo(2);
            }

            @Test
            @DisplayName("un punto negativo vale: un congelador se verifica a -20 grados")
            void puntoNegativo() {
                EquipmentType tipo = conVerificaciones(List.of(
                        temperatura(VerificationMode.EQUIPO_CONSTANTE, 5, List.of(punto("-20")))));

                assertThat(tipo.verificacionesActivas().getFirst().puntosActivos()).hasSize(1);
                assertThat(tipo.verificacionesActivas().getFirst().puntosActivos().getFirst().valor())
                        .isEqualByComparingTo("-20");
            }

            @Test
            @DisplayName("los puntos se recorren ordenados por valor")
            void puntosOrdenados() {
                EquipmentType tipo = conVerificaciones(List.of(temperatura(
                        VerificationMode.PATRON_CONSTANTE, 3,
                        List.of(punto("150"), punto("50"), punto("100")))));

                assertThat(tipo.verificacionesActivas().getFirst().puntosActivos().stream()
                                .map(VerificationPoint::valor))
                        .containsExactly(
                                new BigDecimal("50.0000"),
                                new BigDecimal("100.0000"),
                                new BigDecimal("150.0000"));
            }

            @Test
            @DisplayName("cuantas lecturas pide un reporte completo: puntos por cantidad, sumado")
            void lecturasEsperadas() {
                EquipmentType tipo = conVerificaciones(List.of(
                        temperatura(VerificationMode.PATRON_CONSTANTE, 3,
                                List.of(punto("-20"), punto("0"), punto("37"))),
                        TypeVerification.of(HUMEDAD, PORCIENTO, VerificationMode.EQUIPO_CONSTANTE, 1,
                                List.of(punto("40"), punto("80")))));

                // 3 puntos x 3 lecturas + 2 puntos x 1 lectura. Con el modelo anterior, una sola
                // cantidad para todo el aparato habria dado 15 o 6, nunca 11.
                assertThat(tipo.lecturasEsperadas()).isEqualTo(11);
            }

            @Test
            @DisplayName("reconfigurar retira lo anterior, no lo borra, y arrastra sus puntos")
            void reconfigurarRetira() {
                // Con lo de antes se firmaron los reportes anteriores: tiene que seguir existiendo.
                EquipmentType tipo = conVerificaciones(List.of(
                        temperatura(VerificationMode.PATRON_CONSTANTE, 3, List.of(punto("100")))));

                tipo.declareVerifications(List.of(
                        temperatura(VerificationMode.EQUIPO_CONSTANTE, 5, List.of(punto("200")))));

                assertThat(tipo.verificacionesActivas()).hasSize(1);
                assertThat(tipo.verificacionesActivas().getFirst().cantidadDatos()).isEqualTo(5);
                assertThat(tipo.getVerificaciones()).hasSize(2);

                TypeVerification retirada = tipo.getVerificaciones().stream()
                        .filter(v -> !v.estadoActivo())
                        .findFirst()
                        .orElseThrow();

                // Retirar la verificacion retira sus puntos con ella: dejarlos activos los habria
                // seguido ofreciendo desde algo que ya no se verifica.
                assertThat(retirada.puntos()).hasSize(1);
                assertThat(retirada.puntosActivos()).isEmpty();
            }

            @Test
            @DisplayName("dejar de verificarse retira todo y no borra nada")
            void dejarDeVerificarse() {
                EquipmentType tipo = conVerificaciones(List.of(
                        temperatura(VerificationMode.PATRON_CONSTANTE, 3, List.of(punto("100")))));

                tipo.declareVerifications(List.of());

                assertThat(tipo.isVerificable()).isFalse();
                assertThat(tipo.verificacionesActivas()).isEmpty();
                assertThat(tipo.lecturasEsperadas()).isZero();
                // Pero la verificacion sigue en la lista, retirada, y se puede encontrar por su id:
                // un reporte viejo apunta a ella para poder imprimirse.
                assertThat(tipo.getVerificaciones()).hasSize(1);
                assertThat(tipo.verificacionPorId(tipo.getVerificaciones().getFirst().id()))
                        .isPresent();
            }

            @Test
            @DisplayName("una configuracion identica no emite evento")
            void configuracionIdentica() {
                // La convencion del proyecto: un cambio que no cambia nada no es un hecho del dominio.
                EquipmentType tipo = conVerificaciones(List.of(
                        temperatura(VerificationMode.PATRON_CONSTANTE, 3, List.of(punto("100")))));
                tipo.pullEvents();

                tipo.declareVerifications(List.of(
                        temperatura(VerificationMode.PATRON_CONSTANTE, 3, List.of(punto("100.0000")))));

                assertThat(tipo.pullEvents()).isEmpty();
            }

            @Test
            @DisplayName("cambiar solo la unidad si cuenta como cambio")
            void cambiarSoloLaUnidad() {
                // Pasar de grados Celsius a kelvin no cambia ni la magnitud ni los puntos ni la
                // cantidad, pero cambia lo que el reporte imprime.
                MeasurementUnit kelvin =
                        new MeasurementUnit(UUID.randomUUID(), ID_TEMPERATURA, "K", "kelvin", true);
                EquipmentType tipo = conVerificaciones(List.of(
                        temperatura(VerificationMode.PATRON_CONSTANTE, 3, List.of(punto("100")))));
                tipo.pullEvents();

                tipo.declareVerifications(List.of(TypeVerification.of(TEMPERATURA, kelvin,
                        VerificationMode.PATRON_CONSTANTE, 3, List.of(punto("100")))));

                assertThat(tipo.pullEvents()).hasSize(1);
                assertThat(tipo.verificacionesActivas().getFirst().unidad().simbolo()).isEqualTo("K");
            }

            @Test
            @DisplayName("las verificaciones se entregan como copia inmutable")
            void copiaInmutable() {
                EquipmentType tipo = conVerificaciones(List.of(
                        temperatura(VerificationMode.PATRON_CONSTANTE, 3, List.of(punto("100")))));

                assertThatThrownBy(() -> tipo.getVerificaciones().clear())
                        .isInstanceOf(UnsupportedOperationException.class);
                assertThatThrownBy(() ->
                                tipo.verificacionesActivas().getFirst().puntos().clear())
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
            assertThatThrownBy(() -> Model.create("IdeaPad 3", "INV-1", null, EQUIPO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("fabricante");
            assertThatThrownBy(() -> Model.create("IdeaPad 3", "INV-1", FABRICANTE, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("equipo");
        }

        @Test
        @DisplayName("necesita su nombre: es lo que lo distingue de los otros de su marca")
        void nombreObligatorio() {
            // Hasta el 2026-10-04 lo unico legible que un modelo llevaba era su INVIMA, que es un
            // numero de tramite y ademas anulable: cabia un modelo sin nada que escribir en una fila.
            for (String nombre : new String[] {null, "", "   "}) {
                assertThatThrownBy(() -> Model.create(nombre, "INV-1", FABRICANTE, EQUIPO))
                        .describedAs("nombre %s", nombre == null ? "nulo" : "«" + nombre + "»")
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("nombre");
            }
        }

        @Test
        @DisplayName("el nombre se recorta, y un INVIMA en blanco es lo mismo que no tenerlo")
        void nombreRecortadoEInvimaEnBlanco() {
            // Las dos columnas se tratan distinto a proposito: un modelo SIN registro sanitario es un
            // estado normal mientras se tramita, y un modelo sin nombre no es nada.
            Model modelo = Model.create("  IdeaPad 3  ", "   ", FABRICANTE, EQUIPO);

            assertThat(modelo.getNombre()).isEqualTo("IdeaPad 3");
            assertThat(modelo.getInvima()).isNull();
        }

        @Test
        @DisplayName("renombrar con el mismo nombre no anuncia un cambio que no ocurrio")
        void renombrarIgual() {
            Model modelo = Model.create("IdeaPad 3", null, FABRICANTE, EQUIPO);
            modelo.pullEvents();

            modelo.rename("  IdeaPad 3  ");
            assertThat(modelo.pullEvents()).isEmpty();

            modelo.rename("IdeaPad 5");
            assertThat(modelo.getNombre()).isEqualTo("IdeaPad 5");
            assertThat(modelo.pullEvents()).hasSize(1);
        }

        @Test
        @DisplayName("renombrar a nada se rechaza: el nombre no se puede vaciar")
        void renombrarANada() {
            Model modelo = Model.create("IdeaPad 3", null, FABRICANTE, EQUIPO);

            assertThatThrownBy(() -> modelo.rename("  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nombre");
            assertThat(modelo.getNombre()).isEqualTo("IdeaPad 3");
        }

        @Test
        @DisplayName("el INVIMA se puede anotar despues y corregir")
        void anotarInvima() {
            Model modelo = Model.create("IdeaPad 3", null, FABRICANTE, EQUIPO);
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
                    LocalDate.now().minusYears(1), 5_000_000L, null, null);
        }

        @Test
        @DisplayName("el codigo interno y el proveedor son opcionales, y un blanco es no tenerlos")
        void codigoYProveedorOpcionales() {
            ClientEquipment unidad = ClientEquipment.register("SN-002", MODELO, AREA, null, null, null,
                    "  BAL-07 ", " ");

            assertThat(unidad.getCodigoInterno()).isEqualTo("BAL-07");
            assertThat(unidad.getProveedor()).isNull();
        }

        @Test
        @DisplayName("corregir el proveedor emite un evento; un nulo lo deja y un blanco lo vacia")
        void corregirProveedor() {
            ClientEquipment unidad = unaUnidad();
            unidad.pullEvents();

            unidad.update(null, null, null, null, "Distribuidora Medica");
            assertThat(unidad.getProveedor()).isEqualTo("Distribuidora Medica");
            assertThat(unidad.pullEvents()).hasSize(1);

            unidad.update(null, null, null, null, null);
            assertThat(unidad.getProveedor()).isEqualTo("Distribuidora Medica");
            assertThat(unidad.pullEvents()).isEmpty();

            unidad.update(null, null, null, null, " ");
            assertThat(unidad.getProveedor()).isNull();
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
            assertThatThrownBy(() -> ClientEquipment.register(" ", MODELO, AREA, null, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ClientEquipment.register("SN", null, AREA, null, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ClientEquipment.register("SN", MODELO, null, null, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("un equipo no se compro en el futuro")
        void fechaDeCompraNoFutura() {
            assertThatThrownBy(() -> ClientEquipment.register("SN", MODELO, AREA, null,
                            LocalDate.now().plusDays(1), null, null, null))
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
    @DisplayName("rehidratar no emite, y Brand compara por identidad")
    void identidadYRehidratacion() {
        // RENOMBRADA EL 2026-10-04, porque el nombre afirmaba mas que el cuerpo. Se llamaba «los seis
        // agregados comparan por identidad, y rehidratar no emite» y de los seis solo afirma la
        // igualdad de Brand: de los otros cinco comprueba unicamente que rehidratar no emita eventos.
        //
        // Un nombre que promete mas que el cuerpo es peor que uno vago, porque se lee en una revision
        // y se da por hecho. La igualdad por identidad de los diecisiete agregados la cubre ahora
        // AggregateIdentityContractTest, que lo comprueba por la forma del codigo -- la anotacion de
        // Lombok es de retencion SOURCE y no existe en tiempo de ejecucion.
        UUID id = UUID.randomUUID();

        assertThat(Brand.rehydrate(id, "Uno", true)).isEqualTo(Brand.rehydrate(id, "Otro", false));
        assertThat(Manufacturer.rehydrate(id, "Uno", null, true).hasPendingEvents()).isFalse();
        assertThat(Equipment.rehydrate(id, TIPO, MARCA, true).hasPendingEvents()).isFalse();
        assertThat(Model.rehydrate(id, "IdeaPad 3", null, FABRICANTE, EQUIPO, TechnicalSheet.EMPTY, true).hasPendingEvents()).isFalse();
        assertThat(ClientEquipment.rehydrate(id, "SN", MODELO, AREA, null, null, null, null, null, true)
                        .hasPendingEvents())
                .isFalse();
    }
}
