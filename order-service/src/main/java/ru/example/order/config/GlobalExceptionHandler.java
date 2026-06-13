package ru.example.order.config;

import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
  }

  @ExceptionHandler(FeignException.class)
  public ResponseEntity<String> handleFeignException(FeignException ex) {
    String body = ex.contentUTF8();
    if (body == null || body.isEmpty()) {
      body = ex.getMessage();
    }
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }
}
