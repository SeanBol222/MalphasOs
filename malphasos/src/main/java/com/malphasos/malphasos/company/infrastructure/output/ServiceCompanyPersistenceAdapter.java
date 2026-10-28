package com.malphasos.malphasos.company.infrastructure.output;

import com.malphasos.malphasos.company.application.ports.output.ServiceCompanyPersistencePort;
import com.malphasos.malphasos.company.domain.serviceCompany.CompanyDetails;
import com.malphasos.malphasos.company.domain.serviceCompany.CompanyLogo;
import com.malphasos.malphasos.company.domain.serviceCompany.ServiceCompany;
import com.malphasos.malphasos.company.infrastructure.output.entities.ServiceCompanyEntity;
import com.malphasos.malphasos.company.infrastructure.output.repository.ServiceCompanyRepository;
import com.malphasos.malphasos.shared.application.ports.output.DocumentImageStorePort;
import com.malphasos.malphasos.shared.domain.image.DocumentImage;
import java.util.ArrayList;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementa el almacén de la empresa sobre JPA, con el mapeo a mano como en todo agregado.
 *
 * <p>Desde V43 los teléfonos son una colección perezosa, así que los dos métodos abren su transacción:
 * sin ella el mapeo solo funciona si el llamante ya abrió una, que es una dependencia que el tipo no
 * declara. Y desde V45 el logo vive en el almacén común de imágenes, por su huella.
 */
@Component
@RequiredArgsConstructor
public class ServiceCompanyPersistenceAdapter implements ServiceCompanyPersistencePort {

    private final ServiceCompanyRepository serviceCompanyRepository;
    private final DocumentImageStorePort documentImageStorePort;

    @Override
    @Transactional(readOnly = true)
    public Optional<ServiceCompany> find() {
        return serviceCompanyRepository.findFirstBy().map(this::toDomain);
    }

    @Override
    @Transactional
    public ServiceCompany save(ServiceCompany empresa) {
        CompanyDetails datos = empresa.getDatos();
        // El logo va al almacén común antes que la empresa, que lo apunta por su huella (V45).
        String huellaLogo = empresa.logo()
                .map(logo -> new DocumentImage(logo.contenido(), logo.tipo()))
                .map(imagen -> {
                    documentImageStorePort.store(imagen);
                    return imagen.huella();
                })
                .orElse(null);

        return toDomain(serviceCompanyRepository.save(new ServiceCompanyEntity(
                empresa.getId(), true, datos.nombre(), datos.nit(), datos.direccion(), datos.idCiudad(),
                new ArrayList<>(datos.telefonos()), datos.movil(), datos.correo(), datos.garantia(),
                datos.aclaracion(), datos.codigoHojaDeVida(), datos.codigoInforme(), huellaLogo)));
    }

    private ServiceCompany toDomain(ServiceCompanyEntity entity) {
        return ServiceCompany.rehydrate(
                entity.getId(),
                new CompanyDetails(entity.getNombre(), entity.getNit(), entity.getDireccion(), entity.getIdCiudad(),
                        entity.getTelefonos(), entity.getMovil(), entity.getCorreo(), entity.getGarantia(),
                        entity.getAclaracion(), entity.getCodigoHojaDeVida(), entity.getCodigoInforme()),
                entity.getHuellaLogo() == null ? null : logo(entity.getHuellaLogo()));
    }

    private CompanyLogo logo(String huella) {
        DocumentImage imagen = documentImageStorePort.find(huella).orElseThrow(() -> new IllegalStateException(
                "El logo de la empresa apunta a una imagen que no está: " + huella));

        return new CompanyLogo(imagen.contenido(), imagen.tipo());
    }
}
