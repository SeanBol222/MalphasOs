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

/**
 * Representación JPA de una unidad que posee un cliente.
 *
 * <p>El área de servicio se guarda como identificador aunque pertenezca a otro módulo: mapearla
 * como relación ataría la persistencia de equipos a la de clientes.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "equipo_cliente")
public class ClientEquipmentEntity {

    @Id
    @Column(name = "k_id_equipo_cliente", nullable = false)
    private UUID id;

    @Column(name = "k_serie", nullable = false)
    private String serie;

    @Column(name = "n_no_inventario")
    private String numeroInventario;

    @Column(name = "f_fecha_compra")
    private LocalDate fechaCompra;

    @Column(name = "v_valor_compra")
    private Long valorCompra;

    @Column(name = "k_id_modelo", nullable = false)
    private UUID idModelo;

    @Column(name = "k_id_area_servicio", nullable = false)
    private UUID idAreaServicio;

    @Column(name = "b_estado_activo", nullable = false)
    private boolean estadoActivo;
}
