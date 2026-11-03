package backend.exception;

public class invalid_report extends Exception {
    public invalid_report(String message) {
        super("[Validation Error] " + message);
    }
}
