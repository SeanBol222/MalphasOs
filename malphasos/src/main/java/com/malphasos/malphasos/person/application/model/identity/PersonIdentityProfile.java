package com.malphasos.malphasos.person.application.model.identity;

import lombok.Builder;

/**
 * Datos de una persona que también viven en el proveedor de identidad y pueden cambiar después del
 * alta.
 *
 * <p>Es deliberadamente más pequeño que {@link PersonIdentityRequest}: al actualizar no viajan ni el
 * nombre de usuario, ni el correo, ni la contraseña.
 *
 * <ul>
 *   <li>El <b>nombre de usuario</b> identifica la cuenta y es el sujeto de las sesiones abiertas;
 *       la petición de actualización de personas ni siquiera lo recibe.
 *   <li>El <b>correo</b> se gestiona en su propio recurso, y una persona puede tener varios sin que
 *       ninguno esté marcado como principal: no hay forma de saber cuál es el de la cuenta.
 *   <li>La <b>contraseña</b> es otra operación, con otras garantías —conocer la anterior, o exigir
 *       cambio al siguiente inicio de sesión—, y no debe cambiarse de rebote al editar un apellido.
 * </ul>
 */
@Builder
public record PersonIdentityProfile(String firstName, String lastName) {
}
