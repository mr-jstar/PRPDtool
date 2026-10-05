with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace('float size = Math.max(9.0f, currentFont.getSize2D() - 3.0f);', 'float size = Math.max(9.0f, currentFont.getSize2D() - 2.0f);')

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
