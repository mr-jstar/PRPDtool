with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace('JButton configureOutputBtn = new JButton("Configure Output...");', 'JButton configureOutputBtn = new JButton("Configure Output");')

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
