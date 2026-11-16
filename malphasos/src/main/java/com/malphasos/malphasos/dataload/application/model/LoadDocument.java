package com.malphasos.malphasos.dataload.application.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Un archivo de la carga, leído: la misma forma que el esquema de {@code contracts/carga}, campo por
 * campo y con los mismos nombres, para que se lea sin traducir nada.
 *
 * <p>Solo se construye de un archivo que ya cumplió el esquema, así que los obligatorios están. Las
 * listas que faltan llegan vacías, nunca nulas.
 */
public record LoadDocument(String formato, String contenido, String cliente, String descripcion, Catalog catalogo,
        List<Person> personas, List<Client> clientes, List<Equipment> equipos, List<Order> ordenes) {

    public LoadDocument {
        catalogo = catalogo == null ? new Catalog(null, null, null, null, null, null) : catalogo;
        personas = lista(personas);
        clientes = lista(clientes);
        equipos = lista(equipos);
        ordenes = lista(ordenes);
    }

    static <T> List<T> lista(List<T> valores) {
        return valores == null ? List.of() : List.copyOf(valores);
    }

    public record Catalog(List<Brand> marcas, List<Manufacturer> fabricantes, List<Protocol> protocolos,
            List<EquipmentType> tiposEquipo, List<Model> modelos, List<ReferenceStandard> patrones) {
        public Catalog {
            marcas = lista(marcas);
            fabricantes = lista(fabricantes);
            protocolos = lista(protocolos);
            tiposEquipo = lista(tiposEquipo);
            modelos = lista(modelos);
            patrones = lista(patrones);
        }
    }

    public record Brand(String ref, String nombre) {}

    public record Manufacturer(String ref, String nombre, String pais) {}

    public record Protocol(String ref, String nombre, String descripcion) {}

    public record EquipmentType(String ref, String nombre, String definicionTecnica, String recomendacionesCuidado,
            String tecnologiaPredominante, String uso, String limpiezaCotidiana, Long valorUnitarioMantenimiento,
            List<TypeProtocol> protocolos, List<Verification> verificaciones) {
        public EquipmentType {
            protocolos = lista(protocolos);
            verificaciones = lista(verificaciones);
        }
    }

    public record TypeProtocol(String protocolo, String indicacion) {}

    public record Verification(String ref, String magnitud, String unidad, String modalidad, Integer cantidadDatos,
            BigDecimal tolerancia, List<BigDecimal> puntos, List<BigDecimal> puntosAnteriores) {
        public Verification {
            puntos = lista(puntos);
            puntosAnteriores = lista(puntosAnteriores);
        }
    }

    public record Model(String ref, String tipo, String marca, String fabricante, String nombre, String invima,
            TechnicalSheet fichaTecnica) {}

    public record TechnicalSheet(String riesgo, String alimentacion, BigDecimal voltaje, Integer potencia,
            BigDecimal amperaje, Integer frecuencia, String caracteristicas) {}

    public record ReferenceStandard(String ref, String nombre, String marca, String modelo, String serie,
            String codigoInterno, String rango, String resolucion, List<Certificate> certificados) {
        public ReferenceStandard {
            certificados = lista(certificados);
        }
    }

    public record Certificate(String numero, LocalDate fechaCalibracion, LocalDate vencimiento, String empresaCalibradora) {}

    /** {@code firma} es el PNG en base64, solo de la gente de la casa: la que firman los reportes. */
    public record Person(String ref, String cedula, String tipo, String primerNombre, String segundoNombre,
            String primerApellido, String segundoApellido, List<String> correos, List<String> telefonos, String firma) {
        public Person {
            correos = lista(correos);
            telefonos = lista(telefonos);
        }
    }

    public record Client(String ref, String documento, String tipoIdentificacion, String razonSocial, String pais,
            String sigla, List<String> correos, List<String> telefonos, List<Supervisor> supervisores, List<Site> sedes) {
        public Client {
            correos = lista(correos);
            telefonos = lista(telefonos);
            supervisores = lista(supervisores);
            sedes = lista(sedes);
        }
    }

    public record Supervisor(String persona, String cargo) {}

    public record Site(String ref, String nombre, City ciudad, String calle, String carrera, String numero,
            List<Area> areas) {
        public Site {
            areas = lista(areas);
        }
    }

    public record City(String pais, String nombre) {}

    public record Area(String ref, String nombre) {}

    public record Equipment(String ref, String area, String modelo, String serie, String numeroInventario,
            String codigoInterno, String proveedor, LocalDate fechaCompra, Long valorCompra, Boolean retirado) {}

    public record Order(String ref, String sede, LocalDate fecha, String periodicidad, String tipoServicio,
            Boolean garantia, String ingeniero, List<Report> reportes) {
        public Order {
            reportes = lista(reportes);
        }
    }

    /** {@code finalizado} es una fecha o una fecha con hora: se interpreta al aplicar. */
    public record Report(String ref, String equipo, String finalizado, String fallaReportada, String diagnostico,
            String causa, String repuestos, String procedimientos, String observaciones, String resultado,
            Intake ingreso, List<Step> pasos, List<ReportVerification> verificaciones, Receiver recibe) {
        public Report {
            pasos = lista(pasos);
            verificaciones = lista(verificaciones);
        }
    }

    public record Intake(IntakeState fisico, IntakeState operativo, List<Accessory> accesorios) {
        public Intake {
            accesorios = lista(accesorios);
        }
    }

    public record IntakeState(String estado, String observacion) {}

    public record Accessory(String nombre, Boolean conforme, String observacion) {}

    public record Step(String protocolo, String estado, String nota) {}

    public record ReportVerification(String verificacion, List<String> patrones, List<Reading> lecturas) {
        public ReportVerification {
            patrones = lista(patrones);
            lecturas = lista(lecturas);
        }
    }

    public record Reading(BigDecimal punto, BigDecimal patron, BigDecimal equipo) {}

    public record Receiver(String persona, String nombre, String cedula, String cargo) {}
}
