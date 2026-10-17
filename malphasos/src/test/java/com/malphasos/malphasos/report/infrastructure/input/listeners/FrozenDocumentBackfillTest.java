package com.malphasos.malphasos.report.infrastructure.input.listeners;

import static org.mockito.Mockito.verify;

import com.malphasos.malphasos.report.application.ports.input.ServiceReportServicePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Al arrancar se congelan tarde los informes cerrados antes de V37 (2026-10-17). */
@ExtendWith(MockitoExtension.class)
class FrozenDocumentBackfillTest {

    @Mock private ServiceReportServicePort servicio;

    @InjectMocks private FrozenDocumentBackfill arranque;

    @Test
    @DisplayName("arrancar pide congelar los que falten")
    void congelaAlArrancar() {
        arranque.run(null);

        verify(servicio).freezeClosedWithoutDocument();
    }
}
