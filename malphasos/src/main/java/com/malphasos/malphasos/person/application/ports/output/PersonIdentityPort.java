package com.malphasos.malphasos.person.application.ports.output;

import com.malphasos.malphasos.person.application.model.identity.PersonIdentityRequest;
import com.malphasos.malphasos.person.domain.person.RoleType;

/**
 * Puerto de salida hacia el proveedor de identidad, donde viven los usuarios con los que se accede
 * al sistema.
 *
 * <p>El registro de una persona con acceso implica dos escrituras en sistemas distintos: el usuario
 * en el proveedor de identidad y la persona en la base de datos. No hay transacción que abarque a
 * ambos, así que quien orqueste la operación debe deshacer el usuario si la persistencia falla.
 */
public interface PersonIdentityPort {

    /**
     * Crea el usuario y lo asigna al grupo correspondiente al rol.
     *
     * @return identificador del usuario creado en el proveedor de identidad
     */
    String createUser(PersonIdentityRequest request, RoleType roleType);

    /** Elimina un usuario. Se usa para deshacer un alta cuya persistencia falló. */
    void deleteUser(String userId);
}
