package com.qynl.injector.launcher;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.util.List;

/**
 * Qyn-L Injector launcher. Run {@code java -jar qynl-injector.jar} — the same
 * jar is then passed to the game via {@code -javaagent}, so the agent ships
 * with the launcher.
 */
public final class LauncherMain {

    private LauncherMain() {
    }

    public static void main(String[] args) {
        File agentJar = findAgentJar();
        if (agentJar == null) {
            System.err.println("[Qyn-L] cannot locate the injector jar — run via `java -jar qynl-injector.jar`.");
            System.exit(1);
        }
        SwingUtilities.invokeLater(() -> new LauncherFrame(agentJar).setVisible(true));
    }

    static File findAgentJar() {
        try {
            java.net.URL loc = LauncherMain.class.getProtectionDomain().getCodeSource().getLocation();
            if (loc == null) {
                return null;
            }
            File f = new File(loc.toURI());
            if (f.isFile() && f.getName().endsWith(".jar")) {
                return f;
            }
            // Dev run from classes dir: fall back to the built jar.
            File built = new File(f, "build/libs/qynl-injector-1.0.0.jar");
            if (built.isFile()) {
                return built;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    static File defaultGameDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home", ".");
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null) {
                return new File(appData, ".minecraft");
            }
        }
        if (os.contains("mac")) {
            return new File(home, "Library/Application Support/minecraft");
        }
        return new File(home, ".minecraft");
    }

    private static final class LauncherFrame extends JFrame {

        private final File agentJar;
        private final JTextField gameDirField = new JTextField();
        private final JTextField versionField = new JTextField("1.8.9");
        private final JTextField usernameField = new JTextField(System.getProperty("user.name"));
        private final JTextField memoryField = new JTextField("2G");
        private final JTextField javaField = new JTextField(defaultJava());
        private final JTextArea logArea = new JTextArea();
        private final JButton launchButton = new JButton("Launch");

        LauncherFrame(File agentJar) {
            super("Qyn-L Injector");
            this.agentJar = agentJar;
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setMinimumSize(new Dimension(720, 560));
            setSize(820, 620);
            setLocationRelativeTo(null);

            JPanel form = new JPanel(new GridBagLayout());
            form.setBorder(new EmptyBorder(14, 16, 8, 16));
            form.setBackground(BG);
            GridBagConstraints g = new GridBagConstraints();
            g.insets = new Insets(4, 4, 4, 4);
            g.fill = GridBagConstraints.HORIZONTAL;
            g.weightx = 1.0;

            gameDirField.setText(defaultGameDir().getAbsolutePath());
            JButton browse = new JButton("Browse");
            browse.addActionListener(e -> {
                JFileChooser chooser = new JFileChooser(gameDirField.getText());
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                    gameDirField.setText(chooser.getSelectedFile().getAbsolutePath());
                }
            });

            addRow(form, g, 0, "Game directory", gameDirField, browse);
            addRow(form, g, 1, "Version", versionField, null);
            addRow(form, g, 2, "Username", usernameField, null);
            addRow(form, g, 3, "Memory", memoryField, null);
            addRow(form, g, 4, "Java (8 for 1.8.9)", javaField, null);

            JPanel actions = new JPanel(new BorderLayout(8, 0));
            actions.setOpaque(false);
            launchButton.addActionListener(e -> launch());
            JButton attachButton = new JButton("Attach to running game");
            attachButton.addActionListener(e -> attach());
            actions.add(launchButton, BorderLayout.WEST);
            actions.add(attachButton, BorderLayout.EAST);
            g.gridy = 5;
            g.gridx = 0;
            g.gridwidth = 2;
            form.add(actions, g);

            logArea.setEditable(false);
            logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            logArea.setBackground(BG);
            logArea.setForeground(TEXT);
            logArea.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            JScrollPane scroll = new JScrollPane(logArea);
            scroll.setBorder(BorderFactory.createLineBorder(LINE));

            add(form, BorderLayout.NORTH);
            add(scroll, BorderLayout.CENTER);

            log("Qyn-L Injector " + agentJar.getAbsolutePath());
            log("Game dir auto-detected. Launch 1.8.9 once with the official launcher if the version is missing.");
        }

        private void addRow(JPanel panel, GridBagConstraints g, int y, String label, JTextField field, JButton extra) {
            g.gridy = y;
            g.gridx = 0;
            g.gridwidth = 1;
            g.weightx = 0.0;
            JLabel l = new JLabel(label);
            l.setForeground(TEXT);
            panel.add(l, g);
            g.gridx = 1;
            g.weightx = 1.0;
            panel.add(field, g);
            if (extra != null) {
                g.gridx = 2;
                g.weightx = 0.0;
                panel.add(extra, g);
            }
        }

        private void launch() {
            launchButton.setEnabled(false);
            new SwingWorker<Void, String>() {
                @Override
                protected Void doInBackground() {
                    try {
                        MinecraftLauncher.launch(new File(gameDirField.getText()), versionField.getText().trim(),
                                usernameField.getText().trim(), javaField.getText().trim(),
                                memoryField.getText().trim(), agentJar, LauncherFrame.this::log);
                        publish("[launch] game process started — the injector boots on the first in-game tick.");
                    } catch (Exception ex) {
                        publish("[error] " + ex.getMessage());
                        for (StackTraceElement el : ex.getStackTrace()) {
                            publish("    at " + el);
                        }
                    }
                    return null;
                }

                @Override
                protected void process(List<String> chunks) {
                    for (String line : chunks) {
                        log(line);
                    }
                }

                @Override
                protected void done() {
                    launchButton.setEnabled(true);
                }
            }.execute();
        }

        private void attach() {
            if (!AttachHelper.available()) {
                log("[error] attach API not available — run the launcher on a full JDK.");
                return;
            }
            try {
                List<String[]> jvms = AttachHelper.listJvms();
                String[] options = new String[jvms.size()];
                for (int i = 0; i < jvms.size(); i++) {
                    options[i] = jvms.get(i)[0] + "  " + jvms.get(i)[1];
                }
                if (options.length == 0) {
                    log("[attach] no running JVMs found.");
                    return;
                }
                String picked = (String) JOptionPane.showInputDialog(this, "Pick the Minecraft process",
                        "Attach", JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
                if (picked == null) {
                    return;
                }
                String pid = picked.substring(0, picked.indexOf(' '));
                AttachHelper.attach(pid, agentJar.getAbsolutePath());
                log("[attach] agent loaded into " + pid + " — the client boots on the next tick.");
            } catch (Exception ex) {
                log("[attach] failed: " + ex.getMessage());
            }
        }

        private void log(String line) {
            logArea.append(line + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        }
    }

    private static final Color BG = new Color(0x14161A);
    private static final Color TEXT = new Color(0xE6E6E6);
    private static final Color LINE = new Color(0x2A2D33);

    private static String defaultJava() {
        return System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
    }

    static {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
    }
}
