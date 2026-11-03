package backend;

import backend.exception.invalid_report;
import backend.model.*;
import backend.storage.persistence_worker;
import backend.storage.report_repository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class main {
    private static final int PORT = 8080;
    private static final int THREAD_POOL_SIZE = 10;

    public static void main(String[] args) throws IOException {
        seedInitialData();

        // 1. Initialize HTTP Server in background thread pool for the web frontend
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.setExecutor(Executors.newFixedThreadPool(THREAD_POOL_SIZE));

        server.createContext("/api/reports", new ReportHandler());
        server.createContext("/api/endorse", new EndorseHandler());
        server.createContext("/api/comment", new CommentHandler());
        server.createContext("/api/resolve", new ResolveHandler());
        server.createContext("/api/stats", new StatsHandler());

        // 2. Start background persistence daemon
        new persistence_worker().start();
        server.start();

        System.out.println("==========================================================");
        System.out.println("  Campora Redressal Platform Online (Port: " + PORT + ")");
        System.out.println("  Worker Thread Pool: " + THREAD_POOL_SIZE + " active threads");
        System.out.println("  Frontend & Terminal CLI simultaneously operational");
        System.out.println("==========================================================\n");

        // 3. Keep-alive check: Interactive terminal locally, or daemon hold in cloud
        if (System.console() != null) {
            runPortalMenu();
            server.stop(0);
            System.out.println("\n[Server Terminated. Goodbye!]");
        } else {
            System.out.println("[Headless Server Mode Active - Listening for HTTP Requests]");
            try {
                Thread.currentThread().join();
            } catch (InterruptedException e) {
                System.out.println("[Server Interrupted]");
            }
        }
    }

    // -------------------------------------------------------------------------
    // CONSOLE / TERMINAL MENU SYSTEM
    // -------------------------------------------------------------------------
    private static void runPortalMenu() {
        Scanner scanner = new Scanner(System.in);
        try {
            while (scanner.hasNextLine()) {
                System.out.println("\n==============================================");
                System.out.println("         WELCOME TO CAMPORA PORTAL            ");
                System.out.println("==============================================");
                System.out.println("Select your role / action:");
                System.out.println("  1. Submit Feedback / Report (Student / Faculty)");
                System.out.println("  2. Resolve Issues (Management / Facilities)");
                System.out.println("  3. Exit Application");
                System.out.print("Enter choice (1-3): ");

                String roleChoice = scanner.nextLine().trim();

                if ("1".equals(roleChoice)) {
                    runStudentFacultyPortal(scanner);
                } else if ("2".equals(roleChoice)) {
                    runManagementPortal(scanner);
                } else if ("3".equals(roleChoice)) {
                    break;
                }
            }
        } catch (Exception e) {
            System.out.println("[Terminal Menu Paused. Web API listening on port " + PORT + "]");
        }
    }

    private static void runStudentFacultyPortal(Scanner scanner) {
        report_repository repo = report_repository.getInstance();
        while (true) {
            System.out.println("\n--- [STUDENT / FACULTY PORTAL] ---");
            System.out.println("1. Submit a New Issue / Feedback");
            System.out.println("2. View All Campus Reports & Progress");
            System.out.println("3. Filter Reports by Track (Safety / Facilities / Ideas)");
            System.out.println("4. Upvote / Endorse an Existing Issue");
            System.out.println("5. Add Witness Note / Observation to a Report");
            System.out.println("6. Return to Role Selection");
            System.out.print("Enter choice (1-6): ");

            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1":
                    handleNewSubmission(scanner, repo);
                    break;
                case "2":
                    displayReports(repo.findAll(), false);
                    break;
                case "3":
                    System.out.print("Enter domain (safety / management / suggestion): ");
                    String domain = scanner.nextLine().trim().toLowerCase();
                    displayReports(repo.findByDomain(domain), false);
                    break;
                case "4":
                    handleEndorsement(scanner, repo);
                    break;
                case "5":
                    handleAddNote(scanner, repo);
                    break;
                case "6":
                    return;
                default:
                    System.out.println("[!] Invalid choice.");
            }
        }
    }

    private static void runManagementPortal(Scanner scanner) {
        report_repository repo = report_repository.getInstance();
        while (true) {
            System.out.println("\n--- [MANAGEMENT & RESOLUTION PORTAL] ---");
            System.out.println("1. View Pending / In-Progress Tickets (Triage Queue)");
            System.out.println("2. Update Ticket Status (In-Progress / Resolved / Rejected)");
            System.out.println("3. View Redressal Analytics & Workload Summary");
            System.out.println("4. Return to Role Selection");
            System.out.print("Enter choice (1-4): ");

            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1":
                    displayReports(repo.findAll(), true);
                    break;
                case "2":
                    handleStatusResolution(scanner, repo);
                    break;
                case "3":
                    displayAnalytics(repo);
                    break;
                case "4":
                    return;
                default:
                    System.out.println("[!] Invalid choice.");
            }
        }
    }

    private static void handleNewSubmission(Scanner scanner, report_repository repo) {
        System.out.println("\n--- SUBMIT GRIEVANCE / FEEDBACK ---");

        System.out.print("Select Track [1: Safety Alert, 2: Campus Facilities, 3: Student/Faculty Suggestion]: ");
        String typeChoice = scanner.nextLine().trim();

        System.out.print("Title / Summary: ");
        String title = scanner.nextLine().trim();

        System.out.print("Specific Campus Location: ");
        String location = scanner.nextLine().trim();

        System.out.print("Detailed Description: ");
        String desc = scanner.nextLine().trim();

        System.out.print("Submit Anonymously? (y/n): ");
        boolean isAnon = scanner.nextLine().trim().equalsIgnoreCase("y");

        String reporterType = "ANONYMOUS";
        String reporterIdentity = "ANONYMOUS";

        if (!isAnon) {
            System.out.print("Are you a [1: Student, 2: Faculty Member]: ");
            String roleOption = scanner.nextLine().trim();
            reporterType = "2".equals(roleOption) ? "FACULTY" : "STUDENT";

            System.out.print("Enter your Name or University ID: ");
            reporterIdentity = scanner.nextLine().trim();
            if (reporterIdentity.isEmpty()) reporterIdentity = reporterType;
        }

        report r;
        if ("1".equals(typeChoice)) {
            System.out.print("Hazard Category (e.g., Lighting, Electrical, CCTV, Harassment): ");
            String cat = scanner.nextLine().trim();
            r = new safety(title, desc, location, isAnon, reporterType, reporterIdentity, cat);
        } else if ("3".equals(typeChoice)) {
            System.out.print("Initiative Category (e.g., Academic, Library, Transport, Mess): ");
            String cat = scanner.nextLine().trim();
            r = new suggestion(title, desc, location, isAnon, reporterType, reporterIdentity, cat);
        } else {
            System.out.print("Responsible Department (e.g., IT Support, Hostel, Electrical, Estate): ");
            String dept = scanner.nextLine().trim();
            r = new management(title, desc, location, isAnon, reporterType, reporterIdentity, dept);
        }

        repo.save(r);
        System.out.println("\n[✓] Ticket successfully registered!");
        System.out.println("    Ticket ID    : " + r.getId());
        System.out.println("    Affiliation  : " + (isAnon ? "🔒 Cryptographic Anonymous" : "👤 " + reporterType + " (" + reporterIdentity + ")"));
        System.out.println("    Status       : " + r.getStatus());
    }

    private static void handleStatusResolution(Scanner scanner, report_repository repo) {
        System.out.print("\nEnter the 8-character Ticket ID to update: ");
        String id = scanner.nextLine().trim();
        report r = repo.findById(id);
        if (r == null) {
            System.out.println("[!] Ticket ID not found.");
            return;
        }

        System.out.println("Target: " + r.getTitle() + " [Current Status: " + r.getStatus() + "]");
        System.out.println("Select New Status:");
        System.out.println("  1. IN_PROGRESS (Technician / Officer Assigned)");
        System.out.println("  2. RESOLVED    (Issue Fixed / Implemented)");
        System.out.println("  3. REJECTED    (Duplicate / Invalid Request)");
        System.out.print("Enter status option (1-3): ");

        String opt = scanner.nextLine().trim();
        String newStatus;
        if ("1".equals(opt)) newStatus = "IN_PROGRESS";
        else if ("2".equals(opt)) newStatus = "RESOLVED";
        else if ("3".equals(opt)) newStatus = "REJECTED";
        else {
            System.out.println("[!] Invalid status option.");
            return;
        }

        System.out.print("Enter Resolution Note / Action Taken: ");
        String note = scanner.nextLine().trim();

        boolean updated = repo.updateStatus(id, newStatus, note);
        if (updated) {
            System.out.println("[✓] Status successfully updated to " + newStatus + "!");
        } else {
            System.out.println("[!] Failed to update status in database.");
        }
    }

    private static void handleEndorsement(Scanner scanner, report_repository repo) {
        System.out.print("\nEnter the 8-character Ticket ID to upvote: ");
        String id = scanner.nextLine().trim();
        report r = repo.findById(id);
        if (r != null) {
            int newCount = repo.incrementEndorsement(id);
            System.out.println("[✓] Upvote counted! Total endorsements: " + newCount);
        } else {
            System.out.println("[!] Ticket not found.");
        }
    }

    private static void handleAddNote(Scanner scanner, report_repository repo) {
        System.out.print("\nEnter Ticket ID: ");
        String id = scanner.nextLine().trim();
        report r = repo.findById(id);
        if (r != null) {
            System.out.print("Enter your note / observation: ");
            String note = scanner.nextLine().trim();
            repo.addNote(id, note);
            System.out.println("[✓] Note recorded.");
        } else {
            System.out.println("[!] Ticket not found.");
        }
    }

    private static void displayReports(List<report> reports, boolean isResolverView) {
        if (reports.isEmpty()) {
            System.out.println("\n[No tickets found.]");
            return;
        }
        System.out.println("\n=========================== TICKET REGISTRY ===========================");
        for (report r : reports) {
            System.out.println("ID        : " + r.getId() + " | Track: " + r.getDomain().toUpperCase());
            System.out.println("Title     : " + r.getTitle());
            System.out.println("Location  : " + r.getLocation());
            System.out.println("Status    : [" + r.getStatus() + "] | Urgency Index: " + r.calculateUrgencyIndex() + " | Upvotes: " + r.getSupportCount());

            if (r.isAnonymous()) {
                System.out.println("Reporter  : 🔒 ANONYMOUS");
            } else {
                System.out.println("Reporter  : 👤 [" + r.getReporterType() + "] " + r.getReporterIdentity());
            }

            if (isResolverView) {
                System.out.println("Summary   : " + r.getReportSummary());
            }

            if (!r.getSupplementalNotes().isEmpty()) {
                System.out.println("Notes Log :");
                for (String n : r.getSupplementalNotes()) {
                    System.out.println("   • " + n);
                }
            }
            System.out.println("-----------------------------------------------------------------------");
        }
    }

    private static void displayAnalytics(report_repository repo) {
        System.out.println("\n--- CAMPORA INSTITUTIONAL ANALYTICS ---");
        System.out.println("Total Submissions    : " + repo.findAll().size());
        System.out.println("Safety Alerts        : " + repo.countByDomain("safety"));
        System.out.println("Facilities Tickets   : " + repo.countByDomain("management"));
        System.out.println("Suggestions & Ideas  : " + repo.countByDomain("suggestion"));
        System.out.println("---------------------------------------");
        System.out.println("Pending Resolution   : " + repo.countByStatus("PENDING"));
        System.out.println("In Progress          : " + repo.countByStatus("IN_PROGRESS"));
        System.out.println("Resolved             : " + repo.countByStatus("RESOLVED"));
    }

    private static void seedInitialData() {
        report_repository repo = report_repository.getInstance();
        if (repo.findAll().isEmpty()) {
            safety s1 = new safety(
                "Broken Streetlight & Blind Spot Near Block C",
                "Students reporting feeling unsafe near the pathway between Block C and Hostel 1 after 8 PM.",
                "Block C Pathway",
                true,
                "ANONYMOUS",
                "ANONYMOUS",
                "Lighting & CCTV"
            );
            for (int i = 0; i < 26; i++) s1.endorse();
            repo.save(s1);

            management m1 = new management(
                "Hostel 2 Third Floor Wi-Fi Packet Drop",
                "Frequent disconnects and packet loss during peak evening hours (8 PM to 11 PM).",
                "Hostel 2 - 3rd Floor East Wing",
                false,
                "STUDENT",
                "CSE-3rd-Sem-Rep",
                "IT Support"
            );
            for (int i = 0; i < 18; i++) m1.endorse();
            repo.save(m1);

            suggestion sg1 = new suggestion(
                "Extend Central Library Weekend Hours During CAT",
                "Requesting Central Library reading halls stay open until 2:00 AM on weekends during CAT cycles.",
                "Central Library",
                false,
                "FACULTY",
                "Dr. Academic Coordinator",
                "Academic & Facilities"
            );
            for (int i = 0; i < 41; i++) sg1.endorse();
            repo.save(sg1);
        }
    }

    // -------------------------------------------------------------------------
    // HTTP REST API HANDLERS (FOR FRONTEND WEB PORTAL)
    // -------------------------------------------------------------------------

    static class ReportHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCORSPreflight(exchange);
                return;
            }

            setCORSHeaders(exchange);
            String method = exchange.getRequestMethod();

            if ("GET".equalsIgnoreCase(method)) {
                String query = exchange.getRequestURI().getQuery();
                List<report> reports;
                if (query != null && query.contains("domain=")) {
                    String domain = query.split("domain=")[1].split("&")[0];
                    reports = report_repository.getInstance().findByDomain(domain);
                } else if (query != null && query.contains("status=")) {
                    String status = query.split("status=")[1].split("&")[0];
                    reports = report_repository.getInstance().findByStatus(status);
                } else {
                    reports = report_repository.getInstance().findAll();
                }

                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < reports.size(); i++) {
                    sb.append(reports.get(i).toJson());
                    if (i < reports.size() - 1) sb.append(",");
                }
                sb.append("]");
                sendResponse(exchange, 200, sb.toString());
            } else if ("POST".equalsIgnoreCase(method)) {
                try {
                    String body = readBody(exchange);
                    Map<String, String> data = extractJsonFields(body);

                    String title = data.get("title");
                    String desc = data.get("description");
                    String loc = data.get("location");
                    String domain = data.getOrDefault("domain", "management");
                    boolean anon = "true".equalsIgnoreCase(data.get("anonymous"));
                    String reporterType = anon ? "ANONYMOUS" : data.getOrDefault("reporterType", "STUDENT");
                    String reporter = anon ? "ANONYMOUS" : data.getOrDefault("reporter", "Student");

                    if (title == null || title.trim().isEmpty() || loc == null || loc.trim().isEmpty()) {
                        throw new invalid_report("Title and location are mandatory fields.");
                    }

                    report r;
                    if ("safety".equalsIgnoreCase(domain)) {
                        r = new safety(title, desc, loc, anon, reporterType, reporter, data.getOrDefault("category", "General Safety"));
                    } else if ("suggestion".equalsIgnoreCase(domain)) {
                        r = new suggestion(title, desc, loc, anon, reporterType, reporter, data.getOrDefault("category", "Campus Idea"));
                    } else {
                        r = new management(title, desc, loc, anon, reporterType, reporter, data.getOrDefault("category", "Facilities"));
                    }

                    report_repository.getInstance().save(r);
                    sendResponse(exchange, 201, r.toJson());
                } catch (invalid_report e) {
                    sendResponse(exchange, 400, "{\"error\":\"" + e.getMessage() + "\"}");
                } catch (Exception e) {
                    sendResponse(exchange, 500, "{\"error\":\"Internal server error: " + e.getMessage() + "\"}");
                }
            }
        }
    }

    static class ResolveHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCORSPreflight(exchange);
                return;
            }

            setCORSHeaders(exchange);
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = readBody(exchange);
                Map<String, String> data = extractJsonFields(body);

                String query = exchange.getRequestURI().getQuery();
                String id = data.get("id");
                String status = data.get("status");
                String note = data.getOrDefault("note", "");

                if (id == null && query != null && query.contains("id=")) {
                    id = query.split("id=")[1].split("&")[0];
                }
                if (status == null && query != null && query.contains("status=")) {
                    status = query.split("status=")[1].split("&")[0];
                }

                System.out.println("[ResolveHandler] Received update for ID: '" + id + "' -> Status: '" + status + "'");

                if (id != null && !id.trim().isEmpty() && status != null && !status.trim().isEmpty()) {
                    boolean ok = report_repository.getInstance().updateStatus(id.trim(), status.trim(), note.trim());
                    if (ok) {
                        System.out.println("[ResolveHandler] Successfully updated ID: " + id);
                        sendResponse(exchange, 200, "{\"status\":\"success\",\"newStatus\":\"" + status + "\"}");
                        return;
                    } else {
                        System.err.println("[ResolveHandler] Failed to update DB for ID: " + id);
                        sendResponse(exchange, 404, "{\"error\":\"Ticket ID '" + id + "' does not exist in the database.\"}");
                        return;
                    }
                }
                sendResponse(exchange, 400, "{\"error\":\"Missing required 'id' or 'status' field.\"}");
            } else {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            }
        }
    }

    static class EndorseHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCORSPreflight(exchange);
                return;
            }

            setCORSHeaders(exchange);
            String query = exchange.getRequestURI().getQuery();
            if (query != null && query.contains("id=")) {
                String id = query.split("id=")[1].split("&")[0];
                int updatedVotes = report_repository.getInstance().incrementEndorsement(id);
                if (updatedVotes != -1) {
                    sendResponse(exchange, 200, "{\"id\":\"" + id + "\",\"supportCount\":" + updatedVotes + "}");
                    return;
                }
            }
            sendResponse(exchange, 404, "{\"error\":\"Report not found\"}");
        }
    }

    static class CommentHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCORSPreflight(exchange);
                return;
            }

            setCORSHeaders(exchange);
            String query = exchange.getRequestURI().getQuery();
            if (query != null && query.contains("id=")) {
                String id = query.split("id=")[1].split("&")[0];
                report r = report_repository.getInstance().findById(id);
                if (r != null) {
                    String body = readBody(exchange);
                    Map<String, String> data = extractJsonFields(body);
                    String note = data.getOrDefault("note", "").trim();
                    if (!note.isEmpty()) {
                        report_repository.getInstance().addNote(id, note);
                        sendResponse(exchange, 200, "{\"status\":\"note added\"}");
                        return;
                    }
                }
            }
            sendResponse(exchange, 400, "{\"error\":\"Invalid comment request or missing note\"}");
        }
    }

    static class StatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCORSPreflight(exchange);
                return;
            }

            setCORSHeaders(exchange);
            report_repository repo = report_repository.getInstance();
            String json = "{"
                    + "\"total\":" + repo.findAll().size() + ","
                    + "\"safety\":" + repo.countByDomain("safety") + ","
                    + "\"management\":" + repo.countByDomain("management") + ","
                    + "\"suggestion\":" + repo.countByDomain("suggestion") + ","
                    + "\"pending\":" + repo.countByStatus("PENDING") + ","
                    + "\"in_progress\":" + repo.countByStatus("IN_PROGRESS") + ","
                    + "\"resolved\":" + repo.countByStatus("RESOLVED")
                    + "}";
            sendResponse(exchange, 200, json);
        }
    }

    // -------------------------------------------------------------------------
    // UTILITY METHODS
    // -------------------------------------------------------------------------
    private static String readBody(HttpExchange exchange) throws IOException {
        InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
        BufferedReader br = new BufferedReader(reader);
        StringBuilder body = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) body.append(line);
        return body.toString();
    }

    private static void setCORSHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS, PATCH, PUT");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
        exchange.getResponseHeaders().set("Content-Type", "application/json");
    }

    private static void sendCORSPreflight(HttpExchange exchange) throws IOException {
        setCORSHeaders(exchange);
        exchange.sendResponseHeaders(204, -1);
    }

    private static void sendResponse(HttpExchange exchange, int code, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static Map<String, String> extractJsonFields(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null || json.trim().isEmpty()) return map;

        Pattern pattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(?:\"([^\"]*)\"|([true|false|null|\\d.]+))");
        Matcher matcher = pattern.matcher(json);
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
            map.put(key, value);
        }
        return map;
    }
}
