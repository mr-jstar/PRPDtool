with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

import re

# 1. Rewrite paramHelpText to plain text in English without 60Hz
old_param_help = re.compile(r'    private String paramHelpText\(String key\) \{.*?        \};\n    \}', re.DOTALL)

new_param_help = '''    private String paramHelpText(String key) {
        return switch (key) {
            case "Basic frequency [Hz]" ->
                "Power line frequency.\n\n"
                + "Serves as the reference to calculate the phase angle of each pulse (0-360 degrees) and to filter out slow background drift.\n\n"
                + "Values: Typically 50.0";
            case "Zero-crossing instant [s]" ->
                "Phase shift correction.\n\n"
                + "Determines the fraction of a second where the voltage crosses zero. Set automatically by the software.\n\n"
                + "Values: Changing this manually (e.g., adding 0.005 s) will horizontally shift the PRPD plot by 90 degrees. Useful for manual phase alignment.";
            case "Sampling frequency [Hz]" ->
                "Data acquisition speed.\n\n"
                + "Defines how many measurement points per second were recorded. Set automatically from data files.\n\n"
                + "Values: Typically 1953125.0 (for decimation 64). Edit only if loading a raw text file missing header information.";
            case "Pulse ampl. threshold [V]" ->
                "Noise rejection threshold.\n\n"
                + "Any peaks with a voltage lower than this value are considered background noise and ignored.\n\n"
                + "Values: Typically 0.005 to 0.05. Increase this value slightly until dense background noise disappears from the screen, leaving only clear discharge pulses.";
            case "Dead time [us]" ->
                "Detector blind time.\n\n"
                + "The time immediately following a detected pulse during which the software ignores any further peaks. Prevents falsely counting a single oscillating (ringing) peak as multiple pulses.\n\n"
                + "Values: Typically 5.0 to 20.0 us.";
            case "HPF cutoff frequency [Hz]" ->
                "High-Pass Filter boundary.\n\n"
                + "Removes slow background fluctuations (including the 50 Hz sine wave). Lets only ultra-fast partial discharge spikes pass through.\n\n"
                + "Values: Usually 50000.0 (50 kHz) or 100000.0 (100 kHz).";
            case "Filter Q" ->
                "Filter Quality Factor.\n\n"
                + "Determines the steepness and shape of the digital filter at the cutoff point.\n\n"
                + "Values: Recommended to leave at the default 0.707 (Butterworth) to prevent artificial ringing in the signal.";
            case "Filter Order" ->
                "Digital filter steepness.\n\n"
                + "Determines how aggressively the filter cuts off unwanted frequencies.\n\n"
                + "Values: Typically 2, 4, or 8. Higher values cut the signal more sharply but significantly increase CPU usage and can distort tiny pulses.";
            case "Histogram min [V]" ->
                "Lower boundary of the Y-axis.\n\n"
                + "Sets the absolute minimum physical limit for the rendered PRPD image.\n\n"
                + "Tip: Use the 'Fit' button to let the software automatically calculate the ideal boundaries for maximum image sharpness.";
            case "Histogram max [V]" ->
                "Upper boundary of the Y-axis.\n\n"
                + "Sets the absolute maximum physical limit. Directly influences the vertical resolution of the internal PRPD matrix.\n\n"
                + "Tip: Use the 'Fit' button to automatically scale this to your highest detected pulse.";
            default ->
                "";
        };
    }'''

text = old_param_help.sub(new_param_help, text)

# 2. Fix the CheckBox tooltips which also incorrectly used HTML tags manually
old_auto_help = 'autoHelp.setToolTipText(htmlTooltip("<b>Visual Zoom Adjustment.</b><br><br>Automatically adjusts the visual zoom of the PRPD plot to perfectly frame the visible pulses. Unlike \'Histogram min/max\' which rebuilds the actual image resolution, Autoscale only moves the camera. It turns off automatically if you pan/zoom manually."));'
new_auto_help = 'autoHelp.setToolTipText(htmlTooltip("Visual Zoom Adjustment.\\n\\nAutomatically adjusts the visual zoom of the PRPD plot to perfectly frame the visible pulses. Unlike \\"Histogram min/max\\" which rebuilds the actual image resolution, Autoscale only moves the camera.\\n\\nIt turns off automatically if you pan or zoom manually with the mouse."));'
text = text.replace(old_auto_help, new_auto_help)

old_raw_help = 'rawHelp.setToolTipText(htmlTooltip("<b>Toggle PRPD rendering mode.</b><br><br>When unchecked (default), the plot uses a heatmap interpolation where colors represent pulse density. When checked, it draws the exact, raw individual pulse points as a scatter plot."));'
new_raw_help = 'rawHelp.setToolTipText(htmlTooltip("Toggle PRPD rendering mode.\\n\\nWhen unchecked (default), the plot uses a heatmap interpolation where colors represent pulse density (e.g. red=many, blue=few).\\n\\nWhen checked, it bypasses the heatmap and draws the exact, raw individual pulse points as a simple scatter plot."));'
text = text.replace(old_raw_help, new_raw_help)

old_hw_help = 'hwHelp.setToolTipText(htmlTooltip("<b>Hardware Phase Synchronization.</b><br><br>Forces the software to use the physical signal on IN2 for 50/60 Hz phase synchronization. If unchecked, the software attempts to mathematically extract the reference from the main IN1 signal.<br><br><i>Requires \'Channels\' to be IN1+IN2 and \'Visual\' to be IN1.</i>"));'
new_hw_help = 'hwHelp.setToolTipText(htmlTooltip("Hardware Phase Synchronization.\\n\\nForces the software to use the physical signal on IN2 for 50 Hz phase synchronization.\\nIf unchecked, the software attempts to mathematically extract the reference from the main IN1 signal.\\n\\nNote: Requires \\"Channels\\" to be set to IN1+IN2 and \\"Visual\\" to IN1 in the Red Pitaya Settings."));'
text = text.replace(old_hw_help, new_hw_help)

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
