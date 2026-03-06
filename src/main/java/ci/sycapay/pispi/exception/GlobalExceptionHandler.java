package ci.sycapay.pispi.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<Map<String, Object>> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> {
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("field", fe.getField());
                    detail.put("message", fe.getDefaultMessage());
                    detail.put("rejectedValue", fe.getRejectedValue());
                    return detail;
                }).toList();

        return ResponseEntity.badRequest().body(errorBody(400, "Bad Request", "VALIDATION_ERROR",
                "Validation failed", request.getRequestURI(), details));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(errorBody(404, "Not Found", ex.getErrorCode(), ex.getMessage(), request.getRequestURI(), null));
    }

    @ExceptionHandler(DuplicateRequestException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicate(DuplicateRequestException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(errorBody(409, "Conflict", ex.getErrorCode(), ex.getMessage(), request.getRequestURI(), null));
    }

    @ExceptionHandler(InvalidStateException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidState(InvalidStateException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(errorBody(409, "Conflict", ex.getErrorCode(), ex.getMessage(), request.getRequestURI(), null));
    }

    @ExceptionHandler(AipCommunicationException.class)
    public ResponseEntity<Map<String, Object>> handleAipError(AipCommunicationException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(errorBody(503, "Service Unavailable", ex.getErrorCode(), ex.getMessage(), request.getRequestURI(), null));
    }

    @ExceptionHandler(AipTimeoutException.class)
    public ResponseEntity<Map<String, Object>> handleAipTimeout(AipTimeoutException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(errorBody(504, "Gateway Timeout", ex.getErrorCode(), ex.getMessage(), request.getRequestURI(), null));
    }

    private Map<String, Object> errorBody(int status, String error, String code, String message, String path, List<Map<String, Object>> details) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status);
        body.put("error", error);
        body.put("code", code);
        body.put("message", message);
        body.put("path", path);
        if (details != null) body.put("details", details);
        return body;
    }
}
