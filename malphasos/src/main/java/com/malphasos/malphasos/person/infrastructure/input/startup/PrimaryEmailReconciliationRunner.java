package com.malphasos.malphasos.person.infrastructure.input.startup;

import com.malphasos.malphasos.person.application.services.person.PrimaryEmailReconciliation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Lanza la conciliación del correo de la cuenta al terminar de arrancar.
 *
 * <p><b>Nunca tumba el arranque.</b> Si Keycloak no responde, el API arranca igual y lo registra: la
 * conciliación se repite en el siguiente arranque, y entretanto una ficha sin principal funciona como
 * antes de V40. Se apaga con {@code app.identity.reconcile-primary-email}; las pruebas lo hacen, porque
 * no hay Keycloak al que preguntar.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.identity.reconcile-primary-email", havingValue = "true", matchIfMissing = true)
public class PrimaryEmailReconciliationRunner {

    private final PrimaryEmailReconciliation reconciliation;

    @EventListener(ApplicationReadyEvent.class)
    public void reconcile() {
        try {
            int conciliadas = reconciliation.reconcile();
            if (conciliadas > 0) {
                log.info("Correo de la cuenta marcado en {} fichas de persona.", conciliadas);
            }
        } catch (RuntimeException fallo) {
            log.warn("No se pudo conciliar el correo de la cuenta con Keycloak; se reintenta al próximo arranque.", fallo);
        }
    }
}
