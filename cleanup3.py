with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()
text = text.replace('            JPanel rpPanel = initRedPitayaControls();\n                    left.add(classifyPanel);', '            JPanel rpPanel = initRedPitayaControls();\n            left.add(paramPanel);\n            left.add(rpPanel);\n            left.add(classifyPanel);')
with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
