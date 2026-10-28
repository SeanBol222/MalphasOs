package com.malphasos.malphasos.equipment.infrastructure.output.entities;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

/**
 * Representación JPA de una de las cosas que se verifican en un tipo de equipo.
 *
 * <p>Cuelga de su tipo y no tiene vida propia: no hay repositorio ni adaptador para ella, se guarda y
 * se lee con el tipo. Es la misma forma que los contactos de un cliente.
 *
 * <p><b>La magnitud no tiene columna desde V48</b>: es la de la unidad, que la lleva. Hasta entonces se
 * guardaban las dos, atadas por una foránea compuesta para que no se contradijeran.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "verificacion_tipo_equipo")
public class TypeVerificationEntity {

    @Id
    @Column(name = "k_id_verificacion", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "k_id_tipo_equipo", nullable = false)
    private EquipmentTypeEntity tipoEquipo;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "k_id_unidad_medida", nullable = false)
    private MeasurementUnitEntity unidad;

    @Column(name = "n_modalidad_verificacion", nullable = false, length = 50)
    private String modalidad;

    @Column(name = "i_cantidad_datos")
    private Integer cantidadDatos;

    // orphanRemoval queda fuera a proposito: un punto retirado no se borra, se marca inactivo y sigue en
    // la lista, porque con el se hicieron los reportes anteriores.
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "verificacion", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<VerificationPointEntity> puntos = new ArrayList<>();

    @Column(name = "b_estado_activo", nullable = false)
    private boolean estadoActivo;

    /** El error admitido, en la unidad de la verificación. Nulo si no se declaró. Desde V26. */
    @Column(name = "d_tolerancia", precision = 12, scale = 4)
    private BigDecimal tolerancia;
}
