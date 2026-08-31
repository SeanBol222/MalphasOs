package com.malphasos.malphasos.location.application.services.country;

import com.malphasos.malphasos.location.application.ports.input.CountryServicePort;
import com.malphasos.malphasos.location.application.ports.output.CountryPersistencePort;
import com.malphasos.malphasos.location.application.services.country.commands.CreateCountryCommand;
import com.malphasos.malphasos.location.application.services.country.commands.DeactivateCountryCommand;
import com.malphasos.malphasos.location.application.services.country.commands.UpdateCountryCommand;
import com.malphasos.malphasos.location.domain.country.Country;
import com.malphasos.malphasos.location.domain.exception.CountryNotFoundException;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta los casos de uso de países: recupera el agregado, le pide el cambio, lo persiste y
 * publica lo que registró.
 *
 * <p>Ese orden importa. Los eventos salen <b>después</b> de persistir, de modo que nunca se anuncia
 * un cambio que la base terminó rechazando. Quedan dentro de la transacción, así que un consumidor
 * que necesite reaccionar solo a lo ya confirmado debe escuchar con
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} y no con un {@code @EventListener}
 * corriente.
 *
 * <p>El servicio no valida el código ISO ni el nombre: eso lo hace el agregado, que es quien tiene
 * la regla. Tampoco comprueba que el código no esté repetido, porque la unicidad la garantiza el
 * esquema y una comprobación previa solo abriría una ventana entre la consulta y la escritura; la
 * violación de integridad se traduce a 409 en el manejador transversal.
 */
@Service
@RequiredArgsConstructor
public class CountryService implements CountryServicePort {

    private final CountryPersistencePort countryPersistencePort;
    private final EventDispatcherPort eventDispatcherPort;

    @Override
    @Transactional(readOnly = true)
    public List<Country> findAll() {
        return countryPersistencePort.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Country findById(UUID id) {
        return countryPersistencePort.findById(id)
                .orElseThrow(() -> new CountryNotFoundException(id));
    }

    @Override
    @Transactional
    public Country create(CreateCountryCommand command) {
        return persistAndPublish(Country.create(command.codigoIso(), command.nombre()));
    }

    @Override
    @Transactional
    public Country update(UpdateCountryCommand command) {
        Country pais = findById(command.id());

        if (command.nombre() != null) {
            pais.rename(command.nombre());
        }

        return persistAndPublish(pais);
    }

    @Override
    @Transactional
    public void deactivate(DeactivateCountryCommand command) {
        Country pais = findById(command.id());
        pais.deactivate();

        persistAndPublish(pais);
    }

    /**
     * Guarda el agregado y publica lo que registró.
     *
     * <p>Se publica lo del agregado recibido y no lo del devuelto por el almacén: recoger vacía la
     * lista, y el adaptador reconstruye una instancia distinta que ya no la lleva.
     */
    private Country persistAndPublish(Country pais) {
        Country guardado = countryPersistencePort.save(pais);
        eventDispatcherPort.dispatchAll(pais.pullEvents());

        return guardado;
    }
}
