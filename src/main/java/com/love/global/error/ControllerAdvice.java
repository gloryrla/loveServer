package com.love.global.error;


import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ControllerAdvice {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<?> handleBiz(BizException e) {
        return ResponseEntity
                .status(e.getErrorCode().status)
                .body(Map.of(
                        "code", e.getErrorCode().name(),
                        "message", e.getMessage()
                ));
    }
}