package com.malphasos.malphasos.client.infrastructure.output.entities;

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
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

/** Representación JPA de un cliente, con sus contactos y sus representantes legales. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "cliente")
public class ClientEntity {

    @Id
    @Column(name = "k_id_cliente", nullable = false)
    private UUID id;

    @Column(name = "k_documento", nullable = false, unique = true, length = 11)
    private String documento;

    @Column(name = "n_tipo_identificacion", nullable = false)
    private String tipoIdentificacion;

    @Column(name = "n_razon_social", nullable = false)
    private String razonSocial;

    @Column(name = "k_id_pais")
    private UUID idPais;

    @Column(name = "b_estado_activo", nullable = false)
    private boolean estadoActivo;

    // orphanRemoval queda fuera a proposito: un contacto retirado no se borra, se marca inactivo y
    // sigue en la lista. @BatchSize evita una consulta por cliente al listarlos todos.
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, fetch = jakarta.persistence.FetchType.LAZY)
    private List<EmailClientEntity> correos = new ArrayList<>();

    @BatchSize(size = 50)
    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, fetch = jakarta.persistence.FetchType.LAZY)
    private List<PhoneClientEntity> telefonos = new ArrayList<>();

    @BatchSize(size = 50)
    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, fetch = jakarta.persistence.FetchType.LAZY)
    private List<LegalRepresentativeEntity> representantes = new ArrayList<>();
}
