package com.malphasos.malphasos.equipment.application.ports.output;

import com.malphasos.malphasos.equipment.domain.magnitude.Magnitude;
import com.malphasos.malphasos.equipment.domain.magnitude.MeasurementUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Almacén del catálogo metrológico: qué magnitudes hay y qué unidades tiene cada una.
 *
 * <p><b>Un solo puerto para las dos tablas</b>, al contrario que el resto del módulo, que tiene uno por
 * agregado. La razón: no son agregados y nunca se usan por separado — para declarar una verificación
 * hacen falta las dos a la vez, y la pregunta que siempre se hace es «qué unidades tiene esta
 * magnitud». Partirlo en dos puertos daría dos interfaces que siempre se inyectan juntas.
 *
 * <p><b>Sin {@code save}</b>, y no por olvido: el catálogo entra sembrado por {@code V10} y no hay
 * pantalla que lo administre, igual que los países y las ciudades de {@code V7}. Un puerto que declara
 * una escritura que nadie llama es indistinguible de uno roto.
 */
public interface MetrologyCatalogPersistencePort {

    /** Las magnitudes activas, para la lista de la que se elige. */
    List<Magnitude> findAllMagnitudes();

    Optional<Magnitude> findMagnitudeById(UUID id);

    /** Las unidades activas de una magnitud. Es lo que la pantalla ofrece tras elegirla. */
    List<MeasurementUnit> findUnitsByMagnitude(UUID magnitudId);

    Optional<MeasurementUnit> findUnitById(UUID id);
}
