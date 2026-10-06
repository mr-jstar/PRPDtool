with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

# Update spinner limits
old_spinner = 'JSpinner limitSpinner = new JSpinner(new SpinnerNumberModel(rpFileLimit, 0, 9999, 1));'
new_spinner = 'JSpinner limitSpinner = new JSpinner(new SpinnerNumberModel(Math.max(1, rpFileLimit), 1, 9999, 1));'
text = text.replace(old_spinner, new_spinner)

# Update config loading
old_config = '''        try {
            rpFileLimit = Integer.parseInt(configuration.getValue(RP_FILE_LIMIT).trim());
        } catch (Exception ex) {
        }'''
new_config = '''        try {
            rpFileLimit = Integer.parseInt(configuration.getValue(RP_FILE_LIMIT).trim());
            if (rpFileLimit < 1) rpFileLimit = 30;
        } catch (Exception ex) {
        }'''
text = text.replace(old_config, new_config)

# Update label format
old_label = 'rpMaxFilesDisplayLabel = new JLabel(rpFileLimit == 0 ? "unlimited" : String.valueOf(rpFileLimit));'
new_label = 'rpMaxFilesDisplayLabel = new JLabel(String.valueOf(rpFileLimit));'
text = text.replace(old_label, new_label)

old_label_change = 'rpMaxFilesDisplayLabel.setText(rpFileLimit == 0 ? "unlimited" : String.valueOf(rpFileLimit));'
new_label_change = 'rpMaxFilesDisplayLabel.setText(String.valueOf(rpFileLimit));'
text = text.replace(old_label_change, new_label_change)

# Update help text
old_help = '0 means unlimited (no files are deleted).\\n\\n'
new_help = ''
text = text.replace(old_help, new_help)

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
