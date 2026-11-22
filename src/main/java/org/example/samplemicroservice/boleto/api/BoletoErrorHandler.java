package org.example.samplemicroservice.boleto.api;

import java.time.format.DateTimeParseException;
import org.example.samplemicroservice.boleto.application.BoletoFailure.FailureException;
import org.example.samplemicroservice.boleto.application.BoletoTelemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = BoletoController.class)
class BoletoErrorHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(BoletoErrorHandler.class);
    private final BoletoTelemetry telemetry;

    BoletoErrorHandler(BoletoTelemetry telemetry) {
        this.telemetry = telemetry;
    }

    @ExceptionHandler(FailureException.class)
    ResponseEntity<ProblemDetail> failure(FailureException exception) {
        String code = exception.failure().name();
        if (code.equals("IDEMPOTENCY_CONFLICT") || code.equals("OPERATION_IN_PROGRESS")) {
            telemetry.count("idempotency.conflicts");
        }
        return switch (exception.failure()) {
            case INVALID_REQUEST -> invalidRequest();
            case BOLETO_NOT_FOUND -> problem(HttpStatus.NOT_FOUND, code, "Boleto was not found.", null);
            case REVISION_NOT_FOUND -> problem(HttpStatus.NOT_FOUND, code, "Boleto revision was not found.", null);
            case IDEMPOTENCY_CONFLICT -> problem(HttpStatus.CONFLICT, code,
                    "The key is associated with a different request.", null);
            case OPERATION_IN_PROGRESS -> problem(HttpStatus.CONFLICT, code,
                    "Issuance is in progress. Retry after the indicated delay.", "1");
            case INVALID_STATE_TRANSITION -> problem(HttpStatus.CONFLICT, code,
                    "The requested transition is not valid for the current state.", null);
            case PDF_RENDER_FAILED -> {
                LOGGER.warn("PDF_RENDER_FAILED");
                yield problem(HttpStatus.INTERNAL_SERVER_ERROR, code, "Could not render the document.", null);
            }
        };
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            DateTimeParseException.class})
    ResponseEntity<ProblemDetail> invalidRequest() {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request is invalid.", null);
    }

    @ExceptionHandler({CannotCreateTransactionException.class, DataAccessResourceFailureException.class,
            PessimisticLockingFailureException.class, QueryTimeoutException.class})
    ResponseEntity<ProblemDetail> databaseUnavailable() {
        LOGGER.warn("DATABASE_UNAVAILABLE");
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE",
                "Database is temporarily unavailable.", null);
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail, String retryAfter) {
        telemetry.count("api.errors");
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setProperty("code", code);
        var response = ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (retryAfter != null) {
            response.header(HttpHeaders.RETRY_AFTER, retryAfter);
        }
        return response.body(body);
    }
}
