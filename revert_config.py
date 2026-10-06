with open('src/redpitaya/RedPitayaConfig.java', 'r', encoding='utf-8') as f:
    text = f.read()

import re

old_frame = '''    public int normalizedFrameSize() {
        // Zmniejszamy rozmiar ramki TCP z 1MB na 128KB (~131k próbek)
        // Daje to ~8x szybsze odświeżanie w Live View (np. co 67ms przy dec=64 zamiast co 537ms)
        return 131072;
    }'''

new_frame = '''    public int normalizedFrameSize() {
        return 1048576;
    }'''
text = text.replace(old_frame, new_frame)

with open('src/redpitaya/RedPitayaConfig.java', 'w', encoding='utf-8') as f:
    f.write(text)
