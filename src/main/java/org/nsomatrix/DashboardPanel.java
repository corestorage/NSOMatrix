
package org.nsomatrix;

import org.nsomatrix.SupabaseStorageClient;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.util.List;

public class DashboardPanel extends JPanel {

    private String userEmail;
    private SupabaseStorageClient storageClient;

    private JLabel userLabel;
    private DefaultListModel<String> fileListModel = new DefaultListModel<>();
    private JList<String> fileList;

    private JButton uploadButton;
    private JButton downloadButton;
    private JButton deleteButton;
    private JButton refreshButton;

    public DashboardPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        userLabel = new JLabel("Logged in as: ");
        add(userLabel, BorderLayout.NORTH);

        fileList = new JList<>(fileListModel);
        JScrollPane scrollPane = new JScrollPane(fileList);
        add(scrollPane, BorderLayout.CENTER);

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        uploadButton = new JButton("Upload");
        downloadButton = new JButton("Download");
        deleteButton = new JButton("Delete");
        refreshButton = new JButton("Refresh");

        buttonsPanel.add(uploadButton);
        buttonsPanel.add(downloadButton);
        buttonsPanel.add(deleteButton);
        buttonsPanel.add(refreshButton);

        add(buttonsPanel, BorderLayout.SOUTH);

        uploadButton.addActionListener(e -> onUpload());
        refreshButton.addActionListener(e -> fetchFileList());
        downloadButton.addActionListener(e -> onDownload());
        deleteButton.addActionListener(e -> onDelete());
    }

    public void setUserEmail(String email) {
        this.userEmail = email;
        userLabel.setText("Logged in as: " + email);
    }

    public void setStorageClient(SupabaseStorageClient storageClient) {
        this.storageClient = storageClient;
        fetchFileList();
    }

    public void clearState() {
        userLabel.setText("Logged out.");
        fileListModel.clear();
        storageClient = null;
    }

    private void fetchFileList() {
        if (storageClient == null) return;

        fileListModel.clear();

        SwingWorker<List<String>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                return storageClient.listFiles();
            }

            @Override
            protected void done() {
                try {
                    List<String> files = get();
                    fileListModel.clear();
                    if (files.isEmpty()) {
                        fileListModel.addElement("<no files>");
                    } else {
                        files.forEach(fileListModel::addElement);
                    }
                } catch (Exception e) {
                    // Print full stack trace to console for debugging
                    e.printStackTrace();

                    // Show detailed error message to user
                    String msg = e.getMessage();
                    if (e.getCause() != null) {
                        msg += "\nCause: " + e.getCause().getMessage();
                    }

                    JOptionPane.showMessageDialog(DashboardPanel.this,
                            "Failed to fetch file list:\n" + msg,
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    // onUpload, onDownload and onDelete methods unchanged, or similarly add try/catch with e.printStackTrace()
    private void onUpload() {
        if (storageClient == null) return;

        JFileChooser chooser = new JFileChooser();
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = chooser.getSelectedFile();

            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() throws Exception {
                    storageClient.uploadFile(selectedFile);
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                        fetchFileList();
                        JOptionPane.showMessageDialog(DashboardPanel.this,
                                "Upload completed.", "Success", JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception e) {
                        e.printStackTrace();
                        JOptionPane.showMessageDialog(DashboardPanel.this,
                                "Upload failed:\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            };
            worker.execute();
        }
    }

    private void onDownload() {
        if (storageClient == null) return;

        String selected = fileList.getSelectedValue();
        if (selected == null || selected.equals("<no files>")) {
            JOptionPane.showMessageDialog(this,
                    "Please select a file first.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(selected));
        int result = chooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File saveFile = chooser.getSelectedFile();

            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() throws Exception {
                    storageClient.downloadFile(selected, saveFile);
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                        JOptionPane.showMessageDialog(DashboardPanel.this,
                                "Download completed.", "Success", JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception e) {
                        e.printStackTrace();
                        JOptionPane.showMessageDialog(DashboardPanel.this,
                                "Download failed:\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            };
            worker.execute();
        }
    }

    private void onDelete() {
        if (storageClient == null) return;

        String selected = fileList.getSelectedValue();
        if (selected == null || selected.equals("<no files>")) {
            JOptionPane.showMessageDialog(this,
                    "Please select a file first.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete '" + selected + "'?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() throws Exception {
                    storageClient.deleteFile(selected);
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                        fetchFileList();
                        JOptionPane.showMessageDialog(DashboardPanel.this,
                                "Deleted successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception e) {
                        e.printStackTrace();
                        JOptionPane.showMessageDialog(DashboardPanel.this,
                                "Delete failed:\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            };
            worker.execute();
        }
    }
}