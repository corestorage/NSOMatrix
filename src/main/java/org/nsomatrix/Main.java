package org.nsomatrix;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;

import javax.swing.*;
import java.util.prefs.Preferences;

public class Main {
    public static final String APP_VERSION = "1.0.0"; // Current application version

    public static void main(String[] args) {
        SplashScreen splash = new SplashScreen(600, 400);
        splash.runSequence();

        // Load saved theme from Preferences
        Preferences prefs = Preferences.userNodeForPackage(UI.class);
        String theme = prefs.get("app_theme", "system");

        try {
            switch (theme.toLowerCase()) {
                case "dark":
                    UIManager.setLookAndFeel(new FlatDarkLaf());
                    break;
                case "light":
                    UIManager.setLookAndFeel(new FlatLightLaf());
                    break;
                case "system":
                default:
                    UIManager.setLookAndFeel(new FlatIntelliJLaf());
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to set theme: " + e.getMessage());
        }

        SwingUtilities.invokeLater(() -> {
            UI ui = new UI();
            ui.show();
        });
    }
}