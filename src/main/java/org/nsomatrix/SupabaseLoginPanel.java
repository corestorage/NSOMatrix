package org.nsomatrix;

import org.nsomatrix.SupabaseAuthService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Optional;

public class SupabaseLoginPanel extends JPanel {

    private final JTextField emailField = new JTextField(20);
    private final JPasswordField passwordField = new JPasswordField(20);
    private final JButton loginButton = new JButton("Login");
    private final JButton signupButton = new JButton("Sign Up");

    private final JLabel statusLabel = new JLabel(" ");

    private final SupabaseAuthService authService;

    private LoginCallback loginCallback;

    public SupabaseLoginPanel() {
        this.authService = new SupabaseAuthService();
        initUI();
    }

    private void initUI() {
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        gbc.insets = new Insets(10, 10, 10, 10);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.LINE_END;
        add(new JLabel("Email:"), gbc);

        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.LINE_START;
        add(emailField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.LINE_END;
        add(new JLabel("Password:"), gbc);

        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.LINE_START;
        add(passwordField, gbc);

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(loginButton);
        buttonPanel.add(signupButton);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        add(buttonPanel, gbc);

        gbc.gridy = 3;
        add(statusLabel, gbc);

        loginButton.addActionListener(this::onLogin);
        signupButton.addActionListener(this::onSignUp);
    }

    private void onLogin(ActionEvent e) {
        setStatus("Logging in...");
        loginButton.setEnabled(false);
        signupButton.setEnabled(false);

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            Optional<SupabaseAuthService.AuthResponse> resp;

            @Override
            protected Void doInBackground() {
                try {
                    resp = authService.signIn(emailField.getText().trim(), String.valueOf(passwordField.getPassword()));
                } catch (Exception ex) {
                    resp = Optional.empty();
                    ex.printStackTrace();
                }
                return null;
            }

            @Override
            protected void done() {
                loginButton.setEnabled(true);
                signupButton.setEnabled(true);
                if (resp.isPresent()) {
                    setStatus("Login successful!");
                    if (loginCallback != null) {
                        loginCallback.onLoginSuccess(resp.get());
                    }
                } else {
                    setStatus("Login failed. Check credentials and try again.");
                }
            }
        };

        worker.execute();
    }

    private void onSignUp(ActionEvent e) {
        setStatus("Signing up...");
        loginButton.setEnabled(false);
        signupButton.setEnabled(false);

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            Optional<SupabaseAuthService.AuthResponse> resp;

            @Override
            protected Void doInBackground() {
                try {
                    resp = authService.signUp(emailField.getText().trim(), String.valueOf(passwordField.getPassword()));
                } catch (Exception ex) {
                    resp = Optional.empty();
                    ex.printStackTrace();
                }
                return null;
            }

            @Override
            protected void done() {
                loginButton.setEnabled(true);
                signupButton.setEnabled(true);
                if (resp.isPresent()) {
                    setStatus("Sign up successful! You may now log in.");
                } else {
                    setStatus("Sign up failed. Try again.");
                }
            }
        };

        worker.execute();
    }

    public void setLoginCallback(LoginCallback callback) {
        this.loginCallback = callback;
    }

    private void setStatus(String message) {
        statusLabel.setText(message);
    }

    public interface LoginCallback {
        void onLoginSuccess(SupabaseAuthService.AuthResponse authResponse);
    }
}