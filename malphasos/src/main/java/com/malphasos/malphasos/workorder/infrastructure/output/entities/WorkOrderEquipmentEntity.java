package com.malphasos.malphasos.workorder.infrastructure.output.entities;

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
 * Un equipo dentro del alcance de una orden, con el área en la que estaba al seleccionarlo.
 *
 * <p>El equipo y el área se guardan como identificadores aunque pertenezcan a otros módulos:
 * mapearlos como relación ataría la persistencia de las órdenes a la de equipos y clientes.
 *
 * <p>Retirar un equipo del alcance no borra la fila, la deja inactiva; volver a añadirlo reactiva
 * la misma. Así la llave compuesta nunca choca y el historial queda.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@IdClass(WorkOrderEquipmentId.class)
@Table(name = "orden_trabajo_equipo")
public class WorkOrderEquipmentEntity {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "k_id_orden_trabajo", nullable = false)
    private WorkOrderEntity orden;

    @Id
    @Column(name = "k_id_equipo_cliente", nullable = false)
    private UUID equipoCliente;

    /** Área donde estaba el equipo al añadirlo, no donde está hoy. */
    @Column(name = "k_id_area_servicio", nullable = false)
    private UUID areaServicio;

    @Column(name = "b_estado_activo", nullable = false)
    private boolean estadoActivo;
}
