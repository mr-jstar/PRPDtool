import sys

def safe_replace(text, start_str, end_str):
    start = text.find(start_str)
    if start == -1: return text
    end = text.find(end_str, start)
    if end == -1: return text
    return text[:start] + text[end + len(end_str):]

with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

# 1. Variables (just replace with empty strings)
vars_to_remove = [
    '    private JTextField dataServer;\n',
    '    private JButton startBtn;\n',
    '    private JButton stopBtn;\n',
    '    private JButton startRecordButton;\n',
    '    private JButton stopRecordButton;\n',
    '    private FileChannel recordedData;\n',
    '    private File recordedFile;\n',
    '    private int recordLimit = 1024; // Maximal size of the registerd signal (in MB == 1 GB)\n',
    '    private int recordedMB;\n',
    '    private JLabel recordSizeLabel;\n',
    '    private final String DAQ = "PRPDMonitor.daq.address";\n'
]
for v in vars_to_remove:
    text = text.replace(v, '')

# 2. Legacy Socket UI Panel
text = safe_replace(text, '        JPanel legacyPanel = initLegacySocketControls();\n', '        left.add(legacyPanel);\n')
# 3. Signal Recording UI Panel
text = safe_replace(text, '        JPanel recordPanel = new JPanel(new GridLayout(0, 1, 5, 5));\n', '        left.add(recordPanel);\n')
# 4. Socket Menu Item
text = safe_replace(text, '        JMenuItem socketMI = new JMenuItem("Read (t,u) from socket");\n', '        fileM.add(socketMI);\n')
# 5. Method: initLegacySocketControls
text = safe_replace(text, '    private JPanel initLegacySocketControls() {\n', '        return legacyPanel;\n    }\n')
# 6. Method: openSocket
text = safe_replace(text, '    private void openSocket() {\n', '        }\n    }\n')
# 7. Method: closeSocket
text = safe_replace(text, '    private void closeSocket() {\n', '            realTimeData = false;\n        }\n    }\n')
# 8. Method: writeBuffer
text = safe_replace(text, '    public static double writeBuffer(FileChannel channel, Buffer buf) throws IOException {\n', '        return bytesToWrite / (1024.0 * 1024.0);\n    }\n')
# 9. Method: stopRecorder
text = safe_replace(text, '    private void stopRecorder() {\n', '            refreshReceivedSignals();\n        } catch (IOException ex) {\n            JOptionPane.showMessageDialog(\n                    PRPDTool.this,\n                    ex.getMessage(),\n                    "Warning",\n                    JOptionPane.WARNING_MESSAGE\n            );\n        }\n    }\n')

# Replace strays
text = text.replace('rpStopButton.addActionListener(e -> closeSocket());', 'rpStopButton.addActionListener(e -> stopPipeline());')
text = text.replace('closeSocket();', 'stopPipeline();')
text = text.replace('startRecordButton.setEnabled(true);\n', '')
text = text.replace('startRecordButton.setEnabled(false);\n', '')
text = text.replace('stopRecordButton.setEnabled(true);\n', '')
text = text.replace('stopRecordButton.setEnabled(false);\n', '')
text = text.replace('stopBtn.setEnabled(true);\n', '')
text = text.replace('stopBtn.setEnabled(false);\n', '')

# Remove first buffer recording block
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
                    }
                }'''
text = text.replace(rec_block, '')

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
