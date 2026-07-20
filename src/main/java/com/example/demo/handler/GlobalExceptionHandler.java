package com.example.demo.handler;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.demo.dto.ErrorResponse;

/**
 * 全局异常处理：把 Service 层抛出的异常统一转换成结构化的 HTTP 错误响应，
 * 避免异常直接冒泡变成 500，也让前端拿到稳定的错误结构。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务参数类异常（短码不存在、已过期、未激活、重复等）。
     * 当前 Service 用 IllegalArgumentException 表达这些情况，统一归为 400。
     * 注：若后续引入 BizException + ErrorCode，可在这里按错误码精确映射
     * 404 / 410 等状态，语义会更准确。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), e.getMessage()));
    }

    /**
     * @Valid 请求体校验失败，返回第一个字段错误信息。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldError() != null
                ? e.getBindingResult().getFieldError().getDefaultMessage()
                : "请求参数校验失败";
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), message));
    }

    /**
     * 兜底：其它未预期的异常统一返回 500，不把内部细节暴露给客户端。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleOther(Exception e) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "服务器内部错误"));
    }
}
