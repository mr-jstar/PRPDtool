with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

old_loop = '''                field.addFocusListener(new java.awt.event.FocusAdapter() {
                    @Override
                    public void focusLost(java.awt.event.FocusEvent e) {
                        if (!field.getText().equals(p.getText())) {
                            paramChange.setText("Param(s) change!");
                            applyButton.setBackground(Color.red);
                        }
                    }
                });
                paramPanel.add(labelPanel);
                paramPanel.add(field);'''

new_loop = '''                field.addFocusListener(new java.awt.event.FocusAdapter() {
                    @Override
                    public void focusLost(java.awt.event.FocusEvent e) {
                        if (!field.getText().equals(p.getText())) {
                            paramChange.setText("Param(s) change!");
                            applyButton.setBackground(Color.red);
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
                    fitBtn.addActionListener(ev -> {
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

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
