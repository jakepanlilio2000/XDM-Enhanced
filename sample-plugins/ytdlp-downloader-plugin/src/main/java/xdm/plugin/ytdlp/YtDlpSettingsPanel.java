package xdm.plugin.ytdlp;

import xdm.plugin.api.NotificationLevel;
import xdm.plugin.api.PluginContext;
import xdm.plugin.api.ui.SettingsPanelContributor;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;

/**
 * Settings panel contributor for configuring yt-dlp path, formats, and timeouts.
 */
public class YtDlpSettingsPanel implements SettingsPanelContributor {

    private final YtDlpConfig config;
    private JTextField txtExecutable;
    private JComboBox<String> cmbFormat;
    private JCheckBox chkExtractAudio;
    private JTextField txtOutputDir;
    private JSpinner spnTimeout;
    private JLabel lblStatus;

    public YtDlpSettingsPanel(YtDlpConfig config) {
        this.config = config;
    }

    @Override
    public String getTitle() {
        return "yt-dlp Settings";
    }

    @Override
    public JComponent createPanel(PluginContext context) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(12, 16, 12, 16));

        // 1. Executable Path
        JLabel lblExec = new JLabel("yt-dlp Executable Path:");
        lblExec.setFont(lblExec.getFont().deriveFont(Font.BOLD));
        lblExec.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(lblExec);
        panel.add(Box.createRigidArea(new Dimension(0, 4)));

        JPanel execRow = new JPanel(new BorderLayout(8, 0));
        execRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        txtExecutable = new JTextField(config.getExecutablePath());
        JButton btnBrowseExec = new JButton("Browse...");
        JButton btnTest = new JButton("Test");

        btnBrowseExec.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Select yt-dlp Executable");
            if (chooser.showOpenDialog(panel) == JFileChooser.APPROVE_OPTION) {
                txtExecutable.setText(chooser.getSelectedFile().getAbsolutePath());
                testExecutable();
            }
        });

        btnTest.addActionListener(e -> testExecutable());

        JPanel execBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        execBtns.add(btnBrowseExec);
        execBtns.add(btnTest);

        execRow.add(txtExecutable, BorderLayout.CENTER);
        execRow.add(execBtns, BorderLayout.EAST);
        panel.add(execRow);

        // Status Label for Executable Test
        lblStatus = new JLabel("Enter executable path and click Test to verify.");
        lblStatus.setFont(lblStatus.getFont().deriveFont(Font.PLAIN, 11f));
        lblStatus.setForeground(Color.GRAY);
        lblStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(Box.createRigidArea(new Dimension(0, 4)));
        panel.add(lblStatus);

        panel.add(Box.createRigidArea(new Dimension(0, 16)));

        // 2. Format Preference
        JLabel lblFmt = new JLabel("Format Preference:");
        lblFmt.setFont(lblFmt.getFont().deriveFont(Font.BOLD));
        lblFmt.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(lblFmt);
        panel.add(Box.createRigidArea(new Dimension(0, 4)));

        String[] formats = new String[] {
                "bestvideo+bestaudio/best",
                "bestvideo[height<=1080]+bestaudio/best",
                "bestvideo[height<=720]+bestaudio/best",
                "bestaudio/best",
                "worst"
        };
        cmbFormat = new JComboBox<>(formats);
        cmbFormat.setEditable(true);
        cmbFormat.setSelectedItem(config.getFormatPreference());
        cmbFormat.setAlignmentX(Component.LEFT_ALIGNMENT);
        cmbFormat.setMaximumSize(new Dimension(Integer.MAX_VALUE, cmbFormat.getPreferredSize().height));
        panel.add(cmbFormat);

        panel.add(Box.createRigidArea(new Dimension(0, 12)));

        // 3. Audio Extraction Option
        chkExtractAudio = new JCheckBox("Extract Audio to MP3 (-x --audio-format mp3)", config.isExtractAudio());
        chkExtractAudio.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(chkExtractAudio);

        panel.add(Box.createRigidArea(new Dimension(0, 16)));

        // 4. Default Output Directory
        JLabel lblOut = new JLabel("Custom Output Directory (blank to use XDM default):");
        lblOut.setFont(lblOut.getFont().deriveFont(Font.BOLD));
        lblOut.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(lblOut);
        panel.add(Box.createRigidArea(new Dimension(0, 4)));

        JPanel outRow = new JPanel(new BorderLayout(8, 0));
        outRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        txtOutputDir = new JTextField(config.getOutputDirectory());
        JButton btnBrowseOut = new JButton("Browse...");
        btnBrowseOut.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle("Select Output Directory");
            if (chooser.showOpenDialog(panel) == JFileChooser.APPROVE_OPTION) {
                txtOutputDir.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        outRow.add(txtOutputDir, BorderLayout.CENTER);
        outRow.add(btnBrowseOut, BorderLayout.EAST);
        panel.add(outRow);

        panel.add(Box.createRigidArea(new Dimension(0, 16)));

        // 5. Process Timeout
        JPanel timeoutRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        timeoutRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel lblTimeout = new JLabel("Process Timeout (seconds):");
        spnTimeout = new JSpinner(new SpinnerNumberModel(config.getTimeoutSeconds(), 30, 86400, 60));
        timeoutRow.add(lblTimeout);
        timeoutRow.add(spnTimeout);
        panel.add(timeoutRow);

        return panel;
    }

    private void testExecutable() {
        String path = txtExecutable.getText();
        YtDlpExecutableValidator.ValidationResult result = YtDlpExecutableValidator.validate(path);
        if (result.isValid()) {
            lblStatus.setText("Valid: " + result.getVersion());
            lblStatus.setForeground(new Color(0x2E, 0x7D, 0x32)); // Dark green
        } else {
            lblStatus.setText("Error: " + result.getErrorMessage());
            lblStatus.setForeground(new Color(0xD3, 0x2F, 0x2F)); // Red
        }
    }

    @Override
    public void saveSettings() {
        if (txtExecutable != null) {
            config.setExecutablePath(txtExecutable.getText());
        }
        if (cmbFormat != null) {
            Object selected = cmbFormat.getSelectedItem();
            if (selected != null) config.setFormatPreference(selected.toString().trim());
        }
        if (chkExtractAudio != null) {
            config.setExtractAudio(chkExtractAudio.isSelected());
        }
        if (txtOutputDir != null) {
            config.setOutputDirectory(txtOutputDir.getText());
        }
        if (spnTimeout != null) {
            config.setTimeoutSeconds((Integer) spnTimeout.getValue());
        }
        config.save();
    }
}

