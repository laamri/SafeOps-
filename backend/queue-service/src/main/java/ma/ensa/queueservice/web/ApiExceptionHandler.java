package ma.ensa.queueservice.web;

import jakarta.servlet.http.HttpServletRequest;
import ma.ensa.queueservice.domain.TicketTransitionException;
import ma.ensa.queueservice.web.Views.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(TicketTransitionException.class)
    public ResponseEntity<ApiError> transition(TicketTransitionException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> responseStatus(ResponseStatusException exception, HttpServletRequest request) {
        HttpStatusCode status = exception.getStatusCode();
        String message = exception.getReason() == null ? status.toString() : exception.getReason();
        return error(status, message, request);
    }

    private ResponseEntity<ApiError> error(HttpStatusCode status, String message, HttpServletRequest request) {
        ApiError body = new ApiError(Instant.now(), status.value(), status.toString(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
