package com.malphasos.malphasos.equipment.infrastructure.output.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** La foto de un equipo del cliente (V58): la huella de su imagen en {@code imagen_documento}. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "foto_equipo_cliente")
public class EquipmentPhotoEntity {

    @Id
    @Column(name = "k_id_equipo_cliente", nullable = false)
    private UUID idEquipoCliente;

    @Column(name = "k_huella", nullable = false)
    private String huella;

    @Column(name = "f_actualizada", nullable = false)
    private LocalDateTime actualizada;
}
