package org.nsomatrix;

import javax.swing.*;
import java.io.*;
import java.nio.file.Files;

public class EmulatorLauncher {

    private static File extractResourceToTempFile(String resourcePath, String filename) throws IOException {
        // Use try-with-resources to ensure InputStream is closed
        try (InputStream is = EmulatorLauncher.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new FileNotFoundException("Resource not found: " + resourcePath);
            }
            // Use .jar suffix explicitly and include filename prefix for clarity
            File tempFile = Files.createTempFile(filename, ".jar").toFile();

            try (OutputStream os = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
            }

            // Delete on exit as a fallback for cleanup
            tempFile.deleteOnExit();

            return tempFile;
        }
    }

    /**
     * Launches the emulator with the specified name and game jar file.
     * Uses a new thread and waits for the emulator to exit before cleaning up.
     *
     * @param emulatorName name of the emulator to launch (e.g., "angelchip" or "microemulator")
     * @param gameJarFile  jar file of the game to launch
     */
    public static void launch(final String emulatorName, final File gameJarFile) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String emulatorJarName;

                switch (emulatorName.toLowerCase()) {
                    case "angelchip":
                        emulatorJarName = "angelchip.jar";
                        break;
                    case "microemulator":
                    default:
                        emulatorJarName = "microemulator.jar";
                        break;
                }

                String resourcePath = "/libs/" + emulatorJarName;
                final File extractedEmulatorJar;
                final File finalGameJarFile;
                try {
                    extractedEmulatorJar = extractResourceToTempFile(resourcePath, emulatorJarName);
                    finalGameJarFile = gameJarFile.getCanonicalFile();
                } catch (IOException e) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            JOptionPane.showMessageDialog(null,
                                    "Failed to extract emulator jar: " + e.getMessage(),
                                    "Error",
                                    JOptionPane.ERROR_MESSAGE);
                        }
                    });
                    return;
                }

                if (!finalGameJarFile.exists() || !finalGameJarFile.canRead()) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            JOptionPane.showMessageDialog(null,
                                    "Game file not found or unreadable: " + finalGameJarFile.getName(),
                                    "Error", JOptionPane.ERROR_MESSAGE);
                        }
                    });
                    return;
                }

                // Detect Java executable path
                String javaHome = System.getProperty("java.home");
                File javaExecFile = new File(javaHome, "bin/java");
                final String javaExecPath = (javaExecFile.exists() && javaExecFile.canExecute())
                        ? javaExecFile.getAbsolutePath()
                        : "java";

                try {
                    ProcessBuilder pb = new ProcessBuilder(
                            javaExecPath, "-jar",
                            extractedEmulatorJar.getAbsolutePath(),
                            finalGameJarFile.getAbsolutePath()
                    );
                    // Redirect error stream to standard output for easier debugging
                    pb.redirectErrorStream(true);
                    pb.inheritIO();

                    Process process = pb.start();

                    // Wait for emulator process to exit
                    int exitCode = process.waitFor();

                    // Clean up extracted jar file
                    if (!extractedEmulatorJar.delete()) {
                        System.err.println("Warning: Could not delete temp jar: " + extractedEmulatorJar.getAbsolutePath());
                    }

                    if (exitCode != 0) {
                        final int code = exitCode;
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                JOptionPane.showMessageDialog(null,
                                        "Emulator process exited with code: " + code,
                                        "Error",
                                        JOptionPane.ERROR_MESSAGE);
                            }
                        });
                    }
                } catch (IOException | InterruptedException e) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            JOptionPane.showMessageDialog(null,
                                    "Failed to launch emulator: " + e.getMessage(),
                                    "Error", JOptionPane.ERROR_MESSAGE);
                        }
                    });
                    Thread.currentThread().interrupt();
                }
            }
        }).start();
    }
}