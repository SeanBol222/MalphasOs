package com.malphasos.malphasos.equipment.infrastructure.output.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/** Representación JPA de una intervención externa (V61). */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "intervencion_externa")
public class ExternalInterventionEntity {

    @Id
    @Column(name = "k_id_intervencion_externa", nullable = false)
    private UUID id;

    @Column(name = "k_id_equipo_cliente", nullable = false)
    private UUID idEquipoCliente;

    @Column(name = "f_realizada", nullable = false)
    private LocalDate realizada;

    @Column(name = "t_tipo_servicio", nullable = false)
    private String tipoServicio;

    @Column(name = "t_descripcion", nullable = false)
    private String descripcion;

    @Column(name = "n_empresa", nullable = false)
    private String empresa;

    @Column(name = "t_resultado")
    private String resultado;

    @Column(name = "n_documento")
    private String documento;

    /** La pone la base al insertar, y no cambia: es cuándo se anotó. */
    @Generated(event = EventType.INSERT)
    @Column(name = "f_registrada", nullable = false, insertable = false, updatable = false)
    private LocalDateTime registrada;

    @Column(name = "b_estado_activo", nullable = false)
    private boolean estadoActivo;
}
