package com.malphasos.malphasos.client.application.ports.input;

import com.malphasos.malphasos.client.application.services.serviceArea.commands.CreateServiceAreaCommand;
import com.malphasos.malphasos.client.application.services.serviceArea.commands.DeactivateServiceAreaCommand;
import com.malphasos.malphasos.client.application.services.serviceArea.commands.RenameServiceAreaCommand;
import com.malphasos.malphasos.client.domain.serviceArea.ServiceArea;
import java.util.List;
import java.util.UUID;

/** Casos de uso sobre áreas de servicio. */
public interface ServiceAreaServicePort {

    List<ServiceArea> findAll();

    /** Áreas de una sede. Falla si la sede no existe. */
    List<ServiceArea> findByHeadquarter(UUID idSede);

    ServiceArea findById(UUID id);

    /**
     * Identificador del cliente dueño del área, resuelto en una sola llamada.
     *
     * <p>Es el contrato mínimo que este módulo publica hacia los demás para responder «¿de qué
     * cliente es esta área?». Existe para que nadie fuera de aquí tenga que caminar la jerarquía
     * área → sede → cliente: esa estructura es interna de este contexto y puede cambiar, y el día
     * que cambie no debería obligar a tocar el módulo que pregunta. Lo que cruza la frontera es un
     * identificador, nunca un agregado de aquí.
     *
     * <p>Es una llamada síncrona a propósito: quien pregunta —hoy el traslado de una unidad de
     * equipo, que no puede cruzar de cliente— necesita la respuesta antes de decidir, y un evento
     * no contesta preguntas.
     *
     * @return el identificador del cliente, nunca nulo
     * @throws com.malphasos.malphasos.client.domain.exception.ServiceAreaNotFoundException si el
     *     área no existe
     * @throws com.malphasos.malphasos.client.domain.exception.HeadquarterNotFoundException si la
     *     sede del área no existe
     */
    UUID findOwningClient(UUID idAreaServicio);

    ServiceArea create(CreateServiceAreaCommand command);

    ServiceArea rename(RenameServiceAreaCommand command);

    /** Cierra el área sin borrarla. */
    void deactivate(DeactivateServiceAreaCommand command);
}
