package com.malphasos.malphasos.workorder.infrastructure.output.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Representación JPA de la aprobación de una orden (V54). Sin relaciones: todo va por identificador. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "aprobacion_orden")
public class WorkOrderApprovalEntity {

    @Id
    @Column(name = "k_id_aprobacion", nullable = false)
    private UUID id;

    @Column(name = "k_id_orden_trabajo", nullable = false, updatable = false)
    private UUID idOrdenTrabajo;

    @Column(name = "k_id_supervisor", nullable = false, updatable = false)
    private UUID idSupervisor;

    @Column(name = "k_id_operador", updatable = false)
    private UUID idOperador;

    @Column(name = "k_huella_firma", nullable = false, updatable = false, length = 64)
    private String huellaFirma;

    @Column(name = "f_aprobada", nullable = false, updatable = false)
    private LocalDateTime aprobada;

    @Column(name = "b_estado_activo", nullable = false)
    private boolean estadoActivo;
}
