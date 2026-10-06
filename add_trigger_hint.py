with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

old_actions_layout = '''        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        actions.add(rpStartOnceButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        actions.add(rpStartLiveButton, gbc);
        gbc.gridx = 1;
        actions.add(rpStopButton, gbc);'''

new_actions_layout = '''        gbc.gridx = 0;
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
        gbc.insets = new java.awt.Insets(2, 4, 2, 4);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        actions.add(rpStartOnceButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 1;
        actions.add(rpStartLiveButton, gbc);
        gbc.gridx = 1;
        actions.add(rpStopButton, gbc);'''

text = text.replace(old_actions_layout, new_actions_layout)

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
