package backend.model;

public interface reportable {
    String getReportSummary();
    int calculateUrgencyIndex();
    boolean isAnonymous();
}
