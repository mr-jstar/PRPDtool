with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

import re

old_listeners = '''        rpTriggerCombo.addItemListener(e -> mainTriggerModeValue.setText(e.getItem().toString()));
        rpTriggerLevelSpinner.addChangeListener(e -> mainTriggerLevelValue.setText(String.format("%.4f", ((Number)rpTriggerLevelSpinner.getValue()).doubleValue())));'''

new_listeners = '''        rpTriggerCombo.addActionListener(e -> mainTriggerModeValue.setText(rpTriggerCombo.getSelectedItem().toString()));
        rpTriggerLevelSpinner.addChangeListener(e -> mainTriggerLevelValue.setText(String.format(java.util.Locale.US, "%.4f", ((Number)rpTriggerLevelSpinner.getValue()).doubleValue())));
        
        if (rpTriggerLevelSpinner.getEditor() instanceof javax.swing.JSpinner.DefaultEditor) {
            javax.swing.JSpinner.DefaultEditor editor = (javax.swing.JSpinner.DefaultEditor) rpTriggerLevelSpinner.getEditor();
            editor.getTextField().getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                private void update() {
                    try {
                        double v = Double.parseDouble(editor.getTextField().getText().replace(',', '.'));
                        mainTriggerLevelValue.setText(String.format(java.util.Locale.US, "%.4f", v));
                    } catch (Exception ex) {}
                }
                public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
            });
        }'''

text = text.replace(old_listeners, new_listeners)

# Also fix the initial text for mainTriggerLevelValue to use US locale
text = text.replace('mainTriggerLevelValue = new JLabel(String.format("%.4f", ((Number)rpTriggerLevelSpinner.getValue()).doubleValue()));', 'mainTriggerLevelValue = new JLabel(String.format(java.util.Locale.US, "%.4f", ((Number)rpTriggerLevelSpinner.getValue()).doubleValue()));')


with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
