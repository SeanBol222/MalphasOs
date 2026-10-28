package com.malphasos.malphasos.company.infrastructure.output;

import com.malphasos.malphasos.company.application.ports.output.ServiceCompanyPersistencePort;
import com.malphasos.malphasos.company.domain.serviceCompany.CompanyDetails;
import com.malphasos.malphasos.company.domain.serviceCompany.CompanyLogo;
import com.malphasos.malphasos.company.domain.serviceCompany.ServiceCompany;
import com.malphasos.malphasos.company.infrastructure.output.entities.ServiceCompanyEntity;
import com.malphasos.malphasos.company.infrastructure.output.repository.ServiceCompanyRepository;
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
 * declara.
 */
@Component
@RequiredArgsConstructor
public class ServiceCompanyPersistenceAdapter implements ServiceCompanyPersistencePort {

    private final ServiceCompanyRepository serviceCompanyRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<ServiceCompany> find() {
        return serviceCompanyRepository.findFirstBy().map(ServiceCompanyPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional
    public ServiceCompany save(ServiceCompany empresa) {
        CompanyDetails datos = empresa.getDatos();
        Optional<CompanyLogo> logo = empresa.logo();

        return toDomain(serviceCompanyRepository.save(new ServiceCompanyEntity(
                empresa.getId(), true, datos.nombre(), datos.nit(), datos.direccion(), datos.ciudad(),
                new ArrayList<>(datos.telefonos()), datos.movil(), datos.correo(), datos.garantia(),
                datos.aclaracion(), datos.codigoHojaDeVida(), datos.codigoInforme(),
                logo.map(CompanyLogo::contenido).orElse(null), logo.map(CompanyLogo::tipo).orElse(null))));
    }

    private static ServiceCompany toDomain(ServiceCompanyEntity entity) {
        return ServiceCompany.rehydrate(
                entity.getId(),
                new CompanyDetails(entity.getNombre(), entity.getNit(), entity.getDireccion(), entity.getCiudad(),
                        entity.getTelefonos(), entity.getMovil(), entity.getCorreo(), entity.getGarantia(),
                        entity.getAclaracion(), entity.getCodigoHojaDeVida(), entity.getCodigoInforme()),
                entity.getLogo() == null ? null : new CompanyLogo(entity.getLogo(), entity.getLogoTipo()));
    }
}
