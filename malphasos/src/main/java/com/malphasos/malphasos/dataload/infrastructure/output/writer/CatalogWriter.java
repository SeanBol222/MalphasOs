package com.malphasos.malphasos.dataload.infrastructure.output.writer;

import static com.malphasos.malphasos.dataload.infrastructure.output.writer.Steps.paso;

import com.malphasos.malphasos.dataload.application.model.LoadDocument;
import com.malphasos.malphasos.dataload.application.model.ReadFile;
import com.malphasos.malphasos.dataload.application.services.Names;
import com.malphasos.malphasos.equipment.application.ports.output.BrandPersistencePort;
import com.malphasos.malphasos.equipment.application.ports.output.EquipmentPersistencePort;
import com.malphasos.malphasos.equipment.application.ports.output.EquipmentTypePersistencePort;
import com.malphasos.malphasos.equipment.application.ports.output.ManufacturerPersistencePort;
import com.malphasos.malphasos.equipment.application.ports.output.MetrologyCatalogPersistencePort;
import com.malphasos.malphasos.equipment.application.ports.output.ModelPersistencePort;
import com.malphasos.malphasos.equipment.application.ports.output.ProtocolAssignmentPersistencePort;
import com.malphasos.malphasos.equipment.application.ports.output.ProtocolPersistencePort;
import com.malphasos.malphasos.equipment.application.ports.output.ReferenceStandardPersistencePort;
import com.malphasos.malphasos.equipment.domain.brand.Brand;
import com.malphasos.malphasos.equipment.domain.equipment.Equipment;
import com.malphasos.malphasos.equipment.domain.equipmentType.EquipmentType;
import com.malphasos.malphasos.equipment.domain.equipmentType.TypeVerification;
import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationMode;
import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationPoint;
import com.malphasos.malphasos.equipment.domain.magnitude.Magnitude;
import com.malphasos.malphasos.equipment.domain.magnitude.MeasurementUnit;
import com.malphasos.malphasos.equipment.domain.manufacturer.Manufacturer;
import com.malphasos.malphasos.equipment.domain.model.Model;
import com.malphasos.malphasos.equipment.domain.model.RiskClass;
import com.malphasos.malphasos.equipment.domain.model.TechnicalSheet;
import com.malphasos.malphasos.equipment.domain.protocol.Protocol;
import com.malphasos.malphasos.equipment.domain.protocol.ProtocolAssignment;
import com.malphasos.malphasos.equipment.domain.referenceStandard.ReferenceStandard;
import com.malphasos.malphasos.equipment.domain.referenceStandard.ReferenceStandardSheet;
import com.malphasos.malphasos.location.application.ports.output.CountryPersistencePort;
import com.malphasos.malphasos.location.domain.country.Country;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Escribe el catálogo de una carga: marcas, fabricantes, protocolos, tipos de equipo con sus
 * verificaciones y sus protocolos, modelos y patrones.
 *
 * <p>Lo nuevo entra por el {@code create} de su agregado, que valida como lo haría la pantalla. Lo que
 * ya existe se reconoce por su llave natural, igual que la validación, y no se toca: un tipo que ya está
 * conserva sus verificaciones y sus protocolos, y la carga solo aprende con cuáles de ellos casan las
 * suyas.
 *
 * <p>La única pieza que no sale de un {@code create} son los puntos anteriores: entran retirados, como
 * cuando alguien cambia los puntos de un tipo desde la pantalla, para que las lecturas viejas los apunten.
 */
@Component
@RequiredArgsConstructor
class CatalogWriter {

    private final BrandPersistencePort marcas;
    private final ManufacturerPersistencePort fabricantes;
    private final ProtocolPersistencePort protocolos;
    private final ProtocolAssignmentPersistencePort asignaciones;
    private final EquipmentTypePersistencePort tipos;
    private final EquipmentPersistencePort asociaciones;
    private final ModelPersistencePort modelos;
    private final ReferenceStandardPersistencePort patrones;
    private final MetrologyCatalogPersistencePort metrologia;
    private final CountryPersistencePort paises;
    private final EntityManager entityManager;

    void write(ReadFile archivo, LoadRefs refs) {
        LoadDocument.Catalog catalogo = archivo.documento().catalogo();
        String f = archivo.nombre();
        List<Runnable> asignacionesPendientes = new ArrayList<>();

        Map<String, UUID> marcasQueEstan = new HashMap<>();
        marcas.findAll().stream().filter(Brand::isEstadoActivo)
                .forEach(m -> marcasQueEstan.putIfAbsent(Names.plano(m.getNombre()), m.getId()));
        for (int i = 0; i < catalogo.marcas().size(); i++) {
            LoadDocument.Brand marca = catalogo.marcas().get(i);
            int n = i;
            refs.put(marca.ref(), marcasQueEstan.computeIfAbsent(Names.plano(marca.nombre()), k ->
                    paso(f, "/catalogo/marcas/", n, () -> marcas.save(Brand.create(marca.nombre())).getId())));
        }

        Map<String, UUID> fabricantesQueEstan = new HashMap<>();
        fabricantes.findAll().stream().filter(Manufacturer::isEstadoActivo)
                .forEach(m -> fabricantesQueEstan.putIfAbsent(Names.plano(m.getNombre()), m.getId()));
        Map<String, UUID> paisPorCodigo = paises.findAll().stream().filter(Country::isEstadoActivo)
                .collect(Collectors.toMap(Country::getCodigoIso, Country::getId, (a, b) -> a));
        for (int i = 0; i < catalogo.fabricantes().size(); i++) {
            LoadDocument.Manufacturer fabricante = catalogo.fabricantes().get(i);
            int n = i;
            refs.put(fabricante.ref(), fabricantesQueEstan.computeIfAbsent(Names.plano(fabricante.nombre()), k ->
                    paso(f, "/catalogo/fabricantes/", n, () -> fabricantes.save(Manufacturer.create(fabricante.nombre(),
                            fabricante.pais() == null ? null : paisPorCodigo.get(fabricante.pais()))).getId())));
        }

        Map<String, UUID> protocolosQueEstan = new HashMap<>();
        protocolos.findAll().stream().filter(Protocol::isEstadoActivo)
                .forEach(p -> protocolosQueEstan.putIfAbsent(Names.plano(p.getNombre()), p.getId()));
        for (int i = 0; i < catalogo.protocolos().size(); i++) {
            LoadDocument.Protocol protocolo = catalogo.protocolos().get(i);
            int n = i;
            refs.put(protocolo.ref(), protocolosQueEstan.computeIfAbsent(Names.plano(protocolo.nombre()), k ->
                    paso(f, "/catalogo/protocolos/", n,
                            () -> protocolos.save(Protocol.create(protocolo.nombre(), protocolo.descripcion())).getId())));
        }

        Map<String, EquipmentType> tiposQueEstan = new HashMap<>();
        tipos.findAll().stream().filter(EquipmentType::isEstadoActivo)
                .forEach(t -> tiposQueEstan.putIfAbsent(Names.plano(t.getNombre()), t));
        for (int i = 0; i < catalogo.tiposEquipo().size(); i++) {
            LoadDocument.EquipmentType tipo = catalogo.tiposEquipo().get(i);
            int n = i;
            EquipmentType yaEsta = tiposQueEstan.get(Names.plano(tipo.nombre()));
            EquipmentType escrito = yaEsta != null ? yaEsta : paso(f, "/catalogo/tiposEquipo/", n,
                    () -> nuevoTipo(tipo, refs, asignacionesPendientes, f, n));
            tiposQueEstan.put(Names.plano(tipo.nombre()), escrito);
            refs.put(tipo.ref(), escrito.getId());
            for (int v = 0; v < tipo.verificaciones().size(); v++) {
                LoadDocument.Verification verificacion = tipo.verificaciones().get(v);
                int m = v;
                TypeVerification suya = escrito.verificacionesActivas().stream()
                        .filter(t -> t.magnitud().codigo().equals(verificacion.magnitud()))
                        .findFirst()
                        .orElseThrow(() -> Steps.rechazo(f, "/catalogo/tiposEquipo/" + n + "/verificaciones/" + m,
                                "El tipo «" + tipo.nombre()
                                + "» ya está en MalphasOS y no verifica " + verificacion.magnitud()
                                + ": la carga no cambia un tipo que ya existe. Se añade desde el catálogo antes de cargar"));
                refs.putVerification(verificacion.ref(), new LoadRefs.VerificationRef(escrito.getId(), suya));
            }
        }

        Map<String, UUID> asociacionPorTipoYMarca = new HashMap<>();
        asociaciones.findAll().stream().filter(Equipment::isEstadoActivo)
                .forEach(a -> asociacionPorTipoYMarca.putIfAbsent(a.getIdTipoEquipo() + "|" + a.getIdMarca(), a.getId()));
        Map<String, UUID> modelosQueEstan = new HashMap<>();
        modelos.findAll().stream().filter(Model::isEstadoActivo)
                .forEach(m -> modelosQueEstan.putIfAbsent(m.getIdEquipo() + "|" + Names.plano(m.getNombre()), m.getId()));
        for (int i = 0; i < catalogo.modelos().size(); i++) {
            LoadDocument.Model modelo = catalogo.modelos().get(i);
            int n = i;
            UUID idTipo = refs.id(modelo.tipo());
            UUID idMarca = refs.id(modelo.marca());
            UUID asociacion = asociacionPorTipoYMarca.computeIfAbsent(idTipo + "|" + idMarca, k ->
                    paso(f, "/catalogo/modelos/", n, () -> asociaciones.save(Equipment.create(idTipo, idMarca)).getId()));
            refs.put(modelo.ref(), modelosQueEstan.computeIfAbsent(asociacion + "|" + Names.plano(modelo.nombre()), k ->
                    paso(f, "/catalogo/modelos/", n, () -> modelos.save(Model.create(modelo.nombre(), modelo.invima(),
                            refs.id(modelo.fabricante()), asociacion, ficha(modelo.fichaTecnica()))).getId())));
            refs.putModel(refs.id(modelo.ref()), idTipo);
        }

        Map<String, UUID> patronesQueEstan = new HashMap<>();
        patrones.findAll().stream().filter(ReferenceStandard::isEstadoActivo)
                .forEach(p -> patronesQueEstan.putIfAbsent(Names.plano(p.getSerie()), p.getId()));
        for (int i = 0; i < catalogo.patrones().size(); i++) {
            LoadDocument.ReferenceStandard patron = catalogo.patrones().get(i);
            int n = i;
            UUID id = patronesQueEstan.computeIfAbsent(Names.plano(patron.serie()), k ->
                    paso(f, "/catalogo/patrones/", n, () -> patrones.save(nuevoPatron(patron)).getId()));
            refs.putStandard(patron.ref(), new LoadRefs.StandardRef(id, patron.certificados()));
        }

        // Las asignaciones se escriben con JDBC, por fuera de JPA: el tipo y sus protocolos tienen que
        // estar ya en la base, y hasta el vaciado solo están en la sesión.
        entityManager.flush();
        asignacionesPendientes.forEach(Runnable::run);
    }

    private EquipmentType nuevoTipo(LoadDocument.EquipmentType tipo, LoadRefs refs, List<Runnable> asignacionesPendientes,
            String archivo, int indice) {
        List<TypeVerification> verificaciones = tipo.verificaciones().stream().map(this::verificacion).toList();
        EquipmentType nuevo = tipos.save(EquipmentType.create(tipo.nombre(), tipo.definicionTecnica(),
                tipo.recomendacionesCuidado(), tipo.tecnologiaPredominante(), tipo.uso(), tipo.limpiezaCotidiana(),
                verificaciones, tipo.valorUnitarioMantenimiento() == null ? 0 : tipo.valorUnitarioMantenimiento()));

        if (!tipo.protocolos().isEmpty()) {
            ProtocolAssignment asignacion = ProtocolAssignment.empty(nuevo.getId());
            asignacion.assign(tipo.protocolos().stream()
                    .map(p -> new ProtocolAssignment.Requested(refs.id(p.protocolo()), p.indicacion()))
                    .toList());
            asignacionesPendientes.add(() -> paso(archivo, "/catalogo/tiposEquipo/", indice, () -> asignaciones.save(asignacion)));
        }
        return nuevo;
    }

    /** Con sus puntos de hoy, validados, y los anteriores ya retirados. */
    private TypeVerification verificacion(LoadDocument.Verification v) {
        Magnitude magnitud = metrologia.findAllMagnitudes().stream()
                .filter(m -> m.codigo().equals(v.magnitud())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("La magnitud «" + v.magnitud() + "» no existe"));
        MeasurementUnit unidad = metrologia.findUnitsByMagnitude(magnitud.id()).stream()
                .filter(u -> u.simbolo().equals(v.unidad())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("La unidad «" + v.unidad() + "» no es de " + v.magnitud()));

        TypeVerification hoy = TypeVerification.of(magnitud, unidad, VerificationMode.desdeEsquema(v.modalidad()),
                v.cantidadDatos(), v.puntos().stream().map(VerificationPoint::of).toList(), v.tolerancia());
        if (v.puntosAnteriores().isEmpty()) {
            return hoy;
        }
        List<VerificationPoint> puntos = new ArrayList<>(hoy.puntos());
        v.puntosAnteriores().forEach(p -> puntos.add(VerificationPoint.rehydrate(UUID.randomUUID(), p, false)));
        return TypeVerification.rehydrate(hoy.id(), magnitud, unidad, hoy.modalidad(), hoy.cantidadDatos(), puntos,
                true, hoy.tolerancia());
    }

    private static TechnicalSheet ficha(LoadDocument.TechnicalSheet ficha) {
        if (ficha == null) {
            return TechnicalSheet.EMPTY;
        }
        return TechnicalSheet.of(ficha.riesgo() == null ? null : RiskClass.valueOf(ficha.riesgo()),
                ficha.caracteristicas(), ficha.alimentacion(), ficha.voltaje(), ficha.potencia(), ficha.amperaje(),
                ficha.frecuencia());
    }

    /** El patrón queda con su certificado más reciente: el vigente hoy, que es el que tiene el catálogo. */
    private static ReferenceStandard nuevoPatron(LoadDocument.ReferenceStandard patron) {
        LoadDocument.Certificate ultimo = patron.certificados().stream()
                .max(Comparator.comparing(LoadDocument.Certificate::vencimiento)).orElseThrow();
        return ReferenceStandard.create(patron.nombre(), patron.marca(), patron.serie(), ultimo.numero(),
                ultimo.vencimiento(), new ReferenceStandardSheet(patron.modelo(), patron.codigoInterno(), patron.rango(),
                        patron.resolucion(), ultimo.fechaCalibracion(), ultimo.empresaCalibradora()));
    }
}
