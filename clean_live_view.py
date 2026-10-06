with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

import re

# Remove ALL existing isLiveView blocks around histogram.addPulses
text = re.sub(r'(\s*if \(isLiveView\) \{\s*if \(System\.currentTimeMillis\(\) - lastLiveResetTime > 15000\) \{\s*histogram\.reset\(\);\s*lastLiveResetTime = System\.currentTimeMillis\(\);\s*\}\s*\})', '', text)

# Remove ALL existing lastLiveResetTime declarations
text = re.sub(r'\s*private long lastLiveResetTime = System\.currentTimeMillis\(\);\s*', '\n', text)

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
