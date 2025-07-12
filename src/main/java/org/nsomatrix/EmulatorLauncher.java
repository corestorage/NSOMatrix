package org.nsomatrix;

import javax.swing.*;
import java.io.File;
import java.io.IOException;

public class EmulatorLauncher {

    public static void launch(String emulatorName, File jarFile) {
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

        File emulatorJar = new File("libs", emulatorJarName);
        try {
            emulatorJar = emulatorJar.getCanonicalFile();
            jarFile = jarFile.getCanonicalFile();
        } catch (IOException e) {
            // Could show message or log
        }

        if (!emulatorJar.exists() || !emulatorJar.canRead()) {
            JOptionPane.showMessageDialog(null,
                    emulatorJarName + " not found or unreadable in libs folder",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (!jarFile.exists() || !jarFile.canRead()) {
            JOptionPane.showMessageDialog(null,
                    "Game file not found or unreadable: " + jarFile.getName(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Use absolute java path or simple "java" command (assuming Java in PATH)
        String javaExec = "java";

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    javaExec, "-jar",
                    emulatorJar.getAbsolutePath(),
                    jarFile.getAbsolutePath()
            );
            pb.start();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(null,
                    "Failed to launch " + emulatorName + ": " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}