package backend.storage;

import backend.model.report;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class persistence_worker {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Persistence-Daemon-Worker");
        t.setDaemon(true); // Daemon thread ensures JVM terminates without blocking
        return t;
    });

    public void start() {
        scheduler.scheduleAtFixedRate(this::flushToFile, 10, 60, TimeUnit.SECONDS);
    }

    public void flushToFile() {
        List<report> reports = report_repository.getInstance().findAll();
        StringBuilder json = new StringBuilder("[\n");
        for (int i = 0; i < reports.size(); i++) {
            json.append("  ").append(reports.get(i).toJson());
            if (i < reports.size() - 1) json.append(",");
            json.append("\n");
        }
        json.append("]");

        try (FileWriter writer = new FileWriter("reports_snapshot.json")) {
            writer.write(json.toString());
            System.out.println("[persistence_worker] Flushed " + reports.size() + " reports to reports_snapshot.json");
        } catch (IOException e) {
            System.err.println("[Persistence Error] " + e.getMessage());
        }
    }
}
