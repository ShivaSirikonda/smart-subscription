package com.smartsubscription.authService.exception;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.http.HttpStatus;
@Data
@AllArgsConstructor
public class ErrorMessage
{
    String message;
    HttpStatus status;
}
