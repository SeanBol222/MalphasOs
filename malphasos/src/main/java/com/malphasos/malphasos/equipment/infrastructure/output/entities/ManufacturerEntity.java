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

/** Representación JPA de un fabricante. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "fabricante")
public class ManufacturerEntity {

    @Id
    @Column(name = "k_id_fabricante", nullable = false)
    private UUID id;

    @Column(name = "n_nombre_fabricante", nullable = false, unique = true)
    private String nombre;

    @Column(name = "k_id_pais")
    private UUID idPais;

    @Column(name = "b_estado_activo", nullable = false)
    private boolean estadoActivo;
}
