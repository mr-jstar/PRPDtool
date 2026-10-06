with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

old_init = '''        rpFrameCountSpinner = new JSpinner(new SpinnerNumberModel(configInt(RP_FRAME_COUNT, 1), 1, Integer.MAX_VALUE, 1));'''
new_init = '''        rpFrameCountSpinner = new JSpinner(new SpinnerNumberModel(configInt(RP_FRAME_COUNT, 1), 1, Integer.MAX_VALUE, 1));
        rpMaxFilesSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 1000000, 1));
        rpMaxFilesSpinner.setToolTipText(htmlTooltip("Maximum number of files to save during sequential acquisition. 0 means unlimited."));'''
text = text.replace(old_init, new_init)

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
