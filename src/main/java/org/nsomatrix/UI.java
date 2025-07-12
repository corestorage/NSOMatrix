package org.nsomatrix;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import org.nsomatrix.EmuRunPanel;
import org.nsomatrix.ModsPanel;
import org.nsomatrix.AccountPanel;
import org.nsomatrix.SettingsPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.prefs.Preferences;

public class UI {
    private final JFrame frame;
    private final JPanel contentPanel;
    private final HashMap<String, JButton> tabButtons = new HashMap<>();
    private final JLabel statusBar = new JLabel(" Ready.");
    private final Preferences prefs;

    public UI() {
        prefs = Preferences.userNodeForPackage(UI.class);

        // Initialize Look & Feel with saved theme or dark by default
        setTheme(prefs.get("app_theme", "dark"));

        frame = new JFrame("Matrix Launcher");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(900, 600);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout());

        contentPanel = createContentPanel();
        JPanel sidebar = createSidebar();

        statusBar.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        updateStatusBarColor(prefs.get("app_theme", "dark"));

        frame.add(sidebar, BorderLayout.WEST);
        frame.add(contentPanel, BorderLayout.CENTER);
        frame.add(statusBar, BorderLayout.SOUTH);
    }

    public void setTheme(String themeName) {
        try {
            switch (themeName.toLowerCase()) {
                case "light":
                    UIManager.setLookAndFeel(new FlatLightLaf());
                    break;
                case "dark":
                default:
                    UIManager.setLookAndFeel(new FlatDarkLaf());
                    break;
            }

            for (Window window : Window.getWindows()) {
                Dimension size = window.getSize();
                Point location = window.getLocation();

                SwingUtilities.updateComponentTreeUI(window);

                window.setSize(size);
                window.setLocation(location);

                if (window instanceof JFrame) {
                    JFrame f = (JFrame) window;
                    f.validate();
                    f.repaint();
                }
            }

            prefs.put("app_theme", themeName);
            updateStatusBarColor(themeName);

            setStatus("Theme set to: " + themeName);
        } catch (Exception e) {
            e.printStackTrace();
            setStatus("Failed to set theme: " + e.getMessage());
        }
    }

    private void updateStatusBarColor(String themeName) {
        if ("light".equalsIgnoreCase(themeName)) {
            statusBar.setForeground(Color.DARK_GRAY);
        } else {
            statusBar.setForeground(Color.LIGHT_GRAY);
        }
    }

    public String getSelectedEmulator() {
        return prefs.get("app_emulator", "Microemulator");
    }

    private JPanel createSidebar() {
        JPanel sidebar = new JPanel(new GridLayout(0, 1, 0, 5));
        sidebar.setBackground(new Color(30, 30, 30));
        sidebar.setPreferredSize(new Dimension(180, 0));
        sidebar.setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 5));

        String[] tabs = {"EMU-RUN", "MODS", "ACCOUNT", "SETTINGS"};
        Icon[] icons = {
                UIManager.getIcon("FileView.computerIcon"),
                UIManager.getIcon("FileView.directoryIcon"),
                UIManager.getIcon("FileChooser.detailsViewIcon"),
                UIManager.getIcon("FileChooser.listViewIcon")
        };

        for (int i = 0; i < tabs.length; i++) {
            String tab = tabs[i];
            JButton btn = new JButton(tab, icons[i]);
            btn.setFocusPainted(false);
            btn.setHorizontalAlignment(SwingConstants.LEFT);
            btn.setPreferredSize(new Dimension(180, 50));
            btn.setBackground(new Color(50, 50, 50));
            btn.setForeground(Color.WHITE);
            btn.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 10));
            btn.setIconTextGap(15);

            btn.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    if (!btn.getBackground().equals(new Color(70, 70, 70))) {
                        btn.setBackground(new Color(65, 65, 65));
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (!btn.getBackground().equals(new Color(70, 70, 70))) {
                        btn.setBackground(new Color(50, 50, 50));
                    }
                }
            });

            sidebar.add(btn);
            tabButtons.put(tab, btn);

            btn.addActionListener(e -> {
                switchPanel(tab);
                tabButtons.forEach((k, b) -> b.setBackground(new Color(50, 50, 50)));
                btn.setBackground(new Color(70, 70, 70));
                setStatus("Selected tab: " + tab);
            });
        }
        tabButtons.get("EMU-RUN").doClick();

        return sidebar;
    }

    private JPanel createContentPanel() {
        JPanel content = new JPanel(new CardLayout());

        content.add(new EmuRunPanel(this), "EMU-RUN");
        content.add(new ModsPanel(), "MODS");
        content.add(new AccountPanel(), "ACCOUNT");
        content.add(new SettingsPanel(this), "SETTINGS");

        return content;
    }

    private void switchPanel(String name) {
        CardLayout cl = (CardLayout) contentPanel.getLayout();
        cl.show(contentPanel, name);
    }

    public void setStatus(String message) {
        SwingUtilities.invokeLater(() -> statusBar.setText(" " + message));
    }

    public void show() {
        frame.setVisible(true);
    }
}