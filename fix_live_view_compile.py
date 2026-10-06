with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

import re

# Remove the line actions.add(rpStartLiveViewButton, gbc); and its associated gbc configs if any
text = re.sub(r'\s*gbc\.gridx = 0; gbc\.gridy = 3;\s*actions\.add\(rpStartLiveViewButton, gbc\);', '', text)
text = re.sub(r'\s*actions\.add\(rpStartLiveViewButton, gbc\);', '', text)

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
