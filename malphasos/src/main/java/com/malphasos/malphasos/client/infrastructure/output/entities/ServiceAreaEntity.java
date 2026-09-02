package com.malphasos.malphasos.client.infrastructure.output.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Representación JPA de un área de servicio. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "area_servicio")
public class ServiceAreaEntity {

    @Id
    @Column(name = "k_id_area_servicio", nullable = false)
    private UUID id;

    @Column(name = "n_nombre_area", nullable = false)
    private String nombre;

    @Column(name = "k_id_sede", nullable = false)
    private UUID idSede;

    @Column(name = "b_estado_activo", nullable = false)
    private boolean estadoActivo;
}
