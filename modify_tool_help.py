with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

import re

old_help = '''            case "startLive" ->
                "Starts continuous data acquisition in a loop.\\n\\n"
                + "Each acquired block is processed and added to the cumulative PRPD histogram. "
                + "Data is saved to files automatically based on the Output Configuration.";'''

new_help = '''            case "startLive" ->
                "Starts sequential data acquisition in a loop.\\n\\n"
                + "Each acquired block is processed and added to the cumulative PRPD histogram. "
                + "Data is saved to files automatically until manually stopped or the 'Max files' limit is reached.";'''

text = text.replace(old_help, new_help)

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
