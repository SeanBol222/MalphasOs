package com.malphasos.malphasos.location.infrastructure.input.rest;

import com.malphasos.malphasos.location.application.ports.input.CountryServicePort;
import com.malphasos.malphasos.location.application.services.country.commands.CreateCountryCommand;
import com.malphasos.malphasos.location.application.services.country.commands.DeactivateCountryCommand;
import com.malphasos.malphasos.location.application.services.country.commands.UpdateCountryCommand;
import com.malphasos.malphasos.location.infrastructure.input.mapper.LocationRestMapper;
import com.malphasos.malphasos.location.infrastructure.input.model.request.CountryCreateRequest;
import com.malphasos.malphasos.location.infrastructure.input.model.request.CountryUpdateRequest;
import com.malphasos.malphasos.location.infrastructure.input.model.response.CountryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API de países.
 *
 * <p>Todas las operaciones exigen {@code admin.full}, igual que el resto del proyecto. **El hexágono
 * original no tenía una sola anotación de autorización**: bastaba un token válido de cualquier
 * usuario para crear o borrar un país, que es una tabla de referencia de la que cuelgan clientes,
 * ciudades y fabricantes.
 *
 * <p>Solo hay {@code PATCH} y no {@code PUT}. Un cambio total y uno parcial son la misma operación
 * cuando el único campo mutable es el nombre, y el original mantenía ambos caminos con un comando y
 * un método de agregado propios cada uno, uno de los cuales etiquetaba mal su evento.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/countries")
@Tag(name = "Country", description = "Gestion de paises")
public class CountryRestAdapter {

    private final CountryServicePort countryServicePort;
    private final LocationRestMapper locationRestMapper;

    @Operation(summary = "Listar todos los paises")
    @PreAuthorize("hasAuthority('admin.full')")
    @GetMapping
    public List<CountryResponse> getAllCountries() {
        return locationRestMapper.toCountryResponseList(countryServicePort.findAll());
    }

    @Operation(summary = "Obtener un pais por su identificador")
    @PreAuthorize("hasAuthority('admin.full')")
    @GetMapping("/{id}")
    public CountryResponse getCountryById(
            @Parameter(description = "Identificador del pais") @PathVariable UUID id) {

        return locationRestMapper.toResponse(countryServicePort.findById(id));
    }

    @Operation(summary = "Registrar un pais")
    @PreAuthorize("hasAuthority('admin.full')")
    @PostMapping
    public ResponseEntity<CountryResponse> createCountry(
            @Valid @RequestBody CountryCreateRequest request) {

        CountryResponse creado = locationRestMapper.toResponse(
                countryServicePort.create(new CreateCountryCommand(request.codigoIso(), request.nombre())));

        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @Operation(
            summary = "Cambiar el nombre de un pais",
            description = "Los campos ausentes conservan su valor. El codigo ISO no se puede cambiar.")
    @PreAuthorize("hasAuthority('admin.full')")
    @PatchMapping("/{id}")
    public CountryResponse updateCountry(
            @Parameter(description = "Identificador del pais") @PathVariable UUID id,
            @Valid @RequestBody CountryUpdateRequest request) {

        // El identificador se toma de la ruta y nunca del cuerpo. En el original el comando lo
        // transportaba ademas del parametro de ruta, con lo que habia dos valores para lo mismo y
        // nada que comprobara que coincidian.
        return locationRestMapper.toResponse(
                countryServicePort.update(new UpdateCountryCommand(id, request.nombre())));
    }

    @Operation(
            summary = "Retirar un pais",
            description = "No lo borra: lo deja inactivo, conservando el historial.")
    @PreAuthorize("hasAuthority('admin.full')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateCountry(
            @Parameter(description = "Identificador del pais") @PathVariable UUID id) {

        countryServicePort.deactivate(new DeactivateCountryCommand(id));
    }
}
