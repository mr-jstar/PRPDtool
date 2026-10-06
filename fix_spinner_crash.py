with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

# Fix max duration spinner limit
old_spinner = 'rpDurationSpinner = new JSpinner(new SpinnerNumberModel(configDouble(RP_DURATION, 0.01), 0.000001, 3600.0, 0.000001));'
new_spinner = 'rpDurationSpinner = new JSpinner(new SpinnerNumberModel(Math.min(100000.0, configDouble(RP_DURATION, 0.01)), 0.000001, 100000.0, 0.000001));'
text = text.replace(old_spinner, new_spinner)

# Fix startLiveView to not save hacked config
old_live = '''            config = config.forTotalSamples(totalSamples);
            
            saveRedPitayaConfig(config);
            isLiveView = true;'''
new_live = '''            saveRedPitayaConfig(config); // Zapisujemy oryginalne ustawienia użytkownika!
            config = config.forTotalSamples(totalSamples); // Modyfikujemy kopię w locie
            isLiveView = true;'''
text = text.replace(old_live, new_live)

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
