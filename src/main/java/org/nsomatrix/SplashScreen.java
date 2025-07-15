package org.nsomatrix;

import javax.swing.*;
import java.awt.*;
import java.net.URL;
import java.util.concurrent.atomic.AtomicReference;

public class SplashScreen {
    private final JWindow window = new JWindow();
    private final JLabel statusLabel = new JLabel("Starting...", SwingConstants.RIGHT);
    private final AtomicReference<String> baseTextRef = new AtomicReference<>("Loading...");

    private final String[] SPINNER_FRAMES = {
            "⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"
    };

    private Timer spinnerTimer;
    private int spinnerIndex = 0;

    public SplashScreen(int width, int height) {
        Color darkGray = new Color(0x21, 0x21, 0x21); // #212121

        URL resource = getClass().getResource("/matrix.png");
        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(darkGray);

        if (resource != null) {
            ImageIcon icon = new ImageIcon(resource);
            Image img = icon.getImage();

            double scale = Math.min((double) width / icon.getIconWidth(), (double) height / icon.getIconHeight());
            Image scaled = img.getScaledInstance(
                    (int) (icon.getIconWidth() * scale),
                    (int) (icon.getIconHeight() * scale),
                    Image.SCALE_SMOOTH
            );

            JLabel imageLabel = new JLabel(new ImageIcon(scaled), SwingConstants.CENTER);
            imageLabel.setOpaque(false);
            content.add(imageLabel, BorderLayout.CENTER);
        } else {
            JLabel fallback = new JLabel("Loading...", SwingConstants.CENTER);
            fallback.setForeground(Color.WHITE);
            fallback.setBackground(darkGray);
            fallback.setOpaque(true);
            content.add(fallback, BorderLayout.CENTER);
        }

        statusLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));
        statusLabel.setForeground(Color.LIGHT_GRAY);
        statusLabel.setBackground(darkGray);
        statusLabel.setOpaque(true);
        content.add(statusLabel, BorderLayout.SOUTH);

        window.setContentPane(content);
        window.setBackground(darkGray);
        window.setSize(width, height);
        window.setLocationRelativeTo(null);
    }

    public void runSequence() {
        String[] messages = {
                "Loading configuration...",
                "Initializing modules...",
                "Starting engine...",
                "Almost there..."
        };

        startSpinner();
        for (String msg : messages) {
            setSpinnerBaseText(msg);
            showSplashStep(200);
        }
        stopSpinner();
        showSplash(100);
    }

    public void setSpinnerBaseText(String text) {
        baseTextRef.set(text);
    }

    public void updateStatus(String message) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(message));
    }

    public void startSpinner() {
        stopSpinner(); // Ensure no duplicate timers

        spinnerTimer = new Timer(100, e -> {
            String spinnerChar = SPINNER_FRAMES[spinnerIndex++ % SPINNER_FRAMES.length];
            statusLabel.setText(baseTextRef.get() + " " + spinnerChar);
        });
        spinnerTimer.start();
    }

    public void stopSpinner() {
        if (spinnerTimer != null && spinnerTimer.isRunning()) {
            spinnerTimer.stop();
        }
    }

    public void showSplashStep(int duration) {
        window.setVisible(true);
        try {
            Thread.sleep(duration);
        } catch (InterruptedException ignored) {}
    }

    public void showSplash(int duration) {
        stopSpinner();
        try {
            Thread.sleep(duration);
        } catch (InterruptedException ignored) {}
        window.setVisible(false);
    }
}
