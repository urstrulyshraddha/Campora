package backend.storage;

import backend.model.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class report_repository {
    private static final report_repository INSTANCE = new report_repository();

    private report_repository() {}

    public static report_repository getInstance() {
        return INSTANCE;
    }

    // INSERT INTO MySQL reports table
    public void save(report r) {
        String sql = "INSERT INTO reports (id, title, description, location, domain, status, support_count, anonymous, reporter_type, reporter_identity, created_at, category_or_dept) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                   + "ON DUPLICATE KEY UPDATE support_count = VALUES(support_count), status = VALUES(status)";

        String extra = "";
        if (r instanceof safety) {
            extra = ((safety) r).getHazardCategory();
        } else if (r instanceof management) {
            extra = ((management) r).getDepartment();
        } else if (r instanceof suggestion) {
            extra = ((suggestion) r).getInitiativeType();
        }

        try (Connection conn = database_manager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, r.getId());
            pstmt.setString(2, r.getTitle());
            pstmt.setString(3, r.getDescription());
            pstmt.setString(4, r.getLocation());
            pstmt.setString(5, r.getDomain());
            pstmt.setString(6, r.getStatus());
            pstmt.setInt(7, r.getSupportCount());
            pstmt.setInt(8, r.isAnonymous() ? 1 : 0);
            pstmt.setString(9, r.getReporterType() != null ? r.getReporterType() : "STUDENT");
            pstmt.setString(10, r.getReporterIdentity() != null ? r.getReporterIdentity() : "ANONYMOUS");
            pstmt.setString(11, r.getCreatedAt());
            pstmt.setString(12, extra != null ? extra : "");

            int affected = pstmt.executeUpdate();
            System.out.println("[DB Save Success] Stored report ID: " + r.getId() + " (Rows affected: " + affected + ")");
        } catch (SQLException e) {
            System.err.println("[DB Save CRITICAL FAILURE] Failed to insert report ID " + r.getId() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public report findById(String id) {
        if (id == null) return null;
        String cleanId = id.trim();
        String sql = "SELECT * FROM reports WHERE LOWER(TRIM(id)) = LOWER(?)";
        try (Connection conn = database_manager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, cleanId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    report r = mapRowToReport(rs);
                    loadNotes(conn, r);
                    return r;
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB Find Error] " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    // Atomic SQL upvote
    public synchronized int incrementEndorsement(String id) {
        String cleanId = id.trim();
        String updateSql = "UPDATE reports SET support_count = support_count + 1 WHERE LOWER(TRIM(id)) = LOWER(?)";
        String querySql = "SELECT support_count FROM reports WHERE LOWER(TRIM(id)) = LOWER(?)";

        try (Connection conn = database_manager.getConnection();
             PreparedStatement updatePstmt = conn.prepareStatement(updateSql);
             PreparedStatement queryPstmt = conn.prepareStatement(querySql)) {

            updatePstmt.setString(1, cleanId);
            updatePstmt.executeUpdate();

            queryPstmt.setString(1, cleanId);
            try (ResultSet rs = queryPstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("support_count");
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB Endorse Error] " + e.getMessage());
        }
        return -1;
    }

    // Resolver action: Update status and append resolution note
    public synchronized boolean updateStatus(String id, String newStatus, String resolutionNote) {
        if (id == null) return false;
        String cleanId = id.trim();

        String sql = "UPDATE reports SET status = ? WHERE LOWER(TRIM(id)) = LOWER(?)";
        try (Connection conn = database_manager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, newStatus);
            pstmt.setString(2, cleanId);
            int rows = pstmt.executeUpdate();

            if (rows > 0) {
                System.out.println("[DB Status Update] Updated MySQL row for ID: " + cleanId + " to " + newStatus);
                if (resolutionNote != null && !resolutionNote.trim().isEmpty()) {
                    addNote(cleanId, "[STATUS UPDATE: " + newStatus + "] " + resolutionNote.trim());
                }
                return true;
            } else {
                System.err.println("[DB Status Update Failure] MySQL returned 0 rows updated for ID: '" + cleanId + "'. Running verification...");
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT id FROM reports WHERE LOWER(TRIM(id)) = LOWER('" + cleanId + "')")) {
                    if (!rs.next()) {
                        System.err.println("[DB Status Update Failure] ID '" + cleanId + "' is strictly NOT in the reports table!");
                    } else {
                        System.err.println("[DB Status Update Failure] ID exists, status was already '" + newStatus + "'. Marking successful.");
                        if (resolutionNote != null && !resolutionNote.trim().isEmpty()) {
                            addNote(cleanId, "[STATUS UPDATE: " + newStatus + "] " + resolutionNote.trim());
                        }
                        return true;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB Status Update SQL Error] " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    public void addNote(String reportId, String note) {
        String sql = "INSERT INTO report_notes (report_id, note) VALUES (?, ?)";
        try (Connection conn = database_manager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, reportId.trim());
            pstmt.setString(2, note);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB Note Error] " + e.getMessage());
        }
    }

    public List<report> findAll() {
        return queryList("SELECT * FROM reports ORDER BY support_count DESC");
    }

    public List<report> findByDomain(String domain) {
        String sql = "SELECT * FROM reports WHERE LOWER(domain) = ? ORDER BY support_count DESC";
        List<report> list = new ArrayList<>();
        try (Connection conn = database_manager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, domain.toLowerCase());
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    report r = mapRowToReport(rs);
                    loadNotes(conn, r);
                    list.add(r);
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB Domain Query Error] " + e.getMessage());
        }
        return list;
    }

    public List<report> findByStatus(String status) {
        String sql = "SELECT * FROM reports WHERE LOWER(status) = ? ORDER BY support_count DESC";
        List<report> list = new ArrayList<>();
        try (Connection conn = database_manager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, status.toLowerCase());
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    report r = mapRowToReport(rs);
                    loadNotes(conn, r);
                    list.add(r);
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB Status Query Error] " + e.getMessage());
        }
        return list;
    }

    public int countByDomain(String domain) {
        String sql = "SELECT COUNT(*) AS total FROM reports WHERE LOWER(domain) = ?";
        try (Connection conn = database_manager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, domain.toLowerCase());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getInt("total");
            }
        } catch (SQLException e) {
            System.err.println("[DB Count Error] " + e.getMessage());
        }
        return 0;
    }

    public int countByStatus(String status) {
        String sql = "SELECT COUNT(*) AS total FROM reports WHERE LOWER(status) = ?";
        try (Connection conn = database_manager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, status.toLowerCase());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getInt("total");
            }
        } catch (SQLException e) {
            System.err.println("[DB Status Count Error] " + e.getMessage());
        }
        return 0;
    }

    private List<report> queryList(String sql) {
        List<report> list = new ArrayList<>();
        try (Connection conn = database_manager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                report r = mapRowToReport(rs);
                loadNotes(conn, r);
                list.add(r);
            }
        } catch (SQLException e) {
            System.err.println("[DB Query Error] " + e.getMessage());
        }
        return list;
    }

    private void loadNotes(Connection conn, report r) {
        String sql = "SELECT note FROM report_notes WHERE LOWER(TRIM(report_id)) = LOWER(TRIM(?))";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, r.getId());
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    r.addSupplementalNote(rs.getString("note"));
                }
            }
        } catch (SQLException ignored) {}
    }

    // Retains and maps the exact ID from MySQL
    private report mapRowToReport(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String domain = rs.getString("domain");
        String title = rs.getString("title");
        String desc = rs.getString("description");
        String loc = rs.getString("location");
        boolean anon = rs.getInt("anonymous") == 1;
        String reporterType = rs.getString("reporter_type");
        String reporter = rs.getString("reporter_identity");
        String createdAt = rs.getString("created_at");
        String status = rs.getString("status");
        int count = rs.getInt("support_count");
        String extra = rs.getString("category_or_dept");

        report r;
        if ("safety".equalsIgnoreCase(domain)) {
            r = new safety(id, title, desc, loc, anon, reporterType, reporter, createdAt, status, count, extra);
        } else if ("suggestion".equalsIgnoreCase(domain)) {
            r = new suggestion(id, title, desc, loc, anon, reporterType, reporter, createdAt, status, count, extra);
        } else {
            r = new management(id, title, desc, loc, anon, reporterType, reporter, createdAt, status, count, extra);
        }
        return r;
    }
}
