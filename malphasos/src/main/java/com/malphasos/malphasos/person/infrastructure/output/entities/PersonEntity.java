package com.malphasos.malphasos.person.infrastructure.output.entities;

import com.malphasos.malphasos.person.domain.person.PersonType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Fila de {@code persona} junto con sus correos y teléfonos.
 *
 * <p>El identificador no se genera aquí: es el que asigna el proveedor de identidad al crear el
 * usuario, de modo que ambos sistemas comparten la misma clave.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "persona")
public class PersonEntity {

    @Id
    @Column(name = "k_identificador", nullable = false, unique = true)
    private UUID identificador;

    @Column(name = "k_cedula", nullable = false, unique = true)
    private String cedula;

    @Column(name = "n_primer_nombre", nullable = false)
    private String primerNombre;

    @Column(name = "n_segundo_nombre")
    private String segundoNombre;

    @Column(name = "n_primer_apellido", nullable = false)
    private String primerApellido;

    /**
     * Opcional. En el proyecto original esta columna estaba anotada con {@code @NotBlank} y
     * {@code nullable = false}, en contra tanto del esquema, que la define anulable, como del hecho
     * de que muchas personas tienen un solo apellido.
     */
    @Column(name = "n_segundo_apellido")
    private String segundoApellido;

    /**
     * Se persiste el nombre de la constante y no su posición: con {@code EnumType.ORDINAL},
     * reordenar el enum reinterpretaría en silencio todas las filas ya guardadas.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "t_tipo_persona", nullable = false)
    private PersonType tipoPersona;

    @Enumerated(EnumType.STRING)
    @Column(name = "t_segundo_tipo_persona")
    private PersonType segundoTipoPersona;

    @Column(name = "b_estado_activo", nullable = false)
    private boolean estadoActivo = true;

    /**
     * ⚠️ {@code orphanRemoval = true} borra físicamente la fila del hijo que salga de esta lista, lo
     * que contradice la política de borrado lógico del sistema. Hoy no llega a dispararse porque
     * desactivar un contacto lo marca como inactivo sin sacarlo de la lista, y porque las
     * actualizaciones parten siempre de la persona recién leída, con sus contactos completos.
     *
     * <p>El riesgo real está en guardar una persona construida a mano con las listas vacías: la
     * fusión de Hibernate interpretaría que todos sus contactos fueron eliminados y los borraría de
     * la base. Se conserva el comportamiento del original, pero conviene tenerlo presente.
     */
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "person", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PhonePersonEntity> phonePersonList = new ArrayList<>();

    /**
     * {@code @BatchSize} evita el problema N+1 al listar personas: sin él, cargar cien personas
     * dispararía cien consultas adicionales, una por cada lista de correos.
     */
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "person", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EmailPersonEntity> emailPersonList = new ArrayList<>();
}
