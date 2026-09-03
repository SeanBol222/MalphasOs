package com.malphasos.malphasos.equipment.application.ports.input;

import com.malphasos.malphasos.equipment.application.services.brand.commands.CreateBrandCommand;
import com.malphasos.malphasos.equipment.application.services.brand.commands.DeactivateBrandCommand;
import com.malphasos.malphasos.equipment.application.services.brand.commands.RenameBrandCommand;
import com.malphasos.malphasos.equipment.domain.brand.Brand;
import java.util.List;
import java.util.UUID;

/** Casos de uso sobre marcas. */
public interface BrandServicePort {

    List<Brand> findAll();

    Brand findById(UUID id);

    Brand create(CreateBrandCommand command);

    Brand rename(RenameBrandCommand command);

    void deactivate(DeactivateBrandCommand command);
}
