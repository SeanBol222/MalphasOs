package com.malphasos.malphasos.dataload.infrastructure.input.rest;

import com.malphasos.malphasos.dataload.application.model.DataLoadFile;
import com.malphasos.malphasos.dataload.application.model.DataLoadReport;
import com.malphasos.malphasos.dataload.application.ports.input.DataLoadServicePort;
import com.malphasos.malphasos.dataload.infrastructure.input.model.response.DataLoadReportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
 * validarla, no un fallo de la petición. Aplicar responde 422 con el mismo informe si no escribió nada:
 * ahí sí es la petición la que no se pudo hacer.
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

    @Operation(summary = "Aplicar una carga",
            description = "Valida otra vez y, sin errores, escribe la carga entera en una transacción: todo o nada."
                    + " Lo que ya existe se reconoce por su llave natural y no se toca. Con errores responde 422 con"
                    + " el informe y no escribe nada.")
    @ApiResponse(responseCode = "200", description = "La carga quedó escrita; el informe dice qué entró y qué ya estaba")
    @ApiResponse(responseCode = "422", description = "No se escribió nada; el informe dice por qué",
            content = @Content(schema = @Schema(implementation = DataLoadReportResponse.class)))
    @PreAuthorize("hasAuthority('super.data-load.write')")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DataLoadReportResponse> apply(
            @RequestPart(name = "archivos", required = false) List<MultipartFile> archivos) {
        DataLoadReport informe = dataLoadServicePort.apply(leer(archivos));

        return ResponseEntity.status(informe.valida() ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_CONTENT)
                .body(DataLoadReportResponse.from(informe));
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
