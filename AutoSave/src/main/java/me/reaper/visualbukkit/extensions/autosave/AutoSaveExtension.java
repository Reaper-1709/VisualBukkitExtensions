package me.reaper.visualbukkit.extensions.autosave;

import com.gmail.visualbukkit.VisualBukkitApp;
import com.gmail.visualbukkit.VisualBukkitExtension;
import com.gmail.visualbukkit.blocks.BlockRegistry;
import com.gmail.visualbukkit.project.Project;

import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;

public class AutoSaveExtension implements VisualBukkitExtension {

    private Timer autosaveTimer;
    private AutoSaveSettings settings;

    public AutoSaveExtension() {}

    @Override
    public String getName() {
        return "Auto Save";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public void open(Project project) {
        try {
            settings = new AutoSaveSettings(project.getDirectory().getParent());

            BlockRegistry.register(AutoSaveExtension.class.getClassLoader(),
                    "me.reaper.visualbukkit.extensions.autosave");

            startAutosaveTimer(project);

            VisualBukkitApp.getLogger().info("Auto Save loaded successfully");
        } catch (Exception e) {
            VisualBukkitApp.getLogger().log(
                    Level.WARNING,
                    "Failed to load Auto Save",
                    e
            );
        }
    }

    private void startAutosaveTimer(Project project) {
        if (autosaveTimer != null) {
            autosaveTimer.cancel();
            autosaveTimer.purge();
        }

        autosaveTimer = new Timer("AutoSaveTimer", true);
        long interval = settings.getAutosaveIntervalMs();

        autosaveTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    performAutosave(project);
                } catch (Exception e) {
                    VisualBukkitApp.getLogger().log(
                            Level.WARNING,
                            "Auto Save: unexpected timer failure",
                            e
                    );
                }
            }
        }, interval, interval);

        VisualBukkitApp.getLogger().info(
                "Auto Save timer started: saves every " + (interval / 1000) + " seconds"
        );
    }

    private void performAutosave(Project project) {
        try {
            project.save();
            VisualBukkitApp.getLogger().info("Auto Save: Project saved successfully");
            if (settings.isShowInfoBox()) {
                javafx.application.Platform.runLater(() ->
                        VisualBukkitApp.displayInfo("Auto save: Project saved successfully")
                );
            }
        } catch (Exception e) {
            VisualBukkitApp.getLogger().log(
                    Level.WARNING,
                    "Auto Save: Failed to save project",
                    e
            );
            if(settings.isShowInfoBox()) {
                javafx.application.Platform.runLater(() ->
                        VisualBukkitApp.displayError("Auto save failed: " + e.getMessage())
                );
            }
        }
    }

    @Override
    public void save(Project project) {
        // Intentionally empty: do not stop autosave on normal project save.
    }
}