with open('src/pipeline/DynamicPRPDHistogramData.java', 'r', encoding='utf-8') as f:
    text = f.read()

import re

old_add = '''    public void addPulses(Pulses p) {
        if (p == null || p.n == 0) return;
        
        int added = p.n;
        int startIndex = 0;
        
        if (p.n > 100000) { // Limit for a single update just in case
            added = 100000;
            startIndex = p.n - 100000;
        }
        
        ensureCapacity(size + added);'''

new_add = '''    public void addPulses(Pulses p) {
        if (p == null || p.n == 0) return;
        
        int added = p.n;
        int startIndex = 0;
        
        if (p.n > 100000) { // Limit for a single update just in case
            added = 100000;
            startIndex = p.n - 100000;
        }
        
        int MAX_TOTAL_PULSES = 5000000;
        if (size + added > MAX_TOTAL_PULSES) {
            int toRemove = (size + added) - MAX_TOTAL_PULSES;
            if (toRemove < size) {
                System.arraycopy(phases, toRemove, phases, 0, size - toRemove);
                System.arraycopy(amps, toRemove, amps, 0, size - toRemove);
                System.arraycopy(times, toRemove, times, 0, size - toRemove);
                size -= toRemove;
            } else {
                size = 0;
            }
        }
        
        ensureCapacity(size + added);'''
text = text.replace(old_add, new_add)

with open('src/pipeline/DynamicPRPDHistogramData.java', 'w', encoding='utf-8') as f:
    f.write(text)
