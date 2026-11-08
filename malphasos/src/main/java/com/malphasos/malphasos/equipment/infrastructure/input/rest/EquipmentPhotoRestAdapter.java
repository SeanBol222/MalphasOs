package com.malphasos.malphasos.equipment.infrastructure.input.rest;

import com.malphasos.malphasos.equipment.application.ports.input.EquipmentPhotoServicePort;
import com.malphasos.malphasos.equipment.infrastructure.input.model.response.EquipmentPhotoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * La foto de un equipo del cliente (V58, 2026-11-08), que se pone desde su hoja de vida y viaja dentro de
 * ella: por eso aquí no hay {@code GET}. Con {@code equipment.write}, como el resto de lo que se escribe
 * del equipo: la tienen los ingenieros, que la toman en campo, y los administradores.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/client-equipments/{id}/photo")
@Tag(name = "EquipmentPhoto", description = "La foto de un equipo instalado, para su hoja de vida")
public class EquipmentPhotoRestAdapter {

    private final EquipmentPhotoServicePort equipmentPhotoServicePort;

    @Operation(summary = "Poner o cambiar la foto",
            description = "Solo PNG, de hasta 512 KB; el tipo se mira en los bytes. El navegador la reduce "
                    + "antes de subirla. 404 si el equipo no existe.")
    @PreAuthorize("hasAuthority('equipment.write')")
    @PatchMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EquipmentPhotoResponse changePhoto(
            @Parameter(description = "Identificador del equipo instalado") @PathVariable UUID id,
            @RequestPart("archivo") MultipartFile archivo) {
        try {
            return EquipmentPhotoResponse.from(equipmentPhotoServicePort.change(id, archivo.getBytes()));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer la foto subida", e);
        }
    }

    @Operation(summary = "Quitar la foto",
            description = "La hoja de vida vuelve a mostrar el recuadro vacío. 404 si el equipo no existe.")
    @PreAuthorize("hasAuthority('equipment.write')")
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removePhoto(@Parameter(description = "Identificador del equipo instalado") @PathVariable UUID id) {
        equipmentPhotoServicePort.remove(id);
    }
}
