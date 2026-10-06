with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

import re

# Add isLiveView and button
text = text.replace('private JButton rpStartOnceButton;', 'private JButton rpStartOnceButton;\n    private JButton rpStartLiveViewButton;\n    private boolean isLiveView = false;')

# Instantiate button
old_buttons = '''        rpStartLiveButton = new JButton("Start sequence");
        rpStartLiveButton.setToolTipText(htmlTooltip(helpText("startLive")));
        rpStartLiveButton.addActionListener(e -> startRedPitaya(true));
        rpStopButton = new JButton("Stop RP");'''
new_buttons = '''        rpStartLiveButton = new JButton("Start sequence");
        rpStartLiveButton.setToolTipText(htmlTooltip(helpText("startLive")));
        rpStartLiveButton.addActionListener(e -> { isLiveView = false; startRedPitaya(true); });
        
        rpStartLiveViewButton = new JButton("Live view");
        rpStartLiveViewButton.setToolTipText(htmlTooltip("Starts a high-speed live view of the PRPD pattern. Data is NOT saved to disk. The histogram automatically clears every 15 seconds."));
        rpStartLiveViewButton.addActionListener(e -> startLiveView());
        
        rpStopButton = new JButton("Stop RP");'''
text = text.replace(old_buttons, new_buttons)

# Add to layout
old_layout = '''        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 1;
        actions.add(rpStartLiveButton, gbc);
        gbc.gridx = 1;
        actions.add(rpStopButton, gbc);'''
new_layout = '''        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 1;
        actions.add(rpStartLiveButton, gbc);
        gbc.gridx = 1;
        actions.add(rpStopButton, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        actions.add(rpStartLiveViewButton, gbc);'''
text = text.replace(old_layout, new_layout)

# Update startRedPitaya
text = text.replace('private void startRedPitaya(boolean live) {', 'private void startRedPitaya(boolean live) {\n        isLiveView = false;')

# Add startLiveView method
new_method = '''
    private void startLiveView() {
        if (rpParams == null) return;
        redpitaya.RedPitayaConfig config = rpParams.config();
        
        double durationS = 0.5;
        long totalSamples = (long) (config.sampleRate() * durationS);
        if (totalSamples < config.normalizedFrameSize()) {
            totalSamples = config.normalizedFrameSize();
        }
        config = config.forTotalSamples(totalSamples);
        
        try {
            saveRedPitayaConfig(config);
            isLiveView = true;
            startPipeline(true);
        } catch (Exception ex) {
            javax.swing.JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }
'''
text = text.replace('private void startRedPitaya(boolean live) {', new_method + '\n    private void startRedPitaya(boolean live) {')

# Modify getRedPitayaData
old_reader = '''                () -> new redpitaya.RedPitayaSignalReader(
                        readerConfig,
                        live,
                        3,
                        bufferSize,
                        getOutputBaseDirectory().toPath(),
                        this::onRedPitayaCaptureSaved,
                        RP_LIVE_RESTART_DELAY_MS,
                        rpFilePrefix
                ),'''
new_reader = '''                () -> new redpitaya.RedPitayaSignalReader(
                        readerConfig,
                        live,
                        3,
                        bufferSize,
                        isLiveView ? null : getOutputBaseDirectory().toPath(),
                        this::onRedPitayaCaptureSaved,
                        isLiveView ? 0L : RP_LIVE_RESTART_DELAY_MS,
                        rpFilePrefix
                ),'''
text = text.replace(old_reader, new_reader)

# Add reset to listener
old_pulses = '''            private long lastPaintTime = 0;
            
            @Override
            public void preExtract(Buffer buffer) {'''
new_pulses = '''            private long lastPaintTime = 0;
            private long lastLiveResetTime = System.currentTimeMillis();
            
            @Override
            public void preExtract(Buffer buffer) {'''
text = text.replace(old_pulses, new_pulses)

old_add = 'histogram.addPulses(pulses);'
new_add = '''                if (isLiveView) {
                    if (System.currentTimeMillis() - lastLiveResetTime > 15000) {
                        histogram.reset();
                        lastLiveResetTime = System.currentTimeMillis();
                    }
                }
                histogram.addPulses(pulses);'''
text = text.replace(old_add, new_add)

# Disable/Enable button in start/stop
text = text.replace('rpStartLiveButton.setEnabled(false);', 'rpStartLiveButton.setEnabled(false);\n            if(rpStartLiveViewButton != null) rpStartLiveViewButton.setEnabled(false);')
text = text.replace('rpStartLiveButton.setEnabled(true);', 'rpStartLiveButton.setEnabled(true);\n            if(rpStartLiveViewButton != null) rpStartLiveViewButton.setEnabled(true);')


with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
