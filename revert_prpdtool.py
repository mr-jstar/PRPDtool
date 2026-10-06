with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

import re

# 1. Remove JButton rpStartLiveViewButton
text = text.replace('private JButton rpStartLiveViewButton;\n', '')
text = text.replace('private JButton rpStartLiveViewButton;', '')

# 2. Remove boolean isLiveView
text = text.replace('private boolean isLiveView = false;\n', '')
text = text.replace('private boolean isLiveView = false;', '')

# 3. Remove startLiveView method
start_live_view_pattern = r'\s*private void startLiveView\(\) \{[\s\S]*?javax\.swing\.JOptionPane\.showMessageDialog\(this, ex\.getMessage\(\), "Error", javax\.swing\.JOptionPane\.ERROR_MESSAGE\);\n        \}\n    \}\n'
text = re.sub(start_live_view_pattern, '\n', text)

# 4. Remove isLiveView = false from startRedPitaya
text = text.replace('private void startRedPitaya(boolean live) {\n        isLiveView = false;', 'private void startRedPitaya(boolean live) {')
text = text.replace('rpStartLiveButton.addActionListener(e -> { isLiveView = false; startRedPitaya(true); });', 'rpStartLiveButton.addActionListener(e -> startRedPitaya(true));')

# 5. Remove rpStartLiveViewButton GUI additions
live_btn_pattern = r'\s*rpStartLiveViewButton = new JButton\("Live view"\);\n\s*rpStartLiveViewButton\.setToolTipText\(htmlTooltip\("Starts a high-speed live view of the PRPD pattern\. Data is NOT saved to disk\. The histogram automatically clears every 15 seconds\."\)\);\n\s*rpStartLiveViewButton\.addActionListener\(e -> startLiveView\(\)\);\n'
text = re.sub(live_btn_pattern, '\n', text)

live_btn_add_pattern = r'\s*buttonsPanel\.add\(rpStartLiveViewButton\);'
text = re.sub(live_btn_add_pattern, '', text)

# 6. Revert listener changes
listener_change_1 = r'\s*private long lastLiveResetTime = System\.currentTimeMillis\(\);\n@Override'
text = re.sub(listener_change_1, '\n            @Override', text)

listener_change_2 = r'\s*// W trybie Live View ignorujemy surowy przebieg, \n\s*// aby nie zapchać pamięci RAM i nie wieszać UI na rysowaniu milionów punktów\.\n\s*if \(\!isLiveView\) \{\n\s*receivedSignalCache\.add\(buffer\);\n\s*interactiveSignalPanel\.addBuffer\(buffer\);\n\s*\}'
text = re.sub(listener_change_2, '\n                receivedSignalCache.add(buffer);\n                interactiveSignalPanel.addBuffer(buffer);', text)

listener_change_3 = r'\s*if \(isLiveView && \(System\.currentTimeMillis\(\) - lastLiveResetTime > 15000\)\) \{\n\s*histogram\.reset\(\);\n\s*lastLiveResetTime = System\.currentTimeMillis\(\);\n\s*\}'
text = re.sub(listener_change_3, '', text)

# Just to be sure for other encoding chars in comments
text = re.sub(r'\s*// W trybie Live View ignorujemy surowy przebieg, \n\s*// aby nie zapcha. pami.ci RAM i nie wiesza. UI na rysowaniu milion.w punkt.w\.\n\s*if \(\!isLiveView\) \{\n\s*receivedSignalCache\.add\(buffer\);\n\s*interactiveSignalPanel\.addBuffer\(buffer\);\n\s*\}', '\n                receivedSignalCache.add(buffer);\n                interactiveSignalPanel.addBuffer(buffer);', text)

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
