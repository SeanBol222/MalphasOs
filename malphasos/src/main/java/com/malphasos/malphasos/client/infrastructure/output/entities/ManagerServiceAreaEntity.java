package com.malphasos.malphasos.client.infrastructure.output.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Una persona a cargo de un área de servicio (2026-10-27, `V41`). Gemela de la de sede. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@IdClass(ManagerServiceAreaId.class)
@Table(name = "encargado_area")
public class ManagerServiceAreaEntity {

    @Id
    @Column(name = "k_identificador", nullable = false)
    private UUID persona;

    @Id
    @Column(name = "k_id_area_servicio", nullable = false)
    private UUID area;

    @Column(name = "b_estado_activo", nullable = false)
    private boolean estadoActivo;
}
