package com.malphasos.malphasos.dataload.infrastructure.input.rest;

import com.malphasos.malphasos.dataload.application.model.DataLoadFile;
import com.malphasos.malphasos.dataload.application.ports.input.DataLoadServicePort;
import com.malphasos.malphasos.dataload.infrastructure.input.model.response.DataLoadReportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * API de la carga de datos de otro sistema (2026-11-07), solo para el SuperUsuario.
 *
 * <p>Validar responde siempre 200 con un informe: que una carga tenga errores es el resultado de
 * validarla, no un fallo de la petición.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/data-loads")
@Tag(name = "DataLoad", description = "La carga de datos de otro sistema, en el formato de contracts/carga")
public class DataLoadRestAdapter {

    private final DataLoadServicePort dataLoadServicePort;

    @Operation(summary = "Validar una carga sin aplicarla",
            description = "De uno a tres archivos —el catálogo, un cliente y su historial—, como mucho uno de cada"
                    + " contenido. Responde qué impide aplicarla y qué conviene leer.")
    @PreAuthorize("hasAuthority('super.data-load.write')")
    @PostMapping(path = "/validation", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DataLoadReportResponse validate(
            @RequestPart(name = "archivos", required = false) List<MultipartFile> archivos) {
        return DataLoadReportResponse.from(dataLoadServicePort.validate(leer(archivos)));
    }

    private static List<DataLoadFile> leer(List<MultipartFile> archivos) {
        if (archivos == null) {
            return List.of();
        }
        return archivos.stream().map(archivo -> {
            try {
                return new DataLoadFile(archivo.getOriginalFilename(), archivo.getBytes());
            } catch (IOException e) {
                throw new UncheckedIOException("No se pudo leer el archivo subido " + archivo.getOriginalFilename(), e);
            }
        }).toList();
    }
}
