with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

import re

# 1. Start Record Button block
text = re.sub(r'(?s)\s*startRecordButton = new JButton.*?recBtns\.add\(startRecordButton\);', '', text)
text = re.sub(r'(?s)\s*stopRecordButton = new JButton.*?recBtns\.add\(stopRecordButton\);', '', text)
text = re.sub(r'(?s)\s*recordedData = new FileOutputStream.*?status\.setText\(ex\.getMessage\(\)\);\s*\}', '', text)
text = re.sub(r'(?s)\s*JPanel recBtns = new JPanel\(new GridLayout\(1, 2, 3, 3\)\);.*?recordPanel\.add\(recBtns\);', '', text)

# Just remove all remaining startRecordButton and stopRecordButton strings if they are alone
text = re.sub(r'^\s*startRecordButton = new JButton\(.*?\);\s*\n', '', text, flags=re.MULTILINE)
text = re.sub(r'^\s*stopRecordButton = new JButton\(.*?\);\s*\n', '', text, flags=re.MULTILINE)
text = re.sub(r'^\s*recBtns\.add\(.*?\);\s*\n', '', text, flags=re.MULTILINE)

# Remove the recording blocks inside bufferRead
text = re.sub(r'(?s)\s*if \(realTimeData && recordedData != null && recordedData\.isOpen\(\)\) \{\s*if \(recordedMB < recordLimit\).*?catch \(IOException ex\) \{\s*JOptionPane\.showConfirmDialog\([\s\S]*?\}\s*\} else \{\s*JOptionPane\.showConfirmDialog\([\s\S]*?stopRecorder\(\);\s*recordSizeLabel\.setText\([\s\S]*?\}\s*\}', '', text)
text = re.sub(r'(?s)\s*if \(realTimeData && recordedData != null && recordedData\.isOpen\(\)\) \{.*?catch \(IOException ex\) \{.*?stopRecorder\(\);\s*\}\s*\} else \{.*?stopRecorder\(\);\s*\}', '', text)

# Just fallback to regex for anything inside bufferRead that mentions recordedData
text = re.sub(r'(?s)\s*if \(realTimeData && recordedData != null && recordedData\.isOpen\(\)\) \{.*?\}\s*\} else \{.*?\}\s*\}', '', text)

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
