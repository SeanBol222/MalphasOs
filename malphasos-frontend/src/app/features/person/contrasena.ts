/**
 * El largo mínimo de una contraseña, el mismo que exige la política del realm de Keycloak:
 * `length(12) and notUsername and notEmail`, en el de desarrollo y en el de producción.
 *
 * <p>La política la impone Keycloak, que es quien guarda la contraseña; esto solo evita mandarle una
 * que va a rechazar. Hasta el 2026-10-17 el alta exigía ocho, y una contraseña de ocho a once caracteres
 * pasaba el formulario y la rechazaba el servidor. Si la política del realm cambia, cambia aquí.
 */
export const LARGO_MINIMO_DE_CONTRASENA = 12;
