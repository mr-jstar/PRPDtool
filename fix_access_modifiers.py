with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace('private static class HelpIcon implements Icon', 'public static class HelpIcon implements Icon')
text = text.replace('private static String htmlTooltip(String text)', 'public static String htmlTooltip(String text)')

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
