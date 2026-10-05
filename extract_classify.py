with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

# 1. Modify the top menu
old_menu = '''        JMenu scriptsM = new JMenu("Scripts");
        JMenuItem batchMI = new JMenuItem("Dir->PRPD");
        batchMI.addActionListener(e -> dir2prpd());
        scriptsM.add(batchMI);
        mb.add(scriptsM);'''

new_menu = '''        JMenu scriptsM = new JMenu("Machine Learning");
        JMenuItem batchMI = new JMenuItem("Batch Export (Dir -> YOLO PNGs)");
        batchMI.addActionListener(e -> dir2prpd());
        scriptsM.add(batchMI);
        
        JMenuItem classifyMI = new JMenuItem("Real-time Classification");
        classifyMI.addActionListener(e -> {
            if (classifyDialog != null) {
                classifyDialog.setVisible(true);
            }
        });
        scriptsM.add(classifyMI);
        
        mb.add(scriptsM);'''

text = text.replace(old_menu, new_menu)

# 2. Add classifyDialog field
field_inj = '''public class PRPDTool extends JFrame {

    private volatile boolean inBatchMode;'''
new_field_inj = '''public class PRPDTool extends JFrame {

    private JDialog classifyDialog;
    private volatile boolean inBatchMode;'''
text = text.replace(field_inj, new_field_inj)

# 3. Extract classifyPanel to Dialog in initUI
old_classify_ui = '''            JPanel classifyPanel = new JPanel(new BorderLayout());
            classifyPanel.setBorder(BorderFactory.createTitledBorder("Classification"));

            modelPanel = new JPanel();
            cResults = new HashMap<>();
            updateModelPanel();

            classifyPanel.add(modelPanel, BorderLayout.CENTER);

            classifyButton = new JButton("CLASIFY");
            classifyButton.addActionListener(e -> classifyPRPD(cResults));
            classifyButton.setEnabled(false);
            classifyPanel.add(classifyButton, BorderLayout.SOUTH);

            setFontRecursively(classifyPanel, currentFont, 0);

            left.add(paramPanel);
            left.add(rpPanel);
                left.add(classifyPanel);'''

new_classify_ui = '''            JPanel classifyPanel = new JPanel(new BorderLayout());
            classifyPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

            modelPanel = new JPanel();
            cResults = new HashMap<>();
            updateModelPanel();

            classifyPanel.add(modelPanel, BorderLayout.CENTER);

            classifyButton = new JButton("CLASSIFY NOW");
            classifyButton.setFont(classifyButton.getFont().deriveFont(Font.BOLD));
            classifyButton.addActionListener(e -> classifyPRPD(cResults));
            classifyButton.setEnabled(false);
            classifyPanel.add(classifyButton, BorderLayout.SOUTH);

            setFontRecursively(classifyPanel, currentFont, 0);
            
            classifyDialog = new JDialog(PRPDTool.this, "AI Classification", false);
            classifyDialog.add(classifyPanel);
            classifyDialog.setSize(500, 400);
            classifyDialog.setLocationRelativeTo(PRPDTool.this);

            left.add(paramPanel);
            left.add(rpPanel);'''

text = text.replace(old_classify_ui, new_classify_ui)

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
