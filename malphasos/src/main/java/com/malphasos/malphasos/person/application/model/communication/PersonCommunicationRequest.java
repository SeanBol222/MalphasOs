package com.malphasos.malphasos.person.application.model.communication;

import com.malphasos.malphasos.person.application.model.request.EmailPersonUseCaseRequest;
import com.malphasos.malphasos.person.application.model.request.PhonePersonUseCaseRequest;
import com.malphasos.malphasos.person.domain.person.PersonType;
import java.util.List;
import lombok.Builder;

/**
 * Datos con los que otro módulo pide registrar una persona sin acceso al sistema.
 *
 * <p>Es el caso del encargado de una sede o de un área de servicio: existe como persona en el
 * sistema, con su cédula y sus datos de contacto, pero no recibe usuario en el proveedor de
 * identidad. Para las personas que sí inician sesión está {@code PersonUseCaseRequest}, que además
 * lleva nombre de usuario y contraseña.
 *
 * <p>Vive en la capa de aplicación, no en infraestructura: es parte del contrato que este módulo
 * publica hacia los demás, y el puerto que lo usa no puede depender de un detalle de entrega.
 *
 * @param segundoTipoPersona función secundaria, opcional. El original no lo transportaba, de modo
 *     que a través de este puerto era imposible registrar al representante legal de un cliente que
 *     además es encargado de una sede, una combinación que el esquema sí admite.
 */
@Builder
public record PersonCommunicationRequest(
        String cedula,
        String primerNombre,
        String segundoNombre,
        String primerApellido,
        String segundoApellido,
        PersonType tipoPersona,
        PersonType segundoTipoPersona,
        List<EmailPersonUseCaseRequest> emailPersonList,
        List<PhonePersonUseCaseRequest> phonePersonList) {
}
