with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

import re

old_live_duration = '''            double durationS = 0.5;
            long totalSamples = (long) (config.sampleRate() * durationS);
            if (totalSamples < config.normalizedFrameSize()) {
                totalSamples = config.normalizedFrameSize();
            }
            config = config.forTotalSamples(totalSamples);'''

new_live_duration = '''            // W trybie Live View ustawiamy bardzo długi czas trwania okna (np. 1 godzina),
            // aby pętla w RedPitayaSignalReader nie zamykała i nie otwierała ponownie gniazda TCP co chwilę.
            // Dzięki mniejszemu rozmiarowi ramki (131072) dane będą spływać z FPGA nieprzerwanie
            // i niezwykle płynnie bez zacięć (stutteringu).
            double durationS = 3600.0;
            long totalSamples = (long) (config.sampleRate() * durationS);
            config = config.forTotalSamples(totalSamples);'''

text = text.replace(old_live_duration, new_live_duration)

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
