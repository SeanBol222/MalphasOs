package com.malphasos.malphasos.person.infrastructure.input.rest;

import com.malphasos.malphasos.person.application.ports.input.PersonSignatureServicePort;
import com.malphasos.malphasos.person.domain.exception.PersonNotFoundException;
import com.malphasos.malphasos.person.domain.exception.PersonSignatureNotFoundException;
import com.malphasos.malphasos.person.infrastructure.input.model.request.PersonSignatureRequest;
import com.malphasos.malphasos.person.infrastructure.input.model.response.PersonSignatureResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La firma de quien llama (V38, 2026-10-17). <b>Solo la propia</b>, decidido por el usuario: la ruta no
 * lleva identificador, y la persona sale del token. Nadie puede leer ni cambiar la firma de otro.
 *
 * <p>Guardarla pide {@code report.write}: la firma es de quien cierra reportes, que es quien la necesita.
 * Leerla, {@code report.read}, porque todo {@code GET} pide una autoridad de lectura —lo exige
 * {@code RestAuthorizationCoverageTest}—; y como solo se lee la propia, no abre nada ajeno.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/persons/me/signature")
@Tag(name = "PersonSignature", description = "La firma de quien llama, que copian los informes que cierra")
public class PersonSignatureRestAdapter {

    private static final String PREFIJO_PNG = "data:image/png;base64,";

    private final PersonSignatureServicePort personSignatureServicePort;

    @Operation(summary = "Mi firma", description = "404 si todavia no la he guardado.")
    @PreAuthorize("hasAuthority('report.read')")
    @GetMapping
    public PersonSignatureResponse get(Authentication autenticacion) {
        UUID yo = quienLlama(autenticacion);

        return PersonSignatureResponse.from(personSignatureServicePort.find(yo)
                .orElseThrow(() -> new PersonSignatureNotFoundException(yo)));
    }

    @Operation(summary = "Guardar mi firma",
            description = "La sustituye si ya habia una. Los informes ya cerrados conservan la que copiaron.")
    @PreAuthorize("hasAuthority('report.write')")
    @PatchMapping
    public PersonSignatureResponse save(
            @Valid @RequestBody PersonSignatureRequest request, Authentication autenticacion) {
        if (!request.imagen().startsWith(PREFIJO_PNG)) {
            throw new IllegalArgumentException("La firma tiene que llegar como un PNG en data: URI");
        }
        byte[] imagen;
        try {
            imagen = Base64.getDecoder().decode(request.imagen().substring(PREFIJO_PNG.length()));
        } catch (IllegalArgumentException base64Roto) {
            throw new IllegalArgumentException("La firma no es un base64 válido");
        }

        return PersonSignatureResponse.from(personSignatureServicePort.save(quienLlama(autenticacion), imagen));
    }

    /** La persona de la sesión: el {@code sub} del token es su identificador, como en el filtrado por dueño. */
    private static UUID quienLlama(Authentication autenticacion) {
        String sub = autenticacion instanceof JwtAuthenticationToken token
                ? token.getToken().getSubject()
                : autenticacion == null ? null : autenticacion.getName();
        try {
            return UUID.fromString(sub);
        } catch (IllegalArgumentException | NullPointerException sinPersona) {
            throw new PersonNotFoundException("La sesion no corresponde a ninguna persona registrada");
        }
    }
}
