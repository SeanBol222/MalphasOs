package com.malphasos.malphasos.equipment.application.services.equipmentType;

import com.malphasos.malphasos.equipment.application.ports.input.EquipmentTypeServicePort;
import com.malphasos.malphasos.equipment.application.ports.output.EquipmentTypePersistencePort;
import com.malphasos.malphasos.equipment.application.ports.output.MetrologyCatalogPersistencePort;
import com.malphasos.malphasos.equipment.application.ports.output.VerificationStandardsPersistencePort;
import com.malphasos.malphasos.equipment.application.services.equipmentType.commands.CreateEquipmentTypeCommand;
import com.malphasos.malphasos.equipment.application.services.equipmentType.commands.DeactivateEquipmentTypeCommand;
import com.malphasos.malphasos.equipment.application.services.equipmentType.commands.DeclareVerificationsCommand;
import com.malphasos.malphasos.equipment.application.services.equipmentType.commands.TypeVerificationCommand;
import com.malphasos.malphasos.equipment.application.services.equipmentType.commands.UpdateEquipmentTypeCommand;
import com.malphasos.malphasos.equipment.application.services.equipmentType.commands.VerificationPointCommand;
import com.malphasos.malphasos.equipment.domain.equipmentType.EquipmentType;
import com.malphasos.malphasos.equipment.domain.equipmentType.TypeVerification;
import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationPoint;
import com.malphasos.malphasos.equipment.domain.exception.EquipmentTypeNotFoundException;
import com.malphasos.malphasos.equipment.domain.exception.MagnitudeNotFoundException;
import com.malphasos.malphasos.equipment.domain.exception.MeasurementUnitNotFoundException;
import com.malphasos.malphasos.equipment.domain.exception.UnitOutsideMagnitudeException;
import com.malphasos.malphasos.equipment.domain.magnitude.Magnitude;
import com.malphasos.malphasos.equipment.domain.magnitude.MeasurementUnit;
import com.malphasos.malphasos.equipment.domain.verificationStandards.VerificationStandards;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Orquesta los casos de uso de tipos de equipo. */
@Service
@RequiredArgsConstructor
public class EquipmentTypeService implements EquipmentTypeServicePort {

    private final EquipmentTypePersistencePort equipmentTypePersistencePort;
    private final MetrologyCatalogPersistencePort metrologyCatalogPersistencePort;
    private final VerificationStandardsPersistencePort verificationStandardsPersistencePort;
    private final EventDispatcherPort eventDispatcherPort;

    @Override
    @Transactional(readOnly = true)
    public List<EquipmentType> findAll() {
        return equipmentTypePersistencePort.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public EquipmentType findById(UUID id) {
        return equipmentTypePersistencePort.findById(id)
                .orElseThrow(() -> new EquipmentTypeNotFoundException(id));
    }

    @Override
    @Transactional
    public EquipmentType create(CreateEquipmentTypeCommand command) {
        return persistAndPublish(EquipmentType.create(
                command.nombre(),
                command.definicionTecnica(),
                command.recomendacionesCuidado(),
                command.tecnologiaPredominante(),
                command.uso(),
                command.limpiezaCotidiana(),
                verificacionesDe(command.verificaciones()),
                command.valorUnitarioMantenimiento()));
    }

    @Override
    @Transactional
    public EquipmentType update(UpdateEquipmentTypeCommand command) {
        EquipmentType tipo = findById(command.id());
        tipo.update(
                command.nombre(),
                command.definicionTecnica(),
                command.recomendacionesCuidado(),
                command.tecnologiaPredominante(),
                command.uso(),
                command.limpiezaCotidiana(),
                command.valorUnitarioMantenimiento());

        return persistAndPublish(tipo);
    }

    @Override
    @Transactional
    public EquipmentType declareVerifications(DeclareVerificationsCommand command) {
        EquipmentType tipo = findById(command.id());
        Map<UUID, UUID> anterioresPorMagnitud = tipo.verificacionesActivas().stream()
                .collect(Collectors.toMap(v -> v.magnitud().id(), TypeVerification::id));
        tipo.declareVerifications(verificacionesDe(command.verificaciones()));

        EquipmentType guardado = persistAndPublish(tipo);
        heredarPatrones(anterioresPorMagnitud, guardado);

        return guardado;
    }

    /**
     * Declarar retira las verificaciones vigentes y crea otras con identificador nuevo, aunque solo haya
     * cambiado una tolerancia. Los patrones de cada una (V60) cuelgan de ese identificador, así que sin
     * esto se perderían en cada edición: la nueva de una magnitud hereda los de la que retiró, que es la
     * misma cosa medida —un tipo no declara dos veces la misma magnitud—.
     */
    private void heredarPatrones(Map<UUID, UUID> anterioresPorMagnitud, EquipmentType tipo) {
        Set<UUID> anteriores = Set.copyOf(anterioresPorMagnitud.values());

        for (TypeVerification nueva : tipo.verificacionesActivas()) {
            UUID anterior = anterioresPorMagnitud.get(nueva.magnitud().id());

            if (anterior == null || anteriores.contains(nueva.id())) {
                continue;
            }

            Set<UUID> heredados = verificationStandardsPersistencePort.findByVerification(anterior).getPatrones();

            if (heredados.isEmpty()) {
                continue;
            }

            VerificationStandards patrones = VerificationStandards.empty(nueva.id());
            patrones.assign(List.copyOf(heredados));
            verificationStandardsPersistencePort.save(patrones);
            eventDispatcherPort.dispatchAll(patrones.pullEvents());
        }
    }

    /**
     * Traduce las verificaciones que llegan de fuera a las del dominio, comprobando sus referencias.
     *
     * <p>Aquí y no en el agregado por dos razones. Una: el agregado no debe conocer la forma en que le
     * llegan las cosas. Dos, y es la que manda: <b>comprobar que la magnitud y la unidad existen exige
     * consultar el catálogo</b>, y un agregado no consulta nada — si lo hiciera, el llamante recibiría
     * un conflicto de integridad genérico de PostgreSQL en vez de «esa magnitud no existe».
     *
     * <p><b>Tres reglas cruzadas nuevas</b>, y la tercera es la que no es evidente: que la unidad exista
     * no basta, tiene que ser una unidad <i>de esa</i> magnitud. Desde {@code V48} la verificación solo
     * guarda la unidad, y su magnitud es la de esa unidad: lo que esto evita es guardar otra magnitud de
     * la que se pidió, y es lo que da el mensaje. Ver {@link UnitOutsideMagnitudeException} para por qué lleva código propio.
     */
    private List<TypeVerification> verificacionesDe(List<TypeVerificationCommand> verificaciones) {
        if (verificaciones == null) {
            return List.of();
        }

        return verificaciones.stream().map(this::verificacionDe).toList();
    }

    private TypeVerification verificacionDe(TypeVerificationCommand comando) {
        Magnitude magnitud = requireMagnitude(comando.magnitudId());
        MeasurementUnit unidad = requireUnitOfMagnitude(comando.unidadId(), magnitud);

        return TypeVerification.of(
                magnitud,
                unidad,
                comando.modalidad(),
                comando.cantidadDatos(),
                puntosDe(comando.puntos()),
                comando.tolerancia());
    }

    /**
     * Que la magnitud declarada exista, <b>y la devuelve</b>.
     *
     * <p>Devolverla y no solo comprobarla es lo que hace que embeberla en la verificación no cueste una
     * consulta extra: la que valida es la misma que la trae.
     */
    private Magnitude requireMagnitude(UUID magnitudId) {
        if (magnitudId == null) {
            throw new IllegalArgumentException("Una verificacion necesita saber que magnitud mide");
        }

        return metrologyCatalogPersistencePort.findMagnitudeById(magnitudId)
                .orElseThrow(() -> new MagnitudeNotFoundException(magnitudId));
    }

    /** Que la unidad exista y sea de esa magnitud, que son dos fallos distintos. */
    private MeasurementUnit requireUnitOfMagnitude(UUID unidadId, Magnitude magnitud) {
        if (unidadId == null) {
            throw new IllegalArgumentException("Una verificacion necesita su unidad");
        }

        MeasurementUnit unidad = metrologyCatalogPersistencePort.findUnitById(unidadId)
                .orElseThrow(() -> new MeasurementUnitNotFoundException(unidadId));

        if (!unidad.esDeLaMagnitud(magnitud.id())) {
            throw new UnitOutsideMagnitudeException(unidadId, magnitud.id());
        }

        return unidad;
    }

    /**
     * Traduce los puntos que llegan de fuera a los del dominio.
     *
     * <p>{@code VerificationPoint.of} normaliza el valor a la escala de la columna; esto solo recorre.
     */
    private List<VerificationPoint> puntosDe(List<VerificationPointCommand> puntos) {
        return puntos == null
                ? List.of()
                : puntos.stream().map(punto -> VerificationPoint.of(punto.valor())).toList();
    }

    @Override
    @Transactional
    public void deactivate(DeactivateEquipmentTypeCommand command) {
        EquipmentType tipo = findById(command.id());
        tipo.deactivate();

        persistAndPublish(tipo);
    }

    private EquipmentType persistAndPublish(EquipmentType tipo) {
        EquipmentType guardado = equipmentTypePersistencePort.save(tipo);
        eventDispatcherPort.dispatchAll(tipo.pullEvents());

        return guardado;
    }
}
