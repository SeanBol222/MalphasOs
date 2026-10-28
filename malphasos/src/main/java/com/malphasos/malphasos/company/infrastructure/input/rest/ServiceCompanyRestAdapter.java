package com.malphasos.malphasos.company.infrastructure.input.rest;

import com.malphasos.malphasos.company.application.ports.input.ServiceCompanyServicePort;
import com.malphasos.malphasos.company.application.services.serviceCompany.commands.ChangeCompanyLogoCommand;
import com.malphasos.malphasos.company.application.services.serviceCompany.commands.SaveServiceCompanyCommand;
import com.malphasos.malphasos.company.domain.exception.ServiceCompanyNotConfiguredException;
import com.malphasos.malphasos.company.infrastructure.input.model.request.ServiceCompanyRequest;
import com.malphasos.malphasos.company.infrastructure.input.model.response.ServiceCompanyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * API de la empresa que presta el servicio (2026-10-17): sus datos y su logo, que un administrador edita
 * desde la aplicación. Hay una sola, así que la ruta no lleva identificador.
 *
 * <p>Quien imprime un documento no necesita {@code company.read}: el membrete viaja dentro del
 * documento, con la autoridad de ese documento.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/company")
@Tag(name = "ServiceCompany", description = "La empresa que presta el servicio: el membrete de los documentos")
public class ServiceCompanyRestAdapter {

    private final ServiceCompanyServicePort serviceCompanyServicePort;

    @Operation(summary = "Obtener la empresa", description = "404 si todavia no se ha configurado.")
    @PreAuthorize("hasAuthority('company.read')")
    @GetMapping
    public ServiceCompanyResponse get() {
        return ServiceCompanyResponse.from(
                serviceCompanyServicePort.find().orElseThrow(ServiceCompanyNotConfiguredException::new));
    }

    @Operation(summary = "Guardar los datos de la empresa",
            description = "La configura si no existia y la corrige si si. Los informes ya cerrados no cambian.")
    @PreAuthorize("hasAuthority('company.write')")
    @PatchMapping
    public ServiceCompanyResponse save(@Valid @RequestBody ServiceCompanyRequest request) {
        return ServiceCompanyResponse.from(serviceCompanyServicePort.save(new SaveServiceCompanyCommand(
                request.nombre(), request.nit(), request.direccion(), request.idCiudad(), request.telefonos(),
                request.movil(), request.correo(), request.garantia(), request.aclaracion(),
                request.codigoHojaDeVida(), request.codigoInforme())));
    }

    @Operation(summary = "Poner o cambiar el logo",
            description = "PNG, JPG o SVG de hasta 512 KB; el tipo se deduce de los bytes. 404 sin empresa.")
    @PreAuthorize("hasAuthority('company.write')")
    @PatchMapping(path = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ServiceCompanyResponse changeLogo(@RequestPart("archivo") MultipartFile archivo) {
        try {
            return ServiceCompanyResponse.from(
                    serviceCompanyServicePort.changeLogo(new ChangeCompanyLogoCommand(archivo.getBytes())));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el logo subido", e);
        }
    }

    @Operation(summary = "Quitar el logo", description = "Sin logo, el membrete es el nombre. 404 sin empresa.")
    @PreAuthorize("hasAuthority('company.write')")
    @DeleteMapping("/logo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeLogo() {
        serviceCompanyServicePort.removeLogo();
    }
}
