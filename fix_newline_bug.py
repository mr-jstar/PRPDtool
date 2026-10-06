with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

# Fix broken java strings
broken_str_1 = '''String modeTooltipStr = helpText("trigger") + "

This parameter can be calibrated automatically using the Auto trigger assistant above, or set manually in the Red Pitaya Settings window.";'''
fixed_str_1 = 'String modeTooltipStr = helpText("trigger") + "\\n\\nThis parameter can be calibrated automatically using the Auto trigger assistant above, or set manually in the Red Pitaya Settings window.";'
text = text.replace(broken_str_1, fixed_str_1)

broken_str_2 = '''String levelTooltipStr = helpText("triggerLevel") + "

This parameter can be calibrated automatically using the Auto trigger assistant above, or set manually in the Red Pitaya Settings window.";'''
fixed_str_2 = 'String levelTooltipStr = helpText("triggerLevel") + "\\n\\nThis parameter can be calibrated automatically using the Auto trigger assistant above, or set manually in the Red Pitaya Settings window.";'
text = text.replace(broken_str_2, fixed_str_2)

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
