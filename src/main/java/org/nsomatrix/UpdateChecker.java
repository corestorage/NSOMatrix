package org.nsomatrix;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class UpdateChecker {

    private static final String UPDATE_URL = "https://raw.githubusercontent.com/corestorage/NSOMatrix/refs/heads/master/version.txt";
    private static final String DOWNLOAD_BASE_URL = "https://github.com/corestorage/NSOMatrix/releases/download/";

    public static String getLatestVersion() throws Exception {
        URL url = new URL(UPDATE_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();
            return content.toString().trim();
        } else {
            throw new RuntimeException("Failed to fetch update information. HTTP error code: " + responseCode);
        }
    }

    public static boolean isUpdateAvailable(String currentVersion, String latestVersion) {
        return latestVersion.compareTo(currentVersion) > 0;
    }

    public static void downloadUpdate(String version, File destinationFile) throws IOException {
        String downloadUrl = DOWNLOAD_BASE_URL + "v" + version + "/NSOMatrixLauncher-" + version + ".jar";
        URL url = new URL(downloadUrl);
        try (InputStream in = url.openStream()) {
            Files.copy(in, destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void applyUpdateAndRestart(File downloadedJar) throws IOException {
        String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        File currentJar;
        try {
            currentJar = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (java.net.URISyntaxException e) {
            throw new IOException("Failed to get current JAR location: " + e.getMessage(), e);
        }

        // Create a temporary script to replace the JAR and restart
        // This script is for Linux/macOS. For Windows, a .bat file would be needed.
        File tempScript = File.createTempFile("updater", ".sh");
        tempScript.setExecutable(true);

        try (PrintWriter pw = new PrintWriter(tempScript)) {
            pw.println("#!/bin/bash");
            pw.println("sleep 2"); // Give time for the current app to exit
            pw.println("mv \"" + downloadedJar.getAbsolutePath() + "\" \"" + currentJar.getAbsolutePath() + "\"");
            pw.println("java -jar \"" + currentJar.getAbsolutePath() + "\" &"); // Launch in background
            pw.println("rm \"" + tempScript.getAbsolutePath() + "\""); // Clean up script
        }

        // Execute the script and exit the current application
        new ProcessBuilder("bash", tempScript.getAbsolutePath()).start();
        System.exit(0);
    }
}
