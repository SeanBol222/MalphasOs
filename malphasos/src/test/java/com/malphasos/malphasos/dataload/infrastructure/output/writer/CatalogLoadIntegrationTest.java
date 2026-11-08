package com.malphasos.malphasos.dataload.infrastructure.output.writer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.dataload.EjemplosDeCarga;
import com.malphasos.malphasos.dataload.UbicacionDelEjemplo;
import com.malphasos.malphasos.dataload.application.exception.DataLoadRejectedException;
import com.malphasos.malphasos.dataload.application.model.DataLoadFile;
import com.malphasos.malphasos.dataload.application.model.DataLoadReport;
import com.malphasos.malphasos.dataload.application.model.SectionCount;
import com.malphasos.malphasos.dataload.application.ports.input.DataLoadServicePort;
import com.malphasos.malphasos.equipment.application.ports.output.BrandPersistencePort;
import com.malphasos.malphasos.equipment.application.ports.output.EquipmentTypePersistencePort;
import com.malphasos.malphasos.equipment.application.ports.output.ModelPersistencePort;
import com.malphasos.malphasos.equipment.application.ports.output.ProtocolAssignmentPersistencePort;
import com.malphasos.malphasos.equipment.application.ports.output.ProtocolPersistencePort;
import com.malphasos.malphasos.equipment.application.ports.output.ReferenceStandardPersistencePort;
import com.malphasos.malphasos.equipment.domain.brand.Brand;
import com.malphasos.malphasos.equipment.domain.equipmentType.EquipmentType;
import com.malphasos.malphasos.equipment.domain.equipmentType.TypeVerification;
import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationPoint;
import com.malphasos.malphasos.equipment.domain.model.Model;
import com.malphasos.malphasos.equipment.domain.model.RiskClass;
import com.malphasos.malphasos.equipment.domain.protocol.AssignedProtocol;
import com.malphasos.malphasos.equipment.domain.protocol.Protocol;
import com.malphasos.malphasos.equipment.domain.referenceStandard.ReferenceStandard;
import com.malphasos.malphasos.equipment.domain.referenceStandard.ReferenceStandardSheet;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

/**
 * El catálogo del ejemplo escrito contra PostgreSQL y leído de vuelta con los puertos de {@code equipment}.
 *
 * <p>Las pruebas que lo escriben entero corren en una transacción que se deshace al terminar, para no
 * dejar el catálogo del ejemplo en la base compartida. Las del rechazo no: lo que prueban es justo que la
 * transacción de la carga se deshace sola, y dentro de otra no se vería.
 *
 * <p>No se apoya en la siembra de {@code V7}: otras clases borran {@code pais}, y el fabricante del ejemplo
 * es de {@code USA}. Se crea si falta.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(statements = "DELETE FROM equipo_patron WHERE k_serie LIKE 'CARGA-%'", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class CatalogLoadIntegrationTest {

    @Autowired private DataLoadServicePort carga;
    @Autowired private BrandPersistencePort marcas;
    @Autowired private ProtocolPersistencePort protocolos;
    @Autowired private ProtocolAssignmentPersistencePort asignaciones;
    @Autowired private EquipmentTypePersistencePort tipos;
    @Autowired private ModelPersistencePort modelos;
    @Autowired private ReferenceStandardPersistencePort patrones;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void laUbicacionDelEjemplo() {
        UbicacionDelEjemplo.crear(jdbcTemplate);
    }

    private static final DataLoadFile CATALOGO = EjemplosDeCarga.archivo(EjemplosDeCarga.CATALOGO);

    private static String unico() {
        return String.format("%010d", Math.floorMod(System.nanoTime(), 10_000_000_000L));
    }

    private EquipmentType tipo(String nombre) {
        return tipos.findAll().stream().filter(EquipmentType::isEstadoActivo)
                .filter(t -> t.getNombre().equals(nombre)).findFirst().orElseThrow();
    }

    private long marcasLlamadas(String nombre) {
        return marcas.findAll().stream().filter(Brand::isEstadoActivo).filter(m -> m.getNombre().equals(nombre)).count();
    }

    @Test
    @Transactional
    @DisplayName("el tipo entra con su verificacion, sus puntos de hoy activos y los anteriores retirados")
    void elTipoConSusPuntos() {
        assertThat(carga.apply(List.of(CATALOGO)).errores()).isEmpty();

        TypeVerification presion = tipo("Tensiómetro análogo").verificacionesActivas().stream()
                .filter(v -> v.magnitud().codigo().equals("presion")).findFirst().orElseThrow();
        assertThat(presion.unidad().simbolo()).isEqualTo("mmHg");
        assertThat(presion.tolerancia()).isEqualByComparingTo("3");
        Map<Boolean, List<BigDecimal>> puntos = presion.puntos().stream().collect(Collectors.partitioningBy(
                VerificationPoint::estadoActivo, Collectors.mapping(VerificationPoint::valor, Collectors.toList())));
        assertThat(puntos.get(true)).usingElementComparator(BigDecimal::compareTo)
                .containsExactlyInAnyOrder(new BigDecimal("50"), new BigDecimal("100"), new BigDecimal("150"),
                        new BigDecimal("200"), new BigDecimal("250"));
        assertThat(puntos.get(false)).usingElementComparator(BigDecimal::compareTo).containsExactly(new BigDecimal("300"));

        assertThat(tipo("Termohigrómetro digital").verificacionesActivas())
                .extracting(v -> v.magnitud().codigo()).containsExactlyInAnyOrder("temperatura", "humedad_relativa");
    }

    @Test
    @Transactional
    @DisplayName("los protocolos del tipo entran en el orden del archivo")
    void losProtocolosEnOrden() {
        assertThat(carga.apply(List.of(CATALOGO)).errores()).isEmpty();

        Map<UUID, String> nombres = protocolos.findAll().stream().collect(Collectors.toMap(Protocol::getId, Protocol::getNombre));
        assertThat(asignaciones.findByType(tipo("Tensiómetro análogo").getId()).getProtocolos())
                .extracting(AssignedProtocol::idProtocolo).extracting(nombres::get)
                .containsExactly("Desinfección técnica inicial", "Sistema neumático", "Limpieza general");
    }

    @Test
    @Transactional
    @DisplayName("el modelo entra con su ficha, y el patron con su certificado mas reciente")
    void modeloYPatron() {
        assertThat(carga.apply(List.of(CATALOGO)).errores()).isEmpty();

        Model ds44 = modelos.findAll().stream().filter(m -> m.getNombre().equals("DS44")).findFirst().orElseThrow();
        assertThat(ds44.getFichaTecnica().riesgo()).isEqualTo(RiskClass.IIA);
        assertThat(ds44.getFichaTecnica().alimentacion()).isEqualTo("No aplica");

        ReferenceStandard manometro = patrones.findActiveBySerie("MD-0001").orElseThrow();
        assertThat(manometro.getCertificado()).isEqualTo("CAL-2025-014");
        assertThat(manometro.getVencimientoCertificado()).isEqualTo(LocalDate.of(2026, 1, 28));
        assertThat(manometro.getFicha().fechaCalibracion()).isEqualTo(LocalDate.of(2025, 1, 28));
        assertThat(manometro.getFicha().codigoInterno()).isEqualTo("PAT-01");
    }

    @Test
    @Transactional
    @DisplayName("aplicar dos veces el mismo catalogo no duplica nada: la segunda lo encuentra todo")
    void dosVeces() {
        assertThat(carga.apply(List.of(CATALOGO)).errores()).isEmpty();
        DataLoadReport segunda = carga.apply(List.of(CATALOGO));

        assertThat(segunda.errores()).isEmpty();
        assertThat(segunda.resumen()).filteredOn(s -> !s.seccion().equals("personas"))
                .allSatisfy(s -> assertThat(s.nuevos()).describedAs(s.seccion()).isZero());
        assertThat(segunda.resumen()).extracting(SectionCount::seccion).contains("marcas", "modelos", "patrones");
        assertThat(marcasLlamadas("Welch Allyn")).isEqualTo(1);
        assertThat(tipos.findAll().stream().filter(t -> t.getNombre().equals("Tensiómetro análogo")).count()).isEqualTo(1);
        assertThat(modelos.findAll().stream().filter(Model::isEstadoActivo).filter(m -> m.getNombre().equals("DS44")).count())
                .isEqualTo(1);
        assertThat(patrones.findAll().stream().filter(p -> p.getSerie().equals("MD-0001")).count()).isEqualTo(1);
    }

    /** El catálogo del ejemplo con una marca que ninguna otra prueba usa, para saber si quedó escrita. */
    private static String conMarcaUnica(String u) {
        String catalogo = EjemplosDeCarga.texto(EjemplosDeCarga.CATALOGO);
        String cambiado = catalogo.replace("\"nombre\": \"Welch Allyn\"", "\"nombre\": \"Marca " + u + "\"");
        assertThat(cambiado).describedAs("El ejemplo tiene que traer la marca Welch Allyn").isNotEqualTo(catalogo);
        return cambiado;
    }

    private static DataLoadFile archivo(String texto) {
        return new DataLoadFile("catalogo.json", texto.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("una regla del agregado que la validacion no ve rechaza la carga entera, y dice donde")
    void reglaDelAgregado() {
        String u = unico();
        String catalogo = conMarcaUnica(u).replace("\"fechaCalibracion\": \"2025-01-28\"", "\"fechaCalibracion\": \"2026-03-01\"");

        assertThatThrownBy(() -> carga.apply(List.of(archivo(catalogo))))
                .isInstanceOfSatisfying(DataLoadRejectedException.class, e -> {
                    assertThat(e.problema().ruta()).isEqualTo("/catalogo/patrones/0");
                    assertThat(e.problema().mensaje()).contains("posterior al vencimiento");
                });
        assertThat(marcasLlamadas("Marca " + u)).describedAs("la marca, escrita antes, se deshizo").isZero();
    }

    @Test
    @DisplayName("una restriccion de la base rechaza la carga entera con un problema que se lee")
    void restriccionDeLaBase() {
        String u = unico();
        patrones.save(ReferenceStandard.create("Patron que ya estaba", null, "CARGA-" + u, "C-1", LocalDate.of(2030, 1, 1),
                new ReferenceStandardSheet(null, "COD-" + u, null, null, null, null)));
        String catalogo = conMarcaUnica(u).replace("\"codigoInterno\": \"PAT-01\"", "\"codigoInterno\": \"COD-" + u + "\"");

        assertThatThrownBy(() -> carga.apply(List.of(archivo(catalogo))))
                .isInstanceOfSatisfying(DataLoadRejectedException.class,
                        e -> assertThat(e.problema().mensaje()).contains("La base de datos rechazó la carga"));
        assertThat(marcasLlamadas("Marca " + u)).isZero();
    }
}
