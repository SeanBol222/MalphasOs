package com.malphasos.malphasos.location.infrastructure.output.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Representación JPA de un país. Espeja la tabla creada por {@code V3__location.sql}. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "pais")
public class CountryEntity {

    @Id
    @Column(name = "k_id_pais", nullable = false)
    private UUID id;

    @Column(name = "k_codigo_iso", nullable = false, unique = true, length = 3)
    private String codigoIso;

    @Column(name = "n_nombre_pais", nullable = false, unique = true)
    private String nombre;

    @Column(name = "b_estado_activo", nullable = false)
    private boolean estadoActivo;
}
