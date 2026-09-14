package com.malphasos.malphasos.person.infrastructure.input.security;

import com.malphasos.malphasos.bootstrap.config.security.ApiAuthority;
import com.malphasos.malphasos.person.application.ports.input.PersonServicePort;
import com.malphasos.malphasos.person.domain.person.PersonType;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Decide si quien llama alcanza para escribir sobre una persona <b>concreta</b>.
 *
 * <p>Existe porque la escalera de usuarios depende de <b>a quién</b> se toca, y al editar o retirar
 * ese dato no está en la ruta: está en la fila. {@code @PreAuthorize} con una autoridad literal es
 * estático y no puede saberlo, de modo que las operaciones que reciben un identificador delegan
 * aquí.
 *
 * <p><b>Por qué vive en este módulo y no en {@code bootstrap/config/security}.</b> Necesita conocer
 * {@link PersonType} y el puerto de personas, y {@code bootstrap} no importa nada de ningún módulo
 * de negocio —la dependencia va en un solo sentido y lleva cinco módulos siéndolo—. Ponerlo allí la
 * habría invertido.
 *
 * <p><b>Lo que no hace.</b> El servicio de aplicación sigue sin saber quién llama: la decisión se
 * toma antes de entrar, en la capa que ya se ocupaba de autorizar. Ninguna clase de
 * {@code application} ni de {@code domain} toca {@code Authentication}.
 */
@Component
@RequiredArgsConstructor
public class PersonWriteGuard {

    private final PersonServicePort personServicePort;

    /**
     * Si quien llama puede crear, editar o retirar a la persona indicada.
     *
     * <p>La autenticación llega como parámetro desde la expresión de la anotación y no se lee del
     * contexto estático: así la regla se prueba sin montar un contexto de seguridad.
     *
     * @param id persona sobre la que se quiere escribir
     * @param autenticacion quien llama, tal como la anotación la entrega
     */
    public boolean canWrite(UUID id, Authentication autenticacion) {
        PersonType tipo = personServicePort.findById(id).getTipoPersona();

        return hasAuthority(autenticacion, requiredAuthority(tipo));
    }

    /**
     * Qué autoridad exige escribir sobre alguien de este tipo.
     *
     * <p>Es la escalera entera, en un solo sitio: la gente de la casa —ingenieros y
     * administradores— está un escalón por encima de la gente del cliente —representantes y
     * encargados—.
     *
     * <p>Se resuelve con una expresión {@code switch} y no con una sentencia: al añadir un valor a
     * {@link PersonType}, el compilador obliga a decidir de qué lado de la escalera cae. Es la misma
     * precaución que toma {@code groupFor} al elegir grupo de Keycloak, y por la misma razón: un
     * tipo nuevo sin caso quedaría accesible a quien no debe.
     */
    static String requiredAuthority(PersonType tipo) {
        return switch (tipo) {
            case ENGINEER, ADMIN, SUPER_ADMIN -> ApiAuthority.SUPER_PERSON_WRITE;
            case CEO_CLIENT, MANAGER -> ApiAuthority.PERSON_WRITE;
        };
    }

    private boolean hasAuthority(Authentication autenticacion, String exigida) {
        if (autenticacion == null || !autenticacion.isAuthenticated()) {
            return false;
        }

        return autenticacion.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(exigida::equals);
    }
}
