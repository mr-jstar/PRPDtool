with open('src/prpdtool/RedPitayaTriggerDialog.java', 'r', encoding='utf-8') as f:
    text = f.read()

import re

# 1. Remove theme combobox definition
text = re.sub(r'    private final JComboBox<String> theme = new JComboBox<>\(new String\[\]\{"Light", "Dark"\}\);\s*\n', '', text)

# 2. Remove adding theme to toolbar
text = text.replace('        toolbar.add(new JLabel("Theme"));\n        toolbar.add(theme);\n', '')

# 3. Remove theme listener
text = text.replace('        theme.addActionListener(e -> updateTheme());\n', '')

# 4. Update updateTheme()
old_update_theme = '''    private void updateTheme() {
        boolean dark = theme.getSelectedIndex() == 1;
        referencePlot.setDarkTheme(dark);
        defectPlot.setDarkTheme(dark);
    }'''
new_update_theme = '''    private void updateTheme() {
        boolean dark = PRPDConstants.isDarkTheme();
        referencePlot.setDarkTheme(dark);
        defectPlot.setDarkTheme(dark);
    }'''
text = text.replace(old_update_theme, new_update_theme)

with open('src/prpdtool/RedPitayaTriggerDialog.java', 'w', encoding='utf-8') as f:
    f.write(text)
