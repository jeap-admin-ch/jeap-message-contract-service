package ch.admin.bit.jeap.messagecontract.web.api;

import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepoException;
import ch.admin.bit.jeap.messagecontract.domain.renovate.RenovateRegistryException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import jakarta.validation.ConstraintViolationException;

@Slf4j
@RestControllerAdvice
public class RestControllerExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler({
            ConstraintViolationException.class,
            HttpMessageConversionException.class,
            IllegalArgumentException.class,
            MessageTypeRepoException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void logBadRequestReason(Exception ex) {
        log.warn(ex.getMessage(), ex);
    }

    @ExceptionHandler(RenovateRegistryException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public void logRenovateRegistryFailure(RenovateRegistryException ex) {
        log.error("Renovate registry request failed: {}", ex.getMessage(), ex);
    }
}
