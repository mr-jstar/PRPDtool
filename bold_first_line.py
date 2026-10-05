with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

import re

old_html_tooltip = '''    private String htmlTooltip(String text) {
        StringBuilder html = new StringBuilder("<html><div style='width: 340px; white-space: normal;'>");
        String separator = "";
        for (String line : text.split("\\\\R", -1)) {
            String escaped = line.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\\"", "&quot;")
                    .replace("'", "&#39;");
            html.append(separator).append(escaped);
            separator = "<br>";
        }
        html.append("</div></html>");
        return html.toString();
    }'''

new_html_tooltip = '''    private String htmlTooltip(String text) {
        StringBuilder html = new StringBuilder("<html><div style='width: 340px; white-space: normal;'>");
        String[] lines = text.split("\\\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String escaped = lines[i].replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\\"", "&quot;")
                    .replace("'", "&#39;");
            if (i == 0 && lines.length > 1 && !escaped.isEmpty()) {
                html.append("<b>").append(escaped).append("</b>");
            } else {
                html.append(escaped);
            }
            if (i < lines.length - 1) {
                html.append("<br>");
            }
        }
        html.append("</div></html>");
        return html.toString();
    }'''

text = text.replace(old_html_tooltip, new_html_tooltip)

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
