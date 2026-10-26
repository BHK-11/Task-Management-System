package com.taskos.exception;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.http.HttpStatus;
import com.taskos.task.service.DuplicateTaskTagException;
import com.taskos.task.service.TaskConflictException;
import com.taskos.task.service.TaskNotFoundException;
import com.taskos.task.service.TaskRuleException;
import com.taskos.time.service.TimeConflictException;
import com.taskos.time.service.TimeRuleException;
import com.taskos.collaboration.service.WorkspaceForbiddenException;
import com.taskos.ai.service.AiArtifactNotFoundException;

@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class,
            ConstraintViolationException.class, HandlerMethodValidationException.class})
    public ModelAndView invalidRequest(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "Please check the information and try again.", null);
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ModelAndView pageNotFound(Exception exception) {
        return error(HttpStatus.NOT_FOUND, "This page does not exist or is not available yet.", null);
    }

    @ExceptionHandler(TaskNotFoundException.class)
    public ModelAndView taskNotFound(TaskNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "This task is not available.", null);
    }

    @ExceptionHandler(AiArtifactNotFoundException.class)
    public ModelAndView artifactNotFound(AiArtifactNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "This deliverable is not available.", null);
    }

    @ExceptionHandler(WorkspaceForbiddenException.class)
    public ModelAndView workspaceForbidden(WorkspaceForbiddenException exception) {
        return error(HttpStatus.FORBIDDEN, exception.getMessage(), null);
    }

    @ExceptionHandler(TaskConflictException.class)
    public ModelAndView taskConflict(TaskConflictException exception) {
        return error(HttpStatus.CONFLICT, exception.getMessage(), null);
    }

    @ExceptionHandler({TaskRuleException.class, DuplicateTaskTagException.class})
    public ModelAndView taskRule(RuntimeException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), null);
    }

    @ExceptionHandler(TimeConflictException.class)
    public ModelAndView timeConflict(TimeConflictException exception) {
        return error(HttpStatus.CONFLICT, exception.getMessage(), null);
    }

    @ExceptionHandler(TimeRuleException.class)
    public ModelAndView timeRule(TimeRuleException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), null);
    }

    @ExceptionHandler(DataAccessException.class)
    public ModelAndView databaseUnavailable(DataAccessException exception, HttpServletRequest request) {
        String reference = UUID.randomUUID().toString();
        log.error("Database request failed [{}] at {} ({})", reference, request.getRequestURI(),
                exception.getClass().getSimpleName(), exception);
        return error(HttpStatus.SERVICE_UNAVAILABLE,
                "The service could not reach its data store. Please try again shortly.", reference);
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView unexpectedFailure(Exception exception, HttpServletRequest request) {
        String reference = UUID.randomUUID().toString();
        log.error("Unexpected request failure [{}] at {} ({})", reference, request.getRequestURI(),
                exception.getClass().getSimpleName(), exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong while handling this request. Please try again.", reference);
    }

    private ModelAndView error(HttpStatus status, String message, String reference) {
        ModelAndView view = new ModelAndView("pages/error");
        view.setStatus(status);
        view.addObject("status", status.value());
        view.addObject("title", status == HttpStatus.NOT_FOUND ? "Page not found" : "We hit a snag");
        view.addObject("message", message);
        view.addObject("reference", reference);
        return view;
    }
}
