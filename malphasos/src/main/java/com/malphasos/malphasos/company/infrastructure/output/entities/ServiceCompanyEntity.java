package com.malphasos.malphasos.company.infrastructure.output.entities;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Representación JPA de la empresa. Una sola fila: lo impone {@code b_unica} en la base. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "empresa")
public class ServiceCompanyEntity {

    @Id
    @Column(name = "k_id_empresa", nullable = false)
    private UUID id;

    /** Siempre verdadera: es lo que hace chocar a una segunda fila. */
    @Column(name = "b_unica", nullable = false)
    private boolean unica = true;

    @Column(name = "n_nombre", nullable = false)
    private String nombre;

    @Column(name = "k_nit")
    private String nit;

    @Column(name = "t_direccion")
    private String direccion;

    @Column(name = "n_ciudad")
    private String ciudad;

    /**
     * Los teléfonos fijos, en su tabla desde V43: una lista de valores que se reescribe entera, con su
     * posición como parte de la llave.
     */
    @ElementCollection
    @CollectionTable(name = "telefono_empresa", joinColumns = @JoinColumn(name = "k_id_empresa"))
    @OrderColumn(name = "i_orden")
    @Column(name = "n_telefono_empresa", nullable = false)
    private List<String> telefonos = new ArrayList<>();

    @Column(name = "t_movil")
    private String movil;

    @Column(name = "t_correo")
    private String correo;

    @Column(name = "t_garantia")
    private String garantia;

    @Column(name = "t_aclaracion")
    private String aclaracion;

    @Column(name = "k_codigo_hoja_de_vida")
    private String codigoHojaDeVida;

    @Column(name = "k_codigo_informe")
    private String codigoInforme;

    @Column(name = "b_logo")
    private byte[] logo;

    @Column(name = "t_logo_tipo")
    private String logoTipo;
}
