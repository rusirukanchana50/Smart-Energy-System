package lk.ac.ruh.eng.energy.exception;

/**
 * Common custom exceptions for Ruhuna Smart Energy System REST API.
 */
public class ApiException extends RuntimeException {
    private final int statusCode;

    public ApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public static class BadRequestException extends ApiException {
        public BadRequestException(String message) {
            super(400, message);
        }
    }

    public static class UnauthorizedException extends ApiException {
        public UnauthorizedException(String message) {
            super(401, message);
        }
    }

    public static class ForbiddenException extends ApiException {
        public ForbiddenException(String message) {
            super(403, message);
        }
    }

    public static class NotFoundException extends ApiException {
        public NotFoundException(String message) {
            super(404, message);
        }
    }

    public static class ConflictException extends ApiException {
        public ConflictException(String message) {
            super(409, message);
        }
    }
}
