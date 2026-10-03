package com.malphasos.malphasos.equipment.infrastructure.output.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Representación JPA de una unidad del catálogo metrológico.
 *
 * <p>Cuelga de su magnitud. Solo se lee, como {@link MagnitudeEntity}.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "unidad_medida")
public class MeasurementUnitEntity {

    @Id
    @Column(name = "k_id_unidad_medida", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "k_id_magnitud", nullable = false)
    private MagnitudeEntity magnitud;

    @Column(name = "n_simbolo_unidad", nullable = false, length = 20)
    private String simbolo;

    @Column(name = "n_nombre_unidad", nullable = false, length = 50)
    private String nombre;

    @Column(name = "b_estado_activo", nullable = false)
    private boolean estadoActivo;
}
