package com.malphasos.malphasos.client.infrastructure.output.entities;

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

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "correo_cliente")
public class EmailClientEntity {

    @Id
    @Column(name = "k_id_correo_cliente", nullable = false)
    private UUID id;

    @Column(name = "n_correo_cliente", nullable = false)
    private String correo;

    @Column(name = "b_estado_activo", nullable = false)
    private boolean estadoActivo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "k_id_cliente", nullable = false)
    private ClientEntity cliente;
}
