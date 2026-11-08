package com.malphasos.malphasos.dataload.infrastructure.output.writer;

import com.malphasos.malphasos.dataload.application.exception.DataLoadRejectedException;
import com.malphasos.malphasos.dataload.application.model.DataLoadProblem;
import com.malphasos.malphasos.dataload.application.model.ReadFile;
import com.malphasos.malphasos.dataload.application.ports.output.LoadWriterPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Escribe una carga por contenidos, en el orden en que se nombran: el catálogo primero, porque un cliente
 * nombra sus modelos y un historial sus protocolos. Los archivos pueden llegar en cualquier orden.
 */
@Component
@RequiredArgsConstructor
public class LoadWriterAdapter implements LoadWriterPort {

    private final CatalogWriter catalogo;
    private final EntityManager entityManager;

    /**
     * Al final vacía lo escrito contra la base: una restricción que salte lo hace aquí, o antes en un
     * vaciado intermedio, con un rechazo que se puede leer, y no al confirmar la transacción, donde
     * llegaría como un error del servidor.
     */
    @Override
    @Transactional
    public void write(List<ReadFile> archivos) {
        LoadRefs refs = new LoadRefs();
        try {
            de(archivos, "catalogo").forEach(archivo -> catalogo.write(archivo, refs));
            entityManager.flush();
        } catch (PersistenceException | DataAccessException e) {
            throw new DataLoadRejectedException(DataLoadProblem.of(null, "La base de datos rechazó la carga, y no se"
                    + " escribió nada: " + causa(e)), e);
        }
    }

    private static String causa(Throwable e) {
        Throwable raiz = e;
        while (raiz.getCause() != null) {
            raiz = raiz.getCause();
        }
        return raiz.getMessage();
    }

    private static List<ReadFile> de(List<ReadFile> archivos, String contenido) {
        return archivos.stream().filter(a -> contenido.equals(a.documento().contenido())).toList();
    }
}
