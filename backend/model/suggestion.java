package backend.model;

public class suggestion extends report {
    private final String initiativeType;

    public suggestion(String title, String description, String location, boolean anonymous,
                      String reporterType, String reporter, String initiativeType) {
        super(title, description, location, "suggestion", anonymous, reporterType, reporter);
        this.initiativeType = initiativeType != null ? initiativeType : "Campus Improvement";
    }

    public suggestion(String id, String title, String description, String location, boolean anonymous,
                      String reporterType, String reporter, String createdAt, String status, int supportCount, String initiativeType) {
        super(id, title, description, location, "suggestion", anonymous, reporterType, reporter, createdAt, status, supportCount);
        this.initiativeType = initiativeType != null ? initiativeType : "Campus Improvement";
    }

    public String getInitiativeType() {
        return initiativeType;
    }

    @Override
    public int calculateUrgencyIndex() {
        return getSupportCount();
    }

    @Override
    public String getReportSummary() {
        return "[STUDENT/FACULTY INITIATIVE] " + getTitle() + " (" + getSupportCount() + " votes)";
    }
}
