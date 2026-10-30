package com.malphasos.malphasos.workorder.infrastructure.output.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Representación JPA de una orden de trabajo.
 *
 * <p>El cliente, la sede y el ingeniero se guardan como identificadores: pertenecen a otros módulos
 * y mapearlos como relación ataría estas tablas entre sí.
 *
 * <p>Las tres enumeraciones se guardan por su nombre y no por su posición: {@code EnumType.STRING}
 * en la práctica, escrito a mano al mapear. Guardar el ordinal haría que reordenar el enum
 * reinterpretara en silencio las filas ya escritas, y además el {@code CHECK} del esquema espera el
 * texto.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "orden_trabajo")
public class WorkOrderEntity {

    @Id
    @Column(name = "k_id_orden_trabajo", nullable = false)
    private UUID id;

    /**
     * El cliente, que desde V47 no se guarda: lo dice la sede, y guardarlo aquí era tenerlo dos veces.
     * Se lee de ella con una subconsulta, y por eso las consultas por cliente siguen funcionando. Al
     * construir una orden nueva el valor se pone en memoria, y no se escribe.
     */
    @Formula("(SELECT s.k_id_cliente FROM sede s WHERE s.k_id_sede = k_id_sede)")
    private UUID idCliente;

    @Column(name = "k_id_sede", nullable = false)
    private UUID idSede;

    @Column(name = "f_fecha_mantenimiento", nullable = false)
    private LocalDate fechaMantenimiento;

    @Column(name = "n_periodicidad", nullable = false)
    private String periodicidad;

    @Column(name = "t_tipo_servicio", nullable = false)
    private String tipoServicio;

    @Column(name = "b_garantia", nullable = false)
    private boolean garantia;

    @Column(name = "t_estado_ejecucion", nullable = false)
    private String estadoEjecucion;

    /** Ingeniero asignado. Nulo mientras la orden está creada pero sin asignar. */
    @Column(name = "k_id_ingeniero")
    private UUID idIngeniero;

    @Column(name = "b_estado_activo", nullable = false)
    private boolean estadoActivo;

    /** La orden congelada al ejecutarse, en JSON (V49). Lo escribe y lo lee el mapper. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "t_documento")
    private String documento;

    @Column(name = "b_documento_tardio", nullable = false)
    private boolean documentoTardio;
}
