package com.malphasos.malphasos.bootstrap.exception;

import static com.malphasos.malphasos.bootstrap.exception.utils.GlobalErrorCatalog.DATABASE_ERROR;
import static com.malphasos.malphasos.bootstrap.exception.utils.GlobalErrorCatalog.INVALID_DATA;

import com.malphasos.malphasos.bootstrap.exception.response.GlobalErrorResponse;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Manejo de las excepciones transversales de la aplicación.
 *
 * <p>Solo atiende errores que no pertenecen a ningún módulo de negocio: fallos de acceso a datos
 * y validación de la petición. Cada módulo declara su propio {@code @RestControllerAdvice}
 * acotado a sus controladores para sus errores de dominio.
 */
@Slf4j
@RestControllerAdvice
public class GlobalControllerAdvice {

    /**
     * Traduce cualquier fallo de acceso a la base de datos en una respuesta 500.
     *
     * <p>El mensaje original se registra en el log pero no se devuelve al cliente: suele contener
     * nombres de tablas, columnas o fragmentos de SQL que no deben salir del servidor.
     */
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(DataAccessException.class)
    public GlobalErrorResponse handleDataAccessException(DataAccessException ex) {

        log.error("Fallo de acceso a datos", ex);

        return GlobalErrorResponse.builder()
                .code(DATABASE_ERROR.getCode())
                .message(DATABASE_ERROR.getMessage())
                .details(List.of())
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Traduce los fallos de validación de la petición en una respuesta 400, con un detalle por
     * cada campo que no cumplió las restricciones declaradas.
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public GlobalErrorResponse handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {

        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .toList();

        return GlobalErrorResponse.builder()
                .code(INVALID_DATA.getCode())
                .message(INVALID_DATA.getMessage())
                .details(details)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
