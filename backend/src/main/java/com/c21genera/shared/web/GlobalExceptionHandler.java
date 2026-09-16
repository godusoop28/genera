package com.c21genera.shared.web;

import com.c21genera.shared.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Traduce excepciones a {@link ProblemDetail} consistente (ver AGENTS §116).
 * Nunca se devuelve un stack trace ni información interna al cliente.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private static final URI TYPE_BASE = URI.create("https://c21genera.com/problems/");

  @ExceptionHandler(DomainException.class)
  public ProblemDetail handleDomain(DomainException ex, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
    problem.setTitle(ex.status().getReasonPhrase());
    problem.setType(TYPE_BASE.resolve(ex.code().toLowerCase().replace('_', '-')));
    problem.setProperty("code", ex.code());
    attachTraceId(problem, request);
    return problem;
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "La solicitud contiene datos inválidos.");
    problem.setTitle("Solicitud inválida");
    problem.setProperty("code", "VALIDATION_ERROR");
    problem.setProperty(
        "errors",
        ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getField)
            .distinct()
            .toList());
    attachTraceId(problem, request);
    return problem;
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ProblemDetail handleUnreadable(HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Cuerpo de la solicitud ilegible.");
    problem.setProperty("code", "MALFORMED_REQUEST");
    attachTraceId(problem, request);
    return problem;
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ProblemDetail handleTooLarge(HttpServletRequest request) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, "El archivo excede el tamaño máximo permitido.");
    problem.setProperty("code", "FILE_TOO_LARGE");
    attachTraceId(problem, request);
    return problem;
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ProblemDetail handleOptimisticLock(HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT, "El recurso fue modificado por otra persona. Vuelve a intentarlo.");
    problem.setProperty("code", "STALE_VERSION");
    attachTraceId(problem, request);
    return problem;
  }

  @ExceptionHandler({AccessDeniedException.class})
  public ProblemDetail handleAccessDenied(HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "No tienes permiso para esta acción.");
    problem.setProperty("code", "ACCESS_DENIED");
    attachTraceId(problem, request);
    return problem;
  }

  @ExceptionHandler(BadCredentialsException.class)
  public ProblemDetail handleBadCredentials(HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Credenciales inválidas.");
    problem.setProperty("code", "BAD_CREDENTIALS");
    attachTraceId(problem, request);
    return problem;
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
    log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado.");
    problem.setProperty("code", "INTERNAL_ERROR");
    attachTraceId(problem, request);
    return problem;
  }

  private void attachTraceId(ProblemDetail problem, HttpServletRequest request) {
    Object correlationId = request.getAttribute(CorrelationIdFilter.MDC_KEY);
    problem.setProperty("traceId", correlationId != null ? correlationId : org.slf4j.MDC.get(CorrelationIdFilter.MDC_KEY));
  }
}
