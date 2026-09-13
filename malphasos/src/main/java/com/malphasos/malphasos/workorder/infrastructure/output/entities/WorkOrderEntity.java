package com.malphasos.malphasos.workorder.infrastructure.output.entities;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Representación JPA de una orden de trabajo.
 *
 * <p>El cliente, la sede y el ingeniero se guardan como identificadores: pertenecen a otros módulos
 * y mapearlos como relación ataría estas tablas entre sí.
 *
 * <p>Las tres enumeraciones se guardan por su nombre y no por su posición: {@code EnumType.STRING}
 * en la práctica, escrito a mano al mapear. Guardar el ordinal haría que reordenar el enum
 * reinterpretara en silencio las filas ya escritas, y además el {@code CHECK} del esquema espera el
 * texto.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "orden_trabajo")
public class WorkOrderEntity {

    @Id
    @Column(name = "k_id_orden_trabajo", nullable = false)
    private UUID id;

    @Column(name = "k_id_cliente", nullable = false)
    private UUID idCliente;

    @Column(name = "k_id_sede", nullable = false)
    private UUID idSede;

    @Column(name = "f_fecha_mantenimiento", nullable = false)
    private LocalDate fechaMantenimiento;

    @Column(name = "n_periodicidad", nullable = false)
    private String periodicidad;

    @Column(name = "t_tipo_servicio", nullable = false)
    private String tipoServicio;

    @Column(name = "t_estado_ejecucion", nullable = false)
    private String estadoEjecucion;

    /** Ingeniero asignado. Nulo mientras la orden está creada pero sin asignar. */
    @Column(name = "k_identificador")
    private UUID idIngeniero;

    @Column(name = "b_estado_activo", nullable = false)
    private boolean estadoActivo;

    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<WorkOrderEquipmentEntity> equipos = new ArrayList<>();
}
