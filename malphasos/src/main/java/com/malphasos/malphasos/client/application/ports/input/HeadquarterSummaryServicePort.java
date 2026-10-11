package com.malphasos.malphasos.client.application.ports.input;

import com.malphasos.malphasos.client.application.model.HeadquarterSummary;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import java.util.List;
import java.util.UUID;

/** Las sedes con sus áreas, de todos los clientes del alcance o de uno solo. */
public interface HeadquarterSummaryServicePort {

    /**
     * @param idCliente uno solo; nulo para todos los del alcance. Un cliente fuera del alcance da una
     *                  lista vacía, igual que en el resto de los listados
     */
    List<HeadquarterSummary> summarize(UUID idCliente, ReadScope alcance);
}
