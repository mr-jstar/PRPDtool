with open('src/prpdtool/RedPitayaTriggerDialog.java', 'r', encoding='utf-8') as f:
    text = f.read()

import re

old_toolbar = '''        toolbar.add(referenceButton);
        toolbar.add(defectButton);
        toolbar.add(new JLabel("Mode"));'''

new_toolbar = '''        toolbar.add(referenceButton);
        toolbar.add(defectButton);
        
        JLabel procedureHelp = new JLabel(new PRPDTool.HelpIcon());
        String procedureText = "Calibration Procedure.\\n\\n"
            + "1. Connect your setup with NO discharges active, then click 'Start: reference' to measure the background noise floor.\\n"
            + "2. Turn on the high voltage to activate discharges, then click 'Start: defect'.\\n"
            + "3. The algorithm will automatically place the golden trigger line between the noise and the discharges.\\n"
            + "4. Review the golden line and click 'OK' to apply this trigger level to the main program.";
        procedureHelp.setToolTipText(PRPDTool.htmlTooltip(procedureText));
        procedureHelp.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        toolbar.add(procedureHelp);
        
        toolbar.add(new JLabel("Mode"));'''

text = text.replace(old_toolbar, new_toolbar)

with open('src/prpdtool/RedPitayaTriggerDialog.java', 'w', encoding='utf-8') as f:
    f.write(text)
