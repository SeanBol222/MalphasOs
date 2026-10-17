package com.malphasos.malphasos.person.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.person.application.ports.input.PersonCommunicationPort;
import com.malphasos.malphasos.person.application.ports.output.PersonSignaturePersistencePort;
import com.malphasos.malphasos.person.application.services.signature.PersonSignatureService;
import com.malphasos.malphasos.person.domain.exception.PersonNotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** La firma de una persona (V38, 2026-10-17). */
@ExtendWith(MockitoExtension.class)
class PersonSignatureServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 7};

    @Mock private PersonSignaturePersistencePort persistencia;
    @Mock private PersonCommunicationPort personas;

    @InjectMocks private PersonSignatureService servicio;

    @Test
    @DisplayName("guardar la firma de alguien que no existe dice que no existe, y no guarda nada")
    void personaInexistente() {
        UUID id = UUID.randomUUID();
        when(personas.findById(id)).thenThrow(new PersonNotFoundException(id));

        assertThatThrownBy(() -> servicio.save(id, PNG)).isInstanceOf(PersonNotFoundException.class);
        verify(persistencia, never()).save(any(), any());
    }

    @Test
    @DisplayName("guardar valida la imagen y la sella con la hora")
    void guarda() {
        UUID id = UUID.randomUUID();
        when(persistencia.save(eq(id), any())).thenAnswer(i -> i.getArgument(1));

        assertThat(servicio.save(id, PNG).actualizada()).isNotNull();
        verify(personas).findById(id);
    }

    @Test
    @DisplayName("una imagen que no es PNG no llega al almacén")
    void noPng() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> servicio.save(id, new byte[] {1, 2, 3})).isInstanceOf(IllegalArgumentException.class);
        verify(persistencia, never()).save(any(), any());
    }
}
