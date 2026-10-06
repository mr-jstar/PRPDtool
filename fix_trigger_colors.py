with open('src/prpdtool/RedPitayaTriggerDialog.java', 'r', encoding='utf-8') as f:
    text = f.read()

import re

old_colors = '''            Color background = darkTheme ? new Color(18, 18, 18) : Color.WHITE;
            Color foreground = darkTheme ? new Color(235, 235, 235) : Color.BLACK;
            Color axis = darkTheme ? new Color(150, 150, 150) : Color.GRAY;
            Color grid = darkTheme ? new Color(55, 55, 55) : new Color(225, 225, 225);
            Color labelBackground = darkTheme ? new Color(40, 40, 40) : new Color(255, 255, 230);'''

new_colors = '''            Color background = darkTheme ? new Color(30, 30, 30) : Color.WHITE;
            Color foreground = darkTheme ? Color.LIGHT_GRAY : Color.BLACK;
            Color axis = darkTheme ? Color.GRAY : Color.BLACK;
            Color grid = darkTheme ? new Color(60, 60, 60) : new Color(230, 230, 230);
            Color labelBackground = darkTheme ? new Color(50, 50, 50) : new Color(255, 255, 230);'''

text = text.replace(old_colors, new_colors)

# PlotPanel's background itself (the JPanel background)
old_bg_setup = 'setBackground(darkTheme ? new Color(18, 18, 18) : Color.WHITE);'
new_bg_setup = 'setBackground(darkTheme ? new Color(30, 30, 30) : Color.WHITE);'
text = text.replace(old_bg_setup, new_bg_setup)

with open('src/prpdtool/RedPitayaTriggerDialog.java', 'w', encoding='utf-8') as f:
    f.write(text)
