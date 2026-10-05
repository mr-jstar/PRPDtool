with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

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

            status.setText("Profile saved to " + file.getName());
        } catch (IOException ex) {
            status.setText(ex.getMessage());
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

            if (!editProfileMap(data, "Save Profile: " + file.getName(), "Save to File")) {
                return; // User cancelled
            }

            Configuration prof = new Configuration(file.getAbsolutePath());
            for (java.util.Map.Entry<String, String> entry : data.entrySet()) {
                prof.saveValue(entry.getKey(), entry.getValue());
            }
            status.setText("Profile saved to " + file.getName());
        } catch (Exception ex) {
            ex.printStackTrace();
            status.setText("Failed to save profile: " + ex.getMessage());
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
