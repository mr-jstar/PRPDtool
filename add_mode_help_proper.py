with open('src/prpdtool/RedPitayaTriggerDialog.java', 'r', encoding='utf-8') as f:
    text = f.read()

import re

old_toolbar = '''        toolbar.add(new JLabel("Mode"));
        toolbar.add(mode);
        toolbar.add(new JLabel("Trigger [V]"));'''

new_toolbar = '''        toolbar.add(new JLabel("Mode"));
        toolbar.add(mode);
        
        JLabel modeHelp = new JLabel(new PRPDTool.HelpIcon());
        String modeHelpText = "Trigger algorithm mode.\\n\\n"
            + "ABS (Absolute): Analyzes the absolute value of the signal. The trigger will always be positive (CHx_PE). "
            + "Best for symmetric discharges where you want to trigger on any large pulse regardless of its polarity.\\n\\n"
            + "+/- (Signed): Analyzes the raw, unrectified signal. The algorithm checks both positive and negative peaks "
            + "and selects the direction with the best signal-to-noise ratio. Best when discharges have a clear dominant polarity.";
        modeHelp.setToolTipText(PRPDTool.htmlTooltip(modeHelpText));
        modeHelp.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        toolbar.add(modeHelp);
        
        toolbar.add(new JLabel("Trigger [V]"));'''

text = text.replace(old_toolbar, new_toolbar)

with open('src/prpdtool/RedPitayaTriggerDialog.java', 'w', encoding='utf-8') as f:
    f.write(text)
