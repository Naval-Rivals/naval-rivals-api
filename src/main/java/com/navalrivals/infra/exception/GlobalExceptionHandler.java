package com.navalrivals.infra.exception;

import com.navalrivals.infra.exception.dto.ErrorResponse;
import com.navalrivals.infra.exception.dto.ErrorValidationResponse;
import com.navalrivals.infra.exception.exceptions.*;
import com.navalrivals.infra.exception.exceptions.SecurityException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFoundException(NotFoundException e){
        log.warn("[EXCEPTION] NotFoundException: {}", e.getMessage());
        var response = new ErrorResponse(e.getMessage(), HttpStatus.NOT_FOUND.value(), null);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(BadCredencialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredencialsException(BadCredencialsException e){
        log.warn("[EXCEPTION] BadCredencialsException: {}", e.getMessage());
        var response = new ErrorResponse(e.getMessage(), HttpStatus.UNAUTHORIZED.value(), null);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponse> handleSecurityException(SecurityException e){
        log.error("[EXCEPTION] SecurityException: {}", e.getMessage(), e);
        var response = new ErrorResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value(), null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(TokenJwtException.class)
    public ResponseEntity<ErrorResponse> handleTokenJwtException(TokenJwtException e){
        log.warn("[EXCEPTION] TokenJwtException: {}", e.getMessage());
        var response = new ErrorResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value(), null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserAlreadyExistsException(UserAlreadyExistsException e){
        log.info("[EXCEPTION] UserAlreadyExistsException: {}", e.getMessage());
        var response = new ErrorResponse(e.getMessage(), HttpStatus.CONFLICT.value(), null);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e){
        var message = e.getMessage();
        if (e.getRequiredType() == UUID.class){
            message = "UUID inválido";
        }
        log.warn("[EXCEPTION] MethodArgumentTypeMismatchException: {}", message);
        var response = new ErrorResponse(message, HttpStatus.BAD_REQUEST.value(), null);
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationBadRequestException(MethodArgumentNotValidException e){
        var errors = e.getFieldErrors();
        log.warn("[EXCEPTION] Validation failed — fields: {}", errors.stream().map(f -> f.getField() + "=" + f.getDefaultMessage()).toList());
        var response = new ErrorResponse("Erro de validação dos campos",
                HttpStatus.BAD_REQUEST.value(),
                errors.stream().map(ErrorValidationResponse::new).collect(Collectors.toList()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(PasswordNotConfirmationException.class)
    public ResponseEntity<ErrorResponse> handlePasswordNotConfirmationException(PasswordNotConfirmationException e){
        log.warn("[EXCEPTION] PasswordNotConfirmationException: {}", e.getMessage());
        var response = new ErrorResponse(e.getMessage(), HttpStatus.BAD_REQUEST.value(), null);
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(RoomFullException.class)
    public ResponseEntity<ErrorResponse> handleRoomFullException(RoomFullException e){
        log.info("[EXCEPTION] RoomFullException: {}", e.getMessage());
        var response = new ErrorResponse(e.getMessage(), HttpStatus.CONFLICT.value(), null);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(PlayerWithoutPermissionException.class)
    public ResponseEntity<ErrorResponse> handlePlayerWithoutPermissionException(PlayerWithoutPermissionException e){
        log.warn("[EXCEPTION] PlayerWithoutPermissionException: {}", e.getMessage());
        var response = new ErrorResponse(e.getMessage(), HttpStatus.BAD_REQUEST.value(), null);
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(InvalidCellException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCellException(InvalidCellException e) {
        log.warn("[EXCEPTION] InvalidCellException: {}", e.getMessage());
        var response = new ErrorResponse(e.getMessage(), HttpStatus.BAD_REQUEST.value(), null);
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(MatchStatusException.class)
    public ResponseEntity<ErrorResponse> handleMatchStatusException(MatchStatusException e) {
        log.warn("[EXCEPTION] MatchStatusException: {}", e.getMessage());
        var response = new ErrorResponse(e.getMessage(), HttpStatus.CONFLICT.value(), null);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception e) {
        log.error("[EXCEPTION] Unexpected error: {}", e.getMessage(), e);
        var response = new ErrorResponse("Erro interno do servidor", HttpStatus.INTERNAL_SERVER_ERROR.value(), null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<Void> handleAsyncTimeout(AsyncRequestTimeoutException e) {
        log.debug("[SSE] Conexão SSE expirou por timeout (comportamento esperado)");
        return ResponseEntity.noContent().build();
    }
}
