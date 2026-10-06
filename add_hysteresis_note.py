with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

old_text = '''            case "triggerLevel" ->
                "Trigger level in volts for CH1_PE, CH1_NE, CH2_PE, and CH2_NE modes.\\n"
                + "\\n"
                + "Example:\\n"
                + "CH1_PE with level 0.1 V starts acquisition when IN1 crosses about 0.1 V on a rising edge.";'''

new_text = '''            case "triggerLevel" ->
                "Trigger level in volts for CH1_PE, CH1_NE, CH2_PE, and CH2_NE modes.\\n"
                + "\\n"
                + "Example:\\n"
                + "CH1_PE with level 0.1 V starts acquisition when IN1 crosses about 0.1 V on a rising edge.\\n"
                + "\\n"
                + "Note on Hysteresis:\\n"
                + "Red Pitaya hardware has a built-in trigger hysteresis (typically around 5-10 mV). "
                + "If the signal's peak-to-peak amplitude is extremely small (e.g., background noise of just a few mV), "
                + "the trigger will not arm and will result in a timeout. To observe such tiny signals, use the 'NOW' trigger mode.";'''

text = text.replace(old_text, new_text)

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
