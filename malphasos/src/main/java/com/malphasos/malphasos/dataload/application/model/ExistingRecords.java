package com.malphasos.malphasos.dataload.application.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Lo que ya hay en MalphasOS y una carga podría traer otra vez, con lo justo para reconocerlo por su
 * llave natural (la tabla de la guía del formato). Solo lo activo: lo retirado no se reconoce.
 *
 * <p>{@code cedulasConFirma} son las personas que ya tienen su firma guardada: el ingeniero de una orden
 * del historial la necesita, porque su reporte se congela al cargar.
 *
 * <p>Los nombres van como están escritos; compararlos sin tildes ni mayúsculas es cosa de quien los
 * cruza con la carga.
 */
public record ExistingRecords(List<String> marcas, List<String> fabricantes, List<String> protocolos,
        List<String> tiposEquipo, List<ModelName> modelos, List<String> seriesDePatrones, List<String> cedulas,
        List<ExistingClient> clientes, List<EquipmentName> equipos, List<String> cedulasConFirma) {

    public ExistingRecords {
        marcas = List.copyOf(marcas);
        fabricantes = List.copyOf(fabricantes);
        protocolos = List.copyOf(protocolos);
        tiposEquipo = List.copyOf(tiposEquipo);
        modelos = List.copyOf(modelos);
        seriesDePatrones = List.copyOf(seriesDePatrones);
        cedulas = List.copyOf(cedulas);
        clientes = List.copyOf(clientes);
        equipos = List.copyOf(equipos);
        cedulasConFirma = List.copyOf(cedulasConFirma);
    }

    public ExistingRecords(List<String> marcas, List<String> fabricantes, List<String> protocolos,
            List<String> tiposEquipo, List<ModelName> modelos, List<String> seriesDePatrones, List<String> cedulas,
            List<ExistingClient> clientes, List<EquipmentName> equipos) {
        this(marcas, fabricantes, protocolos, tiposEquipo, modelos, seriesDePatrones, cedulas, clientes, equipos, List.of());
    }

    /** Un modelo es único por tipo, marca y nombre: dos marcas pueden tener su «Una vía». */
    public record ModelName(String tipo, String marca, String nombre) {}

    /** Con los días en que ya tiene órdenes activas: un historial no se carga dos veces. */
    public record ExistingClient(String documento, List<ExistingSite> sedes, List<LocalDate> fechasDeOrdenes) {
        public ExistingClient {
            sedes = List.copyOf(sedes);
            fechasDeOrdenes = List.copyOf(fechasDeOrdenes);
        }

        public ExistingClient(String documento, List<ExistingSite> sedes) {
            this(documento, sedes, List.of());
        }
    }

    public record ExistingSite(String nombre, List<String> areas) {
        public ExistingSite {
            areas = List.copyOf(areas);
        }
    }

    /** Un equipo se reconoce por su modelo y su serie. */
    public record EquipmentName(ModelName modelo, String serie) {}
}
