with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

import re

# We will just fix the specific unescaped quotes in PRPDTool.java
text = text.replace('problems.add("  Channels   zmień na "IN1+IN2" (aktualnie: "" + channels + "")");', 
                    'problems.add("  Channels   zmień na \\"IN1+IN2\\" (aktualnie: \\"" + channels + "\\")");')

text = text.replace('problems.add("  Visual   zmień na "IN1" (IN2 jest zarezerwowany jako referencja 50 Hz)");', 
                    'problems.add("  Visual   zmień na \\"IN1\\" (IN2 jest zarezerwowany jako referencja 50 Hz)");')

text = text.replace('+ "Aby korzystać z "Use HW Phase Ref (CH2)", zmień<br>"', 
                    '+ "Aby korzystać z \\"Use HW Phase Ref (CH2)\\", zmień<br>"')

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
