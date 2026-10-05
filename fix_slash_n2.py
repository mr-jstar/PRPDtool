with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace('\\n    private String htmlTooltip(String text) {', '    private String htmlTooltip(String text) {')

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
