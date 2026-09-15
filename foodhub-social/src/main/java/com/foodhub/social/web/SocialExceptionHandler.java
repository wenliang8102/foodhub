package com.foodhub.social.web;

import com.foodhub.common.core.ApiResponse;
import com.foodhub.common.core.BusinessException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackages = "com.foodhub.social")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SocialExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException exception) {
        return ResponseEntity.status(statusFor(exception.getCode()))
                .body(ApiResponse.failure(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("请求参数校验失败");
        return badRequest(message);
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleMalformedRequest(Exception exception) {
        return badRequest("请求参数格式错误");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure("INTERNAL_ERROR", "服务器内部错误"));
    }

    private ResponseEntity<ApiResponse<Void>> badRequest(String message) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure("VALIDATION_ERROR", message));
    }

    private HttpStatus statusFor(String code) {
        if (code.endsWith("_NOT_FOUND")) {
            return HttpStatus.NOT_FOUND;
        }
        if (code.endsWith("_FORBIDDEN")) {
            return HttpStatus.FORBIDDEN;
        }
        return HttpStatus.BAD_REQUEST;
    }
}
