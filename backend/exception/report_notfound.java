package backend.exception;

public class report_notfound extends RuntimeException {
    public report_notfound(String reportId) {
        super("Report not found with ID: " + reportId);
    }
}
