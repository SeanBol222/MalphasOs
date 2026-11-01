package com.malphasos.malphasos.person.application.services.person;

import com.malphasos.malphasos.person.application.ports.output.PersonIdentityPort;
import com.malphasos.malphasos.person.application.ports.output.PersonPersistencePort;
import com.malphasos.malphasos.person.domain.person.EmailPerson;
import com.malphasos.malphasos.person.domain.person.Person;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Marca el correo de la cuenta en las fichas anteriores a V40 (2026-10-17), preguntándole a Keycloak.
 *
 * <p>La migración no podía hacerlo: el correo de cada cuenta lo sabe Keycloak, no la base, y
 * {@code correo_persona} no guarda fecha de alta para adivinar cuál fue el primero. Esto corre al
 * arrancar y solo mira a quien accede al sistema y aún no tiene principal, así que tras la primera vez
 * no hace ninguna llamada.
 *
 * <p>Si el correo de la cuenta está en la ficha, se marca ese. <b>Si no está, se añade y se marca</b>,
 * decidido por el usuario: la cuenta es la que de verdad recibe los avisos y con la que se puede
 * entrar, de modo que la ficha la tiene que reconocer. Una persona con acceso y sin cuenta —borrada a
 * mano en Keycloak— se deja como está y se registra.
 *
 * <p>Una persona a la vez, cada una con su propia transacción: que Keycloak falle a mitad no deshace
 * las que ya se conciliaron.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrimaryEmailReconciliation {

    /** El ancho de {@code n_correo_persona} desde V2. */
    private static final int ANCHO_DEL_CORREO = 50;

    private final PersonPersistencePort personPersistencePort;
    private final PersonIdentityPort personIdentityPort;

    /** Cuántas fichas quedaron con su principal en esta pasada. */
    public int reconcile() {
        int conciliadas = 0;

        for (Person persona : personPersistencePort.findAll()) {
            if (persona.isEstadoActivo() && persona.primaryEmail().isEmpty()
                    && conciliar(persona)) {
                conciliadas++;
            }
        }

        return conciliadas;
    }

    private boolean conciliar(Person persona) {
        Optional<String> deLaCuenta = personIdentityPort.findUserEmail(persona.getIdentificador().toString());

        if (deLaCuenta.isEmpty() || deLaCuenta.get().isBlank()) {
            log.warn(
                    "La persona {} accede al sistema y no tiene cuenta con correo en Keycloak: no se le "
                            + "marca correo principal.",
                    persona.getIdentificador());
            return false;
        }

        String correo = deLaCuenta.get();
        Optional<EmailPerson> enLaFicha = persona.getEmailPersonList().stream()
                .filter(e -> e.isEstadoActivo() && e.getCorreoPersona().equalsIgnoreCase(correo))
                .findFirst();

        if (enLaFicha.isPresent()) {
            persona.markPrimaryEmail(enLaFicha.get().getIdCorreoPersona());
        } else if (correo.length() > ANCHO_DEL_CORREO) {
            log.warn(
                    "El correo de la cuenta de la persona {} no cabe en la ficha ({} caracteres): se deja "
                            + "sin principal.",
                    persona.getIdentificador(),
                    correo.length());
            return false;
        } else {
            EmailPerson nuevo = EmailPerson.builder()
                    .idCorreoPersona(UUID.randomUUID())
                    .correoPersona(correo)
                    .estadoActivo(true)
                    .build();
            persona.addEmail(nuevo);
            persona.markPrimaryEmail(nuevo.getIdCorreoPersona());
            log.info(
                    "El correo de la cuenta de la persona {} no estaba en su ficha: se añade como principal.",
                    persona.getIdentificador());
        }

        personPersistencePort.save(persona);

        return true;
    }
}
