with open('src/redpitaya/RedPitayaSignalReader.java', 'r', encoding='utf-8') as f:
    text = f.read()

import re

# Revert constructor
old_cons1 = '''    public RedPitayaSignalReader(RedPitayaConfig config, boolean live, int consumerCount, int maxSamples) {
        this(config, live, consumerCount, maxSamples, null, null, 250L, "rp_", 0);
    }'''
new_cons1 = '''    public RedPitayaSignalReader(RedPitayaConfig config, boolean live, int consumerCount, int maxSamples) {
        this(config, live, consumerCount, maxSamples, null, null, 250L, "rp_");
    }'''
text = text.replace(old_cons1, new_cons1)

old_cons2 = '''    public RedPitayaSignalReader(
            RedPitayaConfig config,
            boolean live,
            int consumerCount,
            int maxSamples,
            Path saveDirectory,
            Consumer<Path> savedCaptureConsumer,
            long liveRestartDelayMillis,
            String filePrefix,
            int maxFiles
    ) {'''
new_cons2 = '''    public RedPitayaSignalReader(
            RedPitayaConfig config,
            boolean live,
            int consumerCount,
            int maxSamples,
            Path saveDirectory,
            Consumer<Path> savedCaptureConsumer,
            long liveRestartDelayMillis,
            String filePrefix
    ) {'''
text = text.replace(old_cons2, new_cons2)

old_cons_body = '''        this.maxSamplesPerAcquisition = maxSamplesPerAcquisition(this.config);
        this.filePrefix = filePrefix;
        this.maxFiles = maxFiles;
    }
    
    private final int maxFiles;
    private int completedWindows = 0;'''
new_cons_body = '''        this.maxSamplesPerAcquisition = maxSamplesPerAcquisition(this.config);
        this.filePrefix = filePrefix;
    }'''
text = text.replace(old_cons_body, new_cons_body)

old_finish = '''    private void finishWindow() throws IOException {
        closeWriter();
        windowActive = false;
        
        completedWindows++;
        if (maxFiles > 0 && completedWindows >= maxFiles) {
            onceWindowDone = true;
            return;
        }
        
        if (live && !closed) {
            sleepBeforeNextLiveWindow();
        } else {
            onceWindowDone = true;
        }
    }'''
new_finish = '''    private void finishWindow() throws IOException {
        closeWriter();
        windowActive = false;
        if (live && !closed) {
            sleepBeforeNextLiveWindow();
        } else {
            onceWindowDone = true;
        }
    }'''
text = text.replace(old_finish, new_finish)

with open('src/redpitaya/RedPitayaSignalReader.java', 'w', encoding='utf-8') as f:
    f.write(text)
