with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

# 1. Rename "Trigger" to "Trigger mode" in Red Pitaya Settings
text = text.replace('addFormRow(formPanel, "Trigger", rpTriggerCombo, helpText("trigger"));', 'addFormRow(formPanel, "Trigger mode", rpTriggerCombo, helpText("trigger"));')

# 2. Add class variables for main window display
import re
text = re.sub(r'(private JButton rpTriggerIn2Button;\n)', r'\1    private JLabel mainTriggerModeValue;\n    private JLabel mainTriggerLevelValue;\n', text)

# 3. Add listeners to sync values and replace the old triggerHint with the new triggerDisplayPanel
old_actions_layout = '''        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.insets = new java.awt.Insets(10, 4, 12, 4);
        javax.swing.JLabel triggerHint = new javax.swing.JLabel(
                "<html><div style='text-align: center; font-size: 9px; color: #888888;'>"
                + "<b>Trigger level</b> oraz <b>source</b> filtrują sygnał fizycznie w sprzęcie.<br>"
                + "Zabezpiecza to komputer przed zalaniem bezwartościowym szumem z sieci.<br>"
                + "<i>Użyj przycisków Auto trigger, aby skalibrować je automatycznie.</i>"
                + "</div></html>", javax.swing.SwingConstants.CENTER);
        actions.add(triggerHint, gbc);
        gbc.insets = new java.awt.Insets(2, 4, 2, 4);'''

new_actions_layout = '''        mainTriggerModeValue = new JLabel(rpTriggerCombo.getSelectedItem().toString());
        mainTriggerModeValue.setFont(mainTriggerModeValue.getFont().deriveFont(java.awt.Font.BOLD));
        mainTriggerLevelValue = new JLabel(String.format("%.4f", ((Number)rpTriggerLevelSpinner.getValue()).doubleValue()));
        mainTriggerLevelValue.setFont(mainTriggerLevelValue.getFont().deriveFont(java.awt.Font.BOLD));
        
        rpTriggerCombo.addItemListener(e -> mainTriggerModeValue.setText(e.getItem().toString()));
        rpTriggerLevelSpinner.addChangeListener(e -> mainTriggerLevelValue.setText(String.format("%.4f", ((Number)rpTriggerLevelSpinner.getValue()).doubleValue())));

        JPanel triggerDisplayPanel = new JPanel(new GridLayout(2, 1, 2, 2));
        
        String tooltipHTML = htmlTooltip("Wartości te określają sprzętowy filtr szumu. <b>Ustawiane są przez asystenta Auto trigger</b> (powyżej). Możesz je także edytować ręcznie w oknie Red Pitaya Settings (ikona koła zębatego).");
        
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        modePanel.add(new JLabel("Trigger mode:"));
        modePanel.add(mainTriggerModeValue);
        JLabel modeHelp = new JLabel(new HelpIcon());
        modeHelp.setToolTipText(tooltipHTML);
        modeHelp.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        modePanel.add(modeHelp);
        
        JPanel levelPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        levelPanel.add(new JLabel("Trigger [V]:"));
        levelPanel.add(mainTriggerLevelValue);
        JLabel levelHelp = new JLabel(new HelpIcon());
        levelHelp.setToolTipText(tooltipHTML);
        levelHelp.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        levelPanel.add(levelHelp);
        
        triggerDisplayPanel.add(modePanel);
        triggerDisplayPanel.add(levelPanel);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.insets = new java.awt.Insets(6, 4, 10, 4);
        actions.add(triggerDisplayPanel, gbc);
        gbc.insets = new java.awt.Insets(2, 4, 2, 4);'''

text = text.replace(old_actions_layout, new_actions_layout)

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
