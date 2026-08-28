package com.malphasos.malphasos.person.infrastructure.output.entities;

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

/** Fila de {@code correo_persona}. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "correo_persona")
public class EmailPersonEntity {

    @Id
    @Column(name = "k_id_correo_persona", nullable = false, unique = true)
    private UUID idCorreoPersona;

    /**
     * Carga perezosa: recuperar un correo no debe arrastrar consigo a la persona entera, que a su
     * vez arrastraria el resto de sus contactos.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "k_identificador")
    private PersonEntity person;

    @Column(name = "n_correo_persona", nullable = false)
    private String correoPersona;

    @Column(name = "b_estado_activo", nullable = false)
    private boolean estadoActivo = true;
}
