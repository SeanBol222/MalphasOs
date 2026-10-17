package com.malphasos.malphasos.person.infrastructure.input.model.response;

import com.malphasos.malphasos.person.domain.person.PersonSignature;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(name = "PersonSignatureResponse")
public record PersonSignatureResponse(
        @Schema(description = "PNG como data: URI") String imagen,
        @Schema(description = "Cuando se guardo") LocalDateTime actualizada) {

    public static PersonSignatureResponse from(PersonSignature firma) {
        return new PersonSignatureResponse(firma.dataUri(), firma.actualizada());
    }
}
