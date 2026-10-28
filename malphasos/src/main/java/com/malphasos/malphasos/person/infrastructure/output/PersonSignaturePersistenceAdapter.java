package com.malphasos.malphasos.person.infrastructure.output;

import com.malphasos.malphasos.person.application.ports.output.PersonSignaturePersistencePort;
import com.malphasos.malphasos.person.domain.person.PersonSignature;
import com.malphasos.malphasos.person.infrastructure.output.entities.PersonSignatureEntity;
import com.malphasos.malphasos.person.infrastructure.output.repository.PersonSignatureRepository;
import com.malphasos.malphasos.shared.application.ports.output.DocumentImageStorePort;
import com.malphasos.malphasos.shared.domain.image.DocumentImage;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementa el almacén de firmas sobre JPA.
 *
 * <p>Desde V45 la imagen vive en el almacén común, una vez por su huella, y la fila de la persona guarda
 * la huella: la misma imagen que un informe cerrado ya congeló no se repite. Los dos métodos abren su
 * transacción porque leen y escriben en dos tablas.
 */
@Component
@RequiredArgsConstructor
public class PersonSignaturePersistenceAdapter implements PersonSignaturePersistencePort {

    private final PersonSignatureRepository personSignatureRepository;
    private final DocumentImageStorePort documentImageStorePort;

    @Override
    @Transactional(readOnly = true)
    public Optional<PersonSignature> find(UUID idPersona) {
        return personSignatureRepository.findById(idPersona).map(this::toDomain);
    }

    @Override
    @Transactional
    public PersonSignature save(UUID idPersona, PersonSignature firma) {
        DocumentImage imagen = new DocumentImage(firma.imagen(), "image/png");
        documentImageStorePort.store(imagen);

        return toDomain(personSignatureRepository.save(
                new PersonSignatureEntity(idPersona, imagen.huella(), firma.actualizada())));
    }

    private PersonSignature toDomain(PersonSignatureEntity fila) {
        DocumentImage imagen = documentImageStorePort.find(fila.getHuella())
                .orElseThrow(() -> new IllegalStateException("La firma de " + fila.getIdPersona()
                        + " apunta a una imagen que no está: " + fila.getHuella()));

        return new PersonSignature(imagen.contenido(), fila.getActualizada());
    }
}
