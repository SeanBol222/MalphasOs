package com.malphasos.malphasos.client.infrastructure.output.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una persona a cargo de una sede (2026-10-27, `V41`).
 *
 * <p>La llave es el par (persona, sede): la misma sede no se repite para una persona, y relevarla de
 * ella apaga {@code b_estado_activo} en lugar de borrar la fila. Las columnas son identificadores y no
 * relaciones: el agregado referencia por identificador, y nada aquí necesita cargar la sede.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@IdClass(ManagerHeadquarterId.class)
@Table(name = "encargado_sede")
public class ManagerHeadquarterEntity {

    @Id
    @Column(name = "k_identificador", nullable = false)
    private UUID persona;

    @Id
    @Column(name = "k_id_sede", nullable = false)
    private UUID sede;

    @Column(name = "b_estado_activo", nullable = false)
    private boolean estadoActivo;
}
