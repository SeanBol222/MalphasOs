package com.malphasos.malphasos.dataload.infrastructure.output.writer;

import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.dataload.application.model.LoadDocument;
import com.malphasos.malphasos.report.domain.serviceReport.UsedReferenceStandard;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Qué certificado de un patrón lleva el informe de un día: el catálogo solo guarda el último. */
class HistoryWriterTest {

    private static final UUID PATRON = UUID.randomUUID();
    private static final UsedReferenceStandard HOY = new UsedReferenceStandard(PATRON, "Manómetro", "Fluke", "MD-1",
            "C-2025", LocalDate.of(2026, 1, 1), "700G", "PAT-1", "0 a 300", "0,1", LocalDate.of(2025, 1, 1), "Lab");

    private static LoadRefs conCertificados(LoadDocument.Certificate... certificados) {
        LoadRefs refs = new LoadRefs();
        refs.putStandard("pat", new LoadRefs.StandardRef(PATRON, List.of(certificados)));
        return refs;
    }

    private static LoadDocument.Certificate cert(String numero, String calibracion, String vencimiento) {
        return new LoadDocument.Certificate(numero, LocalDate.parse(calibracion), LocalDate.parse(vencimiento), "Lab " + numero);
    }

    @Test
    @DisplayName("el vigente ese dia, y entre dos vigentes el calibrado mas tarde")
    void elVigente() {
        LoadRefs refs = conCertificados(cert("C-2023", "2023-01-01", "2024-03-01"), cert("C-2024", "2024-01-15", "2025-01-15"),
                cert("C-2025", "2025-01-01", "2026-01-01"));

        UsedReferenceStandard ficha = HistoryWriter.vigenteEl(LocalDate.of(2024, 2, 1), HOY, refs);

        assertThat(ficha.certificado()).isEqualTo("C-2024");
        assertThat(ficha.vencimiento()).isEqualTo(LocalDate.of(2025, 1, 15));
        assertThat(ficha.fechaCalibracion()).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(ficha.empresaCalibradora()).isEqualTo("Lab C-2024");
        assertThat(ficha.serie()).describedAs("lo demás es la ficha de hoy").isEqualTo("MD-1");
    }

    @Test
    @DisplayName("el vigente aunque despues se calibrara otro que vencio antes")
    void elVigenteNoElUltimoCalibrado() {
        LoadRefs refs = conCertificados(cert("LARGO", "2023-01-01", "2025-01-01"), cert("CORTO", "2024-01-01", "2024-01-31"));

        assertThat(HistoryWriter.vigenteEl(LocalDate.of(2024, 6, 1), HOY, refs).certificado()).isEqualTo("LARGO");
    }

    @Test
    @DisplayName("si ninguno estaba vigente, el ultimo calibrado antes: el informe dice lo que hubo")
    void ningunoVigente() {
        LoadRefs refs = conCertificados(cert("C-2022", "2022-01-01", "2023-01-01"), cert("C-2023", "2023-01-01", "2024-01-01"),
                cert("C-2025", "2025-01-01", "2026-01-01"));

        assertThat(HistoryWriter.vigenteEl(LocalDate.of(2024, 6, 1), HOY, refs).certificado()).isEqualTo("C-2023");
    }

    @Test
    @DisplayName("un servicio anterior a toda calibracion lleva el primer certificado")
    void antesDeTodos() {
        LoadRefs refs = conCertificados(cert("C-2025", "2025-01-01", "2026-01-01"), cert("C-2024", "2024-01-01", "2025-01-01"));

        assertThat(HistoryWriter.vigenteEl(LocalDate.of(2023, 6, 1), HOY, refs).certificado()).isEqualTo("C-2024");
    }

    @Test
    @DisplayName("el numero elegido no lo tiene nadie: ni la base ni otro de la misma carga")
    void unNumeroLibre() {
        UUID deLaCarga = UUID.fromString("aaaaaa00-0000-0000-0000-000000000001");
        UUID deLaBase = UUID.fromString("bbbbbb00-0000-0000-0000-000000000002");
        UUID libre = UUID.fromString("cccccc00-0000-0000-0000-000000000003");
        Iterator<UUID> salen = List.of(deLaCarga, deLaBase, libre).iterator();
        Set<String> enLaCarga = new HashSet<>(Set.of("AAAAAA"));

        UUID id = HistoryWriter.libre("BBBBBB"::equals, enLaCarga, salen::next);

        assertThat(id).isEqualTo(libre);
        assertThat(enLaCarga).describedAs("y queda tomado para el siguiente").contains("CCCCCC");
    }

    @Test
    @DisplayName("un patron que no es de la carga se queda con la ficha de hoy")
    void unoQueNoEsDeLaCarga() {
        assertThat(HistoryWriter.vigenteEl(LocalDate.of(2024, 6, 1), HOY, new LoadRefs())).isSameAs(HOY);
    }
}
