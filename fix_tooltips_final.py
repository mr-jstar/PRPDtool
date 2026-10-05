with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

import re

# 1. Fix tooltips wrapping in initUI
old_tooltip_setup = '''                JLabel label = new JLabel(p.name);
                String tooltipText = paramHelpText(p.name);
                label.setToolTipText(tooltipText);
                
                JLabel help = new JLabel(new HelpIcon());
                help.setToolTipText(htmlTooltip(tooltipText));
                help.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
                help.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
                
                labelPanel.add(label, BorderLayout.CENTER);
                labelPanel.add(help, BorderLayout.EAST);

                JTextField field = new JTextField(p.getText(), 8);
                p.setField(field);
                field.setToolTipText(tooltipText);'''

new_tooltip_setup = '''                JLabel label = new JLabel(p.name);
                String tooltipText = paramHelpText(p.name);
                label.setToolTipText(htmlTooltip(tooltipText));
                
                JLabel help = new JLabel(new HelpIcon());
                help.setToolTipText(htmlTooltip(tooltipText));
                help.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
                help.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
                
                labelPanel.add(label, BorderLayout.CENTER);
                labelPanel.add(help, BorderLayout.EAST);

                JTextField field = new JTextField(p.getText(), 8);
                p.setField(field);
                field.setToolTipText(htmlTooltip(tooltipText));'''
text = text.replace(old_tooltip_setup, new_tooltip_setup)

# 2. Rename parameters in Param[] array
text = text.replace('Param.dbl("Zero-crossing instant", () -> t0, v -> t0 = v),', 'Param.dbl("Zero-crossing instant [s]", () -> t0, v -> t0 = v),')
text = text.replace('Param.dbl("Pulse ampl. threshold", () -> threshold, v -> threshold = v),', 'Param.dbl("Pulse ampl. threshold [V]", () -> threshold, v -> threshold = v),')
text = text.replace('Param.dbl("Histogram min", () -> ampMin, v -> ampMin = v),', 'Param.dbl("Histogram min [V]", () -> ampMin, v -> ampMin = v),')
text = text.replace('Param.dbl("Histogram max", () -> ampMax, v -> ampMax = v)', 'Param.dbl("Histogram max [V]", () -> ampMax, v -> ampMax = v)')

# 3. Rename setParamField strings everywhere
text = text.replace('"Zero-crossing instant"', '"Zero-crossing instant [s]"')
text = text.replace('"Pulse ampl. threshold"', '"Pulse ampl. threshold [V]"')
text = text.replace('"Histogram min"', '"Histogram min [V]"')
text = text.replace('"Histogram max"', '"Histogram max [V]"')

# 4. Rewrite paramHelpText completely
old_param_help = re.compile(r'    private String paramHelpText\(String key\) \{.*?        \};\n    \}', re.DOTALL)

new_param_help = '''    private String paramHelpText(String key) {
        return switch (key) {
            case "Basic frequency [Hz]" ->
                "<b>Częstotliwość sieci.</b><br><br>"
                + "Częstotliwość napięcia zasilającego. Służy do synchronizacji fazy na wykresie PRPD oraz usunięcia wolnego falowania tła.<br><br>"
                + "<b>Wartości:</b> Zazwyczaj 50.0 dla Europy lub 60.0 dla USA.";
            case "Zero-crossing instant [s]" ->
                "<b>Korekcja przesunięcia wykresu w poziomie.</b><br><br>"
                + "Określa ułamek sekundy, w którym napięcie przechodzi przez zero. Ustalane automatycznie przez program.<br><br>"
                + "<b>Wartości:</b> Zmieniając ten parametr ręcznie offline (np. dodając 0.005) przesuniesz cały wykres PRPD o 90 stopni. Przydatne do manualnej korekcji fazy.";
            case "Sampling frequency [Hz]" ->
                "<b>Częstotliwość próbkowania.</b><br><br>"
                + "Określa ile punktów pomiarowych na sekundę zebrała Red Pitaya. Ustalane automatycznie.<br><br>"
                + "<b>Wartości:</b> Np. 1953125.0 dla decymacji 64. Edytuj wyłącznie jeśli wgrywasz stary, surowy plik .txt bez nagłówka konfiguracyjnego.";
            case "Pulse ampl. threshold [V]" ->
                "<b>Próg odcięcia szumu (w Woltach).</b><br><br>"
                + "Wszystkie impulsy, których napięcie jest MNIEJSZE od tej wartości, są traktowane jako szum tła i ignorowane.<br><br>"
                + "<b>Wartości:</b> Typowo 0.005 do 0.05. Podnoś delikatnie wartość, aż z ekranu zniknie gęsty szum tła, a zostaną same wyraźne impulsy wyładowań.";
            case "Dead time [us]" ->
                "<b>Czas blokady detektora (w mikrosekundach).</b><br><br>"
                + "Czas po wykryciu impulsu, przez który program 'zamyka oczy' i nie zlicza kolejnych. Zapobiega to błędnemu zliczaniu jednego długiego (falującego) piku jako wielu małych.<br><br>"
                + "<b>Wartości:</b> Typowo od 5.0 do 20.0 us.";
            case "HPF cutoff frequency [Hz]" ->
                "<b>Odcięcie filtru High-Pass.</b><br><br>"
                + "Usuwa z sygnału powolne falowania (w tym sinusoidę 50 Hz i wolne szumy). Przepuszcza tylko ultra-szybkie 'strzały' wyładowań niezupełnych.<br><br>"
                + "<b>Wartości:</b> Zazwyczaj 50000.0 (50 kHz) lub 100000.0 (100 kHz).";
            case "Filter Q" ->
                "<b>Dobroć filtru (Q).</b><br><br>"
                + "Określa stromość i charakterystykę filtru cyfrowego w punkcie odcięcia.<br><br>"
                + "<b>Wartości:</b> Najlepiej zostawić domyślne 0.707 (filtr Butterwortha), aby uniknąć wprowadzania sztucznego 'dzwonienia' do sygnału.";
            case "Filter Order" ->
                "<b>Rząd filtru cyfrowego.</b><br><br>"
                + "Określa jak ostro i brutalnie filtr ucina niechciane częstotliwości.<br><br>"
                + "<b>Wartości:</b> Typowo 2, 4 lub 8. Wyższe wartości tną sygnał idealniej, ale znacząco obciążają procesor komputera i mogą zniekształcać drobne piki.";
            case "Histogram min [V]" ->
                "<b>Dolna granica osi Y wykresu (w Woltach).</b><br><br>"
                + "Ustala fizyczny limit dla renderowanego obrazka PRPD.<br><br>"
                + "<b>Tip:</b> Kliknij przycisk 'Fit' obok, a program sam dopasuje idealne granice z danych, żeby wykres miał maksymalną ostrość.";
            case "Histogram max [V]" ->
                "<b>Górna granica osi Y wykresu (w Woltach).</b><br><br>"
                + "Ustala absolutny limit pionowy. Bezpośrednio wpływa na fizyczną rozdzielczość wewnętrznej macierzy generowanego wykresu.<br><br>"
                + "<b>Tip:</b> Użyj przycisku 'Fit', by program sam obliczył tę wartość na podstawie najwyższego piku w Twoich danych.";
            default ->
                "";
        };
    }'''

text = old_param_help.sub(new_param_help, text)

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
