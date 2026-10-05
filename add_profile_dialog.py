import sys

with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

# Add import for JTable if needed, but we can just use javax.swing.JTable
new_methods = '''
    private boolean editProfileMap(java.util.Map<String, String> profileData, String title, String actionButton) {
        javax.swing.table.DefaultTableModel model = new javax.swing.table.DefaultTableModel(new String[]{"Parameter", "Value"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 1; // Only values are editable
            }
        };
        for (java.util.Map.Entry<String, String> entry : profileData.entrySet()) {
            model.addRow(new Object[]{entry.getKey(), entry.getValue()});
        }
        javax.swing.JTable table = new javax.swing.JTable(model);
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        javax.swing.JScrollPane scroll = new javax.swing.JScrollPane(table);
        scroll.setPreferredSize(new Dimension(400, 300));

        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.add(new JLabel("Review and edit the profile parameters below:"), BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);

        Object[] options = {actionButton, "Cancel"};
        int result = JOptionPane.showOptionDialog(this, panel, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, options, options[0]);

        if (result == JOptionPane.OK_OPTION) {
            if (table.isEditing()) {
                table.getCellEditor().stopCellEditing();
            }
            profileData.clear();
            for (int i = 0; i < model.getRowCount(); i++) {
                profileData.put(model.getValueAt(i, 0).toString(), model.getValueAt(i, 1) != null ? model.getValueAt(i, 1).toString() : "");
            }
            return true;
        }
        return false;
    }

    private void saveProfile(File file) {'''

text = text.replace('    private void saveProfile(File file) {', new_methods)

# Now update saveProfile
old_save = '''    private void saveProfile(File file) {
        try {
            Configuration prof = new Configuration(file.getAbsolutePath());
            prof.saveValue(RP_HOST, rpHostField.getText());
            prof.saveValue(RP_PORT, rpPortSpinner.getValue().toString());
            prof.saveValue(RP_CHANNELS, rpChannelsCombo.getSelectedItem().toString());
            prof.saveValue(RP_VISUAL_CHANNEL, rpVisualChannelCombo.getSelectedItem().toString());
            prof.saveValue(RP_GAIN1, rpGain1Combo.getSelectedItem().toString());
            prof.saveValue(RP_GAIN2, rpGain2Combo.getSelectedItem().toString());
            prof.saveValue(RP_DECIMATION, rpDecimationSpinner.getValue().toString());
            prof.saveValue(RP_AVERAGING, Boolean.toString(rpAveragingBox.isSelected()));
            prof.saveValue(RP_TRIGGER_SOURCE, rpTriggerCombo.getSelectedItem().toString());
            prof.saveValue(RP_TRIGGER_LEVEL, rpTriggerLevelSpinner.getValue().toString());
            prof.saveValue(RP_TRIGGER_DELAY, rpTriggerDelaySpinner.getValue().toString());
            prof.saveValue(RP_TRIGGER_TIMEOUT, rpTriggerTimeoutSpinner.getValue().toString());
            prof.saveValue(RP_MODE, rpModeCombo.getSelectedItem().toString());
            prof.saveValue(RP_DURATION, rpDurationSpinner.getValue().toString());
            prof.saveValue(RP_FRAME_SIZE, rpFrameSizeSpinner.getValue().toString());
            prof.saveValue(RP_FRAME_COUNT, rpFrameCountSpinner.getValue().toString());

            for (Param<?> p : params) {
                prof.saveValue("Param." + p.name, p.getText());
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to save profile: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }'''

new_save = '''    private void saveProfile(File file) {
        try {
            java.util.Map<String, String> data = new java.util.LinkedHashMap<>();
            data.put(RP_HOST, rpHostField.getText());
            data.put(RP_PORT, rpPortSpinner.getValue().toString());
            data.put(RP_CHANNELS, rpChannelsCombo.getSelectedItem().toString());
            data.put(RP_VISUAL_CHANNEL, rpVisualChannelCombo.getSelectedItem().toString());
            data.put(RP_GAIN1, rpGain1Combo.getSelectedItem().toString());
            data.put(RP_GAIN2, rpGain2Combo.getSelectedItem().toString());
            data.put(RP_DECIMATION, rpDecimationSpinner.getValue().toString());
            data.put(RP_AVERAGING, Boolean.toString(rpAveragingBox.isSelected()));
            data.put(RP_TRIGGER_SOURCE, rpTriggerCombo.getSelectedItem().toString());
            data.put(RP_TRIGGER_LEVEL, rpTriggerLevelSpinner.getValue().toString());
            data.put(RP_TRIGGER_DELAY, rpTriggerDelaySpinner.getValue().toString());
            data.put(RP_TRIGGER_TIMEOUT, rpTriggerTimeoutSpinner.getValue().toString());
            data.put(RP_MODE, rpModeCombo.getSelectedItem().toString());
            data.put(RP_DURATION, rpDurationSpinner.getValue().toString());
            data.put(RP_FRAME_SIZE, rpFrameSizeSpinner.getValue().toString());
            data.put(RP_FRAME_COUNT, rpFrameCountSpinner.getValue().toString());

            for (Param<?> p : params) {
                data.put("Param." + p.name, p.getText());
            }

            if (!editProfileMap(data, "Save Profile", "Save to File")) {
                return; // User cancelled
            }

            Configuration prof = new Configuration(file.getAbsolutePath());
            for (java.util.Map.Entry<String, String> entry : data.entrySet()) {
                prof.saveValue(entry.getKey(), entry.getValue());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to save profile: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }'''

text = text.replace(old_save, new_save)


old_load = '''    private void loadProfile(File file) {
        Configuration prof = new Configuration(file.getAbsolutePath());
        
        try { String v = prof.getValue(RP_HOST); if(v != null) rpHostField.setText(v); } catch(Exception e) {}
        try { String v = prof.getValue(RP_PORT); if(v != null) rpPortSpinner.setValue(Integer.parseInt(v)); } catch(Exception e) {}
        try { String v = prof.getValue(RP_CHANNELS); if(v != null) rpChannelsCombo.setSelectedItem(v); } catch(Exception e) {}
        try { String v = prof.getValue(RP_VISUAL_CHANNEL); if(v != null) rpVisualChannelCombo.setSelectedItem(v); } catch(Exception e) {}
        try { String v = prof.getValue(RP_GAIN1); if(v != null) rpGain1Combo.setSelectedItem(v); } catch(Exception e) {}
        try { String v = prof.getValue(RP_GAIN2); if(v != null) rpGain2Combo.setSelectedItem(v); } catch(Exception e) {}
        try { String v = prof.getValue(RP_DECIMATION); if(v != null) rpDecimationSpinner.setValue(Integer.parseInt(v)); } catch(Exception e) {}
        try { String v = prof.getValue(RP_AVERAGING); if(v != null) rpAveragingBox.setSelected(Boolean.parseBoolean(v)); } catch(Exception e) {}
        try { String v = prof.getValue(RP_TRIGGER_SOURCE); if(v != null) rpTriggerCombo.setSelectedItem(v); } catch(Exception e) {}
        try { String v = prof.getValue(RP_TRIGGER_LEVEL); if(v != null) rpTriggerLevelSpinner.setValue(Double.parseDouble(v)); } catch(Exception e) {}
        try { String v = prof.getValue(RP_TRIGGER_DELAY); if(v != null) rpTriggerDelaySpinner.setValue(Integer.parseInt(v)); } catch(Exception e) {}
        try { String v = prof.getValue(RP_TRIGGER_TIMEOUT); if(v != null) rpTriggerTimeoutSpinner.setValue(Double.parseDouble(v)); } catch(Exception e) {}
        try { String v = prof.getValue(RP_MODE); if(v != null) rpModeCombo.setSelectedItem(v); } catch(Exception e) {}
        try { String v = prof.getValue(RP_DURATION); if(v != null) rpDurationSpinner.setValue(Double.parseDouble(v)); } catch(Exception e) {}
        try { String v = prof.getValue(RP_FRAME_SIZE); if(v != null) rpFrameSizeSpinner.setValue(Integer.parseInt(v)); } catch(Exception e) {}
        try { String v = prof.getValue(RP_FRAME_COUNT); if(v != null) rpFrameCountSpinner.setValue(Integer.parseInt(v)); } catch(Exception e) {}

        for (Param<?> p : params) {
            String v = prof.getValue("Param." + p.name);
            if (v != null) {
                p.setFromText(v);
                if (p.field != null) {
                    p.field.setText(p.getText());
                }
            }
        }
        
        onParameterChanged();
    }'''

new_load = '''    private void loadProfile(File file) {
        Configuration prof = new Configuration(file.getAbsolutePath());
        
        java.util.Map<String, String> data = new java.util.LinkedHashMap<>();
        // Read existing keys from the file (or we could pre-populate based on expected keys)
        // Configuration currently doesn't expose a getAll method easily if it's just a file wrapper, 
        // let's try reading standard keys.
        String[] keys = {RP_HOST, RP_PORT, RP_CHANNELS, RP_VISUAL_CHANNEL, RP_GAIN1, RP_GAIN2, 
                         RP_DECIMATION, RP_AVERAGING, RP_TRIGGER_SOURCE, RP_TRIGGER_LEVEL, 
                         RP_TRIGGER_DELAY, RP_TRIGGER_TIMEOUT, RP_MODE, RP_DURATION, 
                         RP_FRAME_SIZE, RP_FRAME_COUNT};
        for(String k : keys) {
            try { String v = prof.getValue(k); if(v != null) data.put(k, v); } catch(Exception e) {}
        }
        for (Param<?> p : params) {
            try { String v = prof.getValue("Param." + p.name); if (v != null) data.put("Param." + p.name, v); } catch(Exception e) {}
        }
        
        if (!editProfileMap(data, "Load Profile: " + file.getName(), "Load into Application")) {
            return;
        }
        
        // Apply data back to UI
        try { String v = data.get(RP_HOST); if(v != null) rpHostField.setText(v); } catch(Exception e) {}
        try { String v = data.get(RP_PORT); if(v != null) rpPortSpinner.setValue(Integer.parseInt(v)); } catch(Exception e) {}
        try { String v = data.get(RP_CHANNELS); if(v != null) rpChannelsCombo.setSelectedItem(v); } catch(Exception e) {}
        try { String v = data.get(RP_VISUAL_CHANNEL); if(v != null) rpVisualChannelCombo.setSelectedItem(v); } catch(Exception e) {}
        try { String v = data.get(RP_GAIN1); if(v != null) rpGain1Combo.setSelectedItem(v); } catch(Exception e) {}
        try { String v = data.get(RP_GAIN2); if(v != null) rpGain2Combo.setSelectedItem(v); } catch(Exception e) {}
        try { String v = data.get(RP_DECIMATION); if(v != null) rpDecimationSpinner.setValue(Integer.parseInt(v)); } catch(Exception e) {}
        try { String v = data.get(RP_AVERAGING); if(v != null) rpAveragingBox.setSelected(Boolean.parseBoolean(v)); } catch(Exception e) {}
        try { String v = data.get(RP_TRIGGER_SOURCE); if(v != null) rpTriggerCombo.setSelectedItem(v); } catch(Exception e) {}
        try { String v = data.get(RP_TRIGGER_LEVEL); if(v != null) rpTriggerLevelSpinner.setValue(Double.parseDouble(v)); } catch(Exception e) {}
        try { String v = data.get(RP_TRIGGER_DELAY); if(v != null) rpTriggerDelaySpinner.setValue(Integer.parseInt(v)); } catch(Exception e) {}
        try { String v = data.get(RP_TRIGGER_TIMEOUT); if(v != null) rpTriggerTimeoutSpinner.setValue(Double.parseDouble(v)); } catch(Exception e) {}
        try { String v = data.get(RP_MODE); if(v != null) rpModeCombo.setSelectedItem(v); } catch(Exception e) {}
        try { String v = data.get(RP_DURATION); if(v != null) rpDurationSpinner.setValue(Double.parseDouble(v)); } catch(Exception e) {}
        try { String v = data.get(RP_FRAME_SIZE); if(v != null) rpFrameSizeSpinner.setValue(Integer.parseInt(v)); } catch(Exception e) {}
        try { String v = data.get(RP_FRAME_COUNT); if(v != null) rpFrameCountSpinner.setValue(Integer.parseInt(v)); } catch(Exception e) {}

        for (Param<?> p : params) {
            String v = data.get("Param." + p.name);
            if (v != null) {
                p.setFromText(v);
                if (p.field != null) {
                    p.field.setText(p.getText());
                }
            }
        }
        
        onParameterChanged();
    }'''
text = text.replace(old_load, new_load)

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
