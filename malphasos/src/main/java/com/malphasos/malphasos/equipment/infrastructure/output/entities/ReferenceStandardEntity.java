package com.malphasos.malphasos.equipment.infrastructure.output.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Representación JPA de un equipo patrón. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "equipo_patron")
public class ReferenceStandardEntity {

    @Id
    @Column(name = "k_id_equipo_patron", nullable = false)
    private UUID id;

    @Column(name = "n_nombre", nullable = false)
    private String nombre;

    @Column(name = "n_marca")
    private String marca;

    @Column(name = "k_serie", nullable = false)
    private String serie;

    @Column(name = "n_certificado", nullable = false)
    private String certificado;

    @Column(name = "f_vencimiento_certificado", nullable = false)
    private LocalDate vencimientoCertificado;

    @Column(name = "b_estado_activo", nullable = false)
    private boolean estadoActivo;
}
