package com.malphasos.malphasos.report.infrastructure.output;

import com.malphasos.malphasos.company.domain.serviceCompany.CompanyLogo;
import com.malphasos.malphasos.report.application.ports.output.DocumentLogoStorePort;
import com.malphasos.malphasos.report.infrastructure.output.entities.DocumentLogoEntity;
import com.malphasos.malphasos.report.infrastructure.output.repository.DocumentLogoRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Las versiones del logo que congelaron los informes, una vez cada una, por su huella (V37). */
@Component
@RequiredArgsConstructor
public class DocumentLogoStoreAdapter implements DocumentLogoStorePort {

    private final DocumentLogoRepository documentLogoRepository;

    @Override
    public void store(CompanyLogo logo) {
        String huella = logo.huella();
        if (!documentLogoRepository.existsById(huella)) {
            documentLogoRepository.save(new DocumentLogoEntity(huella, logo.contenido(), logo.tipo()));
        }
    }

    @Override
    public Optional<CompanyLogo> find(String huella) {
        return documentLogoRepository.findById(huella)
                .map(fila -> new CompanyLogo(fila.getContenido(), fila.getTipo()));
    }
}
