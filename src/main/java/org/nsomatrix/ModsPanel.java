package org.nsomatrix;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.prefs.Preferences;

import org.nsomatrix.UI;

public class ModsPanel extends JPanel {
    private final DefaultListModel<RemoteMod> remoteModsModel = new DefaultListModel<>();
    private final JList<RemoteMod> modsList;

    private final JButton fetchModsBtn;
    private final JButton downloadSelectedBtn;
    private final JComboBox<String> sourceSelector;
    private final JTextField downloadDirField;
    private final JButton chooseDownloadDirBtn;
    private final JLabel statusLabel;

    private final JTextField searchField;

    private final JPanel emptyStatePanel;

    // Using the same Preferences node as your SettingsPanel (UI.class)
    private final Preferences prefs = Preferences.userNodeForPackage(UI.class);
    private Path downloadDir;

    private final List<RemoteMod> allMods = new ArrayList<>();

    private final String GITHUB_API_URL = "https://api.github.com/repos/cloudkore/matrix/contents/data/MODs";
    private final String INTERNET_ARCHIVE_METADATA_URL = "https://archive.org/metadata/nsomtxmods";

    public ModsPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        // Load download directory from preferences or default
        String savedDir = prefs.get("download_dir",
                Paths.get(System.getProperty("user.home"), "Downloads", "mods").toString());
        downloadDir = Paths.get(savedDir);

        // Make sure directory exists, attempt to create if it does not
        if (!Files.exists(downloadDir)) {
            try {
                Files.createDirectories(downloadDir);
            } catch (Exception e) {
                e.printStackTrace();
                // fallback to user home if creation fails
                downloadDir = Paths.get(System.getProperty("user.home"));
            }
        }

        JLabel titleLabel = new JLabel("Mods");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        add(titleLabel, BorderLayout.NORTH);

        searchField = new JTextField(30);
        searchField.setToolTipText("Search mods...");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filterMods(); }
            public void removeUpdate(DocumentEvent e) { filterMods(); }
            public void changedUpdate(DocumentEvent e) { filterMods(); }
        });

        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        JPanel sourcePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        sourcePanel.add(new JLabel("Source:"));
        sourceSelector = new JComboBox<>(new String[]{"GitHub", "Internet Archive"});
        sourcePanel.add(sourceSelector);
        topPanel.add(sourcePanel, BorderLayout.WEST);
        topPanel.add(searchField, BorderLayout.CENTER);
        add(topPanel, BorderLayout.PAGE_START);

        modsList = new JList<>(remoteModsModel);
        modsList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        modsList.setCellRenderer(new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof RemoteMod) {
                    label.setText(((RemoteMod) value).name);
                }
                return label;
            }
        });

        JScrollPane scrollPane = new JScrollPane(modsList);

        emptyStatePanel = new JPanel();
        emptyStatePanel.setLayout(new BoxLayout(emptyStatePanel, BoxLayout.Y_AXIS));
        emptyStatePanel.setOpaque(false);
        emptyStatePanel.setBorder(new EmptyBorder(50, 20, 20, 20));

        JLabel placeholderLabel = new JLabel(" Select Source to fetch MODs");
        placeholderLabel.setForeground(Color.GRAY);
        placeholderLabel.setFont(new Font("SansSerif", Font.BOLD, 22));
        placeholderLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        placeholderLabel.setBorder(new EmptyBorder(20, 0, 0, 0));

        emptyStatePanel.add(placeholderLabel);

        JPanel overlayPanel = new JPanel();
        overlayPanel.setLayout(new OverlayLayout(overlayPanel));
        overlayPanel.add(emptyStatePanel);
        overlayPanel.add(scrollPane);

        add(overlayPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout(10, 10));
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        fetchModsBtn = new JButton("Fetch MODs");
        fetchModsBtn.addActionListener(e -> fetchMods());

        downloadSelectedBtn = new JButton("Download");
        downloadSelectedBtn.setEnabled(false);
        downloadSelectedBtn.addActionListener(e -> downloadSelectedMods());

        buttonsPanel.add(fetchModsBtn);
        buttonsPanel.add(downloadSelectedBtn);

        bottomPanel.add(buttonsPanel, BorderLayout.NORTH);

        JPanel downloadDirPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        downloadDirField = new JTextField(downloadDir.toAbsolutePath().toString(), 30);
        chooseDownloadDirBtn = new JButton("Choose Download Folder");
        chooseDownloadDirBtn.addActionListener(e -> chooseDownloadDirectory());
        downloadDirPanel.add(new JLabel("Download Folder:"));
        downloadDirPanel.add(downloadDirField);
        downloadDirPanel.add(chooseDownloadDirBtn);
        bottomPanel.add(downloadDirPanel, BorderLayout.CENTER);

        statusLabel = new JLabel(" ");
        statusLabel.setForeground(Color.GRAY);
        bottomPanel.add(statusLabel, BorderLayout.SOUTH);

        add(bottomPanel, BorderLayout.SOUTH);

        modsList.addListSelectionListener(e -> downloadSelectedBtn.setEnabled(!modsList.isSelectionEmpty()));

        remoteModsModel.addListDataListener(new javax.swing.event.ListDataListener() {
            public void intervalAdded(javax.swing.event.ListDataEvent e) { updateEmptyStateVisibility(); }
            public void intervalRemoved(javax.swing.event.ListDataEvent e) { updateEmptyStateVisibility(); }
            public void contentsChanged(javax.swing.event.ListDataEvent e) { updateEmptyStateVisibility(); }
        });
        updateEmptyStateVisibility();

        prefs.addPreferenceChangeListener(evt -> {
            if ("download_dir".equals(evt.getKey())) {
                SwingUtilities.invokeLater(() -> {
                    String newDir = evt.getNewValue();
                    if (newDir != null) {
                        Path newPath = Paths.get(newDir);
                        if (!Files.exists(newPath)) {
                            try { Files.createDirectories(newPath); } catch (Exception ignored) {}
                        }
                        downloadDir = newPath;
                        downloadDirField.setText(downloadDir.toAbsolutePath().toString());
                        setStatus("Download folder updated to: " + downloadDir.toAbsolutePath());
                    }
                });
            }
        });
    }

    private void updateEmptyStateVisibility() {
        emptyStatePanel.setVisible(remoteModsModel.isEmpty());
    }

    private void filterMods() {
        String filter = searchField.getText().trim().toLowerCase();
        remoteModsModel.clear();

        if (filter.isEmpty()) {
            allMods.forEach(remoteModsModel::addElement);
        } else {
            allMods.stream()
                    .filter(mod -> mod.name.toLowerCase().contains(filter))
                    .forEach(remoteModsModel::addElement);
        }
    }

    private void fetchMods() {
        setStatus("Fetching mod list...");
        allMods.clear();
        remoteModsModel.clear();

        String source = (String) sourceSelector.getSelectedItem();
        String apiUrl = "Internet Archive".equals(source) ? INTERNET_ARCHIVE_METADATA_URL : GITHUB_API_URL;

        fetchModsBtn.setEnabled(false);

        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .header("Accept", "application/vnd.github.v3+json")
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    SwingUtilities.invokeLater(() -> {
                        setStatus("Failed to fetch mods: HTTP " + response.statusCode());
                        fetchModsBtn.setEnabled(true);
                    });
                    return;
                }

                ObjectMapper mapper = new ObjectMapper();
                List<RemoteMod> fetchedMods = new ArrayList<>();

                if ("Internet Archive".equals(source)) {
                    JsonNode root = mapper.readTree(response.body());
                    JsonNode filesArray = root.get("files");
                    if (filesArray != null && filesArray.isArray()) {
                        for (JsonNode fileNode : filesArray) {
                            String name = fileNode.get("name").asText();
                            if (name.toLowerCase().endsWith(".jar")) {
                                String downloadUrl = "https://archive.org/download/nsomtxmods/" + name;
                                fetchedMods.add(new RemoteMod(name, downloadUrl));
                            }
                        }
                    }
                } else {
                    JsonNode root = mapper.readTree(response.body());
                    for (JsonNode node : root) {
                        String name = node.get("name").asText();
                        String downloadUrl = node.get("download_url").asText();
                        if (name.toLowerCase().endsWith(".jar")) {
                            fetchedMods.add(new RemoteMod(name, downloadUrl));
                        }
                    }
                }

                SwingUtilities.invokeLater(() -> {
                    allMods.clear();
                    allMods.addAll(fetchedMods);
                    filterMods();
                    setStatus("Fetched " + fetchedMods.size() + " mods from " + source);
                    fetchModsBtn.setEnabled(true);
                });

            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    setStatus("Error fetching mods: " + e.getMessage());
                    fetchModsBtn.setEnabled(true);
                });
            }
        });
    }

    private void downloadSelectedMods() {
        List<RemoteMod> selectedMods = modsList.getSelectedValuesList();
        setStatus("Downloading " + selectedMods.size() + " mod(s)...");

        Executors.newSingleThreadExecutor().submit(() -> {
            int successCount = 0;
            for (RemoteMod mod : selectedMods) {
                try {
                    Path targetPath = downloadDir.resolve(mod.name);
                    File targetFile = targetPath.toFile();
                    if (targetFile.exists()) {
                        continue;
                    }
                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(mod.downloadUrl))
                            .build();
                    HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                    if (response.statusCode() == 200) {
                        try (InputStream in = response.body();
                             FileOutputStream fos = new FileOutputStream(targetFile)) {
                            in.transferTo(fos);
                        }
                        successCount++;
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
            final int downloaded = successCount;
            SwingUtilities.invokeLater(() -> setStatus("Downloaded " + downloaded + " mod(s)."));
        });
    }

    private void chooseDownloadDirectory() {
        JFileChooser chooser = new JFileChooser(downloadDir.toFile());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int res = chooser.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File dir = chooser.getSelectedFile();
            if (dir.exists() && dir.isDirectory()) {
                downloadDir = dir.toPath();
                downloadDirField.setText(downloadDir.toAbsolutePath().toString());
                setStatus("Download folder set to: " + downloadDir.toAbsolutePath());

                prefs.put("download_dir", downloadDir.toAbsolutePath().toString());
            } else {
                setStatus("Invalid directory selected.");
            }
        }
    }

    private void setStatus(String message) {
        statusLabel.setText(message);
    }

    private static class RemoteMod {
        public final String name;
        public final String downloadUrl;

        public RemoteMod(String name, String downloadUrl) {
            this.name = name;
            this.downloadUrl = downloadUrl;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}