package com.oficina.os.infrastructure.web.exception;

import com.oficina.os.application.exception.ConflictException;
import com.oficina.os.application.exception.InvalidInputException;
import com.oficina.os.application.exception.ResourceNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;

@RestControllerAdvice
class GlobalExceptionHandler {
	@ExceptionHandler(com.oficina.os.application.authorization.AuthorizationDenied.class)
	ProblemDetail forbidden() {
		return problem(HttpStatus.FORBIDDEN, "Forbidden", "Access denied");
	}

	@ExceptionHandler({ com.oficina.os.application.exception.ClientAccessException.class,
			org.springframework.dao.DataAccessResourceFailureException.class,
			org.springframework.dao.QueryTimeoutException.class,
			org.springframework.transaction.CannotCreateTransactionException.class })
	ProblemDetail authorizationUnavailable() {
		return problem(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable", "Authorization dependency unavailable");
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler({ MethodArgumentNotValidException.class, ConstraintViolationException.class,
			MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
			InvalidInputException.class })
	ProblemDetail badRequest(Exception ex) {
		return problem(HttpStatus.BAD_REQUEST, "Invalid request", publicMessage(ex));
	}

	@ExceptionHandler({ ResourceNotFoundException.class, NoResourceFoundException.class })
	ProblemDetail notFound(Exception ex) {
		String detail = ex instanceof ResourceNotFoundException ? ex.getMessage() : "resource not found";
		return problem(HttpStatus.NOT_FOUND, "Resource not found", detail);
	}

	@ExceptionHandler({ ConflictException.class, ObjectOptimisticLockingFailureException.class,
			DataIntegrityViolationException.class })
	ProblemDetail conflict(Exception ex) {
		LOGGER.warn("Conflict while processing request: {}", ex.getMessage());
		String detail = ex instanceof ConflictException ? ex.getMessage() : "Request conflicts with current resource state";
		return problem(HttpStatus.CONFLICT, "Conflict", detail);
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail internalServerError(Exception ex) {
		LOGGER.error("Unexpected error while processing request", ex);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", "Unexpected internal error");
	}

	private ProblemDetail problem(HttpStatus status, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		problem.setType(URI.create("about:blank"));
		return problem;
	}

	private String publicMessage(Exception ex) {
		if (ex instanceof MethodArgumentNotValidException validationException) {
			return validationException.getBindingResult().getFieldErrors().stream()
					.findFirst()
					.map(error -> error.getField() + " " + error.getDefaultMessage())
					.orElse("Request validation failed");
		}
		return ex.getMessage();
	}
}
