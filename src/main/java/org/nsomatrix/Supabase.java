package org.nsomatrix;

import javax.swing.*;
import java.awt.*;

public class Supabase extends JFrame {

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel container = new JPanel(cardLayout);

    private final AccountPanel accountPanel = new AccountPanel();

    public Supabase() {
        super("Supabase Swing File Storage");

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);

        container.add(accountPanel, "ACCOUNT");
        setContentPane(container);

        cardLayout.show(container, "ACCOUNT");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Supabase app = new Supabase();
            app.setVisible(true);
        });
    }
}
