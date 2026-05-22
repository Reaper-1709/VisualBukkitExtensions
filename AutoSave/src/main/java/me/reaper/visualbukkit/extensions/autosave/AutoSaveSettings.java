package me.reaper.visualbukkit.extensions.autosave;

import com.gmail.visualbukkit.VisualBukkitApp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.logging.Level;

public class AutoSaveSettings {

    private static final String CONFIG_FOLDER = "autosave";
    private static final String CONFIG_FILE = "autosave.properties";

    private static final long DEFAULT_INTERVAL_SECONDS = 60; // 1 minute
    private static final boolean DEFAULT_SHOW_INFO_BOX = true;

    private final Path configDir;
    private final Path configFile;

    private long autosaveIntervalSeconds = DEFAULT_INTERVAL_SECONDS;
    private boolean showInfoBox = DEFAULT_SHOW_INFO_BOX;

    public AutoSaveSettings(Path projectDirectory) {
        Path parent = projectDirectory.getParent();
        if (parent == null) {
            parent = projectDirectory;
        }

        this.configDir = parent.resolve(CONFIG_FOLDER);
        this.configFile = configDir.resolve(CONFIG_FILE);

        loadSettings();
    }

    private void loadSettings() {
        try {
            Files.createDirectories(configDir);

            Properties props = new Properties();

            if (Files.exists(configFile)) {
                try (InputStream in = Files.newInputStream(configFile)) {
                    props.load(in);
                }
            }

            String rawInterval = props.getProperty("autosave.interval.seconds");
            if (rawInterval != null && !rawInterval.isBlank()) {
                try {
                    long parsed = Long.parseLong(rawInterval.trim());
                    if (parsed > 0) {
                        autosaveIntervalSeconds = parsed;
                    }
                } catch (NumberFormatException ignored) {
                    autosaveIntervalSeconds = DEFAULT_INTERVAL_SECONDS;
                }
            }

            String rawShowInfo = props.getProperty("autosave.showInfoBox");
            if (rawShowInfo != null && !rawShowInfo.isBlank()) {
                showInfoBox = Boolean.parseBoolean(rawShowInfo.trim());
            }

            saveSettings(); // ensures file exists and defaults are written

            VisualBukkitApp.getLogger().info(
                    "AutoSave settings loaded: " + autosaveIntervalSeconds + " seconds, showInfoBox=" + showInfoBox
            );
        } catch (Exception e) {
            VisualBukkitApp.getLogger().log(
                    Level.WARNING,
                    "Failed to load AutoSave settings; using defaults",
                    e
            );
            autosaveIntervalSeconds = DEFAULT_INTERVAL_SECONDS;
            showInfoBox = DEFAULT_SHOW_INFO_BOX;
        }
    }

    public void saveSettings() {
        try {
            Files.createDirectories(configDir);

            Properties props = new Properties();
            props.setProperty("autosave.interval.seconds", String.valueOf(autosaveIntervalSeconds));
            props.setProperty("autosave.showInfoBox", String.valueOf(showInfoBox));

            try (OutputStream out = Files.newOutputStream(configFile)) {
                props.store(out, "AutoSave Extension Configuration");
            }

            VisualBukkitApp.getLogger().info(
                    "AutoSave settings saved: " + autosaveIntervalSeconds + " seconds, showInfoBox=" + showInfoBox
            );
        } catch (IOException e) {
            VisualBukkitApp.getLogger().log(
                    Level.WARNING,
                    "Failed to save AutoSave settings",
                    e
            );
        }
    }

    public long getAutosaveIntervalMs() {
        return autosaveIntervalSeconds * 1000L;
    }

    public boolean isShowInfoBox() {
        return showInfoBox;
    }

}