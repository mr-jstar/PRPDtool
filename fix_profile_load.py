with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

import re
old_load = re.compile(r'    private void loadProfile\(File file\) \{.*?\n    \}', re.DOTALL)

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

text = old_load.sub(new_load, text)

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
