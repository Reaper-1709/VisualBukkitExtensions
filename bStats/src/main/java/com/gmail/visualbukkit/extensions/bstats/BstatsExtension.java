package com.gmail.visualbukkit.extensions.bstats;

import com.gmail.visualbukkit.VisualBukkitApp;
import com.gmail.visualbukkit.VisualBukkitExtension;
import com.gmail.visualbukkit.blocks.BlockRegistry;
import com.gmail.visualbukkit.project.Project;
import javafx.scene.control.TextField;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static java.nio.file.StandardWatchEventKinds.*;

public class BstatsExtension implements VisualBukkitExtension {

    protected static String METRICS_CLASS;

    public BstatsExtension() {}

    @Override
    public String getName() {
        return "bStats";
    }

    @Override
    public String getVersion() {
        return "2.0";
    }

    @Override
    public void open(Project project) {
        try (InputStream inputStream = BstatsExtension.class.getResourceAsStream("/Metrics.java")) {
            // noinspection ConstantConditions
            METRICS_CLASS = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Metrics.java template", e);
        }

        BlockRegistry.register(BstatsExtension.class.getClassLoader(),
                "com.gmail.visualbukkit.extensions.bstats");

        // Start our persistent POM watcher
        try {
            startPersistentPomWatcher(project);
        } catch (IOException | NoSuchFieldException | IllegalAccessException e) {
            VisualBukkitApp.getLogger().log(
                    Level.WARNING,
                    "Could not start persistent bStats POM watcher",
                    e
            );
        }
    }

    private void startPersistentPomWatcher(Project project) throws IOException, NoSuchFieldException, IllegalAccessException {
        Path projectRoot = project.getDirectory();

        // reflectively grab the buildDirectory:
        Field bf = Project.class.getDeclaredField("buildDirectory");
        bf.setAccessible(true);
        Path buildDir = (Path) bf.get(project);
        String buildName = buildDir.getFileName().toString();

        WatchService watcher = FileSystems.getDefault().newWatchService();
        Map<WatchKey, Path> keys = new HashMap<>();

        // 1) Always watch projectRoot for buildDir create/delete
        WatchKey rootKey = projectRoot.register(watcher, ENTRY_CREATE, ENTRY_DELETE);
        keys.put(rootKey, projectRoot);
        VisualBukkitApp.getLogger().info("bStats watcher: watching project root for " + buildName);

        // 2) If buildDir already exists (first build), register it right away:
        if (Files.exists(buildDir)) {
            WatchKey bdKey = buildDir.register(watcher, ENTRY_CREATE, ENTRY_MODIFY);
            keys.put(bdKey, buildDir);
            VisualBukkitApp.getLogger().info("bStats watcher: initially watching existing buildDir");
        }

        Thread watcherThread = new Thread(() -> {
            long lastPatchedAt = 0L;
            final long throttleMillis = 500L;

            while (true) {
                try {
                    WatchKey key = watcher.take();
                    Path dir = keys.get(key);
                    if (dir == null) {
                        VisualBukkitApp.getLogger().warning("bStats watcher: Unknown watch key!");
                        key.reset();
                        continue;
                    }

                    for (WatchEvent<?> ev : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = ev.kind();
                        Path name = (Path) ev.context();

                        // A) buildDir created or deleted under projectRoot
                        if (dir.equals(projectRoot) && name.toString().equals(buildName)) {
                            if (kind == ENTRY_CREATE) {
                                WatchKey bdKey = buildDir.register(watcher, ENTRY_CREATE, ENTRY_MODIFY);
                                keys.put(bdKey, buildDir);
                                VisualBukkitApp.getLogger().info("bStats watcher: buildDir created → now watching pom.xml");
                            } else { // ENTRY_DELETE
                                keys.entrySet().removeIf(e -> {
                                    if (e.getValue().equals(buildDir)) {
                                        e.getKey().cancel();
                                        return true;
                                    }
                                    return false;
                                });
                                VisualBukkitApp.getLogger().info("bStats watcher: buildDir deleted → stopped watching pom.xml");
                            }
                            continue;
                        }

                        // B) pom.xml CREATE or MODIFY inside buildDir
                        if (dir.equals(buildDir)
                                && (kind == ENTRY_CREATE || kind == ENTRY_MODIFY)
                                && "pom.xml".equals(name.toString()))
                        {
                            long now = System.currentTimeMillis();
                            if (now - lastPatchedAt < throttleMillis) {
                                VisualBukkitApp.getLogger().info("bStats watcher: skipping repeat patch");
                                continue;
                            }

                            Path pom = buildDir.resolve("pom.xml");
                            VisualBukkitApp.getLogger().info("bStats watcher: detected pom.xml " + kind + ", waiting for stability...");
                            waitForFileStable(pom);

                            String xml = Files.readString(pom, StandardCharsets.UTF_8);
                            if (xml.contains("<pattern>org.bstats</pattern>")) {
                                VisualBukkitApp.getLogger().info("bStats watcher: already patched; skipping");
                            } else {
                                String pkg = reflectPackageName(project);
                                String patched = xml.replaceFirst(
                                        "(</goals>)",
                                        """
                                        $1
                                        \t\t\t\t\t\t<configuration>
                                        \t\t\t\t\t\t\t<relocations>
                                        \t\t\t\t\t\t\t\t<relocation>
                                        \t\t\t\t\t\t\t\t\t<pattern>org.bstats</pattern>
                                        \t\t\t\t\t\t\t\t\t<shadedPattern>%s.bstats</shadedPattern>
                                        \t\t\t\t\t\t\t\t</relocation>
                                        \t\t\t\t\t\t\t</relocations>
                                        \t\t\t\t\t\t</configuration>""".formatted(pkg)
                                );

                                Files.writeString(pom, patched, StandardCharsets.UTF_8);
                                lastPatchedAt = now;
                                VisualBukkitApp.getLogger().info("bStats shading block injected into pom.xml");
                            }
                        }
                    }

                    key.reset();

                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception ex) {
                    VisualBukkitApp.getLogger().log(Level.WARNING, "Persistent POM watcher error", ex);
                }
            }
        }, "bStats-Persistent-POM-Watcher");

        watcherThread.setDaemon(true);
        watcherThread.start();
    }


    private void waitForFileStable(Path file)
            throws IOException, InterruptedException {
        long startTime   = System.currentTimeMillis();
        long stableStart = -1L;
        long lastSize    = -1L;
        final long checkInterval = 20L;

        while (true) {
            long now     = System.currentTimeMillis();
            long elapsed = now - startTime;

            if (elapsed > 2000L) {
                VisualBukkitApp.getLogger()
                        .warning("Timeout waiting for " + file + " to stabilize");
                return;
            }

            long size = Files.size(file);
            if (size == lastSize) {
                if (stableStart < 0) stableStart = now;
                else if (now - stableStart >= 200L) return;
            } else {
                stableStart = -1;
                lastSize    = size;
            }

            TimeUnit.MILLISECONDS.sleep(checkInterval);
        }
    }

    private String reflectPackageName(Project project) {
        try {
            Field f = Project.class.getDeclaredField("packageField");
            f.setAccessible(true);
            TextField pkgField = (TextField) f.get(project);
            String txt = pkgField.getText().trim();
            return txt.isEmpty() ? project.getName() : txt;
        } catch (Exception e) {
            VisualBukkitApp.getLogger().warning(
                    "Failed to reflect package name; defaulting to 'plugin'"
            );
            return "plugin";
        }
    }

    @Override
    public void save(Project project) {
        // No custom save logic needed
    }
}
