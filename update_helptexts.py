with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

# Replace gain1
old_gain1 = '''            case "gain1" ->
                "Red Pitaya input range for channel IN1.\\n"
                + "\\n"
                + "LV: for small signals.\\n"
                + "HV: for larger input voltages.\\n"
                + "\\n"
                + "Board: STEMlab 125-14 Pro Z7020 Gen 2.";'''

new_gain1 = '''            case "gain1" ->
                "Red Pitaya input range configuration for channel IN1.\\n"
                + "\\n"
                + "Available jumper settings:\\n"
                + "- LV (Low Voltage): ±1V range. Use for weak signals requiring high ADC precision.\\n"
                + "- HV (High Voltage): ±20V range. Use for strong signals to prevent clipping/saturation.\\n"
                + "\\n"
                + "Note: This must physically match the jumper position on the STEMlab 125-14 board.";'''
text = text.replace(old_gain1, new_gain1)

# Replace gain2
old_gain2 = '''            case "gain2" ->
                "Red Pitaya input range for channel IN2.\\n"
                + "\\n"
                + "LV: for small signals.\\n"
                + "HV: for larger input voltages.\\n"
                + "\\n"
                + "Board: STEMlab 125-14 Pro Z7020 Gen 2.";'''

new_gain2 = '''            case "gain2" ->
                "Red Pitaya input range configuration for channel IN2.\\n"
                + "\\n"
                + "Available jumper settings:\\n"
                + "- LV (Low Voltage): ±1V range. Use for weak signals requiring high ADC precision.\\n"
                + "- HV (High Voltage): ±20V range. Use for strong signals to prevent clipping/saturation.\\n"
                + "\\n"
                + "Note: This must physically match the jumper position on the STEMlab 125-14 board.";'''
text = text.replace(old_gain2, new_gain2)

# Replace averaging
old_averaging = '''            case "averaging" ->
                "Red Pitaya hardware averaging, if it is supported by the API used on the board.";'''

new_averaging = '''            case "averaging" ->
                "Red Pitaya hardware decimation averaging.\\n"
                + "\\n"
                + "How it works when Decimation > 1:\\n"
                + "- Disabled: Discards the extra samples (aliases high frequencies).\\n"
                + "- Enabled: Averages the extra samples inside the FPGA before outputting.\\n"
                + "\\n"
                + "Effect:\\n"
                + "Enabling acts as a digital low-pass filter, reducing high-frequency noise and increasing effective resolution (ENOB). Highly recommended for high decimation values.";'''
text = text.replace(old_averaging, new_averaging)

# Replace frameSize
old_frameSize = '''            case "frameSize" ->
                "Number of samples per channel in one TCP frame.\\n"
                + "\\n"
                + "The same size is used when writing frames to an RPPR file.";'''

new_frameSize = '''            case "frameSize" ->
                "Number of samples per channel transmitted in a single DMA chunk (TCP packet).\\n"
                + "\\n"
                + "Impact on the system:\\n"
                + "- Small size (e.g., 4096): Lower latency, frequent UI updates, but higher CPU overhead and network traffic.\\n"
                + "- Large size (e.g., 65536): High throughput, efficient for saving large data, but UI updates in larger jumps.\\n"
                + "\\n"
                + "Note: This size directly dictates the chunking used when saving data to offline .rppr.bin files.";'''
text = text.replace(old_frameSize, new_frameSize)

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
