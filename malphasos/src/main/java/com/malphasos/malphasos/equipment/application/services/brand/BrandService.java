package com.malphasos.malphasos.equipment.application.services.brand;

import com.malphasos.malphasos.equipment.application.ports.input.BrandServicePort;
import com.malphasos.malphasos.equipment.application.ports.output.BrandPersistencePort;
import com.malphasos.malphasos.equipment.application.services.brand.commands.CreateBrandCommand;
import com.malphasos.malphasos.equipment.application.services.brand.commands.DeactivateBrandCommand;
import com.malphasos.malphasos.equipment.application.services.brand.commands.RenameBrandCommand;
import com.malphasos.malphasos.equipment.domain.brand.Brand;
import com.malphasos.malphasos.equipment.domain.exception.BrandNotFoundException;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Orquesta los casos de uso de marcas. No consulta a nadie: una marca no depende de nada. */
@Service
@RequiredArgsConstructor
public class BrandService implements BrandServicePort {

    private final BrandPersistencePort brandPersistencePort;
    private final EventDispatcherPort eventDispatcherPort;

    @Override
    @Transactional(readOnly = true)
    public List<Brand> findAll() {
        return brandPersistencePort.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Brand findById(UUID id) {
        return brandPersistencePort.findById(id).orElseThrow(() -> new BrandNotFoundException(id));
    }

    @Override
    @Transactional
    public Brand create(CreateBrandCommand command) {
        return persistAndPublish(Brand.create(command.nombre()));
    }

    @Override
    @Transactional
    public Brand rename(RenameBrandCommand command) {
        Brand marca = findById(command.id());
        marca.rename(command.nombre());

        return persistAndPublish(marca);
    }

    @Override
    @Transactional
    public void deactivate(DeactivateBrandCommand command) {
        Brand marca = findById(command.id());
        marca.deactivate();

        persistAndPublish(marca);
    }

    private Brand persistAndPublish(Brand marca) {
        Brand guardada = brandPersistencePort.save(marca);
        eventDispatcherPort.dispatchAll(marca.pullEvents());

        return guardada;
    }
}
