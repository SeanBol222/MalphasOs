package com.malphasos.malphasos.equipment.infrastructure.output.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

    @Column(name = "n_invima", unique = true)
    private String invima;

    @Column(name = "k_id_fabricante", nullable = false)
    private UUID idFabricante;

    @Column(name = "k_id_equipo", nullable = false)
    private UUID idEquipo;

    @Column(name = "b_estado_activo", nullable = false)
    private boolean estadoActivo;
}
