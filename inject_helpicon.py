with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

help_icon_code = '''
    private static class HelpIcon implements javax.swing.Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(150, 150, 150));
            g2.fillOval(x, y, getIconWidth(), getIconHeight());
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("SansSerif", Font.BOLD, 12));
            FontMetrics fm = g2.getFontMetrics();
            int textWidth = fm.stringWidth("?");
            int textHeight = fm.getAscent();
            g2.drawString("?", x + (getIconWidth() - textWidth) / 2, y + (getIconHeight() + textHeight) / 2 - 2);
            g2.dispose();
        }
        @Override
        public int getIconWidth() { return 16; }
        @Override
        public int getIconHeight() { return 16; }
    }
'''

text = text.replace('    private String htmlTooltip(String text) {', help_icon_code + '\\n    private String htmlTooltip(String text) {')

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
