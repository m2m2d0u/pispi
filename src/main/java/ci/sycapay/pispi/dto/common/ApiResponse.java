package ci.sycapay.pispi.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private int status;
    private String message;
    private T data;
    private ErrorDetail error;
    private String timestamp;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorDetail {
        private String code;
        private String description;
        private List<FieldError> fieldErrors;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldError {
        private String field;
        private String message;
        private Object rejectedValue;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .status(200)
                .message("OK")
                .data(data)
                .timestamp(LocalDateTime.now().toString())
                .build();
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .status(200)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now().toString())
                .build();
    }

    public static <T> ApiResponse<T> created(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .status(201)
                .message("Created")
                .data(data)
                .timestamp(LocalDateTime.now().toString())
                .build();
    }

    public static <T> ApiResponse<T> accepted(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .status(202)
                .message("Accepted")
                .data(data)
                .timestamp(LocalDateTime.now().toString())
                .build();
    }

    public static ApiResponse<Void> accepted() {
        return ApiResponse.<Void>builder()
                .success(true)
                .status(202)
                .message("Accepted")
                .timestamp(LocalDateTime.now().toString())
                .build();
    }

    public static ApiResponse<Void> error(int status, String code, String description) {
        return ApiResponse.<Void>builder()
                .success(false)
                .status(status)
                .message("Error")
                .error(ErrorDetail.builder()
                        .code(code)
                        .description(description)
                        .build())
                .timestamp(LocalDateTime.now().toString())
                .build();
    }

    public static ApiResponse<Void> error(int status, String code, String description, List<FieldError> fieldErrors) {
        return ApiResponse.<Void>builder()
                .success(false)
                .status(status)
                .message("Error")
                .error(ErrorDetail.builder()
                        .code(code)
                        .description(description)
                        .fieldErrors(fieldErrors)
                        .build())
                .timestamp(LocalDateTime.now().toString())
                .build();
    }
}
