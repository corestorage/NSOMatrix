package org.nsomatrix;

import org.nsomatrix.SupabaseStorageClient;
import org.nsomatrix.DashboardPanel;
import org.nsomatrix.SupabaseLoginPanel;

import javax.swing.*;
import java.awt.*;

public class AccountPanel extends JPanel {

    private final CardLayout cardLayout = new CardLayout();

    private final SupabaseLoginPanel loginPanel = new SupabaseLoginPanel();
    private final DashboardPanel dashboardPanel = new DashboardPanel();

    private String accessToken;
    private String refreshToken;
    private String userEmail;
    private String userId;

    private final SupabaseStorageClient storageClient = new SupabaseStorageClient();

    public AccountPanel() {
        setLayout(cardLayout);
        add(loginPanel, "LOGIN");
        add(dashboardPanel, "DASHBOARD");

        loginPanel.setLoginCallback(authResponse -> {
            this.accessToken = authResponse.access_token;
            this.refreshToken = authResponse.refresh_token;
            this.userEmail = authResponse.user.email;
            this.userId = authResponse.user.id;

            // Set tokens and user ID in storage client
            storageClient.setAccessToken(accessToken);
            storageClient.setUserId(userId);

            dashboardPanel.setUserEmail(userEmail);
            dashboardPanel.setStorageClient(storageClient);

            cardLayout.show(AccountPanel.this, "DASHBOARD");
        });

        cardLayout.show(this, "LOGIN");
    }

    public void logout() {
        accessToken = null;
        refreshToken = null;
        userEmail = null;
        userId = null;
        storageClient.setAccessToken(null);
        storageClient.setUserId(null);
        dashboardPanel.clearState();
        cardLayout.show(this, "LOGIN");
    }

    public boolean isLoggedIn() {
        return accessToken != null;
    }

    public String getAccessToken() {
        return accessToken;
    }
}