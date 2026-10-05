with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

import re

old_cb_block = re.compile(r'            autoscaleCb = new JCheckBox\("Autoscale PRPD", true\);.*?            paramPanel\.add\(useHwPhaseRefCb\);', re.DOTALL)

new_cb_block = '''            autoscaleCb = new JCheckBox("Autoscale PRPD", true);
            autoscaleCb.addActionListener(e -> {
                if (autoscaleCb.isSelected()) {
                    applyAutoscale();
                }
            });
            JPanel autoPanel = new JPanel(new BorderLayout(4, 0));
            autoPanel.setOpaque(false);
            autoPanel.add(autoscaleCb, BorderLayout.CENTER);
            JLabel autoHelp = new JLabel(new HelpIcon());
            autoHelp.setToolTipText(htmlTooltip("<b>Visual Zoom Adjustment.</b><br><br>Automatically adjusts the visual zoom of the PRPD plot to perfectly frame the visible pulses. Unlike 'Histogram min/max' which rebuilds the actual image resolution, Autoscale only moves the camera. It turns off automatically if you pan/zoom manually."));
            autoHelp.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            autoHelp.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
            autoPanel.add(autoHelp, BorderLayout.EAST);
            paramPanel.add(autoPanel);
            
            showRawDataCb = new JCheckBox("Show Raw Data", false);
            showRawDataCb.addActionListener(e -> {
                if (histogram instanceof DynamicPRPDHistogram) {
                    ((DynamicPRPDHistogram) histogram).setShowRawData(showRawDataCb.isSelected());
                    center.setImage(histogram.getImage());
                }
            });
            JPanel rawPanel = new JPanel(new BorderLayout(4, 0));
            rawPanel.setOpaque(false);
            rawPanel.add(showRawDataCb, BorderLayout.CENTER);
            JLabel rawHelp = new JLabel(new HelpIcon());
            rawHelp.setToolTipText(htmlTooltip("<b>Toggle PRPD rendering mode.</b><br><br>When unchecked (default), the plot uses a heatmap interpolation where colors represent pulse density. When checked, it draws the exact, raw individual pulse points as a scatter plot."));
            rawHelp.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            rawHelp.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
            rawPanel.add(rawHelp, BorderLayout.EAST);
            paramPanel.add(rawPanel);

            useHwPhaseRefCb = new JCheckBox("Use HW Phase Ref (CH2)", false);
            useHwPhaseRefCb.addActionListener(e -> {
                if (useHwPhaseRefCb.isSelected()) {
                    java.util.List<String> problems = new java.util.ArrayList<>();
                    String channels = rpChannelsCombo != null ? (String) rpChannelsCombo.getSelectedItem() : "IN1";
                    String visual = rpVisualChannelCombo != null ? (String) rpVisualChannelCombo.getSelectedItem() : "IN1";
                    if (!"IN1+IN2".equals(channels)) {
                        problems.add("  Channels   zmień na \"IN1+IN2\" (aktualnie: \"" + channels + "\")");
                    }
                    if ("IN2".equals(visual)) {
                        problems.add("  Visual   zmień na \"IN1\" (IN2 jest zarezerwowany jako referencja 50 Hz)");
                    }
                    if (!problems.isEmpty()) {
                        useHwPhaseRefCb.setSelected(false);
                        JOptionPane.showMessageDialog(
                            PRPDTool.this,
                            "<html><b>Nie można użyć referencji CH2.</b><br><br>"
                            + "Aby korzystać z \"Use HW Phase Ref (CH2)\", zmień<br>"
                            + "następujące ustawienia w zakładce <i>Red Pitaya Settings</i>:<br><br>"
                            + String.join("<br>", problems)
                            + "</html>",
                            "Błąd konfiguracji",
                            JOptionPane.WARNING_MESSAGE
                        );
                    }
                }
            });
            JPanel hwPanel = new JPanel(new BorderLayout(4, 0));
            hwPanel.setOpaque(false);
            hwPanel.add(useHwPhaseRefCb, BorderLayout.CENTER);
            JLabel hwHelp = new JLabel(new HelpIcon());
            hwHelp.setToolTipText(htmlTooltip("<b>Hardware Phase Synchronization.</b><br><br>Forces the software to use the physical signal on IN2 for 50/60 Hz phase synchronization. If unchecked, the software attempts to mathematically extract the reference from the main IN1 signal.<br><br><i>Requires 'Channels' to be IN1+IN2 and 'Visual' to be IN1.</i>"));
            hwHelp.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            hwHelp.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
            hwPanel.add(hwHelp, BorderLayout.EAST);
            paramPanel.add(hwPanel);'''

text = old_cb_block.sub(new_cb_block, text)

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
