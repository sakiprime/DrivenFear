package com.sakiprime.DrivenFear.exception;
import com.sakiprime.DrivenFear.common.util.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Optional;


@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NullPointerException.class)
    public Result<String> handleNullPointerException(NullPointerException e) {
        log.warn("全局异常捕获-空指针异常:", e);
        return Result.fail(400, "参数不合法，请校验并重试。");
    }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {

        String paramName = e.getName();
        String message = "参数[" + paramName + "]格式错误，请传入正确格式";

        log.info("传入参数类型异常, {}", message);
        return Result.fail(400, message);
    }
    //DTO入参校验未通过。
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<Void> handleValidException(BindException e) {
        //美丽的链式调用。
        String message = Optional.of(e)
                .map(BindingResult::getFieldError)
                .map(FieldError::getDefaultMessage)
                .orElse("参数不合法，请重试。");
        log.info("DTO参数校验未通过,错误消息:{}",message);
        return Result.fail(400,"参数不合法，请重试。");
    }
    //单入参数校验未通过。
    //TODO 这里假设了错误消息都是xxx:yyy的格式。也许需要更多安全性。
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        String message = Optional.ofNullable(e)
                .map(ConstraintViolationException::getMessage)
                .map(msg -> {
                    String[] parts = msg.split(":");
                    return parts.length > 1 ? parts[1].trim() : msg.trim();
                })
                .orElse("参数不合法，请重试。");
        log.info("单入参校验未通过,错误消息:{}",message);
        return Result.fail(400, message);
    }
    //Exception.class必须放在方法的最末。因为异常类型是从上而下匹配，若不处于最末尾可能截断子类异常的处理。
    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        log.error("全局异常捕获:", e);
        String msg = e.getMessage();
        String tip = msg != null ? msg : "服务器异常，请稍后重试";
        return Result.fail(500,tip);
    }

}
