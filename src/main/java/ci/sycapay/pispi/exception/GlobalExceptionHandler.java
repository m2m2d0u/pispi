package ci.sycapay.pispi.exception;

import ci.sycapay.pispi.dto.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> ApiResponse.FieldError.builder()
                        .field(fe.getField())
                        .message(fe.getDefaultMessage())
                        .rejectedValue(fe.getRejectedValue())
                        .build())
                .toList();

        return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, "VALIDATION_ERROR", "Validation failed", fieldErrors));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(404, ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(DuplicateRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicate(DuplicateRequestException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(409, ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(InvalidStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidState(InvalidStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(409, ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(AipCommunicationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAipError(AipCommunicationException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(503, ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(AipTimeoutException.class)
    public ResponseEntity<ApiResponse<Void>> handleAipTimeout(AipTimeoutException ex) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(ApiResponse.error(504, ex.getErrorCode(), ex.getMessage()));
    }
}
