package org.nsomatrix;

import org.nsomatrix.UI;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.util.prefs.Preferences;

public class SettingsPanel extends JPanel {

    private final JTextField downloadDirField;
    private final JButton chooseDirBtn;
    private final JComboBox<String> themeSelector;
    private final JComboBox<String> emulatorSelector;
    private final UI appUI;  // reference to UI for callbacks
    private final Preferences prefs;

    // Store original values to restore on cancel
    private String originalDownloadDir;
    private String originalTheme;
    private String originalEmulator;

    public SettingsPanel(UI ui) {
        this.appUI = ui;
        this.prefs = Preferences.userNodeForPackage(UI.class);

        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(20, 20, 20, 20));

        // Title centered at top
        JLabel titleLabel = new JLabel("Settings");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 24));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        add(titleLabel, BorderLayout.NORTH);

        JPanel outerContentPanel = new JPanel(new BorderLayout());
        add(outerContentPanel, BorderLayout.CENTER);

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();

        gbc.insets = new Insets(8, 10, 8, 10);
        gbc.anchor = GridBagConstraints.LINE_START;

        // Mods Download Folder
        JLabel downloadDirLabel = new JLabel("MODs Download Directory:");
        gbc.gridx = 0;
        gbc.gridy = 0;
        formPanel.add(downloadDirLabel, gbc);

        String savedDir = prefs.get("download_dir", System.getProperty("user.home") + File.separator + "Downloads" + File.separator + "mods");
        downloadDirField = new JTextField(savedDir, 30);
        downloadDirField.setEditable(false);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(downloadDirField, gbc);

        chooseDirBtn = new JButton("Choose...");
        gbc.gridx = 2;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        formPanel.add(chooseDirBtn, gbc);
        chooseDirBtn.addActionListener(e -> chooseDownloadDirectory());

        // Theme selection
        JLabel themeLabel = new JLabel("Theme:");
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        formPanel.add(themeLabel, gbc);

        themeSelector = new JComboBox<>(new String[]{"Light", "Dark"});
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(themeSelector, gbc);

        String savedTheme = prefs.get("app_theme", "Dark");
        themeSelector.setSelectedItem(savedTheme);

        themeSelector.addActionListener(e -> {
            String selected = (String) themeSelector.getSelectedItem();
            if (selected != null && appUI != null) {
                appUI.setTheme(selected);
            }
        });

        // Emulator selection
        JLabel emulatorLabel = new JLabel("App Emulator:");
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        formPanel.add(emulatorLabel, gbc);

        String[] emulators = {"Microemulator", "Angelchip"};
        emulatorSelector = new JComboBox<>(emulators);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(emulatorSelector, gbc);

        String savedEmulator = prefs.get("app_emulator", "Microemulator");
        emulatorSelector.setSelectedItem(savedEmulator);

        // Optionally add listener here for live updates if needed
        emulatorSelector.addActionListener(e -> {
            String selectedEmulator = (String) emulatorSelector.getSelectedItem();
            // Can notify appUI or other components here if you want live updates
        });

        outerContentPanel.add(formPanel, BorderLayout.NORTH);

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton checkUpdatesBtn = new JButton("Check for Updates");
        JButton saveBtn = new JButton("Save");
        JButton cancelBtn = new JButton("Cancel");
        buttonsPanel.add(checkUpdatesBtn);
        buttonsPanel.add(cancelBtn);
        buttonsPanel.add(saveBtn);
        add(buttonsPanel, BorderLayout.SOUTH);

        // Load original settings once at startup
        loadSettingsToUI();

        saveBtn.addActionListener(e -> {
            onSave();
            loadSettingsToUI(); // refresh originals on save
        });
        cancelBtn.addActionListener(e -> onCancel());
        checkUpdatesBtn.addActionListener(e -> onCheckForUpdates());
    }

    private void loadSettingsToUI() {
        originalDownloadDir = prefs.get("download_dir", System.getProperty("user.home") + File.separator + "Downloads" + File.separator + "mods");
        originalTheme = prefs.get("app_theme", "Dark");
        originalEmulator = prefs.get("app_emulator", "Microemulator");

        downloadDirField.setText(originalDownloadDir);
        themeSelector.setSelectedItem(originalTheme);
        emulatorSelector.setSelectedItem(originalEmulator);
    }

    private void chooseDownloadDirectory() {
        JFileChooser chooser = new JFileChooser(downloadDirField.getText());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int res = chooser.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File dir = chooser.getSelectedFile();
            downloadDirField.setText(dir.getAbsolutePath());
        }
    }

    private void onSave() {
        prefs.put("download_dir", downloadDirField.getText());

        String theme = (String) themeSelector.getSelectedItem();
        if (theme != null) {
            prefs.put("app_theme", theme);
            if (appUI != null) {
                appUI.setTheme(theme);
            }
        }

        String emulator = (String) emulatorSelector.getSelectedItem();
        if (emulator != null) {
            prefs.put("app_emulator", emulator);
            // notify appUI or others if needed
        }

        JOptionPane.showMessageDialog(this, "Settings saved successfully!", "Save", JOptionPane.INFORMATION_MESSAGE);
    }

    private void onCancel() {
        downloadDirField.setText(originalDownloadDir);
        themeSelector.setSelectedItem(originalTheme);
        emulatorSelector.setSelectedItem(originalEmulator);
    }

    private void onCheckForUpdates() {
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            String latestVersion = null;
            boolean updateAvailable = false;

            @Override
            protected Void doInBackground() throws Exception {
                try {
                    latestVersion = UpdateChecker.getLatestVersion();
                    updateAvailable = UpdateChecker.isUpdateAvailable(Main.APP_VERSION, latestVersion);
                } catch (Exception e) {
                    e.printStackTrace();
                    latestVersion = null; // Indicate error
                }
                return null;
            }

            @Override
            protected void done() {
                if (latestVersion == null) {
                    JOptionPane.showMessageDialog(SettingsPanel.this,
                            "Failed to check for updates. Please check your internet connection.",
                            "Update Check Failed",
                            JOptionPane.ERROR_MESSAGE);
                } else if (updateAvailable) {
                    int dialogResult = JOptionPane.showConfirmDialog(SettingsPanel.this,
                            "A new version (v" + latestVersion + ") is available!\nWould you like to download and install it?",
                            "Update Available",
                            JOptionPane.YES_NO_OPTION);

                    if (dialogResult == JOptionPane.YES_OPTION) {
                        SwingWorker<Void, Void> downloadWorker = new SwingWorker<Void, Void>() {
                            @Override
                            protected Void doInBackground() throws Exception {
                                File tempDir = new File(System.getProperty("java.io.tmpdir"));
                                File downloadedJar = new File(tempDir, "NSOMatrixLauncher-new.jar");

                                UpdateChecker.downloadUpdate(latestVersion, downloadedJar);
                                UpdateChecker.applyUpdateAndRestart(downloadedJar);
                                return null;
                            }

                            @Override
                            protected void done() {
                                try {
                                    get();
                                } catch (Exception ex) {
                                    JOptionPane.showMessageDialog(SettingsPanel.this,
                                            "Update download failed: " + ex.getMessage(),
                                            "Update Error",
                                            JOptionPane.ERROR_MESSAGE);
                                }
                            }
                        };
                        downloadWorker.execute();
                    }
                } else {
                    JOptionPane.showMessageDialog(SettingsPanel.this,
                            "You are running the latest version (v" + Main.APP_VERSION + ").",
                            "No Update Available",
                            JOptionPane.INFORMATION_MESSAGE);
                }
            }
        };
        worker.execute();
    }
}