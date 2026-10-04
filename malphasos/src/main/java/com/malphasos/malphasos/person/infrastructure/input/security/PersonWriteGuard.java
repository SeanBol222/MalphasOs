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
     * Si quien llama puede escribir sobre esta persona <b>y dejarla de este tipo</b>.
     *
     * <p>Existe porque {@link #canWrite} mira el tipo que la persona <b>tiene</b>, y la edición puede
     * cambiárselo en la misma petición. Con solo esa comprobación, {@code person.write} alcanzaba para
     * tomar a un representante de cliente y dejarlo {@code ADMIN} —o {@code SUPER_ADMIN}—, que es
     * exactamente lo que {@code POST /persons/admins} exige {@code super.person.write} para hacer: el
     * escalón de arriba dejaba de significar nada por la puerta de al lado. Se exigen <b>las dos</b>
     * autoridades, la del tipo de origen y la del de destino.
     *
     * <p><b>Y desde el 2026-10-04 no es solo un escalón saltado.</b> El alcance de lectura se decide
     * por el tipo de la persona que llama, así que cambiárselo a una cuenta del grupo {@code clients}
     * le quitaba el filtrado por dueño <b>sin tocarle ni un rol de Keycloak</b>: pasaba de ver sus
     * clientes a ver todos los que sus autoridades alcanzan. Un escalón de datos se convirtió en una
     * fuga de lectura al construirse el filtro, y es el argumento de por qué esto no podía esperar.
     *
     * <p>Degradar también cuesta el escalón de arriba, y no por simetría: tocar a un ingeniero es
     * tocar a gente de la casa, y el tipo de destino no lo abarata.
     *
     * <p><b>Un {@code nuevoTipo} nulo significa «no se pide cambio de tipo», no «prohibido».</b> Por
     * la ruta de hoy no llega: {@code tipoPersona} es {@code @NotNull} y la validación del cuerpo
     * corre antes que {@code @PreAuthorize}. Se declara igual porque es el contrato de esta guarda y
     * no un detalle del DTO, y porque el día que esta operación pase a {@code PATCH} —que es lo que
     * la convención del proyecto exige— una edición parcial sin tipo será lo normal. Lo destapó una
     * mutación que sobrevivía: tratar el nulo como prohibido no rompía ninguna prueba.
     *
     * <p>El segundo tipo no entra en esta cuenta porque solo admite {@code MANAGER} —lo exige el
     * dominio y lo repite un {@code CHECK} del esquema—, de modo que no hay nada que escalar por ahí.
     * Si algún día admitiera más valores, este método es el sitio donde añadirlo.
     */
    public boolean canUpdate(UUID id, PersonType nuevoTipo, Authentication autenticacion) {
        if (!canWrite(id, autenticacion)) {
            return false;
        }

        return nuevoTipo == null || hasAuthority(autenticacion, requiredAuthority(nuevoTipo));
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
