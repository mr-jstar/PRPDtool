with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

# Fix startLiveView
old_live = '''
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
new_live = '''
    private void startLiveView() {
        if (autoscaleCb != null) {
            autoscaleCb.setSelected(true);
        }
        currentSessionFiles.clear();
        inBatchMode = false;
        realTimeData = true;
        try {
            redpitaya.RedPitayaConfig config = readRedPitayaConfig();
            
            double durationS = 0.5;
            long totalSamples = (long) (config.sampleRate() * durationS);
            if (totalSamples < config.normalizedFrameSize()) {
                totalSamples = config.normalizedFrameSize();
            }
            config = config.forTotalSamples(totalSamples);
            
            saveRedPitayaConfig(config);
            isLiveView = true;
            getRedPitayaData(config, true);
            rpStopButton.setEnabled(true);
            dataSource.setText("Red Pitaya (" + config.host + ":" + config.port + ", live view)");
        } catch (Exception ex) {
            realTimeData = false;
            javax.swing.JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }
'''
text = text.replace(old_live, new_live)

# Fix lastLiveResetTime missing
import re
text = re.sub(r'(\s*private long lastPaintTime = 0;\s*)(?!private long lastLiveResetTime)', r'\g<1>            private long lastLiveResetTime = System.currentTimeMillis();\n', text)

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
