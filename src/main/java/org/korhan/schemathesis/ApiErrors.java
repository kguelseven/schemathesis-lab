package org.korhan.schemathesis;

import org.apache.tomcat.util.http.InvalidParameterException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;

/** Fehler, die Spring nicht selbst als ProblemDetail rendert. */
@RestControllerAdvice
public class ApiErrors {

    /** Tomcat wirft das bei Query-Strings wie "?=1" (Parameter ohne Namen). */
    @ExceptionHandler(InvalidParameterException.class)
    ProblemDetail invalidParameter(InvalidParameterException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Ungültiger Query-String");
    }

    /** Content-Type multipart/form-data ohne boundary: Spring scheitert schon beim Parsen. */
    @ExceptionHandler(MultipartException.class)
    ProblemDetail invalidMultipart(MultipartException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Ungültiger Multipart-Request");
    }
}
