with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

old_format = '''            rpEstimatedSizeLabel.setText(String.format(
                    Locale.US,
                    "<html>Approx. file/data: %s<br>Samples: %s | Frames: %s | %d ch | fs=%.6g Hz</html>",
                    formatBytes(approximateFileBytes),
                    formatInteger(totalSamples),
                    formatInteger(frameCount),
                    channelCount,
                    sampleRate
            ));'''

new_format = '''            String fsStr;
            if (sampleRate >= 1_000_000) {
                fsStr = String.format(Locale.US, "%.3f MHz", sampleRate / 1_000_000.0);
            } else if (sampleRate >= 1_000) {
                fsStr = String.format(Locale.US, "%.3f kHz", sampleRate / 1_000.0);
            } else {
                fsStr = String.format(Locale.US, "%.3f Hz", sampleRate);
            }

            rpEstimatedSizeLabel.setText(String.format(
                    Locale.US,
                    "<html>Approx. file/data: %s<br>Samples: %s | Frames: %s | %d ch | fs=%s</html>",
                    formatBytes(approximateFileBytes),
                    formatInteger(totalSamples),
                    formatInteger(frameCount),
                    channelCount,
                    fsStr
            ));'''

text = text.replace(old_format, new_format)

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
