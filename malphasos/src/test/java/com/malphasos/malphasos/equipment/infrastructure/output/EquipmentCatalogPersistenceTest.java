package com.malphasos.malphasos.equipment.infrastructure.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.equipment.domain.brand.Brand;
import com.malphasos.malphasos.equipment.domain.equipmentType.EquipmentType;
import com.malphasos.malphasos.equipment.domain.equipmentType.TypeVerification;
import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationMode;
import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationPoint;
import com.malphasos.malphasos.equipment.domain.magnitude.Magnitude;
import com.malphasos.malphasos.equipment.domain.magnitude.MeasurementUnit;
import com.malphasos.malphasos.equipment.domain.manufacturer.Manufacturer;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

/**
 * Ejercita los adaptadores del catálogo base contra un PostgreSQL real.
 *
 * <p>Lo que más importa aquí es el tipo de equipo, y desde el 2026-10-03 por una razón nueva: sus
 * verificaciones cuelgan de un nivel intermedio con un <b>índice único parcial</b> sobre (tipo,
 * magnitud activa), y Hibernate vacía los {@code INSERT} antes que los {@code UPDATE}. Eso es
 * exactamente la trampa que el módulo de reportes pagó el 2026-09-27, y aquí había quedado latente: el
 * mismo problema existía ya con el índice de los puntos y <b>ninguna prueba lo ejercía</b>, porque todas
 * las reconfiguraciones de la batería cambiaban el valor del punto y el índice es parcial.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(
        statements = {"DELETE FROM marca", "DELETE FROM punto_verificacion",
            "DELETE FROM verificacion_tipo_equipo", "DELETE FROM tipo_equipo",
            "DELETE FROM fabricante"},
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class EquipmentCatalogPersistenceTest {

    @Autowired private ManufacturerPersistenceAdapter manufacturerAdapter;
    @Autowired private BrandPersistenceAdapter brandAdapter;
    @Autowired private EquipmentTypePersistenceAdapter equipmentTypeAdapter;
    @Autowired private MetrologyCatalogPersistenceAdapter metrologyAdapter;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Magnitude temperatura;
    private MeasurementUnit grados;
    private Magnitude humedad;
    private MeasurementUnit porciento;

    /**
     * El catálogo <b>no se construye aquí: se lee</b>, porque {@code V10} lo siembra.
     *
     * <p>Es la primera prueba del proyecto que depende de datos de una migración, como las de ubicación
     * dependen de los 249 países de {@code V7}. Si la siembra cambiara de códigos, esto fallaría, que es
     * lo que se quiere: el código los cita por su llave natural.
     */
    @BeforeEach
    void leerCatalogo() {
        temperatura = magnitud("temperatura");
        humedad = magnitud("humedad_relativa");
        grados = unidad(temperatura, "°C");
        porciento = unidad(humedad, "%HR");
    }

    private Magnitude magnitud(String codigo) {
        return metrologyAdapter.findAllMagnitudes().stream()
                .filter(m -> m.codigo().equals(codigo))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "V10 deberia sembrar la magnitud " + codigo));
    }

    private MeasurementUnit unidad(Magnitude magnitud, String simbolo) {
        return metrologyAdapter.findUnitsByMagnitude(magnitud.id()).stream()
                .filter(u -> u.simbolo().equals(simbolo))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "V10 deberia sembrar " + simbolo + " en " + magnitud.codigo()));
    }

    private String unico() {
        return String.valueOf(System.nanoTime());
    }

    private EquipmentType unTipo(List<TypeVerification> verificaciones) {
        return EquipmentType.create("Tipo " + unico(), "Definicion", "Cuidados", "Electronica",
                "Pesaje de pacientes", "Paño con alcohol al 70 %", verificaciones, 150_000L);
    }

    private TypeVerification enTemperatura(Integer cantidad, String... valores) {
        return TypeVerification.of(temperatura, grados,
                cantidad == null ? VerificationMode.PATRON_EQUIPO_VARIABLE
                        : VerificationMode.PATRON_CONSTANTE,
                cantidad,
                java.util.Arrays.stream(valores)
                        .map(valor -> VerificationPoint.of(new BigDecimal(valor)))
                        .toList());
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
    @DisplayName("el catalogo metrologico esta sembrado, y cada unidad en su magnitud")
    void catalogoSembrado() {
        assertThat(metrologyAdapter.findAllMagnitudes()).hasSizeGreaterThanOrEqualTo(20);
        assertThat(grados.esDeLaMagnitud(temperatura.id())).isTrue();
        assertThat(grados.esDeLaMagnitud(humedad.id())).isFalse();
        // El simbolo es unico POR magnitud y no en toda la tabla: '%' esta en las dos.
        assertThat(unidad(humedad, "%").simbolo()).isEqualTo(unidad(magnitud("concentracion"), "%").simbolo());
        assertThat(unidad(humedad, "%").id()).isNotEqualTo(unidad(magnitud("concentracion"), "%").id());
    }

    @Test
    @DisplayName("un tipo verificable guarda su verificacion, y el no verificable ninguna")
    void verificableEsDerivado() {
        // Ya no hay booleano ni columna de modalidad en tipo_equipo: 'se verifica' es 'tiene filas'.
        EquipmentType conUna = equipmentTypeAdapter.save(unTipo(List.of(enTemperatura(3, "100"))));
        EquipmentType sinNinguna = equipmentTypeAdapter.save(unTipo(List.of()));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM verificacion_tipo_equipo WHERE k_id_tipo_equipo = ?",
                        Integer.class, conUna.getId()))
                .isOne();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT n_modalidad_verificacion FROM verificacion_tipo_equipo"
                                + " WHERE k_id_tipo_equipo = ?",
                        String.class, conUna.getId()))
                .isEqualTo("patron_constante");
        assertThat(equipmentTypeAdapter.findById(conUna.getId()).orElseThrow().isVerificable())
                .isTrue();
        assertThat(equipmentTypeAdapter.findById(sinNinguna.getId()).orElseThrow().isVerificable())
                .isFalse();
    }

    @Test
    @DisplayName("dos magnitudes en el mismo tipo van y vuelven, cada una con su unidad y sus puntos")
    void dosMagnitudesPersisten() {
        // El termohigrometro, que es el caso que forzo el cambio del 2026-10-03.
        EquipmentType tipo = equipmentTypeAdapter.save(unTipo(List.of(
                enTemperatura(3, "-20.5", "37"),
                TypeVerification.of(humedad, porciento, VerificationMode.EQUIPO_CONSTANTE, 1,
                        List.of(VerificationPoint.of(new BigDecimal("40")))))));

        EquipmentType recuperado = equipmentTypeAdapter.findById(tipo.getId()).orElseThrow();

        assertThat(recuperado.verificacionesActivas()).hasSize(2);
        assertThat(recuperado.verificacionesActivas().stream().map(v -> v.magnitud().codigo()))
                .containsExactlyInAnyOrder("temperatura", "humedad_relativa");

        TypeVerification deTemperatura = recuperado.verificacionesActivas().stream()
                .filter(v -> v.magnitud().codigo().equals("temperatura"))
                .findFirst()
                .orElseThrow();

        assertThat(deTemperatura.unidad().simbolo()).isEqualTo("°C");
        assertThat(deTemperatura.cantidadDatos()).isEqualTo(3);
        // Ordenados por valor, y el negativo primero: un congelador se verifica bajo cero.
        assertThat(deTemperatura.puntosActivos().getFirst().valor()).isEqualByComparingTo("-20.5");
        // 2 puntos x 3 lecturas + 1 punto x 1 lectura.
        assertThat(recuperado.lecturasEsperadas()).isEqualTo(7);
    }

    @Test
    @DisplayName("una modalidad variable persiste sin cantidad y sin puntos")
    void variablePersiste() {
        EquipmentType tipo = equipmentTypeAdapter.save(unTipo(List.of(enTemperatura(null))));

        TypeVerification recuperada = equipmentTypeAdapter.findById(tipo.getId()).orElseThrow()
                .verificacionesActivas()
                .getFirst();

        assertThat(recuperada.modalidad()).isEqualTo(VerificationMode.PATRON_EQUIPO_VARIABLE);
        assertThat(recuperada.cantidadDatos()).isNull();
        assertThat(recuperada.puntosActivos()).isEmpty();
    }

    @Test
    @DisplayName("cambiar SOLO la cantidad de lecturas, dejando la misma magnitud y el mismo punto")
    void cambiarSoloLaCantidad() {
        // ESTA ES LA QUE DESTAPA LA TRAMPA, y no existia ninguna igual. Las reconfiguraciones de la
        // bateria cambiaban siempre el valor del punto, de modo que la fila nueva no chocaba con la
        // vieja: el indice es parcial y la retirada deja de contar. Aqui la magnitud sigue siendo
        // temperatura y el punto sigue siendo 100, asi que la fila retirada y la nueva compiten por la
        // misma clave del indice parcial -- y Hibernate vacia los INSERT antes que los UPDATE.
        EquipmentType tipo = equipmentTypeAdapter.save(unTipo(List.of(enTemperatura(3, "100"))));

        EquipmentType recuperado = equipmentTypeAdapter.findById(tipo.getId()).orElseThrow();
        recuperado.declareVerifications(List.of(enTemperatura(5, "100")));

        assertThatCode(() -> equipmentTypeAdapter.save(recuperado)).doesNotThrowAnyException();

        EquipmentType despues = equipmentTypeAdapter.findById(tipo.getId()).orElseThrow();

        assertThat(despues.verificacionesActivas()).hasSize(1);
        assertThat(despues.verificacionesActivas().getFirst().cantidadDatos()).isEqualTo(5);
        // La anterior se queda, retirada: con ella se firmaron los reportes de antes.
        assertThat(despues.getVerificaciones()).hasSize(2);
    }

    @Test
    @DisplayName("reconfigurar retira lo anterior en la base, y se puede volver a ello")
    void reconfigurarRetiraYSePuedeVolver() {
        // La segunda mitad justifica que los dos indices sean PARCIALES: volver a 100 cuando ya hay una
        // fila retirada con ese valor. Con una restriccion normal, reconfigurar hacia atras seria
        // imposible.
        EquipmentType tipo = equipmentTypeAdapter.save(unTipo(List.of(enTemperatura(3, "100"))));

        EquipmentType aDoscientos = equipmentTypeAdapter.findById(tipo.getId()).orElseThrow();
        aDoscientos.declareVerifications(List.of(enTemperatura(3, "200")));
        equipmentTypeAdapter.save(aDoscientos);

        EquipmentType conDoscientos = equipmentTypeAdapter.findById(tipo.getId()).orElseThrow();

        assertThat(conDoscientos.verificacionesActivas().getFirst().puntosActivos().getFirst().valor())
                .isEqualByComparingTo("200");
        assertThat(conDoscientos.getVerificaciones()).hasSize(2);

        conDoscientos.declareVerifications(List.of(enTemperatura(3, "100")));
        equipmentTypeAdapter.save(conDoscientos);

        EquipmentType deVuelta = equipmentTypeAdapter.findById(tipo.getId()).orElseThrow();

        assertThat(deVuelta.verificacionesActivas().getFirst().puntosActivos().getFirst().valor())
                .isEqualByComparingTo("100");
        assertThat(deVuelta.getVerificaciones()).hasSize(3);
    }

    @Test
    @DisplayName("retirar una verificacion retira sus puntos en la base, no solo en memoria")
    void retirarArrastraLosPuntos() {
        EquipmentType tipo = equipmentTypeAdapter.save(unTipo(List.of(enTemperatura(3, "100", "200"))));

        EquipmentType recuperado = equipmentTypeAdapter.findById(tipo.getId()).orElseThrow();
        recuperado.declareVerifications(List.of());
        equipmentTypeAdapter.save(recuperado);

        // Las filas siguen ahi -- nada se borra -- y todas inactivas.
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM punto_verificacion p"
                                + " JOIN verificacion_tipo_equipo v USING (k_id_verificacion)"
                                + " WHERE v.k_id_tipo_equipo = ? AND p.b_estado_activo",
                        Integer.class, tipo.getId()))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM punto_verificacion p"
                                + " JOIN verificacion_tipo_equipo v USING (k_id_verificacion)"
                                + " WHERE v.k_id_tipo_equipo = ?",
                        Integer.class, tipo.getId()))
                .isEqualTo(2);
        assertThat(equipmentTypeAdapter.findById(tipo.getId()).orElseThrow().isVerificable())
                .isFalse();
    }

    @Test
    @DisplayName("declarar y dejar de verificarse sobrevive al viaje de ida y vuelta")
    void declararYQuitarPersiste() {
        EquipmentType tipo = equipmentTypeAdapter.save(unTipo(List.of()));

        EquipmentType recuperado = equipmentTypeAdapter.findById(tipo.getId()).orElseThrow();
        recuperado.declareVerifications(List.of(enTemperatura(null)));
        equipmentTypeAdapter.save(recuperado);

        EquipmentType conVerificacion = equipmentTypeAdapter.findById(tipo.getId()).orElseThrow();
        assertThat(conVerificacion.isVerificable()).isTrue();
        assertThat(conVerificacion.verificacionesActivas().getFirst().modalidad())
                .isEqualTo(VerificationMode.PATRON_EQUIPO_VARIABLE);

        conVerificacion.declareVerifications(List.of());
        equipmentTypeAdapter.save(conVerificacion);

        assertThat(equipmentTypeAdapter.findById(tipo.getId()).orElseThrow().isVerificable())
                .isFalse();
    }

    @Test
    @DisplayName("el uso y la limpieza cotidiana van y vuelven de la base")
    void usoYLimpiezaPersisten() {
        // Entraron con V15, en el lugar de voltaje y amperaje, que bajaron al modelo: su ida y vuelta
        // se prueba ahora en ModelTechnicalSheetPersistenceTest.
        EquipmentType tipo = equipmentTypeAdapter.save(unTipo(List.of()));

        EquipmentType leido = equipmentTypeAdapter.findById(tipo.getId()).orElseThrow();
        assertThat(leido.getUso()).isEqualTo("Pesaje de pacientes");
        assertThat(leido.getLimpiezaCotidiana()).isEqualTo("Paño con alcohol al 70 %");
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
