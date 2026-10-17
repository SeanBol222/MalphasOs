package com.malphasos.malphasos.company.application.ports.output;

import com.malphasos.malphasos.company.domain.serviceCompany.ServiceCompany;
import java.util.Optional;

/** El almacén de la empresa: una sola fila. */
public interface ServiceCompanyPersistencePort {

    Optional<ServiceCompany> find();

    ServiceCompany save(ServiceCompany empresa);
}
