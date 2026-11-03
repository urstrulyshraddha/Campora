package backend.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class report implements reportable {
    private final String id;
    private final String title;
    private final String description;
    private final String location;
    private final String domain; // "safety", "management", "suggestion"
    private final boolean anonymous;
    private final String reporterType; // "STUDENT", "FACULTY", "ANONYMOUS"
    private final String reporterIdentity;
    private final String createdAt;

    // Concurrency primitives
    private final AtomicInteger supportCount;
    private final CopyOnWriteArrayList<String> supplementalNotes;

    // Cross-thread volatile visibility
    private volatile String status; // "PENDING", "IN_PROGRESS", "RESOLVED", "REJECTED"

    // Constructor for new reports (generates random UUID)
    public report(String title, String description, String location, String domain,
                  boolean anonymous, String reporterType, String reporterIdentity) {
        this(UUID.randomUUID().toString().substring(0, 8), title, description, location, domain,
             anonymous, reporterType, reporterIdentity,
             LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")), "PENDING", 1);
    }

    // Overloaded constructor for existing reports reconstructed from MySQL
    public report(String id, String title, String description, String location, String domain,
                  boolean anonymous, String reporterType, String reporterIdentity,
                  String createdAt, String status, int supportCount) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.location = location;
        this.domain = domain;
        this.anonymous = anonymous;
        this.reporterType = anonymous ? "ANONYMOUS" : (reporterType != null ? reporterType : "STUDENT");
        this.reporterIdentity = anonymous ? "ANONYMOUS" : reporterIdentity;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        this.status = status != null ? status : "PENDING";
        this.supportCount = new AtomicInteger(supportCount > 0 ? supportCount : 1);
        this.supplementalNotes = new CopyOnWriteArrayList<>();
    }

    public int endorse() {
        return this.supportCount.incrementAndGet();
    }

    public void addSupplementalNote(String note) {
        if (note != null && !note.trim().isEmpty()) {
            this.supplementalNotes.add(note.trim());
        }
    }

    public synchronized void updateStatus(String newStatus) {
        this.status = newStatus;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getLocation() { return location; }
    public String getDomain() { return domain; }
    public boolean isAnonymous() { return anonymous; }
    public String getReporterType() { return reporterType; }
    public String getReporterIdentity() { return reporterIdentity; }
    public String getCreatedAt() { return createdAt; }
    public int getSupportCount() { return supportCount.get(); }
    public List<String> getSupplementalNotes() { return Collections.unmodifiableList(supplementalNotes); }
    public String getStatus() { return status; }

    public String toJson() {
        StringBuilder notesJson = new StringBuilder("[");
        for (int i = 0; i < supplementalNotes.size(); i++) {
            notesJson.append("\"").append(escape(supplementalNotes.get(i))).append("\"");
            if (i < supplementalNotes.size() - 1) notesJson.append(",");
        }
        notesJson.append("]");

        return "{"
                + "\"id\":\"" + escape(id) + "\","
                + "\"title\":\"" + escape(title) + "\","
                + "\"description\":\"" + escape(description) + "\","
                + "\"location\":\"" + escape(location) + "\","
                + "\"domain\":\"" + escape(domain) + "\","
                + "\"status\":\"" + escape(status) + "\","
                + "\"supportCount\":" + supportCount.get() + ","
                + "\"anonymous\":" + anonymous + ","
                + "\"reporterType\":\"" + escape(reporterType) + "\","
                + "\"reporterIdentity\":\"" + escape(reporterIdentity) + "\","
                + "\"urgency\":" + calculateUrgencyIndex() + ","
                + "\"createdAt\":\"" + escape(createdAt) + "\","
                + "\"notes\":" + notesJson
                + "}";
    }

    protected String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", "");
    }
}
