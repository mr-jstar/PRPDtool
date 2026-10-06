with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

# Add declaration
text = text.replace('private JSpinner rpFrameCountSpinner;', 'private JSpinner rpFrameCountSpinner;\n    private JSpinner rpMaxFilesSpinner;')

# Add initialization inside initUI (or anywhere near rpFrameCountSpinner initialization)
old_init = 'rpFrameCountSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 1000000, 1));'
new_init = 'rpFrameCountSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 1000000, 1));\n        rpMaxFilesSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 1000000, 1));\n        rpMaxFilesSpinner.setToolTipText(htmlTooltip("Maximum number of files to save during sequential acquisition. 0 means unlimited."));'
text = text.replace(old_init, new_init)

# Fix constructor call
old_reader = '''                () -> new RedPitayaSignalReader(
                        readerConfig,
                        live,
                        3,
                        bufferSize,
                        getOutputBaseDirectory().toPath(),
                        this::onRedPitayaCaptureSaved,
                        RP_LIVE_RESTART_DELAY_MS,
                        rpFilePrefix
                ),'''

new_reader = '''                () -> new RedPitayaSignalReader(
                        readerConfig,
                        live,
                        3,
                        bufferSize,
                        getOutputBaseDirectory().toPath(),
                        this::onRedPitayaCaptureSaved,
                        RP_LIVE_RESTART_DELAY_MS,
                        rpFilePrefix,
                        (int) rpMaxFilesSpinner.getValue()
                ),'''
text = text.replace(old_reader, new_reader)

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
