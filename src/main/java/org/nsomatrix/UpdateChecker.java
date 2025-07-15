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

    public static void applyUpdateAndRestart(File downloadedJar, String latestVersion) throws IOException {
        String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        File currentJar;
        try {
            currentJar = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (java.net.URISyntaxException e) {
            throw new IOException("Failed to get current JAR location: " + e.getMessage(), e);
        }

        // Determine the new JAR filename
        String newJarName = "NSOMatrixLauncher-" + latestVersion + ".jar";
        File newJarFile = new File(currentJar.getParentFile(), newJarName);

        String os = System.getProperty("os.name").toLowerCase();
        boolean isWindows = os.contains("win");

        File tempScript;
        String scriptContent;
        String[] command;

        if (isWindows) {
            tempScript = File.createTempFile("updater", ".bat");
            scriptContent =
                    "@echo off\n" +
                    "timeout /t 2 /nobreak > NUL\n" + // Give time for the current app to exit
                    "del \"" + currentJar.getAbsolutePath() + "\"\n" + // Remove the old JAR
                    "move /Y \"" + downloadedJar.getAbsolutePath() + "\" \"" + newJarFile.getAbsolutePath() + "\"\n" + // Move downloaded to new name
                    "start \"\" /B \"" + javaBin + "\" -jar \"" + newJarFile.getAbsolutePath() + "\"\n" + // Launch in background
                    "del \"" + tempScript.getAbsolutePath() + "\"\n" + // Clean up script
                    "exit\n"; // Explicitly exit the batch script
            command = new String[]{"cmd.exe", "/c", tempScript.getAbsolutePath()};
        } else { // Linux or macOS
            tempScript = File.createTempFile("updater", ".sh");
            tempScript.setExecutable(true);
            scriptContent =
                    "#!/bin/bash\n" +
                    "sleep 2\n" + // Give time for the current app to exit
                    "rm -f \"" + currentJar.getAbsolutePath() + "\"\n" + // Remove the old JAR
                    "mv \"" + downloadedJar.getAbsolutePath() + "\" \"" + newJarFile.getAbsolutePath() + "\"\n" + // Move downloaded to new name
                    "java -jar \"" + newJarFile.getAbsolutePath() + "\" &\n" + // Launch in background
                    "rm \"" + tempScript.getAbsolutePath() + "\"\n"; // Clean up script
            command = new String[]{"bash", tempScript.getAbsolutePath()};
        }

        try (PrintWriter pw = new PrintWriter(tempScript)) {
            pw.println(scriptContent);
        }

        // Execute the script and exit the current application
        new ProcessBuilder(command).start();
        System.exit(0);
    }
}
