package com.malphasos.malphasos.equipment.infrastructure.output.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Representación JPA de un modelo. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "modelo")
public class ModelEntity {

    @Id
    @Column(name = "k_id_modelo", nullable = false)
    private UUID id;

    @Column(name = "n_nombre_modelo", nullable = false, length = 50)
    private String nombre;

    @Column(name = "n_invima", unique = true)
    private String invima;

    @Column(name = "k_id_fabricante", nullable = false)
    private UUID idFabricante;

    @Column(name = "k_id_equipo", nullable = false)
    private UUID idEquipo;

    // La ficha tecnica, desde V15. Voltaje y amperaje estaban en tipo_equipo hasta entonces.
    @Column(name = "n_clase_riesgo", length = 3)
    private String claseRiesgo;

    @Column(name = "t_caracteristicas")
    private String caracteristicas;

    @Column(name = "n_alimentacion")
    private String alimentacion;

    @Column(name = "i_voltaje")
    private Integer voltaje;

    @Column(name = "i_potencia")
    private Integer potencia;

    @Column(name = "d_amperaje")
    private BigDecimal amperaje;

    @Column(name = "i_frecuencia")
    private Integer frecuencia;

    @Column(name = "b_estado_activo", nullable = false)
    private boolean estadoActivo;
}
