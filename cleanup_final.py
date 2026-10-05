with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

# 1. Variables
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
legacy_panel_str = '''
        JPanel legacyPanel = initLegacySocketControls();
        left.add(legacyPanel);'''
text = text.replace(legacy_panel_str, '')

# 3. Method: initLegacySocketControls
s1 = text.find('    private JPanel initLegacySocketControls() {')
e1 = text.find('    private JPanel createReceivedSignalsPanel() {')
if s1 != -1 and e1 != -1:
    text = text[:s1] + text[e1:]

# 4. Signal Recording UI Panel
s2 = text.find('        JPanel recordPanel = new JPanel(new GridLayout(0, 1, 5, 5));')
e2 = text.find('        histogram = new DynamicPRPDHistogram(')
if s2 != -1 and e2 != -1:
    text = text[:s2] + text[e2:]

text = text.replace('        left.add(recordPanel);\n', '')

# 5. Socket Menu Item
s3 = text.find('        JMenuItem socketMI = new JMenuItem("Read (t,u) from socket");')
e3 = text.find('        fileM.add(socketMI);')
if s3 != -1 and e3 != -1:
    text = text[:s3] + text[e3+29:]

# 6. Method: openSocket
s4 = text.find('    private void openSocket() {')
e4 = text.find('    private void closeSocket() {')
if s4 != -1 and e4 != -1:
    text = text[:s4] + text[e4:]

# 7. Old closeSocket logic
s5 = text.find('    private void closeSocket() {')
e5 = text.find('    private void getRedPitayaData(RedPitayaConfig config, boolean live) throws Exception {')
if s5 != -1 and e5 != -1:
    text = text[:s5] + text[e5:]

# Replace closeSocket calls -> stopPipeline
text = text.replace('rpStopButton.addActionListener(e -> closeSocket());', 'rpStopButton.addActionListener(e -> stopPipeline());')
text = text.replace('closeSocket();', 'stopPipeline();')

# 8. Method: writeBuffer
s6 = text.find('    public static double writeBuffer(FileChannel channel, Buffer buf) throws IOException {')
e6 = text.find('    private void onExit() {')
if s6 != -1 and e6 != -1:
    text = text[:s6] + text[e6:]

# 9. Method: stopRecorder
s7 = text.find('    private void stopRecorder() {')
e7 = text.find('    private void classifyPRPD(Classifier classifier, JLabel resultView) {')
if s7 != -1 and e7 != -1:
    text = text[:s7] + text[e7:]

# 10. Stray statements
text = text.replace('            startRecordButton.setEnabled(true);\n', '')
text = text.replace('            startRecordButton.setEnabled(false);\n', '')
text = text.replace('            stopRecordButton.setEnabled(true);\n', '')
text = text.replace('            stopRecordButton.setEnabled(false);\n', '')
text = text.replace('            stopBtn.setEnabled(true);\n', '')
text = text.replace('            stopBtn.setEnabled(false);\n', '')
text = text.replace('                    startRecordButton.setEnabled(false);\n', '')
text = text.replace('                    stopRecordButton.setEnabled(false);\n', '')

# 11. Buffer Recording Block in bufferRead (Live)
# Just find the exact block and replace
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
