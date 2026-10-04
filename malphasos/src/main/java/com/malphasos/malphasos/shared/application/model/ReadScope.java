package com.malphasos.malphasos.shared.application.model;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Hasta dónde llega lo que quien consulta tiene derecho a ver, expresado en clientes.
 *
 * <p>Existe porque el modelo de permisos dice <b>qué</b> se puede hacer y nada decía <b>sobre qué
 * filas</b>: un representante legal con {@code client.read} leía todos los clientes del sistema, no
 * solo los suyos. El alcance viaja como <b>argumento</b> de cada consulta en vez de leerse de un
 * contexto estático, y esa es la decisión de diseño que sostiene todo lo demás: ninguna clase de
 * {@code application} ni de {@code domain} toca {@code Authentication}, pero el filtro queda
 * declarado en la firma, de modo que una consulta nueva no puede olvidarlo sin que el compilador lo
 * diga.
 *
 * <p><b>La trampa que este tipo existe para hacer imposible.</b> «Ve todo» y «no ve nada» son dos
 * estados que en un {@code Set} se parecen demasiado: un conjunto vacío interpretado como «sin
 * filtro» abre el sistema entero, y un «sin filtro» interpretado como conjunto vacío lo cierra. Aquí
 * son ramas distintas y no hay forma de confundirlas: {@link #visibleClients()} <b>lanza</b> si el
 * alcance no tiene restricción, así que un adaptador no puede construir un {@code WHERE IN} con la
 * lista de nadie y creer que ha filtrado algo.
 *
 * <p>Un alcance restringido al conjunto vacío es legítimo y significa lo que dice: alguien que no
 * representa a ningún cliente no ve ninguno.
 */
public final class ReadScope {

    private static final ReadScope SIN_RESTRICCION = new ReadScope(null);

    /** Clientes visibles, o {@code null} cuando el alcance no tiene restricción. */
    private final Set<UUID> clientesVisibles;

    private ReadScope(Set<UUID> clientesVisibles) {
        this.clientesVisibles = clientesVisibles;
    }

    /** Quien consulta ve el sistema entero: la gente de la casa. */
    public static ReadScope unrestricted() {
        return SIN_RESTRICCION;
    }

    /**
     * Quien consulta ve únicamente estos clientes y lo que cuelga de ellos.
     *
     * <p>Se copia el conjunto recibido: el alcance es un valor y no debe cambiar a espaldas de quien
     * ya lo está usando para filtrar.
     */
    public static ReadScope ofClients(Collection<UUID> ids) {
        Objects.requireNonNull(ids, "Un alcance restringido necesita la lista de clientes, aunque esté vacía");

        return new ReadScope(Set.copyOf(ids));
    }

    /** Si este alcance no filtra nada. */
    public boolean coversEverything() {
        return clientesVisibles == null;
    }

    /**
     * Si este cliente entra en el alcance.
     *
     * <p>Es lo que usan las consultas por identificador: queda fuera del alcance y se responde como
     * si no existiera, que es la decisión tomada para no confirmar la existencia de datos ajenos.
     */
    public boolean covers(UUID idCliente) {
        return coversEverything() || clientesVisibles.contains(idCliente);
    }

    /**
     * Los clientes visibles, para construir la consulta de un listado.
     *
     * @throws IllegalStateException si el alcance no tiene restricción. No es un caso que deba
     *     tratarse en tiempo de ejecución: es un error de programación —preguntar por la lista sin
     *     haber comprobado {@link #coversEverything()}— y fallar es lo único que impide que se convierta
     *     en un filtro vacío que no filtra.
     */
    public Set<UUID> visibleClients() {
        if (coversEverything()) {
            throw new IllegalStateException(
                    "Este alcance no tiene restriccion: no hay lista de clientes que pedir. "
                            + "Comprueba coversEverything() antes de filtrar");
        }

        return clientesVisibles;
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof ReadScope alcance && Objects.equals(clientesVisibles, alcance.clientesVisibles);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(clientesVisibles);
    }

    @Override
    public String toString() {
        return coversEverything() ? "ReadScope[sin restriccion]" : "ReadScope" + clientesVisibles;
    }
}
