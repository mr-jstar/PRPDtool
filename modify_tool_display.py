with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

import re

# Remove rpMaxFilesSpinner declaration and add rpMaxFilesDisplayLabel
text = text.replace('private JSpinner rpMaxFilesSpinner;', 'private JLabel rpMaxFilesDisplayLabel;')

# Remove rpMaxFilesSpinner initialization
text = re.sub(r'rpMaxFilesSpinner = new JSpinner.*?0 means unlimited\."\)\);\n', '', text, flags=re.DOTALL)

# Add rpMaxFilesDisplayLabel instantiation in initUI
old_gui = '''        fcGbc.gridx = 0; fcGbc.gridy = 1; fcGbc.weightx = 0.0;
        fileConfigPanel.add(new JLabel("File:"), fcGbc);
        fcGbc.gridx = 1; fcGbc.weightx = 1.0;
        fileConfigPanel.add(outputFileLabel, fcGbc);

        fcGbc.gridx = 0; fcGbc.gridy = 2; fcGbc.weightx = 0.0;
        fileConfigPanel.add(new JLabel("Max files:"), fcGbc);
        fcGbc.gridx = 1; fcGbc.weightx = 1.0;
        fileConfigPanel.add(rpMaxFilesSpinner, fcGbc);'''

new_gui = '''        fcGbc.gridx = 0; fcGbc.gridy = 1; fcGbc.weightx = 0.0;
        fileConfigPanel.add(new JLabel("File:"), fcGbc);
        fcGbc.gridx = 1; fcGbc.weightx = 1.0;
        fileConfigPanel.add(outputFileLabel, fcGbc);

        fcGbc.gridx = 0; fcGbc.gridy = 2; fcGbc.weightx = 0.0;
        fileConfigPanel.add(new JLabel("Max files:"), fcGbc);
        
        JPanel maxFilesPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        rpMaxFilesDisplayLabel = new JLabel(rpFileLimit == 0 ? "unlimited" : String.valueOf(rpFileLimit));
        rpMaxFilesDisplayLabel.setFont(rpMaxFilesDisplayLabel.getFont().deriveFont(Font.BOLD, 11f));
        maxFilesPanel.add(rpMaxFilesDisplayLabel);
        JLabel maxFilesHelp = new JLabel(new HelpIcon());
        maxFilesHelp.setToolTipText(htmlTooltip("Controls how many files are kept on disk during sequential acquisition.\\n\\nFor example, if set to 30, the program acts as a rolling buffer, keeping only the 30 newest files and automatically deleting older ones to save disk space.\\n\\n0 means unlimited (no files are deleted).\\n\\nYou can change this limit in the 'Configure Output' window."));
        maxFilesHelp.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        maxFilesPanel.add(maxFilesHelp);
        
        fcGbc.gridx = 1; fcGbc.weightx = 1.0;
        fileConfigPanel.add(maxFilesPanel, fcGbc);'''
text = text.replace(old_gui, new_gui)

# Revert constructor call for reader
old_reader = '''                () -> new RedPitayaSignalReader(
                        readerConfig,
                        live,
                        3,
                        bufferSize,
                        getOutputBaseDirectory().toPath(),
                        this::onRedPitayaCaptureSaved,
                        RP_LIVE_RESTART_DELAY_MS,
                        rpFilePrefix,
                        (int) rpMaxFilesSpinner.getValue()
                ),'''

new_reader = '''                () -> new RedPitayaSignalReader(
                        readerConfig,
                        live,
                        3,
                        bufferSize,
                        getOutputBaseDirectory().toPath(),
                        this::onRedPitayaCaptureSaved,
                        RP_LIVE_RESTART_DELAY_MS,
                        rpFilePrefix
                ),'''
text = text.replace(old_reader, new_reader)

# Update rpMaxFilesDisplayLabel when changed in limitSpinner
old_spinner_change = '''        limitSpinner.addChangeListener(e -> {
            rpFileLimit = (Integer) limitSpinner.getValue();
            try { configuration.saveValue(RP_FILE_LIMIT, "" + rpFileLimit); } catch (IOException ex) {}
        });'''
new_spinner_change = '''        limitSpinner.addChangeListener(e -> {
            rpFileLimit = (Integer) limitSpinner.getValue();
            if (rpMaxFilesDisplayLabel != null) {
                rpMaxFilesDisplayLabel.setText(rpFileLimit == 0 ? "unlimited" : String.valueOf(rpFileLimit));
            }
            try { configuration.saveValue(RP_FILE_LIMIT, "" + rpFileLimit); } catch (IOException ex) {}
        });'''
text = text.replace(old_spinner_change, new_spinner_change)

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
