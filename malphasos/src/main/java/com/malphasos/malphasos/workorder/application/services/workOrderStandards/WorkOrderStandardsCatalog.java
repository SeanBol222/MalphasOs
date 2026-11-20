package com.malphasos.malphasos.workorder.application.services.workOrderStandards;

import com.malphasos.malphasos.equipment.application.ports.input.ReferenceStandardServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.VerificationStandardsServicePort;
import com.malphasos.malphasos.equipment.domain.referenceStandard.ReferenceStandard;
import com.malphasos.malphasos.equipment.domain.verificationStandards.VerificationStandards;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Lo que el catálogo dice de los patrones de un tipo, visto desde la orden (V60).
 *
 * <p>Una orden lleva para un tipo los patrones que el catálogo admite en <b>alguna</b> de sus verificaciones
 * vigentes: una foránea no puede decirlo, porque el tipo está un salto por encima de la verificación. Y si
 * entre todos hay uno solo vigente —activo y con el certificado al día—, la orden lo pone sola.
 *
 * <p>Vive aparte del servicio de patrones de la orden porque lo usan los dos servicios del módulo: el de
 * patrones al asignarlos y el de órdenes al añadir el primer equipo de un tipo.
 */
@Component
public class WorkOrderStandardsCatalog {

    private final VerificationStandardsServicePort verificationStandardsServicePort;
    private final ReferenceStandardServicePort referenceStandardServicePort;
    private final Clock reloj;

    /**
     * El día es el de la zona de la empresa y no el del servidor, como en el documento de la orden: en
     * Bogotá, desde las siete de la noche, el de Greenwich ya es mañana.
     */
    @Autowired
    public WorkOrderStandardsCatalog(
            VerificationStandardsServicePort verificationStandardsServicePort,
            ReferenceStandardServicePort referenceStandardServicePort,
            @Value("${app.time-zone:America/Bogota}") String zona) {
        this(verificationStandardsServicePort, referenceStandardServicePort, Clock.system(ZoneId.of(zona)));
    }

    WorkOrderStandardsCatalog(
            VerificationStandardsServicePort verificationStandardsServicePort,
            ReferenceStandardServicePort referenceStandardServicePort,
            Clock reloj) {
        this.verificationStandardsServicePort = verificationStandardsServicePort;
        this.referenceStandardServicePort = referenceStandardServicePort;
        this.reloj = reloj;
    }

    /** Los patrones que el catálogo admite en alguna verificación vigente del tipo. */
    public Set<UUID> admitted(UUID idTipoEquipo) {
        Set<UUID> admitidos = new LinkedHashSet<>();
        verificationStandardsServicePort.standardsOf(idTipoEquipo).stream()
                .map(VerificationStandards::getPatrones)
                .forEach(admitidos::addAll);

        return admitidos;
    }

    /**
     * El único patrón vigente hoy entre los que el catálogo admite para el tipo, si hay exactamente uno.
     * Con varios no se elige: decide quien arma la orden.
     */
    public Optional<UUID> onlyCurrent(UUID idTipoEquipo) {
        LocalDate hoy = LocalDate.now(reloj);
        List<UUID> vigentes = admitted(idTipoEquipo).stream()
                .map(referenceStandardServicePort::findById)
                .filter(patron -> patron.isEstadoActivo() && patron.vigenteEl(hoy))
                .map(ReferenceStandard::getId)
                .toList();

        return vigentes.size() == 1 ? Optional.of(vigentes.getFirst()) : Optional.empty();
    }
}
