package org.nsomatrix;

import org.nsomatrix.EmulatorLauncher;
import org.nsomatrix.UI;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class EmuRunPanel extends JPanel {
    private static final File STORAGE_FILE = new File(System.getProperty("user.home"), ".matrix_launcher_games.txt");

    private final UI appUI;
    private final DefaultListModel<GameEntry> gamesListModel = new DefaultListModel<>();
    private final JList<GameEntry> gamesList;
    private final JButton launchBtn;
    private final JButton removeBtn;
    private final JLabel messageLabel;
    private final JPanel emptyStatePanel;

    // Remember last directory used for JFileChooser
    private File lastUsedDir = null;

    public EmuRunPanel(UI ui) {
        this.appUI = ui;

        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(20, 20, 20, 20));

        // Use platform default font for labels and sizes
        Font labelFont = UIManager.getFont("Label.font").deriveFont(Font.BOLD, 18f);
        JLabel gamesLabel = new JLabel("Available Games");
        gamesLabel.setFont(labelFont);
        add(gamesLabel, BorderLayout.NORTH);

        gamesList = new JList<>(gamesListModel);
        gamesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        gamesList.setCellRenderer(new GameEntryCellRenderer());

        JScrollPane scrollPane = new JScrollPane(gamesList);

        emptyStatePanel = createEmptyStatePanel();
        emptyStatePanel.setVisible(gamesListModel.isEmpty());

        JPanel overlayPanel = new JPanel();
        overlayPanel.setLayout(new OverlayLayout(overlayPanel));
        overlayPanel.add(emptyStatePanel);
        overlayPanel.add(scrollPane);

        add(overlayPanel, BorderLayout.CENTER);

        gamesListModel.addListDataListener(new javax.swing.event.ListDataListener() {
            @Override public void intervalAdded(javax.swing.event.ListDataEvent e) { updateEmptyStateVisibility(); }
            @Override public void intervalRemoved(javax.swing.event.ListDataEvent e) { updateEmptyStateVisibility(); }
            @Override public void contentsChanged(javax.swing.event.ListDataEvent e) { updateEmptyStateVisibility(); }
        });
        updateEmptyStateVisibility();

        enableFileDragAndDrop(this);
        enableFileDragAndDrop(gamesList);

        JPanel bottomPanel = new JPanel(new BorderLayout(10, 10));
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));

        launchBtn = new JButton("Launch");
        launchBtn.setEnabled(false);
        launchBtn.addActionListener(e -> launchSelectedGame());
        // Conditional button styling to avoid macOS Aqua painting issues
        if (!UIManager.getLookAndFeel().getName().toLowerCase().contains("aqua")) {
            launchBtn.setBackground(new Color(0, 128, 0));
            launchBtn.setForeground(Color.WHITE);
            launchBtn.setOpaque(true);
            launchBtn.setBorderPainted(false);
        }

        JButton addBtn = new JButton("Add Game");
        addBtn.addActionListener(e -> addGame());

        removeBtn = new JButton("Remove");
        removeBtn.setEnabled(false);
        removeBtn.addActionListener(e -> removeSelectedGame());
        if (!UIManager.getLookAndFeel().getName().toLowerCase().contains("aqua")) {
            removeBtn.setBackground(new Color(200, 0, 0));
            removeBtn.setForeground(Color.WHITE);
            removeBtn.setOpaque(true);
            removeBtn.setBorderPainted(false);
        }

        buttonsPanel.add(addBtn);
        buttonsPanel.add(removeBtn);
        buttonsPanel.add(launchBtn);

        bottomPanel.add(buttonsPanel, BorderLayout.NORTH);

        messageLabel = new JLabel(" ");
        messageLabel.setForeground(Color.GRAY);
        bottomPanel.add(messageLabel, BorderLayout.SOUTH);

        add(bottomPanel, BorderLayout.SOUTH);

        gamesList.addListSelectionListener(e -> updateButtons());

        gamesList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    launchSelectedGame();
                }
            }
        });

        loadGamesFromStorage();
    }

    private JPanel createEmptyStatePanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(50, 20, 20, 20));

        JLabel iconLabel = new JLabel(new DragDropIcon(64, 64));
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        Font font = UIManager.getFont("Label.font").deriveFont(Font.BOLD, 22f);
        JLabel textLabel = new JLabel("Drag and Drop or Select JAR to Launch");
        textLabel.setFont(font);
        textLabel.setForeground(Color.GRAY);
        textLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        textLabel.setBorder(new EmptyBorder(20, 0, 0, 0));

        panel.add(iconLabel);
        panel.add(textLabel);

        return panel;
    }

    private void updateEmptyStateVisibility() {
        emptyStatePanel.setVisible(gamesListModel.isEmpty());
    }

    private void enableFileDragAndDrop(Component comp) {
        new DropTarget(comp, new DropTargetListener() {
            @Override public void dragEnter(DropTargetDragEvent dtde) {
                if (canAcceptDrop(dtde)) dtde.acceptDrag(DnDConstants.ACTION_COPY);
                else dtde.rejectDrag();
            }
            @Override public void dragOver(DropTargetDragEvent dtde) {}
            @Override public void dropActionChanged(DropTargetDragEvent dtde) {}
            @Override public void dragExit(DropTargetEvent dte) {}
            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    if (canAcceptDrop(dtde)) {
                        dtde.acceptDrop(DnDConstants.ACTION_COPY);
                        @SuppressWarnings("unchecked")
                        List<File> droppedFiles = (List<File>) dtde.getTransferable()
                                .getTransferData(DataFlavor.javaFileListFlavor);

                        int launchedCount = 0;
                        for (File f : droppedFiles) {
                            if (isJarFile(f) && f.canRead()) {  // <-- check readability
                                String emulator = appUI.getSelectedEmulator();
                                EmulatorLauncher.launch(emulator, f);

                                if (!containsFile(f)) {
                                    gamesListModel.addElement(new GameEntry(f, 0));
                                    saveGamesToStorage();
                                }
                                launchedCount++;
                            }
                        }
                        if (launchedCount > 0)
                            setMessage("Launched " + launchedCount + " game(s) by drag and drop.");
                        else
                            setMessage("No valid readable JAR files found in drop.");
                        dtde.dropComplete(true);
                    } else {
                        dtde.rejectDrop();
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    setMessage("Error processing dropped files.");
                    dtde.dropComplete(false);
                }
            }
            private boolean canAcceptDrop(DropTargetDragEvent dtde) {
                return dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }
            private boolean canAcceptDrop(DropTargetDropEvent dtde) {
                return dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }
        });
    }

    private boolean isJarFile(File file) {
        return file.isFile() && file.getName().toLowerCase().endsWith(".jar");
    }

    private void addGame() {
        JFileChooser chooser = new JFileChooser();
        if (lastUsedDir != null && lastUsedDir.exists()) {
            chooser.setCurrentDirectory(lastUsedDir);
        }
        chooser.setFileFilter(new FileNameExtensionFilter("JAR Files", "jar"));
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File jar = chooser.getSelectedFile();
            lastUsedDir = jar.getParentFile();
            if (jar.canRead() && !containsFile(jar)) {
                gamesListModel.addElement(new GameEntry(jar, 0));
                saveGamesToStorage();
                setMessage("Added: " + jar.getName());
                gamesList.setSelectedValue(new GameEntry(jar, 0), true);
            } else if (containsFile(jar)) {
                setMessage("Game already in list: " + jar.getName());
                gamesList.setSelectedValue(new GameEntry(jar, 0), true);
            } else {
                setMessage("Cannot read selected file: " + jar.getName());
            }
        }
    }

    private void removeSelectedGame() {
        int idx = gamesList.getSelectedIndex();
        if (idx >= 0) {
            GameEntry removed = gamesListModel.getElementAt(idx);
            gamesListModel.remove(idx);
            saveGamesToStorage();
            setMessage("Removed: " + removed.getFile().getName());
        }
    }

    private void launchSelectedGame() {
        GameEntry selectedEntry = gamesList.getSelectedValue();
        if (selectedEntry != null) {
            String emulator = appUI.getSelectedEmulator();
            EmulatorLauncher.launch(emulator, selectedEntry.getFile());
            setMessage("Launching: " + selectedEntry.getFile().getName());

            // Update last played timestamp
            selectedEntry.setLastPlayedTimestamp(System.currentTimeMillis());
            saveGamesToStorage();
            gamesList.repaint();
        } else {
            setMessage("No game selected to launch.");
        }
    }

    private void updateButtons() {
        boolean selected = gamesList.getSelectedIndex() >= 0;
        launchBtn.setEnabled(selected);
        removeBtn.setEnabled(selected);
    }

    private void setMessage(String message) {
        messageLabel.setText(message);
    }

    private boolean containsFile(File file) {
        try {
            String canonicalPath = file.getCanonicalPath();
            for (int i = 0; i < gamesListModel.size(); i++) {
                if (gamesListModel.get(i).getFile().getCanonicalPath().equals(canonicalPath)) {
                    return true;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    private void saveGamesToStorage() {
        // Use UTF-8 explicitly
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(STORAGE_FILE), StandardCharsets.UTF_8))) {
            for (int i = 0; i < gamesListModel.size(); i++) {
                GameEntry entry = gamesListModel.get(i);
                // Use canonical path for storage
                try {
                    String path = entry.getFile().getCanonicalPath();
                    pw.println(path + "|" + entry.getLastPlayedTimestamp());
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            setMessage("Failed to save games list.");
        }
    }

    private void loadGamesFromStorage() {
        if (!STORAGE_FILE.exists()) return;

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(STORAGE_FILE), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\\|");
                File file = new File(parts[0]);
                long ts = 0L;
                if (parts.length > 1) {
                    try {
                        ts = Long.parseLong(parts[1]);
                    } catch (NumberFormatException ignored) {}
                }
                if (file.exists() && isJarFile(file) && file.canRead()) {
                    GameEntry entry = new GameEntry(file, ts);
                    if (!containsFile(file)) {
                        gamesListModel.addElement(entry);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            setMessage("Failed to load saved games.");
        }
    }

    private static class GameEntry {
        private final File file;
        private long lastPlayedTimestamp;

        public GameEntry(File file, long lastPlayedTimestamp) {
            this.file = file;
            this.lastPlayedTimestamp = lastPlayedTimestamp;
        }

        public File getFile() {
            return file;
        }

        public long getLastPlayedTimestamp() {
            return lastPlayedTimestamp;
        }

        public void setLastPlayedTimestamp(long timestamp) {
            this.lastPlayedTimestamp = timestamp;
        }

        @Override
        public String toString() {
            return file.getName();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            GameEntry that = (GameEntry) other;
            try {
                return this.file.getCanonicalPath().equals(that.file.getCanonicalPath());
            } catch (IOException e) {
                e.printStackTrace();
                return this.file.equals(that.file);
            }
        }

        @Override
        public int hashCode() {
            try {
                return file.getCanonicalPath().hashCode();
            } catch (IOException e) {
                e.printStackTrace();
                return file.hashCode();
            }
        }
    }

    private class GameEntryCellRenderer extends DefaultListCellRenderer {
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof GameEntry) {
                GameEntry ge = (GameEntry) value;
                File file = ge.getFile();

                long sizeKb = (file.length() + 1023) / 1024; // rounded KB

                String lastPlayedStr = ge.getLastPlayedTimestamp() > 0
                        ? dateFormat.format(new Date(ge.getLastPlayedTimestamp()))
                        : "Never";

                label.setText(String.format("<html>%s<br/><small>Size: %d KB | Last Played: %s</small></html>",
                        file.getName(), sizeKb, lastPlayedStr));
            }
            return label;
        }
    }

    private static class DragDropIcon implements Icon {
        private final int width;
        private final int height;

        public DragDropIcon(int width, int height) {
            this.width = width;
            this.height = height;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2d = (Graphics2D) g.create();
            try {
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(Color.LIGHT_GRAY);

                int centerX = x + width / 2;
                int centerY = y + height / 2;

                g2d.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER));
                g2d.drawLine(centerX, y + 10, centerX, y + height - 20);

                Polygon arrowHead = new Polygon();
                arrowHead.addPoint(centerX - 15, y + height - 45);
                arrowHead.addPoint(centerX + 15, y + height - 45);
                arrowHead.addPoint(centerX, y + height - 10);
                g2d.fill(arrowHead);
            } finally {
                g2d.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return width;
        }

        @Override
        public int getIconHeight() {
            return height;
        }
    }
}