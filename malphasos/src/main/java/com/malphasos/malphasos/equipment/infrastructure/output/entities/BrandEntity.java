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

/** Representación JPA de una marca. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "marca")
public class BrandEntity {

    @Id
    @Column(name = "k_id_marca", nullable = false)
    private UUID id;

    @Column(name = "n_nombre_marca", nullable = false, unique = true)
    private String nombre;

    @Column(name = "b_estado_activo", nullable = false)
    private boolean estadoActivo;
}
