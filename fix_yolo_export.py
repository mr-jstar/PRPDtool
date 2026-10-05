import re

with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

# 1. Update the menu action
old_menu = '''        JMenuItem prpdMI = new JMenuItem("Export YOLO image");
        prpdMI.addActionListener(e -> exportPRPD4YOLO(lastDataFile));'''

new_menu = '''        JMenuItem prpdMI = new JMenuItem("Export YOLO image");
        prpdMI.addActionListener(e -> exportPRPD4YOLODialog());'''

text = text.replace(old_menu, new_menu)

# 2. Add the new dialog method next to exportPRPD4YOLO
old_method = '''    private void exportPRPD4YOLO(String fileName) {'''

new_method = '''    private void exportPRPD4YOLODialog() {
        if (histogram == null) return;
        
        prpd4YOLO = histogram.getPRPD(224, 224);
        JFileChooser fileChooser = new JFileChooser(getLastUsedDirectory());
        setFontRecursively(fileChooser, currentFont, 0);
        
        if (lastDataFile != null) {
            String defaultName = new File(lastDataFile).getName().replaceAll("\\\\..*$", ".png");
            fileChooser.setSelectedFile(new File(getLastUsedDirectory(), defaultName));
        }

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            String path = file.getAbsolutePath();
            if (!path.toLowerCase().endsWith(".png")) {
                file = new File(path + ".png");
            }
            try {
                javax.imageio.ImageIO.write(prpd4YOLO, "png", file);
                status.setText("YOLO IMG saved to " + file.getName());
            } catch (Exception ex) {
                ex.printStackTrace();
                status.setText("Failed to save YOLO image: " + ex.getMessage());
            }
        }
    }

    private void exportPRPD4YOLO(String fileName) {'''

text = text.replace(old_method, new_method)

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
