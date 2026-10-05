with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

# Replace Channels
old_channels = '''            case "channels" ->
                "ADC input channels used for acquisition.\\n"
                + "\\n"
                + "Options:\\n"
                + "- IN1\\n"
                + "- IN2\\n"
                + "- IN1+IN2";'''

new_channels = '''            case "channels" ->
                "ADC input channels to record during acquisition.\\n"
                + "\\n"
                + "Available options:\\n"
                + "- IN1: Record only channel 1 (typically the main PD signal).\\n"
                + "- IN2: Record only channel 2.\\n"
                + "- IN1+IN2: Record both channels simultaneously. Required if you want to use the 'HW Phase Ref' feature to synchronize the PRPD plot with a reference voltage on IN2.\\n"
                + "\\n"
                + "Note: Recording two channels doubles the memory and network bandwidth requirements compared to a single channel.";'''

text = text.replace(old_channels, new_channels)

# Replace Timeout
old_timeout = '''            case "triggerTimeout" ->
                "Maximum time to wait for the trigger and for the DMA buffer to fill.\\n"
                + "\\n"
                + "If this time elapses, acquisition is interrupted with an error.";'''

new_timeout = '''            case "triggerTimeout" ->
                "Maximum time (in seconds) the system will wait for an acquisition to complete.\\n"
                + "\\n"
                + "This timer includes both:\\n"
                + "1. Waiting for the trigger condition to occur (if not using 'NOW').\\n"
                + "2. Filling the DMA buffer with the requested number of samples.\\n"
                + "\\n"
                + "If this time elapses before the data is ready, the acquisition will abort with a timeout error. It is recommended to set this value slightly higher than your expected Duration + Trigger wait time.";'''

text = text.replace(old_timeout, new_timeout)

# Replace Mode
old_mode = '''            case "mode" ->
                "Mode used to determine acquisition length:\\n"
                + "\\n"
                + "- by duration,\\n"
                + "- by number of frames.";'''

new_mode = '''            case "mode" ->
                "Defines how the total length of the acquisition is specified.\\n"
                + "\\n"
                + "Available modes:\\n"
                + "- duration: You specify the exact time [s]. The program automatically calculates how many samples and frames are needed to cover this time period.\\n"
                + "- frames: You manually specify the exact number of frames (chunks) to capture. The duration is then calculated based on the Frame size and sampling frequency.";'''

text = text.replace(old_mode, new_mode)

# Replace Duration
old_duration = '''            case "duration" ->
                "Time used to collect data in duration mode.";'''

new_duration = '''            case "duration" ->
                "Total time (in seconds) to record data per single acquisition.\\n"
                + "\\n"
                + "This field is active only when Mode is set to 'duration'.\\n"
                + "\\n"
                + "Example:\\n"
                + "A duration of 0.06 seconds at a 50 Hz network frequency captures exactly 3 full sine wave cycles (20 ms each). The exact number of samples collected is calculated as: Duration * fs.";'''

text = text.replace(old_duration, new_duration)

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
