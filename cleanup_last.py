with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace('            JPanel legacyPanel = initLegacySocketControls();\n', '')
rec_panel = '''            JPanel recordPanel = new JPanel();
            recordPanel.setLayout(new GridLayout(0, 1, 5, 5));
            recordPanel.setBorder(BorderFactory.createTitledBorder("Signal recording"));
            recordPanel.add(new JLabel("Limit [MB]"));
            JTextField limitTF = new JTextField("" + recordLimit);
            limitTF.addActionListener(e -> {
                recordLimit = Integer.parseInt(limitTF.getText());
            });
            recordPanel.add(limitTF);
            
            recordSizeLabel = new JLabel("0 MB used");
            recordPanel.add(recordSizeLabel);\n\n'''
text = text.replace(rec_panel, '')

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
