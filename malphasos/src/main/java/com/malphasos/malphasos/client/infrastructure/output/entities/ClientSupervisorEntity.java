package com.malphasos.malphasos.client.infrastructure.output.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una persona que supervisa a un cliente, con su cargo (V53; hasta entonces, un representante legal).
 *
 * <p>La llave primaria son las dos columnas, porque la relación es de muchos a muchos. Se usa
 * <b>identidad derivada</b>: el {@code @ManyToOne} hacia el cliente es a la vez parte de la llave,
 * de modo que {@code k_id_cliente} se mapea una sola vez. Declararla dos veces —como campo del
 * identificador y como columna de la relación— hace que Hibernate la repita en el {@code INSERT}.
 *
 * <p>Retirar a un supervisor no borra la fila: la deja inactiva, como todo en este sistema. El
 * agregado solo reconstruye los activos, y los demás quedan como historial.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@IdClass(ClientSupervisorId.class)
@Table(name = "supervisor_cliente")
public class ClientSupervisorEntity {

    @Id
    @Column(name = "k_identificador", nullable = false)
    private UUID persona;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "k_id_cliente", nullable = false)
    private ClientEntity cliente;

    @Column(name = "n_cargo", length = 80)
    private String cargo;

    @Column(name = "b_estado_activo", nullable = false)
    private boolean estadoActivo;
}
