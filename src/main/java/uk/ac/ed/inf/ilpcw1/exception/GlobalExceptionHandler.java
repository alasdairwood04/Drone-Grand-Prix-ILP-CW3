package uk.ac.ed.inf.ilpcw1.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles our custom InvalidRequestException and returns a 400 Bad Request.
     * @param ex - the exception
     * @return - ResponseEntity with error details being invalid request
     */
    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<Map<String, String>> handleInvalidRequestException(InvalidRequestException ex) {
        logger.warn("Bad request: {}", ex.getMessage());
        Map<String, String> error = new HashMap<>();
        error.put("error", "Bad Request");
        error.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handles malformed JSON requests.
     * @param ex - the exception
     * @return - ResponseEntity with error details being malformed JSON
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        logger.warn("Malformed JSON request: {}", ex.getMessage());
        Map<String, String> error = new HashMap<>();
        error.put("error", "Malformed JSON");
        error.put("message", "The JSON request body is malformed or invalid.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handles validation errors for request bodies annotated with @Valid.
     * @param ex - the exception
     * @return - ResponseEntity with error details being validation errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        logger.warn("Validation error: {}", ex.getMessage());

        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.toList());

        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("error", "Validation Failed");
        errorBody.put("messages", errors);

        return new ResponseEntity<>(errorBody, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles our custom InvalidCoordinateException and returns a 400 Bad Request.
     * @param ex - the exception
     * @return - ResponseEntity with error details being invalid coordinate
     */
    @ExceptionHandler(InvalidCoordinateException.class)
    public ResponseEntity<Map<String, String>> handleInvalidCoordinateException(InvalidCoordinateException ex) {
        logger.warn("Invalid coordinate: {}", ex.getMessage());
        Map<String, String> error = new HashMap<>();
        error.put("error", "Bad Request");
        error.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handles our custom InvalidAngleException and returns a 400 Bad Request.
     * @param ex - the exception
     * @return - ResponseEntity with error details being invalid angle
     */
    @ExceptionHandler(InvalidAngleException.class)
    public ResponseEntity<Map<String, String>> handleInvalidAngleException(InvalidAngleException ex) {
        logger.warn("Invalid angle: {}", ex.getMessage());
        Map<String, String> error = new HashMap<>();
        error.put("error", "Bad Request");
        error.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }


    /**
     * Handles custom DroneNotFoundException and returns 404 not found
     * @param ex
     * @return
     */
    @ExceptionHandler(DroneNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleDroneNotFoundException(DroneNotFoundException ex) {
        logger.warn("Drone not found: {}", ex.getMessage());
        Map<String, String> error = new HashMap<>();
        error.put("error", "Not Found");
        error.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Fallback handler for any other unhandled exceptions (NullPointer, etc.)
     * ensures we always return JSON, never HTML.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        logger.error("Unhandled exception occurred", ex); // This prints the full error to your console
        Map<String, String> error = new HashMap<>();
        error.put("error", "Internal Server Error");
        error.put("message", ex.getMessage()); // Sends the error text to the frontend
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
