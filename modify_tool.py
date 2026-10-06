with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

import re

# Add spinner field
text = text.replace('private JSpinner rpVisualMaxFreqSpinner;', 'private JSpinner rpVisualMaxFreqSpinner;\n    private JSpinner rpMaxFilesSpinner;')

# Initialize spinner
text = text.replace('rpVisualMaxFreqSpinner = new JSpinner(new SpinnerNumberModel(200.0, 1.0, 500.0, 10.0));', 'rpVisualMaxFreqSpinner = new JSpinner(new SpinnerNumberModel(200.0, 1.0, 500.0, 10.0));\n        rpMaxFilesSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 1000000, 1));\n        rpMaxFilesSpinner.setToolTipText(htmlTooltip("Maximum number of files to save during sequential acquisition. 0 means unlimited."));')

# Add to GUI
old_gui = '''        fcGbc.gridx = 0; fcGbc.gridy = 1; fcGbc.weightx = 0.0;
        fileConfigPanel.add(new JLabel("File:"), fcGbc);
        fcGbc.gridx = 1; fcGbc.weightx = 1.0;
        fileConfigPanel.add(outputFileLabel, fcGbc);

        fcGbc.gridx = 0; fcGbc.gridy = 2; fcGbc.gridwidth = 2;
        fcGbc.fill = GridBagConstraints.NONE; fcGbc.anchor = GridBagConstraints.CENTER;
        fcGbc.insets = new Insets(6, 4, 2, 4);
        fileConfigPanel.add(configureOutputBtn, fcGbc);'''

new_gui = '''        fcGbc.gridx = 0; fcGbc.gridy = 1; fcGbc.weightx = 0.0;
        fileConfigPanel.add(new JLabel("File:"), fcGbc);
        fcGbc.gridx = 1; fcGbc.weightx = 1.0;
        fileConfigPanel.add(outputFileLabel, fcGbc);

        fcGbc.gridx = 0; fcGbc.gridy = 2; fcGbc.weightx = 0.0;
        fileConfigPanel.add(new JLabel("Max files:"), fcGbc);
        fcGbc.gridx = 1; fcGbc.weightx = 1.0;
        fileConfigPanel.add(rpMaxFilesSpinner, fcGbc);

        fcGbc.gridx = 0; fcGbc.gridy = 3; fcGbc.gridwidth = 2;
        fcGbc.fill = GridBagConstraints.NONE; fcGbc.anchor = GridBagConstraints.CENTER;
        fcGbc.insets = new Insets(6, 4, 2, 4);
        fileConfigPanel.add(configureOutputBtn, fcGbc);'''
text = text.replace(old_gui, new_gui)

# Rename button
text = text.replace('rpStartLiveButton = new JButton("Start live");', 'rpStartLiveButton = new JButton("Start sequence");')

# Pass max files
old_reader = '''        RedPitayaSignalReader reader = new RedPitayaSignalReader(
                config,
                live,
                consumerCount,
                maxPoints,
                saveDir,
                this::setLastDataFile,
                1000,
                rpParams.getFilePrefix()
        );'''

new_reader = '''        RedPitayaSignalReader reader = new RedPitayaSignalReader(
                config,
                live,
                consumerCount,
                maxPoints,
                saveDir,
                this::setLastDataFile,
                1000,
                rpParams.getFilePrefix(),
                (int) rpMaxFilesSpinner.getValue()
        );'''
text = text.replace(old_reader, new_reader)

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
