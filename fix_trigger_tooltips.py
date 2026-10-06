with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

import re

old_tooltip_code = '''        String tooltipHTML = htmlTooltip("Wartości te określają sprzętowy filtr szumu. <b>Ustawiane są przez asystenta Auto trigger</b> (powyżej). Możesz je także edytować ręcznie w oknie Red Pitaya Settings (ikona koła zębatego).");
        
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
        levelHelp.setToolTipText(tooltipHTML);'''

new_tooltip_code = '''        String modeTooltipStr = helpText("trigger") + "\\n\\nThis parameter can be calibrated automatically using the Auto trigger assistant above, or set manually in the Red Pitaya Settings window.";
        String levelTooltipStr = helpText("triggerLevel") + "\\n\\nThis parameter can be calibrated automatically using the Auto trigger assistant above, or set manually in the Red Pitaya Settings window.";
        
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        modePanel.add(new JLabel("Trigger mode:"));
        modePanel.add(mainTriggerModeValue);
        JLabel modeHelp = new JLabel(new HelpIcon());
        modeHelp.setToolTipText(htmlTooltip(modeTooltipStr));
        modeHelp.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        modePanel.add(modeHelp);
        
        JPanel levelPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        levelPanel.add(new JLabel("Trigger [V]:"));
        levelPanel.add(mainTriggerLevelValue);
        JLabel levelHelp = new JLabel(new HelpIcon());
        levelHelp.setToolTipText(htmlTooltip(levelTooltipStr));'''

# handle encoding differences in my previous python script that wrote Wartości as Wartoci (iso-8859-1 vs utf-8)
text = re.sub(r'String tooltipHTML = htmlTooltip\("Warto.ci te okre.laj. sprz.towy filtr szumu\. <b>Ustawiane s. przez asystenta Auto trigger</b> \(powy.ej\)\. Mo.esz je tak.e edytowa. r.cznie w oknie Red Pitaya Settings \(ikona ko.a z.batego\)\."\);\s*JPanel modePanel = new JPanel\(new FlowLayout\(FlowLayout\.CENTER, 5, 0\)\);\s*modePanel\.add\(new JLabel\("Trigger mode:"\)\);\s*modePanel\.add\(mainTriggerModeValue\);\s*JLabel modeHelp = new JLabel\(new HelpIcon\(\)\);\s*modeHelp\.setToolTipText\(tooltipHTML\);\s*modeHelp\.setCursor\(java\.awt\.Cursor\.getPredefinedCursor\(java\.awt\.Cursor\.HAND_CURSOR\)\);\s*modePanel\.add\(modeHelp\);\s*JPanel levelPanel = new JPanel\(new FlowLayout\(FlowLayout\.CENTER, 5, 0\)\);\s*levelPanel\.add\(new JLabel\("Trigger \[V\]:"\)\);\s*levelPanel\.add\(mainTriggerLevelValue\);\s*JLabel levelHelp = new JLabel\(new HelpIcon\(\)\);\s*levelHelp\.setToolTipText\(tooltipHTML\);', new_tooltip_code, text)

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
