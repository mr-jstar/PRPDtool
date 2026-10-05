with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace('JButton fitBtn = new JButton("FIT");', 'JButton fitBtn = new JButton("Fit");')

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
