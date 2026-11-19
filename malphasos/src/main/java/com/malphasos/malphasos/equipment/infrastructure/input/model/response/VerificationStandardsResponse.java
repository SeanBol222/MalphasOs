package com.malphasos.malphasos.equipment.infrastructure.input.model.response;

import com.malphasos.malphasos.equipment.domain.verificationStandards.VerificationStandards;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Los patrones de una verificación, por identificador: quien los muestra ya tiene el catálogo de
 * patrones, que es donde están sus nombres y sus certificados.
 */
@Schema(name = "VerificationStandardsResponse")
public record VerificationStandardsResponse(
        UUID idVerificacion,
        @Schema(description = "Vigentes y retirados; vacía si no tiene ninguno") List<UUID> patrones) {

    public static VerificationStandardsResponse from(VerificationStandards patrones) {
        return new VerificationStandardsResponse(
                patrones.getIdVerificacion(),
                patrones.getPatrones().stream().sorted(Comparator.comparing(UUID::toString)).toList());
    }

    public static List<VerificationStandardsResponse> fromList(List<VerificationStandards> lista) {
        return lista.stream().map(VerificationStandardsResponse::from).toList();
    }
}
