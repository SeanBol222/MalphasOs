package com.malphasos.malphasos.person.application.ports.input;

import com.malphasos.malphasos.person.application.model.communication.PersonCommunicationResponse;
import java.util.UUID;

/**
 * Contrato que este módulo publica hacia los demás para operar sobre personas.
 *
 * <p>Existe para que otro contexto —los de clientes, órdenes y reportes— pueda consultar personas
 * <b>sin conocer el modelo de dominio de este módulo</b>. Todo lo que cruza la frontera son los DTO de {@code application.model.communication}.
 *
 * <p>Es distinto de {@link PersonServicePort}, que expone el modelo de dominio completo y está
 * pensado para los adaptadores de entrada de este mismo módulo. Aquí la superficie es mínima a
 * propósito: cuanto menos publique, menos acopla.
 *
 * <p>En el original este puerto declaraba sus operaciones con DTO que vivían en
 * {@code infrastructure}, de modo que la capa de aplicación dependía de la de infraestructura y se
 * invertía la dirección de las dependencias. Los DTO pasan aquí a ser de la capa de aplicación,
 * que es donde corresponde a un contrato entre módulos.
 */
public interface PersonCommunicationPort {

    /**
     * Consulta una persona por su identificador.
     *
     * @throws com.malphasos.malphasos.person.domain.exception.PersonNotFoundException si no existe
     */
    PersonCommunicationResponse findById(UUID id);

    /**
     * Si hay una ficha con ese identificador, retirada o no.
     *
     * <p>Existe para quien necesita preguntar sin que la ausencia sea un error: {@link #findById} lanza, y
     * dentro de una transacción esa excepción la deja marcada para deshacerse aunque se capture. Lo
     * destapó la aprobación en sitio del SuperUsuario, que no tiene ficha (V54, 2026-11-01).
     */
    boolean exists(UUID id);
}
