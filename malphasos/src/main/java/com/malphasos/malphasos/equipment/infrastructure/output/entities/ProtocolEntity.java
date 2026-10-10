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

/** Representación JPA de un protocolo de mantenimiento. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "protocolo")
public class ProtocolEntity {

    @Id
    @Column(name = "k_id_protocolo", nullable = false)
    private UUID id;

    @Column(name = "n_nombre", nullable = false)
    private String nombre;

    /** El nombre sin mayúsculas ni tildes. La calcula el dominio; aquí solo se guarda. */
    @Column(name = "n_clave", nullable = false)
    private String clave;

    @Column(name = "t_descripcion", nullable = false)
    private String descripcion;

    @Column(name = "b_estado_activo", nullable = false)
    private boolean estadoActivo;
}
