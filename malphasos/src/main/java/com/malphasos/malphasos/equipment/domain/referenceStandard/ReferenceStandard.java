package com.malphasos.malphasos.equipment.domain.referenceStandard;

import com.malphasos.malphasos.equipment.domain.referenceStandard.events.ReferenceStandardCreatedEvent;
import com.malphasos.malphasos.equipment.domain.referenceStandard.events.ReferenceStandardDeactivatedEvent;
import com.malphasos.malphasos.equipment.domain.referenceStandard.events.ReferenceStandardPayload;
import com.malphasos.malphasos.equipment.domain.referenceStandard.events.ReferenceStandardUpdatedEvent;
import com.malphasos.malphasos.shared.domain.events.AggregateRoot;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Un equipo patrón de la empresa: el aparato de referencia, con su certificado de calibración vigente,
 * contra el que se toman las lecturas de una verificación (2026-10-14).
 *
 * <p>Decidido por el usuario: los patrones son un catálogo y no texto en cada reporte. El reporte copia lo
 * que el patrón tenía el día del servicio, así que renovar aquí un certificado no reescribe los reportes
 * viejos —es la misma decisión que con los pasos del protocolo—.
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ReferenceStandard extends AggregateRoot {

    static final int LARGO_NOMBRE = 120;
    static final int LARGO_MARCA = 80;
    static final int LARGO_SERIE = 60;
    static final int LARGO_CERTIFICADO = 80;

    @EqualsAndHashCode.Include
    private final UUID id;

    private String nombre;

    /** Opcional: un patrón hecho en casa no tiene marca. */
    private String marca;

    private String serie;

    private String certificado;

    private LocalDate vencimientoCertificado;

    /** Lo demás que dicen de él los informes impresos. Nunca nula: sin datos es la ficha vacía. */
    private ReferenceStandardSheet ficha;

    private boolean estadoActivo;

    private ReferenceStandard(
            UUID id, String nombre, String marca, String serie, String certificado,
            LocalDate vencimientoCertificado, ReferenceStandardSheet ficha, boolean estadoActivo) {
        this.id = id;
        this.nombre = nombre;
        this.marca = marca;
        this.serie = serie;
        this.certificado = certificado;
        this.vencimientoCertificado = vencimientoCertificado;
        this.ficha = ficha;
        this.estadoActivo = estadoActivo;
    }

    public static ReferenceStandard create(
            String nombre, String marca, String serie, String certificado, LocalDate vencimientoCertificado,
            ReferenceStandardSheet ficha) {
        LocalDate vencimiento = exigirFecha(vencimientoCertificado);
        ReferenceStandard patron = new ReferenceStandard(UUID.randomUUID(),
                obligatorio(nombre, "nombre", LARGO_NOMBRE), opcional(marca, "marca", LARGO_MARCA),
                obligatorio(serie, "serie", LARGO_SERIE), obligatorio(certificado, "certificado", LARGO_CERTIFICADO),
                vencimiento, exigirFicha(ficha, vencimiento), true);
        patron.registerEvent(new ReferenceStandardCreatedEvent(
                patron.metadataFor(ReferenceStandardCreatedEvent.TYPE), patron.payload()));

        return patron;
    }

    public static ReferenceStandard rehydrate(
            UUID id, String nombre, String marca, String serie, String certificado,
            LocalDate vencimientoCertificado, ReferenceStandardSheet ficha, boolean estadoActivo) {
        return new ReferenceStandard(
                id, nombre, marca, serie, certificado, vencimientoCertificado, ficha, estadoActivo);
    }

    /**
     * Corrige sus datos o renueva su certificado: van siempre todos, ficha incluida. No hace nada si son los
     * que ya tenía. Los reportes que ya copiaron este patrón no cambian.
     */
    public void update(
            String nuevoNombre, String nuevaMarca, String nuevaSerie, String nuevoCertificado,
            LocalDate nuevoVencimiento, ReferenceStandardSheet nuevaFicha) {
        String n = obligatorio(nuevoNombre, "nombre", LARGO_NOMBRE);
        String m = opcional(nuevaMarca, "marca", LARGO_MARCA);
        String s = obligatorio(nuevaSerie, "serie", LARGO_SERIE);
        String c = obligatorio(nuevoCertificado, "certificado", LARGO_CERTIFICADO);
        LocalDate v = exigirFecha(nuevoVencimiento);
        ReferenceStandardSheet f = exigirFicha(nuevaFicha, v);

        if (n.equals(nombre) && Objects.equals(m, marca) && s.equals(serie) && c.equals(certificado)
                && v.equals(vencimientoCertificado) && f.equals(ficha)) {
            return;
        }

        this.nombre = n;
        this.marca = m;
        this.serie = s;
        this.certificado = c;
        this.vencimientoCertificado = v;
        this.ficha = f;
        registerEvent(new ReferenceStandardUpdatedEvent(metadataFor(ReferenceStandardUpdatedEvent.TYPE), payload()));
    }

    /** Retira el patrón sin borrarlo. Retirar dos veces no emite dos eventos. */
    public void deactivate() {
        if (!estadoActivo) {
            return;
        }

        this.estadoActivo = false;
        registerEvent(new ReferenceStandardDeactivatedEvent(
                metadataFor(ReferenceStandardDeactivatedEvent.TYPE), payload()));
    }

    /**
     * Si su certificado vale ese día. El día del vencimiento todavía vale: un certificado «válido hasta
     * el 30» se puede usar el 30.
     */
    public boolean vigenteEl(LocalDate dia) {
        return !dia.isAfter(vencimientoCertificado);
    }

    @Override
    protected String aggregateType() {
        return "ReferenceStandard";
    }

    @Override
    protected String aggregateId() {
        return id.toString();
    }

    private ReferenceStandardPayload payload() {
        return new ReferenceStandardPayload(nombre, serie, certificado, vencimientoCertificado);
    }

    private static String obligatorio(String valor, String campo, int largo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Un equipo patrón necesita " + campo);
        }

        return acotado(valor.trim(), campo, largo);
    }

    static String opcional(String valor, String campo, int largo) {
        return valor == null || valor.isBlank() ? null : acotado(valor.trim(), campo, largo);
    }

    private static String acotado(String valor, String campo, int largo) {
        if (valor.length() > largo) {
            throw new IllegalArgumentException(
                    "El campo " + campo + " de un equipo patrón tiene como mucho " + largo + " caracteres");
        }

        return valor;
    }

    private static LocalDate exigirFecha(LocalDate vencimiento) {
        if (vencimiento == null) {
            throw new IllegalArgumentException(
                    "Un equipo patrón necesita la fecha de vencimiento de su certificado");
        }

        return vencimiento;
    }

    /** La calibración que produjo el certificado no puede ser posterior a su vencimiento. */
    private static ReferenceStandardSheet exigirFicha(ReferenceStandardSheet ficha, LocalDate vencimiento) {
        Objects.requireNonNull(ficha, "La ficha de un equipo patrón no es nula: sin datos es VACIA");
        if (ficha.fechaCalibracion() != null && ficha.fechaCalibracion().isAfter(vencimiento)) {
            throw new IllegalArgumentException(
                    "La calibración de un equipo patrón no puede ser posterior al vencimiento de su certificado");
        }

        return ficha;
    }
}
