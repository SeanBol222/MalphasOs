package com.malphasos.malphasos.equipment.application.services.manufacturer;

import com.malphasos.malphasos.equipment.application.ports.input.ManufacturerServicePort;
import com.malphasos.malphasos.equipment.application.ports.output.ManufacturerPersistencePort;
import com.malphasos.malphasos.equipment.application.services.manufacturer.commands.CreateManufacturerCommand;
import com.malphasos.malphasos.equipment.application.services.manufacturer.commands.DeactivateManufacturerCommand;
import com.malphasos.malphasos.equipment.application.services.manufacturer.commands.UpdateManufacturerCommand;
import com.malphasos.malphasos.equipment.domain.exception.ManufacturerNotFoundException;
import com.malphasos.malphasos.equipment.domain.manufacturer.Manufacturer;
import com.malphasos.malphasos.location.application.ports.input.CountryServicePort;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta los casos de uso de fabricantes.
 *
 * <p>Comprueba contra el módulo de ubicaciones que el país exista, cuando se indica: es opcional,
 * pero si viene tiene que ser real.
 */
@Service
@RequiredArgsConstructor
public class ManufacturerService implements ManufacturerServicePort {

    private final ManufacturerPersistencePort manufacturerPersistencePort;
    private final CountryServicePort countryServicePort;
    private final EventDispatcherPort eventDispatcherPort;

    @Override
    @Transactional(readOnly = true)
    public List<Manufacturer> findAll() {
        return manufacturerPersistencePort.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Manufacturer findById(UUID id) {
        return manufacturerPersistencePort.findById(id)
                .orElseThrow(() -> new ManufacturerNotFoundException(id));
    }

    @Override
    @Transactional
    public Manufacturer create(CreateManufacturerCommand command) {
        requireCountryIfPresent(command.idPais());

        return persistAndPublish(Manufacturer.create(command.nombre(), command.idPais()));
    }

    @Override
    @Transactional
    public Manufacturer update(UpdateManufacturerCommand command) {
        requireCountryIfPresent(command.idPais());

        Manufacturer fabricante = findById(command.id());
        fabricante.update(command.nombre(), command.idPais());

        return persistAndPublish(fabricante);
    }

    @Override
    @Transactional
    public void deactivate(DeactivateManufacturerCommand command) {
        Manufacturer fabricante = findById(command.id());
        fabricante.deactivate();

        persistAndPublish(fabricante);
    }

    /** Lanza CountryNotFoundException si se indicó un país que no existe. */
    private void requireCountryIfPresent(UUID idPais) {
        if (idPais != null) {
            countryServicePort.findById(idPais);
        }
    }

    private Manufacturer persistAndPublish(Manufacturer fabricante) {
        Manufacturer guardado = manufacturerPersistencePort.save(fabricante);
        eventDispatcherPort.dispatchAll(fabricante.pullEvents());

        return guardado;
    }
}
