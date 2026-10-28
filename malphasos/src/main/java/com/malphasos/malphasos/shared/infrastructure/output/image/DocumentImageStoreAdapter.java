package com.malphasos.malphasos.shared.infrastructure.output.image;

import com.malphasos.malphasos.shared.application.ports.output.DocumentImageStorePort;
import com.malphasos.malphasos.shared.domain.image.DocumentImage;
import com.malphasos.malphasos.shared.infrastructure.output.image.entities.DocumentImageEntity;
import com.malphasos.malphasos.shared.infrastructure.output.image.repository.DocumentImageRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Las imágenes que congelaron los informes, una vez cada versión, por su huella (V37 y V38). */
@Component
@RequiredArgsConstructor
public class DocumentImageStoreAdapter implements DocumentImageStorePort {

    private final DocumentImageRepository documentImageRepository;

    @Override
    public void store(DocumentImage imagen) {
        String huella = imagen.huella();
        if (!documentImageRepository.existsById(huella)) {
            documentImageRepository.save(new DocumentImageEntity(huella, imagen.contenido(), imagen.tipo()));
        }
    }

    @Override
    public Optional<DocumentImage> find(String huella) {
        return documentImageRepository.findById(huella)
                .map(fila -> new DocumentImage(fila.getContenido(), fila.getTipo()));
    }
}
