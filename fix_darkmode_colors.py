with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

old_dir_color = '''        outputDirLabel = new JLabel("...");
        outputDirLabel.setFont(outputDirLabel.getFont().deriveFont(Font.ITALIC, 10f));
        outputDirLabel.setForeground(Color.DARK_GRAY);'''

new_dir_color = '''        outputDirLabel = new JLabel("...");
        outputDirLabel.setFont(outputDirLabel.getFont().deriveFont(Font.ITALIC, 10f));'''
text = text.replace(old_dir_color, new_dir_color)

old_file_color = '''        outputFileLabel = new JLabel("...");
        outputFileLabel.setFont(outputFileLabel.getFont().deriveFont(Font.BOLD, 11f));
        outputFileLabel.setForeground(Color.BLUE.darker());'''

new_file_color = '''        outputFileLabel = new JLabel("...");
        outputFileLabel.setFont(outputFileLabel.getFont().deriveFont(Font.BOLD, 11f));'''
text = text.replace(old_file_color, new_file_color)

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
