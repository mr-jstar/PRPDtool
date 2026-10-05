with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

old_rp = '''    private JPanel initRedPitayaControls() {
        JPanel rpPanel = new JPanel(new BorderLayout(4, 4));
        rpPanel.setBorder(BorderFactory.createTitledBorder("Red Pitaya"));'''

new_rp = '''    private JPanel initRedPitayaControls() {
        JPanel rpPanel = new JPanel(new BorderLayout(4, 4)) {
            @Override
            public Dimension getMaximumSize() {
                Dimension pref = getPreferredSize();
                return new Dimension(Integer.MAX_VALUE, pref.height);
            }
        };
        rpPanel.setBorder(BorderFactory.createTitledBorder("Red Pitaya"));'''

text = text.replace(old_rp, new_rp)

# Also fix actionsAndFiles just to be extremely safe about inner spacing
old_actions = '''        JPanel actionsAndFiles = new JPanel(new BorderLayout());
        actionsAndFiles.add(actions, BorderLayout.CENTER);
        actionsAndFiles.add(fileConfigPanel, BorderLayout.SOUTH);
        rpPanel.add(actionsAndFiles, BorderLayout.CENTER);
        rpPanel.add(rpEstimatedSizeLabel, BorderLayout.SOUTH);'''

new_actions = '''        JPanel actionsAndFiles = new JPanel();
        actionsAndFiles.setLayout(new BoxLayout(actionsAndFiles, BoxLayout.Y_AXIS));
        actionsAndFiles.add(actions);
        actionsAndFiles.add(fileConfigPanel);
        rpPanel.add(actionsAndFiles, BorderLayout.NORTH);
        rpPanel.add(rpEstimatedSizeLabel, BorderLayout.SOUTH);'''

text = text.replace(old_actions, new_actions)

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
