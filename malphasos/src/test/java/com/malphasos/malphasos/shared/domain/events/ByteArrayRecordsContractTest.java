package com.malphasos.malphasos.shared.domain.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.company.application.services.serviceCompany.commands.ChangeCompanyLogoCommand;
import com.malphasos.malphasos.company.application.services.serviceCompany.commands.SeedServiceCompanyCommand;
import com.malphasos.malphasos.dataload.application.model.DataLoadFile;
import com.malphasos.malphasos.equipment.application.model.lifeSheet.LifeSheetPdf;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.ReceiptScope;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.SignReceiptCommand;
import com.malphasos.malphasos.workorder.application.services.approval.commands.ApproveOnSiteCommand;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Los records que llevan bytes (2026-11-25, de la auditoría con SonarQube, regla S6218): un record compara
 * un arreglo por referencia, así que dos con los mismos bytes en arreglos distintos no eran iguales, y su
 * {@code toString} pintaba la dirección del arreglo. Aquí se comparan por contenido, y {@code toString} dice
 * cuántos bytes son sin volcarlos —una firma o un PDF en un log no sirven a nadie—.
 */
class ByteArrayRecordsContractTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    static Stream<Arguments> records() {
        return Stream.of(
                caso("ChangeCompanyLogoCommand", ChangeCompanyLogoCommand::new),
                caso("SeedServiceCompanyCommand", b -> new SeedServiceCompanyCommand(null, b)),
                caso("DataLoadFile", b -> new DataLoadFile("cliente.json", b)),
                caso("LifeSheetPdf", b -> new LifeSheetPdf("hoja.pdf", b)),
                caso("ApproveOnSiteCommand", b -> new ApproveOnSiteCommand(ID, ID, b, ID)),
                caso("SignReceiptCommand",
                        b -> new SignReceiptCommand(ID, "Quien recibe", "123", "Cargo", b, ReceiptScope.AREA)));
    }

    private static Arguments caso(String nombre, Function<byte[], Object> crear) {
        return Arguments.of(nombre, crear);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("records")
    @DisplayName("dos con los mismos bytes en arreglos distintos son iguales y tienen el mismo hash")
    void mismosBytes(String nombre, Function<byte[], Object> crear) {
        Object uno = crear.apply(new byte[] {1, 2, 3});
        Object otro = crear.apply(new byte[] {1, 2, 3});

        assertThat(uno).isEqualTo(otro);
        assertThat(uno.hashCode()).isEqualTo(otro.hashCode());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("records")
    @DisplayName("con otros bytes, no son iguales")
    void otrosBytes(String nombre, Function<byte[], Object> crear) {
        assertThat(crear.apply(new byte[] {1, 2, 3})).isNotEqualTo(crear.apply(new byte[] {1, 2, 4}));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("records")
    @DisplayName("toString dice cuántos bytes son, y admite que no haya")
    void toStringSinVolcar(String nombre, Function<byte[], Object> crear) {
        assertThat(crear.apply(new byte[] {1, 2, 3}).toString()).startsWith(nombre + "[").contains("3 bytes");
        assertThat(crear.apply(null).toString()).contains("=null").doesNotContain("bytes");
        assertThat(crear.apply(null)).isEqualTo(crear.apply(null));
    }
}
