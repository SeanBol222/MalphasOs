package com.malphasos.malphasos.location.infrastructure.input.rest;

import com.malphasos.malphasos.location.application.ports.input.CityServicePort;
import com.malphasos.malphasos.location.application.services.city.commands.CreateCityCommand;
import com.malphasos.malphasos.location.application.services.city.commands.DeactivateCityCommand;
import com.malphasos.malphasos.location.application.services.city.commands.UpdateCityCommand;
import com.malphasos.malphasos.location.domain.city.City;
import com.malphasos.malphasos.location.infrastructure.input.mapper.LocationRestMapper;
import com.malphasos.malphasos.location.infrastructure.input.model.request.CityCreateRequest;
import com.malphasos.malphasos.location.infrastructure.input.model.request.CityUpdateRequest;
import com.malphasos.malphasos.location.infrastructure.input.model.response.CityResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API de ciudades. Todas las operaciones exigen {@code admin.full}. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/cities")
@Tag(name = "City", description = "Gestion de ciudades")
public class CityRestAdapter {

    private final CityServicePort cityServicePort;
    private final LocationRestMapper locationRestMapper;

    @Operation(
            summary = "Listar ciudades",
            description = "Con el parametro idPais, solo las de ese pais. Sin el, todas.")
    @PreAuthorize("hasAuthority('admin.full')")
    @GetMapping
    public List<CityResponse> getCities(
            @Parameter(description = "Filtra por pais") @RequestParam(required = false) UUID idPais) {

        List<City> ciudades =
                idPais == null ? cityServicePort.findAll() : cityServicePort.findByCountry(idPais);

        return locationRestMapper.toCityResponseList(ciudades);
    }

    @Operation(summary = "Obtener una ciudad por su identificador")
    @PreAuthorize("hasAuthority('admin.full')")
    @GetMapping("/{id}")
    public CityResponse getCityById(
            @Parameter(description = "Identificador de la ciudad") @PathVariable UUID id) {

        return locationRestMapper.toResponse(cityServicePort.findById(id));
    }

    @Operation(summary = "Registrar una ciudad")
    @PreAuthorize("hasAuthority('admin.full')")
    @PostMapping
    public ResponseEntity<CityResponse> createCity(@Valid @RequestBody CityCreateRequest request) {

        CityResponse creada = locationRestMapper.toResponse(
                cityServicePort.create(new CreateCityCommand(request.nombre(), request.idPais())));

        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @Operation(
            summary = "Cambiar el nombre de una ciudad o trasladarla de pais",
            description = "Los campos ausentes conservan su valor. Cada cambio registra su propio hecho.")
    @PreAuthorize("hasAuthority('admin.full')")
    @PatchMapping("/{id}")
    public CityResponse updateCity(
            @Parameter(description = "Identificador de la ciudad") @PathVariable UUID id,
            @Valid @RequestBody CityUpdateRequest request) {

        return locationRestMapper.toResponse(
                cityServicePort.update(new UpdateCityCommand(id, request.nombre(), request.idPais())));
    }

    @Operation(
            summary = "Retirar una ciudad",
            description = "No la borra: la deja inactiva, conservando el historial.")
    @PreAuthorize("hasAuthority('admin.full')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateCity(
            @Parameter(description = "Identificador de la ciudad") @PathVariable UUID id) {

        cityServicePort.deactivate(new DeactivateCityCommand(id));
    }
}
