with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

old_loop = '''                JTextField field = new JTextField(p.getText(), 8);
                p.setField(field);
                field.setToolTipText(tooltipText);
                field.addActionListener(e -> {
                    if (realTimeData) {
                        try {
                            String text1 = field.getText();
                            p.setter.accept(Double.valueOf(text1));
                            onParameterChanged();
                        } catch (Exception ex) {
                        }
                    }
                });
                paramPanel.add(labelPanel);
                paramPanel.add(field);'''

new_loop = '''                JTextField field = new JTextField(p.getText(), 8);
                p.setField(field);
                field.setToolTipText(tooltipText);
                field.addActionListener(e -> {
                    if (realTimeData) {
                        try {
                            String text1 = field.getText();
                            p.setter.accept(Double.valueOf(text1));
                            onParameterChanged();
                        } catch (Exception ex) {
                        }
                    }
                });
                
                Component toAdd = field;
                if ("Histogram max".equals(p.name)) {
                    JPanel fp = new JPanel(new BorderLayout(4, 0));
                    fp.setOpaque(false);
                    fp.add(field, BorderLayout.CENTER);
                    JButton fitBtn = new JButton("FIT");
                    fitBtn.setToolTipText("Fit histogram vertical resolution to current data bounds");
                    fitBtn.setMargin(new Insets(1, 4, 1, 4));
                    fitBtn.addActionListener(e -> {
                        if (histogram != null) {
                            setParamField("Histogram min", "" + roundme(histogram.getDataMin(), 3));
                            setParamField("Histogram max", "" + roundme(histogram.getDataMax(), 3));
                            onParameterChanged();
                        }
                    });
                    fp.add(fitBtn, BorderLayout.EAST);
                    toAdd = fp;
                }
                
                paramPanel.add(labelPanel);
                paramPanel.add(toAdd);'''

text = text.replace(old_loop, new_loop)

old_menu_item = '''        JMenuItem ftHistMB = new JMenuItem("Fit histogram to data");
        ftHistMB.addActionListener(e -> {
            if (lastDataFile != null) {
                setParamField("Histogram min", "" + roundme(histogram.getDataMin(), 3));
                setParamField("Histogram max", "" + roundme(histogram.getDataMax(), 3));
                bipolarHistogram = bipolarMB.isSelected();
                onParameterChanged();
            }
        });
        optM.add(ftHistMB);
        optM.addSeparator();'''

new_menu_item = '''        // "Fit histogram to data" removed from menu, moved to FIT button in UI
        optM.addSeparator();'''

text = text.replace(old_menu_item, new_menu_item)

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
