package backend.model;

public class management extends report {
    private final String department;

    public management(String title, String description, String location, boolean anonymous,
                      String reporterType, String reporter, String department) {
        super(title, description, location, "management", anonymous, reporterType, reporter);
        this.department = department != null ? department : "Facilities & Estate";
    }

    public management(String id, String title, String description, String location, boolean anonymous,
                      String reporterType, String reporter, String createdAt, String status, int supportCount, String department) {
        super(id, title, description, location, "management", anonymous, reporterType, reporter, createdAt, status, supportCount);
        this.department = department != null ? department : "Facilities & Estate";
    }

    public String getDepartment() {
        return department;
    }

    @Override
    public int calculateUrgencyIndex() {
        return 20 + (getSupportCount() * 2);
    }

    @Override
    public String getReportSummary() {
        return "[FACILITY ISSUE] " + getTitle() + " under " + department + " at " + getLocation();
    }
}
