import re

with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace('        left.add(recordPanel);\n', '')

# Remove both buffer recording blocks
rec_block = '''                if (realTimeData && recordedData != null && recordedData.isOpen()) {
                    if (recordedMB < recordLimit) {
                        try {
                            recordedMB += (int) writeBuffer(recordedData, buffer);
                            recordSizeLabel.setText(recordedMB + " MB used");
                        } catch (IOException ex) {
                            JOptionPane.showConfirmDialog(
                                    PRPDTool.this,
                                    "Error while recording",
                                    "Warning",
                                    JOptionPane.WARNING_MESSAGE
                            );
                            stopRecorder();
                        }
                    } else {
                        JOptionPane.showConfirmDialog(
                                PRPDTool.this,
                                "Record size limit reached",
                                "Warning",
                                JOptionPane.WARNING_MESSAGE
                        );
                        stopRecorder();
                        recordSizeLabel.setText(recordedMB + " MB used");
                    }
                }'''
text = text.replace(rec_block, '')

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
