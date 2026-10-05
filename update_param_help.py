with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

import re

old_param_help = re.compile(r'    private String paramHelpText\(String key\) \{.*?        \};\n    \}', re.DOTALL)

new_param_help = '''    private String paramHelpText(String key) {
        return switch (key) {
            case "Basic frequency [Hz]" ->
                "<b>Nominal power line frequency</b> (usually 50 Hz or 60 Hz).<br><br>"
                + "This is used as the base reference to calculate the phase angle (0-360 degrees) of each partial discharge pulse. "
                + "It is also used by the internal baseline filter to remove low-frequency fluctuations.";
            case "Zero-crossing instant" ->
                "<b>Phase alignment reference point.</b><br><br>"
                + "Time (in seconds) of the first positive zero-crossing of the reference sine wave in the current buffer. "
                + "This is automatically estimated by the software. If you change it manually in offline mode, it will horizontally shift the entire PRPD plot.";
            case "Sampling frequency [Hz]" ->
                "<b>The acquisition sampling frequency (points per second).</b><br><br>"
                + "Typically auto-detected from the Red Pitaya settings or binary file headers. "
                + "You only need to manually set this if you are loading a raw text file (.txt) that lacks metadata. Crucial for accurate time-domain digital filtering.";
            case "Pulse ampl. threshold" ->
                "<b>Minimum amplitude limit for pulse detection.</b><br><br>"
                + "Any peaks below this threshold (in V or mV) are considered background noise and will not appear on the PRPD histogram. "
                + "Raise this value to filter out continuous background noise.";
            case "Dead time [us]" ->
                "<b>The 'blind' time (in microseconds) after a pulse.</b><br><br>"
                + "Triggered immediately after detecting a pulse. During this time, the software ignores any further peaks. "
                + "This prevents a single, oscillating (ringing) discharge pulse from being falsely counted as multiple separate pulses.";
            case "HPF cutoff frequency [Hz]" ->
                "<b>High-Pass Filter (HPF) boundary.</b><br><br>"
                + "This filter removes the main 50/60 Hz power sine wave and slow-moving industrial noise (e.g., thyristor switching). "
                + "For example, setting it to 100,000 Hz will strip away everything except the very fast, sharp Partial Discharge pulses.";
            case "Filter Q" ->
                "<b>Quality factor (resonance) of the digital filters.</b><br><br>"
                + "The default value of 0.707 creates a maximally flat filter response (Butterworth) without artificial ringing. "
                + "Unless you are designing a specific resonant filter, it is highly recommended to leave this at 0.707.";
            case "Filter Order" ->
                "<b>The mathematical steepness of the digital filters.</b><br><br>"
                + "A higher order (e.g., 4 or 8) creates a sharper cutoff, meaning unwanted frequencies are blocked more aggressively. "
                + "However, excessively high values will increase CPU load and may introduce artificial ringing (Gibbs phenomenon).";
            case "Histogram min" ->
                "<b>Absolute minimum physical limit for the PRPD matrix.</b><br><br>"
                + "Used to allocate matrix resolution. Use the 'Fit' button to automatically set this based on the actual signal data, maximizing vertical sharpness.";
            case "Histogram max" ->
                "<b>Absolute maximum physical limit for the PRPD matrix.</b><br><br>"
                + "Dictates the internal memory resolution of the plot. Use the 'Fit' button to perfectly adapt this to your highest detected pulse.";
            default ->
                "";
        };
    }'''

text = old_param_help.sub(new_param_help, text)

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
