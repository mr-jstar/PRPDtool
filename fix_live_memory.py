with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

import re

old_buffer_read = '''            @Override
            public void bufferRead(Buffer buffer) {
                receivedSignalCache.add(buffer);
                interactiveSignalPanel.addBuffer(buffer);
            }'''

new_buffer_read = '''            @Override
            public void bufferRead(Buffer buffer) {
                // W trybie Live View ignorujemy surowy przebieg, 
                // aby nie zapchać pamięci RAM i nie wieszać UI na rysowaniu milionów punktów.
                if (!isLiveView) {
                    receivedSignalCache.add(buffer);
                    interactiveSignalPanel.addBuffer(buffer);
                }
            }'''
            
text = text.replace(old_buffer_read, new_buffer_read)

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
