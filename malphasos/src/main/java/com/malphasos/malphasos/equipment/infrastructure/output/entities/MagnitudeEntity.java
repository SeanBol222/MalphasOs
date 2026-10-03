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

/**
 * Representación JPA de una magnitud del catálogo metrológico.
 *
 * <p>Solo se lee: el catálogo entra sembrado por {@code V10} y no hay operación que lo escriba.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "magnitud")
public class MagnitudeEntity {

    @Id
    @Column(name = "k_id_magnitud", nullable = false)
    private UUID id;

    @Column(name = "n_codigo_magnitud", nullable = false, unique = true, length = 30)
    private String codigo;

    @Column(name = "n_nombre_magnitud", nullable = false, unique = true, length = 50)
    private String nombre;

    @Column(name = "b_estado_activo", nullable = false)
    private boolean estadoActivo;
}
