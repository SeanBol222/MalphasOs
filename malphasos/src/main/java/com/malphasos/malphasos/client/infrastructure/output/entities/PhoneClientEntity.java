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
@Table(name = "telefono_cliente")
public class PhoneClientEntity {

    @Id
    @Column(name = "k_id_telefono_cliente", nullable = false)
    private UUID id;

    @Column(name = "n_telefono_cliente", nullable = false)
    private String telefono;

    @Column(name = "b_estado_activo", nullable = false)
    private boolean estadoActivo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "k_id_cliente", nullable = false)
    private ClientEntity cliente;
}
