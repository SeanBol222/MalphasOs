package com.malphasos.malphasos.company.application.services.serviceCompany;

import com.malphasos.malphasos.company.application.ports.input.ServiceCompanyServicePort;
import com.malphasos.malphasos.company.application.ports.output.ServiceCompanyPersistencePort;
import com.malphasos.malphasos.company.application.services.serviceCompany.commands.ChangeCompanyLogoCommand;
import com.malphasos.malphasos.company.application.services.serviceCompany.commands.SaveServiceCompanyCommand;
import com.malphasos.malphasos.company.application.services.serviceCompany.commands.SeedServiceCompanyCommand;
import com.malphasos.malphasos.company.domain.exception.ServiceCompanyNotConfiguredException;
import com.malphasos.malphasos.company.domain.serviceCompany.CompanyDetails;
import com.malphasos.malphasos.company.domain.serviceCompany.CompanyLogo;
import com.malphasos.malphasos.company.domain.serviceCompany.ServiceCompany;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ServiceCompanyService implements ServiceCompanyServicePort {

    private final ServiceCompanyPersistencePort serviceCompanyPersistencePort;
    private final EventDispatcherPort eventDispatcherPort;

    @Override
    @Transactional(readOnly = true)
    public Optional<ServiceCompany> find() {
        return serviceCompanyPersistencePort.find();
    }

    @Override
    @Transactional
    public ServiceCompany save(SaveServiceCompanyCommand command) {
        CompanyDetails datos = datos(command);
        ServiceCompany empresa = serviceCompanyPersistencePort.find()
                .map(existente -> {
                    existente.update(datos);
                    return existente;
                })
                .orElseGet(() -> ServiceCompany.configure(datos));

        return persistAndPublish(empresa);
    }

    @Override
    @Transactional
    public ServiceCompany changeLogo(ChangeCompanyLogoCommand command) {
        ServiceCompany empresa = configurada();
        empresa.changeLogo(CompanyLogo.of(command.contenido()));

        return persistAndPublish(empresa);
    }

    @Override
    @Transactional
    public ServiceCompany removeLogo() {
        ServiceCompany empresa = configurada();
        empresa.removeLogo();

        return persistAndPublish(empresa);
    }

    @Override
    @Transactional
    public boolean seed(SeedServiceCompanyCommand command) {
        if (serviceCompanyPersistencePort.find().isPresent()) {
            return false;
        }

        ServiceCompany empresa = ServiceCompany.configure(datos(command.datos()));
        if (command.logo() != null) {
            empresa.changeLogo(CompanyLogo.of(command.logo()));
        }
        persistAndPublish(empresa);

        return true;
    }

    private ServiceCompany configurada() {
        return serviceCompanyPersistencePort.find().orElseThrow(ServiceCompanyNotConfiguredException::new);
    }

    private static CompanyDetails datos(SaveServiceCompanyCommand command) {
        return new CompanyDetails(command.nombre(), command.nit(), command.direccion(), command.ciudad(),
                command.telefonos(), command.movil(), command.correo(), command.garantia(), command.aclaracion(),
                command.codigoHojaDeVida(), command.codigoInforme());
    }

    private ServiceCompany persistAndPublish(ServiceCompany empresa) {
        ServiceCompany guardada = serviceCompanyPersistencePort.save(empresa);
        eventDispatcherPort.dispatchAll(empresa.pullEvents());

        return guardada;
    }
}
