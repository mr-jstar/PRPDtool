with open('src/prpdtool/PRPDConstants.java', 'r', encoding='utf-8') as f:
    text = f.read()

old_fonts = '''    public static final Font[] FONTS = {
        new Font("Courier", Font.PLAIN, 12),
        new Font("Courier", Font.PLAIN, 16),
        new Font("Courier", Font.PLAIN, 18)
    };'''

new_fonts = '''    public static final Font[] FONTS = {
        new Font("Courier", Font.PLAIN, 12),
        new Font("Courier", Font.PLAIN, 14),
        new Font("Courier", Font.PLAIN, 16)
    };'''
text = text.replace(old_fonts, new_fonts)

with open('src/prpdtool/PRPDConstants.java', 'w', encoding='utf-8') as f:
    f.write(text)

with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text2 = f.read()

old_menu = '''        JMenu optM = new JMenu("Options");
        JMenuItem fontMI = new JMenuItem("Font size");
        optM.add(fontMI);
        ButtonGroup fgroup = new ButtonGroup();
        for (Font f : PRPDConstants.FONTS) {
            JRadioButtonMenuItem fontOpt = new JRadioButtonMenuItem("\\t\\t" + String.valueOf(f.getSize()));
            final Font cf = f;
            fontOpt.addActionListener(e -> {
                currentFont = cf;
                setCurrentFont();
                try {
                    configuration.saveValue(FONTSIZE, "" + cf.getSize());
                } catch (IOException ex) {

                }
            });
            fontOpt.setSelected(f == currentFont);
            fgroup.add(fontOpt);
            optM.add(fontOpt);
        }
        optM.addSeparator();'''

new_menu = '''        JMenu optM = new JMenu("View");
        JMenu fontM = new JMenu("Font size");
        ButtonGroup fgroup = new ButtonGroup();
        for (Font f : PRPDConstants.FONTS) {
            JRadioButtonMenuItem fontOpt = new JRadioButtonMenuItem(String.valueOf(f.getSize()));
            final Font cf = f;
            fontOpt.addActionListener(e -> {
                currentFont = cf;
                setCurrentFont();
                try {
                    configuration.saveValue(FONTSIZE, "" + cf.getSize());
                } catch (IOException ex) {
                }
            });
            fontOpt.setSelected(f == currentFont);
            fgroup.add(fontOpt);
            fontM.add(fontOpt);
        }
        optM.add(fontM);
        optM.addSeparator();'''

text2 = text2.replace(old_menu, new_menu)

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text2)
