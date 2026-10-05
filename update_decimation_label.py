with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace('addFormRow(formPanel, "Decimation", rpDecimationSpinner, helpText("decimation"));', 'addFormRow(formPanel, "Decimation (fs)", rpDecimationSpinner, helpText("decimation"));')

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
