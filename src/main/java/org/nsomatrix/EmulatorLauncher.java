package org.nsomatrix;

import javax.swing.*;
import java.io.*;
import java.nio.file.Files;

public class EmulatorLauncher {

    private static File extractResourceToTempFile(String resourcePath, String filename) throws IOException {
        InputStream is = EmulatorLauncher.class.getResourceAsStream(resourcePath);
        if (is == null) {
            throw new FileNotFoundException("Resource not found: " + resourcePath);
        }
        File tempFile = Files.createTempFile(filename, null).toFile();
        // Don't delete on exit here, delete manually after process ends

        try (OutputStream os = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
        }

        return tempFile;
    }

    public static void launch(String emulatorName, File gameJarFile) {
        new Thread(() -> {
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
            File extractedEmulatorJar;
            final File finalGameJarFile;
            try {
                extractedEmulatorJar = extractResourceToTempFile(resourcePath, emulatorJarName);
                finalGameJarFile = gameJarFile.getCanonicalFile();
            } catch (IOException e) {
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(null,
                                "Failed to extract emulator jar: " + e.getMessage(),
                                "Error",
                                JOptionPane.ERROR_MESSAGE)
                );
                return;
            }

            if (!finalGameJarFile.exists() || !finalGameJarFile.canRead()) {
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(null,
                                "Game file not found or unreadable: " + finalGameJarFile.getName(),
                                "Error", JOptionPane.ERROR_MESSAGE)
                );
                return;
            }

            String javaExec = "java"; // Or full path to java executable

            try {
                ProcessBuilder pb = new ProcessBuilder(
                        javaExec, "-jar",
                        extractedEmulatorJar.getAbsolutePath(),
                        finalGameJarFile.getAbsolutePath()
                );
                pb.inheritIO();

                Process process = pb.start();

                // Wait for emulator to finish and clean up in a separate thread
                process.waitFor();

                // Clean up extracted jar file
                if (!extractedEmulatorJar.delete()) {
                    System.err.println("Warning: Could not delete temp jar: " + extractedEmulatorJar.getAbsolutePath());
                }

                int exitCode = process.exitValue();
                if (exitCode != 0) {
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(null,
                                    "Emulator process exited with code: " + exitCode,
                                    "Error",
                                    JOptionPane.ERROR_MESSAGE)
                    );
                }
            } catch (IOException | InterruptedException e) {
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(null,
                                "Failed to launch emulator: " + e.getMessage(),
                                "Error", JOptionPane.ERROR_MESSAGE)
                );
                Thread.currentThread().interrupt();
            }
        }).start();
    }
}