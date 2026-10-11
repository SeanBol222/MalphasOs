package com.malphasos.malphasos.client.application.ports.input;

import com.malphasos.malphasos.client.application.model.ServiceAreaLocation;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Lo cuarto que este módulo publica hacia los demás: dónde está un lote de áreas de servicio.
 *
 * <p>Existe para el listado de equipos, que pinta cliente, sede y área de cada fila. Pedirlo área por
 * área serían tres consultas por fila; esto es una para la página entera. Es el mismo patrón que
 * {@link ClientOwnershipPort}: una superficie mínima hacia un módulo que ya dependía de este.
 */
public interface ServiceAreaLocationPort {

    /**
     * La ubicación de cada área pedida, por su identificador. Un área que no existe no aparece en el
     * mapa; las retiradas sí, porque un equipo dado de baja sigue diciendo dónde estuvo.
     */
    Map<UUID, ServiceAreaLocation> locate(Collection<UUID> idsAreas);
}
