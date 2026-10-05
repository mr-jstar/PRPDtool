with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

# 1. Store createReceivedSignalsPanel in initUI, and remove from bottom.
old_bottom = '''            bottom.setLayout(new BorderLayout());
            bottom.add(createReceivedSignalsPanel(), BorderLayout.WEST);
            bottom.add(interactiveSignalPanel, BorderLayout.CENTER);'''

new_bottom = '''            JPanel recSignalsPanel = createReceivedSignalsPanel();
            bottom.setLayout(new BorderLayout());
            bottom.add(interactiveSignalPanel, BorderLayout.CENTER);'''

text = text.replace(old_bottom, new_bottom)

# 2. Add it to left panel
old_left = '''            left.add(paramPanel);
            left.add(rpPanel);
            left.add(Box.createVerticalGlue());'''

new_left = '''            left.add(paramPanel);
            left.add(rpPanel);
            left.add(recSignalsPanel);
            left.add(Box.createVerticalGlue());'''

text = text.replace(old_left, new_left)

# 3. Remove weird size constraints from createReceivedSignalsPanel
old_create = '''    private JPanel createReceivedSignalsPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Received signals"));
        panel.setPreferredSize(new Dimension(330, 1));
        panel.setMinimumSize(new Dimension(260, 1));'''

new_create = '''    private JPanel createReceivedSignalsPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Received signals"));'''

text = text.replace(old_create, new_create)

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
