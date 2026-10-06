with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

# Replace %.4f with %.6f in the trigger labels
text = text.replace('String.format(java.util.Locale.US, "%.4f"', 'String.format(java.util.Locale.US, "%.6f"')

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
