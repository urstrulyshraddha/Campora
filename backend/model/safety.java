package backend.model;

public class safety extends report {
    private final String hazardCategory;

    public safety(String title, String description, String location, boolean anonymous,
                  String reporterType, String reporter, String hazardCategory) {
        super(title, description, location, "safety", anonymous, reporterType, reporter);
        this.hazardCategory = hazardCategory != null ? hazardCategory : "General Safety";
    }

    public safety(String id, String title, String description, String location, boolean anonymous,
                  String reporterType, String reporter, String createdAt, String status, int supportCount, String hazardCategory) {
        super(id, title, description, location, "safety", anonymous, reporterType, reporter, createdAt, status, supportCount);
        this.hazardCategory = hazardCategory != null ? hazardCategory : "General Safety";
    }

    public String getHazardCategory() {
        return hazardCategory;
    }

    @Override
    public int calculateUrgencyIndex() {
        return 50 + (getSupportCount() * 5);
    }

    @Override
    public String getReportSummary() {
        return "[SAFETY ALERT] " + getTitle() + " (" + hazardCategory + ") at " + getLocation();
    }
}
