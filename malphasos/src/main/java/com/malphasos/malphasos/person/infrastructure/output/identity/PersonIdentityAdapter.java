package com.malphasos.malphasos.person.infrastructure.output.identity;

import com.malphasos.malphasos.person.application.model.identity.PersonIdentityProfile;
import com.malphasos.malphasos.person.application.model.identity.PersonIdentityRequest;
import com.malphasos.malphasos.person.application.ports.output.PersonIdentityPort;
import com.malphasos.malphasos.person.domain.exception.KeycloakConnectionException;
import com.malphasos.malphasos.person.domain.exception.KeycloakInvalidDataException;
import com.malphasos.malphasos.person.domain.exception.KeycloakUnauthorizedException;
import com.malphasos.malphasos.person.domain.exception.KeycloakUserAlreadyExistsException;
import com.malphasos.malphasos.person.domain.exception.KeycloakUserNotFoundException;
import com.malphasos.malphasos.person.domain.person.RoleType;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.function.Consumer;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Implementa el puerto de identidad sobre la Admin API de Keycloak.
 *
 * <p>Traduce los códigos de respuesta HTTP de Keycloak a excepciones del dominio, de modo que las
 * capas superiores razonen sobre "el usuario ya existe" y no sobre "409".
 */
@Component
public class PersonIdentityAdapter implements PersonIdentityPort {

    private final Keycloak keycloakClient;

    /** Realm donde viven los usuarios de la aplicación. En el original estaba escrito en el código. */
    private final String realm;

    public PersonIdentityAdapter(Keycloak keycloakClient, @Value("${keycloak.admin.realm}") String realm) {
        this.keycloakClient = keycloakClient;
        this.realm = realm;
    }

    @Override
    public String createUser(PersonIdentityRequest request, RoleType roleType) {

        UserRepresentation user = new UserRepresentation();
        user.setUsername(request.userName());
        user.setEmail(request.email());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setGroups(List.of(groupFor(roleType)));
        user.setEnabled(true);
        user.setEmailVerified(true);
        user.setCredentials(List.of(credential(request.password())));

        // El Response de JAX-RS retiene la conexion hasta que se cierra. El original nunca lo
        // cerraba, de modo que cada alta de usuario dejaba una conexion sin liberar.
        try (Response response = keycloakClient.realm(realm).users().create(user)) {
            return createdUserId(response);

        } catch (ProcessingException | WebApplicationException e) {
            // El cliente puede fallar antes de entregar un Response: si no consigue autenticarse
            // contra la Admin API o no alcanza el servidor, lanza en vez de devolver un codigo.
            // Sin traducirlo aqui, la excepcion escapaba hasta el servlet y el cliente recibia un
            // 500 generico, fuera del contrato de errores del API.
            throw translateClientFailure(e, "crear el usuario");
        }
    }

    @Override
    public void deleteUser(String userId) {
        try (Response response = keycloakClient.realm(realm).users().delete(userId)) {

            if (response.getStatus() >= 400) {
                throw new KeycloakConnectionException(
                        "Keycloak respondio " + response.getStatus() + " al eliminar el usuario " + userId);
            }
        } catch (KeycloakConnectionException e) {
            throw e;
        } catch (ProcessingException | WebApplicationException e) {
            throw translateClientFailure(e, "eliminar el usuario " + userId);
        } catch (RuntimeException e) {
            // El original envolvia el fallo en un RuntimeException generico concatenando el mensaje,
            // con lo que se perdia la excepcion original y su traza.
            throw new KeycloakConnectionException("No se pudo eliminar el usuario " + userId, e);
        }
    }

    @Override
    public void disableUser(String userId) {
        modifyUser(userId, "deshabilitar el usuario " + userId, user -> user.setEnabled(false));
    }

    @Override
    public void updateUserProfile(String userId, PersonIdentityProfile profile) {
        modifyUser(userId, "actualizar el usuario " + userId, user -> {
            user.setFirstName(profile.firstName());
            user.setLastName(profile.lastName());
        });
    }

    /**
     * Lee la representación actual del usuario, le aplica el cambio y la devuelve completa.
     *
     * <p>La Admin API actualiza con un PUT sobre el recurso entero. Comprobado contra Keycloak
     * 26.6.1, los campos ausentes de la representación enviada se conservan en lugar de borrarse,
     * así que un PUT parcial también habría funcionado; se lee antes de escribir de todos modos por
     * dos razones. La primera es que ese comportamiento no es contractual: depende de la versión y
     * de la configuración del perfil de usuario, y un atributo no gestionado sí puede perderse.
     * La segunda es que la lectura es la llamada que responde 404 cuando el usuario no existe, de
     * modo que el fallo se detecta antes de intentar ningún cambio.
     *
     * <p>La representación que devuelve Keycloak no incluye credenciales, de modo que este camino no
     * puede reescribir la contraseña de nadie ni siquiera por accidente.
     *
     * <p>A diferencia de {@link #createUser} y {@link #deleteUser}, aquí no hay {@code Response} que
     * cerrar: el cliente generado devuelve el objeto ya deserializado y libera la conexión él mismo.
     */
    private void modifyUser(String userId, String operacion, Consumer<UserRepresentation> cambio) {
        try {
            // users().get(userId) no llama al servidor: solo construye el recurso. La primera
            // llamada real es toRepresentation(), y es la que puede responder 404.
            UserResource userResource = keycloakClient.realm(realm).users().get(userId);

            UserRepresentation user = userResource.toRepresentation();
            cambio.accept(user);
            userResource.update(user);

        } catch (ProcessingException | WebApplicationException e) {
            throw translateClientFailure(e, operacion);

        } catch (RuntimeException e) {
            throw new KeycloakConnectionException("No se pudo " + operacion + " en Keycloak", e);
        }
    }

    /**
     * Traduce un fallo del propio cliente de Keycloak a una excepción del dominio.
     *
     * <p>A diferencia de {@link #createdUserId(Response)}, que interpreta el codigo de una respuesta
     * recibida, aqui la llamada ni siquiera llego a completarse. El motivo real suele venir envuelto
     * en un {@code ProcessingException}, de modo que hay que recorrer la cadena de causas para
     * encontrar el codigo HTTP que lo explica.
     *
     * <p>El caso mas habitual en desarrollo es un 401: el secreto del client administrativo no esta
     * configurado o no coincide, y este servicio no consigue autenticarse contra la Admin API.
     */
    private RuntimeException translateClientFailure(RuntimeException fallo, String operacion) {

        for (Throwable causa = fallo; causa != null; causa = causa.getCause()) {
            if (causa instanceof WebApplicationException web) {
                int status = web.getResponse().getStatus();

                return switch (status) {
                    // El usuario pudo eliminarse a mano desde la consola, o la persona pudo darse
                    // de alta sin cuenta. Quien llama decide si eso interrumpe su caso de uso.
                    case 404 -> new KeycloakUserNotFoundException(
                            "Keycloak no encontro en el realm " + realm + " el usuario al "
                                    + operacion, fallo);
                    case 401, 403 -> new KeycloakUnauthorizedException(
                            "El cliente administrativo no pudo autenticarse contra Keycloak al "
                                    + operacion + ". Revisa el secreto configurado.",
                            fallo);
                    case 409 -> new KeycloakUserAlreadyExistsException(
                            "Ya existe un usuario con esos datos en el realm " + realm, fallo);
                    case 400 -> new KeycloakInvalidDataException(
                            "Keycloak rechazo los datos al " + operacion, fallo);
                    default -> new KeycloakConnectionException(
                            "Keycloak respondio " + status + " al " + operacion, fallo);
                };
            }
        }

        return new KeycloakConnectionException("No se pudo contactar con Keycloak al " + operacion, fallo);
    }

    /**
     * Grupo de Keycloak que corresponde a cada rol.
     *
     * <p>Se resuelve con una expresión switch y no con una sentencia: al agregar un valor nuevo a
     * {@link RoleType}, el compilador obliga a decidir su grupo. En el original era una sentencia
     * sin caso por defecto, así que un rol nuevo habría creado usuarios sin ningún grupo, es decir,
     * sin permiso alguno y sin aviso.
     */
    private String groupFor(RoleType roleType) {
        return switch (roleType) {
            case ENGINEER -> "engineers";
            case CEO_CLIENT -> "clients";
            case ADMIN -> "admins";
        };
    }

    private CredentialRepresentation credential(String password) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(false);

        return credential;
    }

    /**
     * Extrae el identificador del usuario recién creado de la cabecera {@code Location}, o traduce
     * el código de error a la excepción de dominio correspondiente.
     */
    private String createdUserId(Response response) {
        return switch (response.getStatus()) {
            case 201 -> response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");
            case 409 -> throw new KeycloakUserAlreadyExistsException(
                    "Ya existe un usuario con ese nombre o correo en el realm " + realm);
            case 400 -> throw new KeycloakInvalidDataException(
                    "Keycloak rechazo los datos del usuario");
            case 401, 403 -> throw new KeycloakUnauthorizedException(
                    "El cliente administrativo no tiene permisos para crear usuarios en el realm " + realm);
            default -> throw new KeycloakConnectionException(
                    "Respuesta inesperada de Keycloak al crear el usuario: " + response.getStatus());
        };
    }
}
