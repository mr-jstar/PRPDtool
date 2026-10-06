with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

import re

# Add declaration ONLY where lastPaintTime is declared
text = re.sub(r'(\s*private long lastPaintTime = 0;\s*)', r'\g<1>            private long lastLiveResetTime = System.currentTimeMillis();\n', text)

# Add logic ONLY in the two places
# One is in createPipelineListener
old_pulses1 = '''                if (Math.abs((dfs - fs) / fs) > 1e-8) {
                    fs = dfs;
                    setParamField("Sampling frequency [Hz]", String.format(Locale.US, "%.12g", fs));
                    JOptionPane.showMessageDialog(
                            PRPDTool.this,
                            "The sampling frequency estimated from data is " + String.format(Locale.US, "%.12g", fs) + " Hz",
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
                histogram.addPulses(pulses);'''

new_pulses1 = '''                if (Math.abs((dfs - fs) / fs) > 1e-8) {
                    fs = dfs;
                    setParamField("Sampling frequency [Hz]", String.format(Locale.US, "%.12g", fs));
                    JOptionPane.showMessageDialog(
                            PRPDTool.this,
                            "The sampling frequency estimated from data is " + String.format(Locale.US, "%.12g", fs) + " Hz",
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
                if (isLiveView && (System.currentTimeMillis() - lastLiveResetTime > 15000)) {
                    histogram.reset();
                    lastLiveResetTime = System.currentTimeMillis();
                }
                histogram.addPulses(pulses);'''
text = text.replace(old_pulses1, new_pulses1)

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
