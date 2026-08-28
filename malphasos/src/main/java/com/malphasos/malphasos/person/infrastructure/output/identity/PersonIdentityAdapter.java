package com.malphasos.malphasos.person.infrastructure.output.identity;

import com.malphasos.malphasos.person.application.model.identity.PersonIdentityRequest;
import com.malphasos.malphasos.person.application.ports.output.PersonIdentityPort;
import com.malphasos.malphasos.person.domain.exception.KeycloakConnectionException;
import com.malphasos.malphasos.person.domain.exception.KeycloakInvalidDataException;
import com.malphasos.malphasos.person.domain.exception.KeycloakUnauthorizedException;
import com.malphasos.malphasos.person.domain.exception.KeycloakUserAlreadyExistsException;
import com.malphasos.malphasos.person.domain.person.RoleType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.keycloak.admin.client.Keycloak;
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
        user.setGroups(List.of(grupoDe(roleType)));
        user.setEnabled(true);
        user.setEmailVerified(true);
        user.setCredentials(List.of(credencial(request.password())));

        // El Response de JAX-RS retiene la conexion hasta que se cierra. El original nunca lo
        // cerraba, de modo que cada alta de usuario dejaba una conexion sin liberar.
        try (Response response = keycloakClient.realm(realm).users().create(user)) {
            return idDeUsuarioCreado(response);
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
        } catch (RuntimeException e) {
            // El original envolvia el fallo en un RuntimeException generico concatenando el mensaje,
            // con lo que se perdia la excepcion original y su traza.
            throw new KeycloakConnectionException("No se pudo eliminar el usuario " + userId, e);
        }
    }

    /**
     * Grupo de Keycloak que corresponde a cada rol.
     *
     * <p>Se resuelve con una expresión switch y no con una sentencia: al agregar un valor nuevo a
     * {@link RoleType}, el compilador obliga a decidir su grupo. En el original era una sentencia
     * sin caso por defecto, así que un rol nuevo habría creado usuarios sin ningún grupo, es decir,
     * sin permiso alguno y sin aviso.
     */
    private String grupoDe(RoleType roleType) {
        return switch (roleType) {
            case ENGINEER -> "engineers";
            case CEO_CLIENT -> "clients";
            case ADMIN -> "admins";
        };
    }

    private CredentialRepresentation credencial(String password) {
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
    private String idDeUsuarioCreado(Response response) {
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
