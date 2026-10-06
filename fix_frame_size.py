with open('src/redpitaya/RedPitayaConfig.java', 'r', encoding='utf-8') as f:
    text = f.read()

# Replace normalizedFrameSize
old_frame_size = '''    public int normalizedFrameSize() {
        return 1048576;
    }'''

new_frame_size = '''    public int normalizedFrameSize() {
        // Zmniejszamy rozmiar ramki TCP z 1MB na 128KB (~131k próbek)
        // Daje to ~8x szybsze odświeżanie w Live View (np. co 67ms przy dec=64 zamiast co 537ms)
        return 131072;
    }'''
text = text.replace(old_frame_size, new_frame_size)

with open('src/redpitaya/RedPitayaConfig.java', 'w', encoding='utf-8') as f:
    f.write(text)
