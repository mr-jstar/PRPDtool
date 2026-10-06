with open('src/prpdtool/RedPitayaTriggerDialog.java', 'r', encoding='utf-8') as f:
    text = f.read()

import re

# 1. Update margins
old_margins = '''            int left = 78;
            int top = 22;
            int right = 24;
            int bottom = 54;'''

new_margins = '''            int left = 85;
            int top = 22;
            int right = 35;
            int bottom = 54;'''
text = text.replace(old_margins, new_margins)

# 2. Update formatTick
old_format_tick = '''        private static String formatTick(double value) {
            double abs = Math.abs(value);
            if ((abs > 0.0 && abs < 0.001) || abs >= 10_000.0) {
                return String.format("%.6e", value);
            }
            if (abs < 10.0) {
                return String.format("%.8g", value);
            }
            return String.format("%.8g", value);
        }'''

new_format_tick = '''        private static String formatTick(double value) {
            if (Math.abs(value) < 1e-12) return "0";
            String s = String.format(java.util.Locale.US, "%.4g", value);
            if (s.contains("e") || s.contains("E")) return s;
            if (s.indexOf('.') > 0) {
                s = s.replaceAll("0*$", "").replaceAll("\\\\.$", "");
            }
            return s;
        }'''
text = text.replace(old_format_tick, new_format_tick)

with open('src/prpdtool/RedPitayaTriggerDialog.java', 'w', encoding='utf-8') as f:
    f.write(text)
