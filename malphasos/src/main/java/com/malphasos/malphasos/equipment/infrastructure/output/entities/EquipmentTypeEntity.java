package com.malphasos.malphasos.equipment.infrastructure.output.entities;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.hibernate.annotations.BatchSize;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Representación JPA de un tipo de equipo.
 *
 * <p><b>Ya no tiene {@code b_verificable}, {@code n_tipo_verificacion} ni {@code i_cantidad_datos}</b>
 * (2026-10-03): las tres bajaron a {@link TypeVerificationEntity}, porque un tipo se verifica en varias
 * magnitudes y cada una tiene su modalidad y su cantidad. El booleano no se sustituyó por nada: era
 * exactamente «hay modalidad», redundante por construcción, y ahora «se verifica» es «tiene alguna
 * verificación activa», que se cuenta.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "tipo_equipo")
public class EquipmentTypeEntity {

    @Id
    @Column(name = "k_id_tipo_equipo", nullable = false)
    private UUID id;

    @Column(name = "n_nombre_tipo_equipo", nullable = false, unique = true)
    private String nombre;

    @Column(name = "t_definicion_tecnica", nullable = false)
    private String definicionTecnica;

    @Column(name = "t_recomendaciones_cuidado", nullable = false)
    private String recomendacionesCuidado;

    @Column(name = "t_tecnologia_predominante", nullable = false)
    private String tecnologiaPredominante;

    // Voltaje y amperaje vivian aqui hasta V15: bajaron al modelo, ver ModelEntity.
    @Column(name = "t_uso")
    private String uso;

    @Column(name = "t_limpieza_cotidiana")
    private String limpiezaCotidiana;

    // orphanRemoval queda fuera a proposito, igual que en los contactos de un cliente: una verificacion
    // retirada no se borra, se marca inactiva y sigue en la lista, porque con ella se firmaron reportes.
    // @BatchSize evita una consulta por tipo al listarlos todos.
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "tipoEquipo", cascade = CascadeType.ALL, fetch = jakarta.persistence.FetchType.LAZY)
    private List<TypeVerificationEntity> verificaciones = new ArrayList<>();

    @Column(name = "m_valor_unitario_mantenimiento", nullable = false)
    private long valorUnitarioMantenimiento;

    @Column(name = "b_estado_activo", nullable = false)
    private boolean estadoActivo;
}
