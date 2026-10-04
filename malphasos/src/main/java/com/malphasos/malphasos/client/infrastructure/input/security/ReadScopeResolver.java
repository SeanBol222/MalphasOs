package com.malphasos.malphasos.client.infrastructure.input.security;

import com.malphasos.malphasos.client.application.ports.input.ClientOwnershipPort;
import com.malphasos.malphasos.person.application.model.communication.PersonCommunicationResponse;
import com.malphasos.malphasos.person.application.ports.input.PersonCommunicationPort;
import com.malphasos.malphasos.person.domain.person.PersonType;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Traduce quién llama al alcance de lectura que le corresponde.
 *
 * <p>Es la única pieza del sistema que convierte una {@code Authentication} en un filtro de datos.
 * Los adaptadores REST la consultan y pasan el {@link ReadScope} resultante a su caso de uso, de
 * modo que la capa de aplicación recibe el alcance como un argumento y sigue sin saber quién llama.
 *
 * <p><b>Por qué vive en este módulo y no en {@code bootstrap/config/security}.</b> La misma razón
 * que {@code PersonWriteGuard}: necesita el puerto de clientes y el de personas, y {@code bootstrap}
 * no importa nada de ningún módulo de negocio. Y por qué en <b>este</b> módulo y no en los otros
 * tres que lo usan: {@code equipment}, {@code work-order} y {@code report} ya dependían de
 * {@code client} antes de esto, así que no hubo que invertir ninguna dependencia ni duplicar nada.
 *
 * <p><b>La regla: solo restringe cuando identifica positivamente a un representante.</b> El
 * identificador de una persona es el que le asigna Keycloak, de modo que el {@code sub} del token
 * es la llave de la tabla {@code persona} y no hace falta tabla de correspondencia. Si esa fila dice
 * {@code CEO_CLIENT}, el alcance queda en los clientes que representa; si dice cualquier otra cosa
 * —ingeniero, administrador—, el alcance es libre.
 *
 * <p><b>Y qué hace con un llamante que no puede identificar</b>, que es la decisión delicada de esta
 * clase. Una cuenta de Keycloak sin fila en {@code persona} <b>ve todo</b>, y es deliberado: el
 * SuperUsuario se crea a mano en Keycloak y por definición no tiene esa fila, de modo que cerrarle
 * la vista rompería el único camino de arranque que el sistema documenta. El riesgo que esto deja
 * —una cuenta hecha a mano y metida en el grupo {@code clients} vería todo— no amplía nada: quien
 * puede crear usuarios en Keycloak puede ponerse en {@code admins} igual. Queda anotado como deuda.
 *
 * <p>Lo contrario pasa sin autenticación: ahí el alcance es <b>vacío</b>. Esa rama no se alcanza en
 * producción —las treinta y tres lecturas del API exigen una autoridad—, y precisamente porque es
 * inalcanzable conviene que sea la cerrada: no cuesta nada y es la respuesta correcta a «no hay
 * nadie llamando».
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReadScopeResolver {

    private final PersonCommunicationPort personCommunicationPort;
    private final ClientOwnershipPort clientOwnershipPort;

    /** El alcance de quien llama. Nunca devuelve {@code null}. */
    public ReadScope de(Authentication autenticacion) {
        if (autenticacion == null || !autenticacion.isAuthenticated()) {
            return ReadScope.deClientes(Set.of());
        }

        UUID idPersona = identificadorDe(autenticacion);

        if (idPersona == null) {
            return ReadScope.sinRestriccion();
        }

        PersonCommunicationResponse persona = personaONulo(idPersona);

        if (persona == null || persona.tipoPersona() != PersonType.CEO_CLIENT) {
            return ReadScope.sinRestriccion();
        }

        return ReadScope.deClientes(clientOwnershipPort.clientesRepresentadosPor(idPersona));
    }

    /**
     * El {@code sub} del token como UUID, o {@code null} si no lo hay o no lo es.
     *
     * <p>Se lee del propio token y no de {@code getName()} porque ese depende de
     * {@code principalClaimName}: hoy nadie lo configura y {@code getName()} devolvería el
     * {@code sub}, pero el día que alguien lo ponga en {@code preferred_username} este filtro
     * dejaría de encontrar a nadie <b>y abriría la vista</b> en lugar de fallar. Depender del claim
     * explícitamente quita ese modo de fallo silencioso.
     */
    private UUID identificadorDe(Authentication autenticacion) {
        String sub = autenticacion instanceof JwtAuthenticationToken token
                ? token.getToken().getSubject()
                : autenticacion.getName();

        if (sub == null) {
            return null;
        }

        try {
            return UUID.fromString(sub);
        } catch (IllegalArgumentException noEsUnIdentificador) {
            return null;
        }
    }

    /**
     * La persona, o {@code null} si el proveedor de identidad tiene una cuenta que la base no.
     *
     * <p>El puerto lanza cuando no existe, y aquí eso no es un error: es el caso del operador creado
     * a mano. Se registra en el log porque, aunque sea legítimo, conviene poder verlo.
     */
    private PersonCommunicationResponse personaONulo(UUID idPersona) {
        try {
            return personCommunicationPort.findById(idPersona);

        } catch (RuntimeException noExiste) {
            log.warn(
                    "La cuenta {} no tiene persona en la base: se le da alcance libre por ser una "
                            + "cuenta creada fuera del API. Causa: {}",
                    idPersona,
                    noExiste.getMessage());

            return null;
        }
    }
}
