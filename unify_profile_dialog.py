with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

import re

# 1. Update editProfileMap to return String and take isSaveMode + defaultName
old_edit = re.compile(r'    private boolean editProfileMap\(.*?return false;\n    \}', re.DOTALL)
new_edit = '''    private String editProfileMap(java.util.Map<String, String> profileData, String title, String actionButton, boolean isSaveMode, String defaultName) {
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
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.add(new JLabel("Review and edit the profile parameters below:"), BorderLayout.NORTH);
        
        JTextField nameField = new JTextField(defaultName != null ? defaultName : "my_profile");
        if (isSaveMode) {
            JPanel namePanel = new JPanel(new BorderLayout(5, 5));
            namePanel.add(new JLabel("Profile Name: "), BorderLayout.WEST);
            namePanel.add(nameField, BorderLayout.CENTER);
            topPanel.add(namePanel, BorderLayout.SOUTH);
        }
        
        panel.add(topPanel, BorderLayout.NORTH);
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
            return isSaveMode ? nameField.getText().trim() : (defaultName != null ? defaultName : "");
        }
        return null;
    }'''
text = old_edit.sub(new_edit, text)

# 2. Rewrite createNewProfile (formerly saveProfile wrapper)
old_build_menu = re.compile(r'    private void buildProfilesMenu\(JMenu profilesM\) \{.*?\n        profilesM\.add\(saveProfileMI\);', re.DOTALL)
new_build_menu = '''    private void buildProfilesMenu(JMenu profilesM) {
        profilesM.removeAll();
        
        JMenuItem saveProfileMI = new JMenuItem("Save current profile...");
        saveProfileMI.addActionListener(e -> {
            createNewProfile(profilesM);
        });
        profilesM.add(saveProfileMI);'''
text = old_build_menu.sub(new_build_menu, text)

# 3. Add createNewProfile and remove the old saveProfile(File file)
old_save_profile = re.compile(r'    private void saveProfile\(File file\) \{.*?\n    \}', re.DOTALL)
new_create_new = '''    private void createNewProfile(JMenu profilesM) {
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

            String profileName = editProfileMap(data, "Save New Profile", "Save Profile", true, "my_profile");
            if (profileName == null || profileName.isEmpty()) {
                return; // User cancelled or entered empty name
            }

            File dir = new File("profiles");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File file = new File(dir, profileName + ".cfg");
            
            Configuration prof = new Configuration(file.getAbsolutePath());
            for (java.util.Map.Entry<String, String> entry : data.entrySet()) {
                prof.saveValue(entry.getKey(), entry.getValue());
            }
            status.setText("Profile saved to " + file.getName());
            buildProfilesMenu(profilesM);
        } catch (Exception ex) {
            ex.printStackTrace();
            status.setText("Failed to save profile: " + ex.getMessage());
        }
    }'''
text = old_save_profile.sub(new_create_new, text)

# 4. Update loadProfile to match the new signature of editProfileMap
old_load_profile_call = 'if (!editProfileMap(data, "Load Profile: " + file.getName(), "Load into Application")) {'
new_load_profile_call = 'if (editProfileMap(data, "Load Profile: " + file.getName(), "Load into Application", false, file.getName()) == null) {'
text = text.replace(old_load_profile_call, new_load_profile_call)

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
