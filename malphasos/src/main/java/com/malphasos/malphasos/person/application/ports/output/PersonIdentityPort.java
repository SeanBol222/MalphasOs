package com.malphasos.malphasos.person.application.ports.output;

import com.malphasos.malphasos.person.application.model.identity.PersonIdentityProfile;
import com.malphasos.malphasos.person.application.model.identity.PersonIdentityRequest;
import com.malphasos.malphasos.person.domain.exception.KeycloakUserNotFoundException;
import com.malphasos.malphasos.person.domain.person.RoleType;

/**
 * Puerto de salida hacia el proveedor de identidad, donde viven los usuarios con los que se accede
 * al sistema.
 *
 * <p>El registro de una persona con acceso implica dos escrituras en sistemas distintos: el usuario
 * en el proveedor de identidad y la persona en la base de datos. No hay transacción que abarque a
 * ambos, así que quien orqueste la operación debe deshacer el usuario si la persistencia falla, y
 * ordenar las llamadas de modo que un fallo a mitad de camino deje el sistema cerrado y no abierto.
 */
public interface PersonIdentityPort {

    /**
     * Crea el usuario y lo asigna al grupo correspondiente al rol.
     *
     * @return identificador del usuario creado en el proveedor de identidad
     */
    String createUser(PersonIdentityRequest request, RoleType roleType);

    /**
     * Elimina un usuario. Se usa para deshacer un alta cuya persistencia falló, cuando el usuario
     * acaba de crearse y no tiene historial que valga la pena conservar.
     */
    void deleteUser(String userId);

    /**
     * Retira el acceso de un usuario sin borrarlo: deja de poder autenticarse y de renovar sus
     * credenciales, pero la cuenta y su historial siguen existiendo.
     *
     * <p>Es la contraparte del borrado lógico de la base de datos, y por eso no se usa
     * {@link #deleteUser}: el identificador de la persona <em>es</em> el del usuario, de modo que
     * borrar la cuenta rompería para siempre esa correspondencia —volver a crearla daría un
     * identificador distinto del que la persona ya tiene almacenado.
     *
     * <p>Es idempotente: deshabilitar un usuario ya deshabilitado no falla ni cambia nada.
     *
     * @throws KeycloakUserNotFoundException si no hay ningún usuario con ese identificador
     */
    void disableUser(String userId);

    /**
     * Actualiza en el proveedor de identidad los datos personales que también guarda la aplicación.
     *
     * <p>Solo viaja lo que aparece en {@link PersonIdentityProfile}: ni el nombre de usuario, ni el
     * correo, ni la contraseña.
     *
     * @throws KeycloakUserNotFoundException si no hay ningún usuario con ese identificador
     */
    void updateUserProfile(String userId, PersonIdentityProfile profile);

    /**
     * Deja al usuario en el grupo que le corresponde, y en ninguno más.
     *
     * <p>Existe porque cambiar el tipo de una persona **no movía su cuenta de grupo**: quien dejaba
     * de ser ingeniero conservaba sus permisos, y desde que hay filtrado por dueño también pasaba lo
     * contrario —un representante al que se le cambia el tipo perdía el filtro sin perder su grupo—.
     * La fila y la identidad decían cosas distintas, y el sistema creía las dos a la vez.
     *
     * <p><b>Un {@code roleType} nulo significa «en ningún grupo»</b>, no «déjalo como está». Es el
     * caso de un encargado, que por definición no accede al sistema: la cuenta queda sin ninguna
     * autoridad y toda llamada suya responde 403. Quién corresponde a cada tipo lo decide la capa de
     * aplicación y no este puerto, que sigue hablando de {@link RoleType} y no de
     * {@code PersonType}: son dos vocabularios distintos a propósito.
     *
     * <p>Es idempotente: si el usuario ya está donde debe, no se toca nada.
     */
    void syncGroup(String userId, RoleType roleType);
}
