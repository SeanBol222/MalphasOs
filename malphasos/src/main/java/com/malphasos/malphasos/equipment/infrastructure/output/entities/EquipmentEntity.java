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

/** Representación JPA de la asociación entre una marca y un tipo de equipo. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "equipo")
public class EquipmentEntity {

    @Id
    @Column(name = "k_id_equipo", nullable = false)
    private UUID id;

    @Column(name = "k_id_tipo_equipo", nullable = false)
    private UUID idTipoEquipo;

    @Column(name = "k_id_marca", nullable = false)
    private UUID idMarca;

    @Column(name = "b_estado_activo", nullable = false)
    private boolean estadoActivo;
}
