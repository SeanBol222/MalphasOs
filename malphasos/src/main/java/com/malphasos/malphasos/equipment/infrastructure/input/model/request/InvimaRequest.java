package com.malphasos.malphasos.equipment.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** Anota o corrige el registro INVIMA. Ausente lo deja sin registro. */
@Schema(name = "InvimaRequest")
public record InvimaRequest(@Size(max = 50) String invima) {
}
