package com.malphasos.malphasos.dataload.infrastructure.output.writer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.malphasos.malphasos.dataload.application.exception.DataLoadRejectedException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El vaciado del final, por su cuenta. Contra la base no se ve: Hibernate vacía antes de cada consulta,
 * y casi toda violación salta en una de esas. Lo que queda para el final es lo escrito después de la
 * última consulta, y sin este vaciado llegaría al confirmar, como un error del servidor.
 */
class LoadWriterAdapterTest {

    @Test
    @DisplayName("una violacion que salta al vaciar al final llega como un rechazo que se lee")
    void elVaciadoDelFinal() {
        EntityManager entityManager = mock(EntityManager.class);
        doThrow(new PersistenceException("fallo", new RuntimeException("duplicate key value violates unique constraint")))
                .when(entityManager).flush();
        LoadWriterAdapter adaptador = new LoadWriterAdapter(mock(CatalogWriter.class), mock(PeopleWriter.class),
                mock(ClientWriter.class), mock(HistoryWriter.class), entityManager);

        assertThatThrownBy(() -> adaptador.write(List.of()))
                .isInstanceOfSatisfying(DataLoadRejectedException.class, e -> assertThat(e.problema().mensaje())
                        .contains("La base de datos rechazó la carga")
                        .contains("duplicate key"));
    }
}
