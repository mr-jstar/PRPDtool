package prpdtool;

import java.util.*;

/**
 * Knowledge base and interactive guide for the Single Pulse Waveform & FFT Scope.
 * Explains chart architectures, X/Y axes justification, mathematical definitions,
 * physical meanings of metrics, CIGRE T-F classification, and numerical benchmarks
 * ("dużo czy mało" - comparison of real PDs, noise, and synthetic AWG generator signals).
 * Rendered with native Swing HTML formatting (tables, sub, sup, unicode math symbols).
 */
public class SinglePulseScopeDoc {

    public static class Entry {
        public final String key;
        public final String category;
        public final String titlePl;
        public final String subtitleEn;
        public final String icon;
        public final String shortSummary;
        private final HtmlProvider htmlProvider;

        public Entry(String key, String category, String titlePl, String subtitleEn,
                     String icon, String shortSummary, HtmlProvider htmlProvider) {
            this.key = key;
            this.category = category;
            this.titlePl = titlePl;
            this.subtitleEn = subtitleEn;
            this.icon = icon;
            this.shortSummary = shortSummary;
            this.htmlProvider = htmlProvider;
        }

        public String getFullHtml(boolean dark) {
            return htmlProvider.getHtml(dark);
        }

        @Override
        public String toString() {
            return titlePl;
        }
    }

    @FunctionalInterface
    public interface HtmlProvider {
        String getHtml(boolean dark);
    }

    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();

    static {
        register(createOverviewEntry());
        register(createPulseSelectionEntry());
        register(createChartTimeEntry());
        register(createChartFreqEntry());
        register(createDspSettingsEntry());
        register(createTimeMetricsEntry());
        register(createQPeakEntry());
        register(createRiseTimeEntry());
        register(createPulseWidthEntry());
        register(createFallTimeEntry());
        register(createEnergyEntry());
        register(createTeqEntry());
        register(createFreqMetricsEntry());
        register(createFPeakEntry());
        register(createFCentEntry());
        register(createBwEntry());
        register(createFeqEntry());
        register(createCigreTfEntry());
        register(createBenchmarksEntry());
        register(createBibliographyEntry());
    }

    private static void register(Entry e) {
        ENTRIES.put(e.key.toUpperCase(), e);
    }

    public static List<Entry> getAll() {
        return new ArrayList<>(ENTRIES.values());
    }

    public static Entry get(String key) {
        if (key == null) return null;
        return ENTRIES.get(key.toUpperCase());
    }

    // =========================================================================
    // HTML STYLING HELPERS (Swing JEditorPane compatible)
    // =========================================================================

    private static String page(String titlePl, String subtitleEn, String bodyHtml, boolean dark) {
        String bg = dark ? "#1e2127" : "#ffffff";
        String text = dark ? "#dcdcdc" : "#1f2937";
        String titleColor = dark ? "#00e1ff" : "#0284c7";
        String subColor = dark ? "#94a3b8" : "#64748b";

        return "<html><body style='font-family: Segoe UI, Arial, sans-serif; font-size: 11px; color: " + text + "; background-color: " + bg + "; margin: 14px;'>"
                + "<div style='font-size: 16px; font-weight: bold; color: " + titleColor + "; margin-bottom: 2px;'>"
                + titlePl + "</div>"
                + "<div style='font-size: 12px; color: " + subColor + "; margin-bottom: 14px;'>"
                + subtitleEn + "</div>"
                + bodyHtml
                + "<div style='margin-top: 20px; padding-top: 8px; border-top: 1px solid " + (dark ? "#333842" : "#e2e8f0") + "; font-size: 10px; color: #888888; text-align: right;'>"
                + "PRPD Diagnostic Tool &bull; Standard IEC 60270 / IEC 62478 / CIGRE WG D1.33</div>"
                + "</body></html>";
    }

    private static String sectionBox(String title, String colorHex, String contentHtml, boolean dark) {
        String boxBg = dark ? "#282c34" : "#f8fafc";
        String border = dark ? "#3e4451" : "#cbd5e1";
        return "<div style='background-color: " + boxBg + "; border: 1px solid " + border + "; border-left: 4px solid " + colorHex + "; padding: 10px 12px; border-radius: 4px; margin-bottom: 12px;'>"
                + "<div style='font-size: 12px; font-weight: bold; color: " + colorHex + "; margin-bottom: 6px;'>" + title + "</div>"
                + "<div style='line-height: 1.45;'>" + contentHtml + "</div>"
                + "</div>";
    }

    private static String alertBox(String title, String message, String type, boolean dark) {
        String borderCol = "#38bdf8";
        String bgCol = dark ? "#1a2a3a" : "#eff6ff";
        String titleCol = dark ? "#7dd3fc" : "#0369a1";

        if ("WARNING".equals(type)) {
            borderCol = "#f59e0b";
            bgCol = dark ? "#2c2214" : "#fefce8";
            titleCol = dark ? "#fde047" : "#b45309";
        } else if ("DANGER".equals(type)) {
            borderCol = "#ef4444";
            bgCol = dark ? "#301818" : "#fef2f2";
            titleCol = dark ? "#fca5a5" : "#b91c1c";
        } else if ("SUCCESS".equals(type)) {
            borderCol = "#10b981";
            bgCol = dark ? "#142c20" : "#f0fdf4";
            titleCol = dark ? "#86efac" : "#15803d";
        }

        return "<div style='background-color: " + bgCol + "; border: 1px solid " + borderCol + "; padding: 8px 12px; border-radius: 4px; margin: 10px 0;'>"
                + "<b style='color: " + titleCol + "; font-size: 11px;'>" + title + "</b><br>"
                + "<span style='font-size: 11px;'>" + message + "</span>"
                + "</div>";
    }

    public static String eqFraction(String lhs, String num, String den, String extra, String note, boolean dark) {
        String textColor = dark ? "#e2f1ff" : "#111827";
        String lineColor = dark ? "#38bdf8" : "#0284c7";
        String bg = dark ? "#17191e" : "#f1f5f9";
        String border = dark ? "#2d333f" : "#cbd5e1";
        String noteColor = dark ? "#94a3b8" : "#64748b";

        String noteHtml = (note != null && !note.isEmpty())
                ? "<div style='font-family: Segoe UI, sans-serif; font-size: 10px; color: " + noteColor + "; margin-top: 5px;'>" + note + "</div>"
                : "";

        StringBuilder sb = new StringBuilder();
        sb.append("<div style='background-color: ").append(bg)
                .append("; border: 1px solid ").append(border)
                .append("; border-left: 3px solid #00d4ff; padding: 7px 12px; border-radius: 4px; margin: 6px 0;'>");

        sb.append("<table cellpadding='0' cellspacing='0' border='0'>");
        sb.append("<tr>");
        if (lhs != null && !lhs.isEmpty()) {
            sb.append("<td style='vertical-align: middle; color: ").append(textColor)
                    .append("; font-family: Cambria, Georgia, serif; font-size: 13px; padding-right: 6px;'>")
                    .append(lhs).append("</td>");
        }
        sb.append("<td style='vertical-align: middle; text-align: center;'>");
        sb.append("<table cellpadding='0' cellspacing='0' border='0'>");
        sb.append("<tr><td style='border-bottom: 1px solid ").append(lineColor)
                .append("; color: ").append(textColor)
                .append("; font-family: Cambria, Georgia, serif; font-size: 12px; text-align: center; padding: 1px 6px;'>")
                .append(num).append("</td></tr>");
        sb.append("<tr><td style='color: ").append(textColor)
                .append("; font-family: Cambria, Georgia, serif; font-size: 12px; text-align: center; padding: 1px 6px;'>")
                .append(den).append("</td></tr>");
        sb.append("</table>");
        sb.append("</td>");

        if (extra != null && !extra.isEmpty()) {
            sb.append("<td style='vertical-align: middle; color: ").append(textColor)
                    .append("; font-family: Cambria, Georgia, serif; font-size: 13px; padding-left: 6px;'>")
                    .append(extra).append("</td>");
        }
        sb.append("</tr>");
        sb.append("</table>");

        sb.append(noteHtml);
        sb.append("</div>");
        return sb.toString();
    }

    public static String eqSimple(String mathHtml, String note, boolean dark) {
        String textColor = dark ? "#e2f1ff" : "#111827";
        String bg = dark ? "#17191e" : "#f1f5f9";
        String border = dark ? "#2d333f" : "#cbd5e1";
        String noteColor = dark ? "#94a3b8" : "#64748b";

        String noteHtml = (note != null && !note.isEmpty())
                ? "<div style='font-family: Segoe UI, sans-serif; font-size: 10px; color: " + noteColor + "; margin-top: 5px;'>" + note + "</div>"
                : "";

        return "<div style='background-color: " + bg
                + "; border: 1px solid " + border
                + "; border-left: 3px solid #00d4ff; padding: 7px 12px; border-radius: 4px; margin: 6px 0; font-family: Cambria, Georgia, serif; font-size: 13px; color: " + textColor + ";'>"
                + "<div>" + mathHtml + "</div>"
                + noteHtml
                + "</div>";
    }

    private static String tableRow(String col1, String col2, String col3, String col4, boolean isHeader, boolean dark) {
        String bg = isHeader ? (dark ? "#323844" : "#e2e8f0") : "transparent";
        String fw = isHeader ? "bold" : "normal";
        String text = isHeader ? (dark ? "#00e1ff" : "#0369a1") : (dark ? "#dcdcdc" : "#1e293b");
        String border = dark ? "#3e4451" : "#cbd5e1";

        return "<tr style='background-color: " + bg + "; font-weight: " + fw + "; color: " + text + ";'>"
                + "<td style='border: 1px solid " + border + "; padding: 5px 8px;'>" + col1 + "</td>"
                + "<td style='border: 1px solid " + border + "; padding: 5px 8px;'>" + col2 + "</td>"
                + "<td style='border: 1px solid " + border + "; padding: 5px 8px;'>" + col3 + "</td>"
                + "<td style='border: 1px solid " + border + "; padding: 5px 8px;'>" + col4 + "</td>"
                + "</tr>";
    }

    private static String tableRow(String c1, String c2, String c3, boolean isHeader, boolean dark) {
        String bg = isHeader ? (dark ? "#323844" : "#e2e8f0") : "transparent";
        String fw = isHeader ? "bold" : "normal";
        String text = isHeader ? (dark ? "#00e1ff" : "#0369a1") : (dark ? "#dcdcdc" : "#1e293b");
        String border = dark ? "#3e4451" : "#cbd5e1";

        return "<tr style='background-color: " + bg + "; font-weight: " + fw + "; color: " + text + ";'>"
                + "<td style='border: 1px solid " + border + "; padding: 5px 8px;'>" + c1 + "</td>"
                + "<td style='border: 1px solid " + border + "; padding: 5px 8px;'>" + c2 + "</td>"
                + "<td style='border: 1px solid " + border + "; padding: 5px 8px;'>" + c3 + "</td>"
                + "</tr>";
    }

    // =========================================================================
    // 1. OVERVIEW
    // =========================================================================

    private static Entry createOverviewEntry() {
        return new Entry(
                "OVERVIEW",
                "Wprowadzenie i obsługa",
                "Przegląd okna i cel analizy pojedynczych impulsów WNZ",
                "Single Pulse Waveform & FFT Scope Overview (IEC 60270 / CIGRE)",
                "[📈]",
                "Cel badania pojedynczego impulsu, różnice względem zbiorczego wykresu PRPD.",
                dark -> page(
                        "Wprowadzenie: Analiza pojedynczego impulsu WNZ w czasie i częstotliwości",
                        "Single Pulse Waveform & FFT Scope Overview &bull; Standardy IEC 60270, IEC 62478, CIGRE WG D1.33",
                        sectionBox("Dlaczego analiza pojedynczego impulsu jest kluczowa w diagnostyce WNZ?", "#00e1ff",
                                "<p>Klasyczny wykres fazowo-rozdzielczy <b>PRPD (Phase-Resolved Partial Discharge)</b> przedstawia zagregowany obraz tysięcy impulsów w funkcji fazy napięcia probierczego. "
                                        + "Chociaż jest on podstawą normy <b>IEC 60270</b>, ma jedną fundamentalną wadę: <b>nie pozwala bezpośrednio rozróżnić kształtu fali i mechanizmu fizycznego wyładowania</b>, gdy w obiekcie występuje kilka defektów jednocześnie lub gdy sygnałowi towarzyszą zakłócenia impulsowe i sinusoidalne.</p>"
                                        + "<p>Okno <b>Single Pulse Waveform & FFT Scope</b> udostępnia możliwość podejrzenia <b>dokładnego, nieskompresowanego przebiegu czasowego fali pojedynczego impulsu</b> z rozdzielczością nanosekundową (125 MS/s, próbkowanie co 8 ns) oraz jego <b>widma częstotliwościowego FFT</b>. "
                                        + "Umożliwia to:</p>"
                                        + "<ul>"
                                        + "<li><b>Identyfikację rodzaju defektu:</b> Czas narastania i pasmo FFT bezbłędnie odróżniają wyładowania w gazie (SF6 / powietrze – nanosekundy) od wyładowań wtrętowych w dielektryku stałym i powierzchniowych (setki nanosekund).</li>"
                                        + "<li><b>Oddzielenie szumu i zakłóceń:</b> Płaskie sygnały z generatora funkcyjnego, zakłócenia komutacyjne z falowników oraz stacje radiowe (NBI) mają zupełnie inny profil czasowo-częstotliwościowy niż naturalne wyładowania WNZ.</li>"
                                        + "<li><b>Klasyfikację CIGRE T-F:</b> Odwzorowanie impulsu na mapie czas-częstotliwość wg wytycznych grupy roboczej CIGRE WG D1.33.</li>"
                                        + "<li><b>Weryfikację kalibracji ładunku pozornego:</b> Podgląd wartości szczytowej i całkowej w miliwoltach [mV] lub bezpośrednio w pikocoulombach [pC].</li>"
                                        + "</ul>", dark)
                                + alertBox("Wskazówka praktyczna",
                                "W oknie Scope możesz przeglądać impulsy za pomocą przycisków <b>◀ Poprzedni</b> i <b>Następny ▶</b>, wybrać konkretny impuls z listy rozwijanej lub natychmiast przeskoczyć do najsilniejszego impulsu przyciskiem <b>★ Najwyższy (Qmax)</b>.",
                                "INFO", dark)
                                + sectionBox("Struktura okna oscyloskopu", dark ? "#38bdf8" : "#0284c7",
                                "<table cellpadding='0' cellspacing='0' style='width: 100%; border-collapse: collapse; font-size: 11px;'>"
                                        + tableRow("Obszar interfejsu", "Zawartość", "Rola diagnostyczna", "Standard", true, dark)
                                        + tableRow("Wykres górny", "Przebieg czasowy (Time Scope)", "Kształt czoła i grzbietu impulsu, czas narastania tr, tłumienie oscylacji", "IEC 60270 / IEC 62478", false, dark)
                                        + tableRow("Wykres dolny", "Widmo częstotliwościowe FFT", "Rozkład energii w paśmie HF/VHF (0 – 62.5 MHz), pik f_peak, środek ciężkości", "IEC 62478 / CIGRE D1.33", false, dark)
                                        + tableRow("Prawy panel", "Parametry czasowe i widmowe", "Liczbowe wskaźniki kształtu impulsu, energii E, czasu i częstotliwości CIGRE", "CIGRE TF 15/33.03.05", false, dark)
                                        + tableRow("Dolny pasek", "Eksport PNG / CSV", "Zrzut wykresu wysokiej rozdzielczości lub surowych danych punkt po punkcie", "Raportowanie", false, dark)
                                        + "</table>", dark),
                        dark)
        );
    }

    // =========================================================================
    // 2. PULSE SELECTION & RETENTION
    // =========================================================================

    private static Entry createPulseSelectionEntry() {
        return new Entry(
                "PULSE_SELECTION",
                "Wprowadzenie i obsługa",
                "Wybór impulsu, priorytetyzacja sygnałów i ochrona Qmax",
                "Pulse Selection, Significant Discharges Retention & Qmax Persistence",
                "[📦]",
                "Zasada inteligentnego bufora, eliminacja zalewania szumem i sortowanie od najwyższych wyładowań.",
                dark -> page(
                        "Mechanizm rejestracji impulsów i inteligentna priorytetyzacja",
                        "Significance-Aware Buffer Architecture &bull; Amplitude Ranking &bull; Qmax Retention",
                        sectionBox("Jak impulsy trafiają na listę?", "#00e1ff",
                                "<p>Przetwornik Red Pitaya STEMlab 125-14 pobiera próbki w sposób ciągły z częstotliwością <b>125 MS/s</b> (próbka co 8 ns). "
                                        + "Podczas pomiaru w czasie rzeczywistym lub odczytu pliku binarnego (<code>.rppr.bin</code>), silnik akwizycji identyfikuje zjawiska wyładowcze.</p>"
                                        + "<p>Po wykryciu czoła impulsu, bufor zapisuje <b>pełny wycinek fali o długości 1024 próbek (8.192 &mu;s)</b> bez jakiejkolwiek decymacji:</p>"
                                        + "<ul>"
                                        + "<li><b>256 próbek przed pikiem (pre-trigger = 2.048 &mu;s):</b> rejestruje poziom szumu tła i strome czoło narastania.</li>"
                                        + "<li><b>768 próbek po piku (post-trigger = 6.144 &mu;s):</b> rejestruje grzbiet, czas opadania oraz tłumione oscylacje dielektryka.</li>"
                                        + "</ul>", dark)
                                + sectionBox("Ochrona przed zalaniem szumem tła (Significance-Aware Retention)", "#10b981",
                                "<p>Gdy próg detekcji ustawiony jest nisko (np. 0 mV), przetwornik rejestruje tysiące drobnych wahań szumowych (amplituda rzędu 1.0 – 1.5 mV). "
                                        + "W tradycyjnym buforze FIFO szum natychmiast wyparłby prawdziwe wyładowania WNZ. "
                                        + "Nasz silnik stosuje <b>inteligentną priorytetyzację amplitudy</b> w pamięci podręcznej o pojemności 800 wyładowań:</p>"
                                        + "<ul>"
                                        + "<li><b>Pojemność bufora (np. 'Bufor: 800 WNZ'):</b> Wskaźnik na górnym pasku informuje, ile wyładowań znajduje się obecnie w podręcznym buforze oscyloskopu. Maksymalny rozmiar wynosi 800 impulsów, co w zupełności wystarcza do dokładnej analizy statystycznej i porównawczej.</li>"
                                        + "<li><b>Słaby szum tła nigdy nie wypiera silnych wyładowań:</b> Jeśli bufor jest pełny (800 impulsów), nowy impuls może zastąpić tylko impuls o <i>mniejszej</i> amplitudzie niż on sam. Szum termiczny o amplitudzie 1–2 mV jest natychmiast odrzucany, jeśli w buforze znajdują się wyładowania o wyższej energii.</li>"
                                        + "<li><b>Zachowanie charakterystycznych zjawisk:</b> Wszystkie znaczące wyładowania WNZ (od kilkunastu do setek miliwoltów) są trwale chronione w pamięci.</li>"
                                        + "<li><b>Gwarancja zachowania Qmax:</b> Impuls o najwyższej zarejestrowanej wartości szczytowej w całej sesji jest bezwzględnie chroniony i oznaczony gwiazdką <code>★ [Qmax]</code>.</li>"
                                        + "</ul>", dark)
                                + sectionBox("Tryby sortowania i filtrowania listy", dark ? "#38bdf8" : "#0284c7",
                                "<p>Za pomocą przełącznika <b>Widok listy</b> możesz wybrać sposób prezentacji impulsów:</p>"
                                        + "<ul>"
                                        + "<li><b>Najwyższe amplitudy (|Q| malejąco) [Zalecane]:</b> Impulsy są ułożone od najsilniejszego do najsłabszego. Pozwala to błyskawicznie porównać 50 lub 100 najbardziej krytycznych wyładowań.</li>"
                                        + "<li><b>Chronologicznie (wg czasu t):</b> Układ w kolejności występowania w badaniu.</li>"
                                        + "<li><b>Tylko wyładowania WNZ (|Q| &ge; 10 mV):</b> Całkowicie ukrywa szum pomiarowy poniżej 10 mV.</li>"
                                        + "</ul>", dark),
                        dark)
        );
    }

    // =========================================================================
    // 3. CHART TIME DOMAIN
    // =========================================================================

    private static Entry createChartTimeEntry() {
        return new Entry(
                "CHART_TIME",
                "Wykresy i osie współrzędnych",
                "Przebieg czasowy impulsu (Time Domain Scope) – osie i interpretacja",
                "Time Domain Scope Chart Architecture, X/Y Axes & Physical Meaning",
                "[⏱]",
                "Uzasadnienie osi X [ns] i Y [mV/pC], rola pre-triggera (t=0 w piku) oraz odróżnienie sygnału surowego od filtrowanego.",
                dark -> page(
                        "Przebieg czasowy impulsu WNZ: Budowa wykresu i interpretacja osi",
                        "Time Domain Waveform Scope &bull; 1024 Samples @ 125 MS/s &bull; Nanosecond Precision",
                        sectionBox("Za co odpowiada górny wykres przebiegu czasowego?", "#00e1ff",
                                "<p>Wykres górny prezentuje rzeczywistą ewolucję napięcia elektrycznego w czasie <i>u(t)</i> wywołaną pojedynczym przeskokiem ładunku w dielektryku. "
                                        + "Pokazuje on fizyczny charakter zjawiska: szybkość narastania prądu plazmowego, szerokość impulsu oraz rezonanse obwodu pomiarowego.</p>"
                                        + "<p>Za pomocą rozwijanej listy <b>Sygnał czasowy</b> możesz wybrać:</p>"
                                        + "<ul>"
                                        + "<li><b>Filtrowany (Filtered) [Błękitny]:</b> Przebieg po przejściu przez cyfrowy filtr pasmowoprzepustowy (np. 1–20 MHz) lub bank filtrów notch. Usunięte są zakłócenia sieciowe 50 Hz oraz szum poza pasmem.</li>"
                                        + "<li><b>Surowy (Raw) [Pomarańczowy]:</b> Czysty sygnał prosto z 14-bitowego przetwornika ADC Red Pitaya. Pozwala ocenić, czy filtr nie zniekształcił czoła impulsu.</li>"
                                        + "<li><b>Oba sygnały (Overlay):</b> Nałożenie obu przebiegów na siebie dla precyzyjnego porównania efektywności filtracji.</li>"
                                        + "</ul>", dark)
                                + sectionBox("Uzasadnienie osi współrzędnych X i Y", dark ? "#38bdf8" : "#0284c7",
                                "<p><b>1. Oś pozioma X [ns] (Czas względny):</b></p>"
                                        + "<ul>"
                                        + "<li><b>Dlaczego t = 0 ns znajduje się w punkcie szczytowym (Qp)?</b> "
                                        + "Jest to celowy zabieg diagnostyczny zgodny ze standardami oscyloskopowymi. Bufor rejestruje 256 próbek przed pikiem (pre-trigger = 2048 ns) oraz 768 próbek po piku (6144 ns). "
                                        + "Umieszczenie zera w punkcie maksimum sprawia, że <b>czas narastania czoła t<sub>r</sub> odczytujesz intuicyjnie na lewo od 0 (od &minus;t<sub>r</sub> do 0)</b>, natomiast <b>czas opadania t<sub>f</sub> i tłumione oscylacje na prawo od zera</b>. "
                                        + "Całkowity czas obserwacji okna wynosi 1024 &times; 8 ns = 8.192 &mu;s.</li>"
                                        + "</ul>"
                                        + "<p><b>2. Oś pionowa Y [mV] lub [pC / nC] (Amplituda chwilowa):</b></p>"
                                        + "<ul>"
                                        + "<li>W trybie domyślnym oś Y wyskalowana jest w <b>miliwoltach [mV]</b> napięcia chwilowego na wejściu Red Pitaya (zakres dynamiki ADC: &plusmn;1000 mV przy zworkach LV lub &plusmn;20 V przy HV).</li>"
                                        + "<li>Gdy włączona jest <b>Kalibracja ładunku pozornego IEC 60270</b>, oś pionowa oraz znacznik piku Q<sub>p</sub> automatycznie przeliczają się na pikocoulomby [pC] lub nanocoulomby [nC] w oparciu o współczynnik kalibratora k<sub>cal</sub>.</li>"
                                        + "</ul>", dark)
                                + sectionBox("Co kształt fali mówi o dielektryku?", "#10b981",
                                "<ul>"
                                        + "<li><b>Pojedyncza, ostra szpilka bez oscylacji (t<sub>r</sub> &lt; 20 ns):</b> Wyładowanie w gazie (SF6 w GIS lub ulot w powietrzu na ostrzu). Brak pojemnościowych oscylacji obwodu.</li>"
                                        + "<li><b>Impuls z tłumionymi oscylacjami wysokoczęstotliwościowymi (ringing):</b> Wyładowanie w maszynie wirującej (uzwojenie stojana generatora) lub transformatorze olejowym. Indukcyjność uzwojeń tworzy obwód rezonansowy LC.</li>"
                                        + "<li><b>Płaski wierzchołek o szerokości ponad 1 &mu;s (plateau):</b> Wskazuje na <b>sztuczny sygnał z generatora funkcyjnego (AWG)</b> lub impulsowe zakłócenie tyrystorowe/falownikowe. Naturalne wyładowania WNZ nigdy nie mają płaskiego plateau trwającego mikrosekundy!</li>"
                                        + "</ul>", dark),
                        dark)
        );
    }

    // =========================================================================
    // 4. CHART FREQ DOMAIN (FFT)
    // =========================================================================

    private static Entry createChartFreqEntry() {
        return new Entry(
                "CHART_FREQ",
                "Wykresy i osie współrzędnych",
                "Widmo częstotliwościowe FFT (Frequency Domain) – osie i skale",
                "FFT Frequency Domain Spectrum Chart, Nyquist Limit & dBmV Scale",
                "[📊]",
                "Uzasadnienie osi X [MHz] (limit Nyquista 62.5 MHz), skali decybelowej [dBmV] vs liniowej [mV] oraz doboru okna czasowego.",
                dark -> page(
                        "Widmo częstotliwościowe FFT: Budowa wykresu i analiza pasma",
                        "FFT Spectral Analysis &bull; DC to 62.5 MHz Nyquist &bull; dBmV vs Linear Scale",
                        sectionBox("Za co odpowiada dolny wykres FFT?", "#00e1ff",
                                "<p>Dolny wykres przedstawia <b>Dyskretną Transformatę Fouriera (1024-punktowe Radix-2 FFT)</b> obliczoną z zarejestrowanego wycinka fali. "
                                        + "Pozwala ona precyzyjnie ustalić, w których pasmach częstotliwości skupiona jest energia impulsu oraz odróżnić wąskopasmowe zakłócenia zewnętrzne (NBI) od szerokopasmowych wyładowań WNZ.</p>", dark)
                                + sectionBox("Uzasadnienie osi współrzędnych X i Y", dark ? "#38bdf8" : "#0284c7",
                                "<p><b>1. Oś pozioma X [MHz] (Częstotliwość):</b></p>"
                                        + "<ul>"
                                        + "<li>Oś obejmuje zakres od <b>0.0 MHz (składowa stała DC) do 62.5 MHz</b>.</li>"
                                        + "<li><b>Dlaczego akurat 62.5 MHz?</b> Zgodnie z twierdzeniem Nyquista-Shannona, maksymalna częstotliwość możliwa do bezbłędnego zrekonstruowania w układzie dyskretnym wynosi dokładnie połowę częstotliwości próbkowania:</li>"
                                        + "</ul>"
                                        + eqFraction("<b>f<sub>Nyquist</sub></b> =", "f<sub>s</sub>", "2", "= 125 MS/s / 2 = <b>62.5 MHz</b>", "Krok częstotliwościowy pojedynczego prążka FFT wynosi &Delta;f = 125 MHz / 1024 &approx; 122.07 kHz.", dark)
                                        + "<p><b>2. Oś pionowa Y: Skala Decybelowa [dBmV] vs Liniowa [mV]:</b></p>"
                                        + "<ul>"
                                        + "<li><b>Skala Decybelowa [dBmV] (Zalecana):</b> Wyraża amplitudę prążka w decybelach odniesionych do 1 mV RMS:</li>"
                                        + "</ul>"
                                        + eqFraction("<b>dBmV</b> = 20 &middot; log<sub>10</sub> (", "U<sub>RMS</sub>", "1 mV", ")", "Umożliwia jednoczesną obserwację składowych o ogromnej dynamice: 0 dB = 1 mV, +20 dB = 10 mV, +40 dB = 100 mV, natomiast &minus;40 dB = 0.01 mV (szum tła).", dark)
                                        + "<ul>"
                                        + "<li><b>Skala Liniowa [mV]:</b> Prezentuje bezwzględną amplitudę napięciową harmonicznych w miliwoltach. Przydatna do oceny udziału procentowego dominującej częstotliwości.</li>"
                                        + "</ul>", dark)
                                + sectionBox("Wskaźniki widmowe na wykresie", "#10b981",
                                "<ul>"
                                        + "<li><b>Żółta linia przerywana (fpk):</b> Częstotliwość dominującego piku widma f<sub>peak</sub>. Wskazuje składową o największej energii.</li>"
                                        + "<li><b>Niebieska linia przerywana (f_cent):</b> Środek ciężkości widma f<sub>cent</sub>. Wskazuje, czy rozkład energii przesuwa się w stronę pasma HF (2–15 MHz), czy VHF (20–60 MHz).</li>"
                                        + "</ul>", dark)
                                + sectionBox("Wybór okna FFT (Windowing)", dark ? "#f59e0b" : "#b45309",
                                "<p>Przekształcenie fouriera skończonego odcinka czasu obarczone jest zjawiskiem przecieku widma (spectral leakage). "
                                        + "Wybór okna pozwala kontrolować ten kompromis:</p>"
                                        + "<ul>"
                                        + "<li><b>Hanning (Zalecane):</b> Znakomity kompromis między rozdzielczością prążków a tłumieniem listków bocznych (tłumienie &gt; 31 dB).</li>"
                                        + "<li><b>Blackman:</b> Bardzo silne tłumienie listków bocznych (&gt; 58 dB), idealne do wykrywania słabych prążków WNZ w obecności silnego zakłócenia sinusoidalnego.</li>"
                                        + "<li><b>Hamming:</b> Dobra ostrość piku głównego.</li>"
                                        + "<li><b>Prostokątne (Rectangular):</b> Brak ważenia próbek – zalecane tylko wtedy, gdy cały impuls WNZ wygasa całkowicie wewnątrz okna do zera.</li>"
                                        + "</ul>", dark),
                        dark)
        );
    }

    // =========================================================================
    // 4b. DSP SETTINGS & FFT WINDOWING
    // =========================================================================

    private static Entry createDspSettingsEntry() {
        return new Entry(
                "DSP_SETTINGS",
                "Przetwarzanie sygnału i FFT",
                "Ustawienia analizy: Sygnał czasowy, skale FFT i okna czasowe",
                "Signal Conditioning, FFT Scaling & Windowing Functions (IEC 62478 / DSP)",
                "[🎛]",
                "Szczegółowe omówienie opcji: sygnał surowy vs filtrowany, skala dBmV vs liniowa oraz funkcje okienkowania FFT.",
                dark -> page(
                        "Ustawienia analizy: Sygnał czasowy, skale FFT i okna czasowe",
                        "Signal Conditioning, Spectral Scaling & FFT Windowing &bull; Standard IEC 62478",
                        sectionBox("1. Wybór sygnału czasowego (Sygnał czasowy)", "#00e1ff",
                                "<p>Opcja ta decyduje, jakie dane czasowe są wyświetlane na górnym wykresie oscyloskopu i przekazywane do transformaty Fouriera:</p>"
                                        + "<ul>"
                                        + "<li><b>Filtrowany (Filtered) [Zalecany]:</b> Sygnał po cyfrowej filtracji DSP (filtr górnoprzepustowy High-Pass 1 MHz lub 3 MHz oraz filtry pasmowozaporowe Notch). "
                                        + "Usuwa składową 50 Hz napięcia zasilania, dryft zera oraz szumy niskoczęstotliwościowe, odsłaniając rzeczywisty kształt nanosekundowego wyładowania plazmowego. "
                                        + "Wszystkie parametry czasowe (t<sub>r</sub>, t<sub>w</sub>, t<sub>f</sub>) są w tym trybie wyliczane z najwyższą dokładnością bez wpływu tętnień sieci.</li>"
                                        + "<li><b>Surowy (Raw):</b> Przebieg prosto z 14-bitowego przetwornika ADC Red Pitaya próbkowany z częstotliwością 125 MS/s bez żadnej obróbki cyfrowej. "
                                        + "Pozwala inżynierowi ocenić rzeczywisty poziom zakłóceń otoczenia, ewentualne nasycenie wejścia przetwornika (obcięcie szczytów fali – clipping) oraz zweryfikować, czy do układu dociera składowa sieciowa 50 Hz.</li>"
                                        + "<li><b>Oba sygnały (Overlay):</b> Równoczesne nałożenie przebiegu surowego i filtrowanego na jednym wspólnym wykresie. "
                                        + "Pozwala naocznie zweryfikować, jak filtr cyfrowy zmodyfikował sygnał — upewniając się, że filtr nie stłumił właściwej amplitudy szczytowej Q wyładowania WNZ.</li>"
                                        + "</ul>", dark)
                                + sectionBox("2. Skala widma częstotliwościowego (Skala FFT)", dark ? "#38bdf8" : "#0284c7",
                                "<p>Określa sposób skalowania pionowej osi Y na dolnym wykresie widma częstotliwościowego:</p>"
                                        + "<ul>"
                                        + "<li><b>Decybelowa [dBmV] [Zalecana]:</b> Skala logarytmiczna zdefiniowana jako: "
                                        + "<div style='margin: 4px 0 6px 12px; font-family: Cambria, serif; font-size: 12px;'><b>U<sub>dB</sub> = 20 &middot; log<sub>10</sub>( U / 1 mV )</b></div>"
                                        + "Zapewnia bardzo szeroki zakres dynamiki (od &minus;80 dBmV do +40 dBmV, czyli 1:10&nbsp;000). "
                                        + "Dzięki temu na jednym wykresie doskonale widać zarówno potężny rezonans dominujący (np. +10 dBmV), jak i słabe harmoniczne wysokich częstotliwości (np. &minus;30 dBmV) oraz poziom szumu tła (&minus;60 dBmV). Jest to standard przemysłowy w analizatorach widma wysokich częstotliwości (HF / VHF).</li>"
                                        + "<li><b>Liniowa [mV]:</b> Skala bezpośrednia w miliwoltach [mV]. Przedstawia bezwzględną amplitudę napięcia poszczególnych składowych harmonicznych. "
                                        + "Ułatwia szybki odczyt siły składowej podstawowej, jednak z powodu liniowej kompresji niemal całkowicie maskuje zjawiska o małej energii i szum tła.</li>"
                                        + "</ul>", dark)
                                + sectionBox("3. Okno czasowe FFT (Okno FFT) i problem wycieku widma", "#10b981",
                                "<p>Dyskretna Transformata Fouriera (FFT) zakłada, że rejestrowany bufor (1024 próbki) powtarza się w nieskończoność w czasie. "
                                        + "Jeżeli wartości sygnału na początku i na końcu bufora różnią się od siebie, na stykach bloków powstaje sztuczny skok napięcia (nieciągłość). "
                                        + "Powoduje to tzw. <b>wyciek widma (spectral leakage)</b> — energia z prawdziwych częstotliwości 'rozlewa się' na sąsiednie prążki, zniekształcając obraz.</p>"
                                        + "<p>Funkcje okienkujące (Windowing) mnożą sygnał przez łagodnie opadającą funkcję wagową, wygaszając krawędzie bufora do zera:</p>"
                                        + "<ul>"
                                        + "<li><b>Hanning (Hann) [Zalecane]:</b> Definiowane jako <i>w(n) = 0.5 &minus; 0.5 &middot; cos(2&pi;n / (N&minus;1))</i>. "
                                        + "Płynnie i symetrycznie sprowadza sygnał do zera na brzegach okna z tłumieniem listków bocznych &minus;31.5 dB. "
                                        + "Stanowi <b>złoty standard w diagnostyce WNZ</b>, ponieważ zapewnia optymalny kompromis pomiędzy dokładnością wskazania częstotliwości piku (f_peak) a skutecznym wytłumieniem pozornych prążków.</li>"
                                        + "<li><b>Hamming:</b> Posiada nieco węższy listek główny i lepsze tłumienie najbliższego listka bocznego (&minus;43 dB), lecz nie wygasza sygnału idealnie do zera na samych końcach (pozostawia skok ~8%).</li>"
                                        + "<li><b>Blackman:</b> Trójharmoniczne okno o bardzo silnym tłumieniu listków bocznych (&minus;58 dB). "
                                        + "Zalecane w środowiskach o dużych zakłóceniach stacji radiowych NBI, gdy słabe wyładowanie WNZ leży blisko silnego wąskopasmowego prążka zakłócającego.</li>"
                                        + "<li><b>Flat-top:</b> Okno o spłaszczonym wierzchołku. Zapewnia bezwzględnie najwyższą precyzję pomiaru wartości szczytowej amplitudy składowej harmonicznej, kosztem gorszej rozdzielczości częstotliwościowej (szerszy pik).</li>"
                                        + "<li><b>Prostokątne (Rectangular / Brak okna):</b> Brak jakiegokolwiek tłumienia (mnożenie przez 1). "
                                        + "Może być stosowane <b>wyłącznie wtedy</b>, gdy badany impuls WNZ jest bardzo krótki (np. 100 ns) i jego oscylacje samoistnie wygasają do absolutnego zera przed końcem 8-mikrosekundowego bufora — wówczas zjawisko wycieku widma fizycznie nie występuje.</li>"
                                        + "</ul>", dark),
                        dark)
        );
    }

    // =========================================================================
    // 5. TIME DOMAIN METRICS
    // =========================================================================

    private static Entry createTimeMetricsEntry() {
        return new Entry(
                "TIME_METRICS",
                "Parametry czasowe",
                "Parametry czasowe impulsu – przegląd i definicje",
                "Time Domain Scope Metrics Overview & Mathematical Formulations",
                "[⏱]",
                "Kompletne zestawienie parametrów czasowych (Q, faza, polaryzacja, tr, tw, tf, E, Teq) wraz ze wzorami.",
                dark -> page(
                        "Parametry czasowe impulsu: Definicje i znaczenie diagnostyczne",
                        "IEC 60270 & CIGRE WG D1.33 Time-Domain Pulse Parameters",
                        sectionBox("Wprowadzenie do metryk czasowych", "#00e1ff",
                                "<p>Prawy górny panel okna Scope prezentuje zestaw parametrów obliczanych bezpośrednio z próbek czasowych <i>u(t<sub>i</sub>)</i> pojedynczego impulsu. "
                                        + "Parametry te opisują dynamikę czasową zjawiska i stanowią wejście do klasyfikacji defektów izolacji.</p>", dark)
                                + sectionBox("Zestawienie parametrów czasowych", dark ? "#38bdf8" : "#0284c7",
                                "<table cellpadding='0' cellspacing='0' style='width: 100%; border-collapse: collapse; font-size: 11px;'>"
                                        + tableRow("Parametr", "Symbol i Jednostka", "Wzór matematyczny / Definicja", "Znaczenie fizyczne i diagnostyczne", true, dark)
                                        + tableRow("Amplituda szczytowa", "Q [mV lub pC]", "max |u(t<sub>i</sub>)|", "Maksymalna chwilowa wartość napięcia impulsu. Główna miara intensywności wyładowania. Po włączeniu kalibracji wg IEC 60270 przeliczana na ładunek pozorny q [pC]. Wartości poniżej 3–5 mV to zwykle szum tła przetwornika, 10–50 mV to typowe wyładowania (np. ulot), a powyżej 100–300 mV to silne, niszczące wyładowania w dielektryku stałym.", false, dark)
                                        + tableRow("Kąt fazowy", "&phi; [&deg;]", "(t mod T) / T &times; 360&deg;", "Położenie impulsu na sinusoidzie napięcia probierczego 50 Hz (0&deg;–360&deg;). Klucz do rozpoznania typu defektu na wykresie fazowo-rozdzielczym PRPD: wyładowania ulotowe (korona) grupują się ściśle wokół szczytów sinusoidy (90&deg; i 270&deg;), pęcherzyki gazu wewnątrz izolacji (void) w strefach narastania napięcia (30&deg;–90&deg; i 210&deg;–270&deg;), a szum jest rozproszony losowo.", false, dark)
                                        + tableRow("Polaryzacja", "Polaryzacja (+ / &minus;)", "znak u(t<sub>peak</sub>)", "Znak pierwszego wychylenia fali napięciowej w chwili przeskoku. Określa kierunek pola elektrycznego w defekcie. Asymetria liczby impulsów dodatnich i ujemnych wskazuje na geometrię defektu (np. wyładowania z ostrza pod napięciem występują głównie na ujemnym półokresie, a z uziemionego na dodatnim).", false, dark)
                                        + tableRow("Czas wystąpienia", "t [s]", "Czas od startu rejestracji", "Bezwzględny czas rejestracji impulsu w sekundach liczony od początku pomiaru. Umożliwia śledzenie dynamiki czasowej zjawiska: czy wyładowania występują ciągle, w seriach, czy ich intensywność narasta wraz z czasem podgrzewania izolacji.", false, dark)
                                        + tableRow("Czas narastania", "t<sub>r</sub> [ns]", "t(90%) &minus; t(10%)", "Stromość czoła fali; czas przejścia impulsu od 10% do 90% amplitudy szczytowej. Informuje o mechanizmie rozwoju iskry plazmowej: ultraszybkie czoło (&lt; 25 ns) oznacza wyładowanie w gazie (SF6 / powietrze) tuż przy czujniku; powolne czoło (&gt; 200 ns) świadczy o silnym tłumieniu wysokich częstotliwości po propagacji przez kabel WN lub o sztucznym sygnale testowym z generatora.", false, dark)
                                        + tableRow("Czas trwania (FWHM)", "t<sub>w</sub> [ns]", "Szerokość w 50% piku", "Szerokość czasowa impulsu mierzona na poziomie połowy amplitudy (50%). Mówi, jak długo trwał główny transfer ładunku w kanale plazmowym. Prawdziwe wyładowania niezupełne trwają od kilkunastu do 200 nanosekund. Szerokie impulsy powyżej 1000 ns to sygnały z generatora funkcyjnego lub zakłócenia falownikowe.", false, dark)
                                        + tableRow("Czas opadania", "t<sub>f</sub> [ns]", "t(10%) &minus; t(90%) grzbietu", "Czas wygasania fali od 90% do 10% na opadającym grzbiecie impulsu. Wskazuje na szybkość rekombinacji ładunku oraz tłumienie rezonansów w obwodzie pomiarowym (obwód RLC czujnika i badanego obiektu).", false, dark)
                                        + tableRow("Energia całkowa", "E [mV&sup2;&middot;&mu;s]", "&int; u&sup2;(t) dt", "Całka z kwadratu napięcia w czasie. Reprezentuje całkowitą energię elektryczną niesioną przez pojedynczy impuls fali. Pozwala niezawodnie odróżnić wąskie szpilki szumowe o małej energii (&lt; 100 mV&sup2;&middot;&mu;s) od rzeczywistych, energetycznych wyładowań WNZ (&gt; 1000 mV&sup2;&middot;&mu;s) oraz szerokich impulsów testowych z generatora.", false, dark)
                                        + tableRow("Czas równoważny", "T<sub>eq</sub> [ns]", "Moment 2. rzędu rozkładu", "Pierwiastek z drugiego momentu czasowego rozkładu energii impulsu. Mierzy całkowity efektywny czas trwania impulsu wraz z jego stłumionym ogonem oscylacyjnym. Stanowi poziomą oś (X) na mapie klastrów CIGRE T-F: wartości &lt; 200 ns oznaczają szybkie wyładowania wtrętowe w dielektryku bez tłumienia, 300–1200 ns to wyładowania powierzchniowe lub w kablu XLPE, a &gt; 1500 ns to powolne zakłócenia sieciowe i generatory testowe.", false, dark)
                                        + "</table>", dark),
                        dark)
        );
    }

    private static Entry createQPeakEntry() {
        return new Entry(
                "Q_PEAK",
                "Parametry czasowe",
                "Amplituda szczytowa Q (Qp) [mV / pC]",
                "Apparent Charge Peak Magnitude Q & Peak Voltage Qp",
                "[⚡]",
                "Maksymalna bezwzględna wartość napięcia sygnału chwilowego i ładunek pozorny IEC 60270.",
                dark -> page(
                        "Amplituda szczytowa impulsu Q / Qp",
                        "Peak Magnitude & Apparent Charge &bull; Standard IEC 60270",
                        eqSimple("<b>Q<sub>p</sub></b> = max<sub>i</sub> |u(t<sub>i</sub>)|",
                                "u(t<sub>i</sub>) – próbka napięcia w miliwoltach [mV]. Po włączeniu kalibracji ładunku pozornego: <b>Q<sub>cal</sub> = Q<sub>p</sub> &middot; k<sub>cal</sub> [pC]</b>.", dark)
                                + sectionBox("Znaczenie fizyczne", "#00e1ff",
                                "<p>Amplituda szczytowa Q<sub>p</sub> określa maksymalną chwilową odpowiedź napięciową czujnika pomiarowego (np. HFCT, dzielnika pojemnościowego lub anteny VHF). "
                                        + "W przypadku włączonej kalibracji ładunku pozornej zgodnie z <b>IEC 60270</b>, wartość ta jest przeliczana na ładunek pozorny <i>q</i> w pikocoulombach [pC] lub nanocoulombach [nC].</p>", dark)
                                + sectionBox("Wartości referencyjne: Dużo czy mało?", "#10b981",
                                "<table cellpadding='0' cellspacing='0' style='width: 100%; border-collapse: collapse; font-size: 11px;'>"
                                        + tableRow("Przedział wartości [mV]", "Szacowany ładunek [pC]", "Ocena stanu / Diagnoza", "Typowe źródło", true, dark)
                                        + tableRow("&lt; 2.0 – 5.0 mV", "&lt; 5 – 10 pC", "<span style='color:#10b981; font-weight:bold;'>BARDZO MAŁO / SZUM</span>", "Szum termiczny toru pomiarowego Red Pitaya", false, dark)
                                        + tableRow("5.0 – 25.0 mV", "10 – 50 pC", "<span style='color:#38bdf8; font-weight:bold;'>MAŁO</span>", "Wczesne stadium wyładowań koronowych lub drobne wtręty w żywicy", false, dark)
                                        + tableRow("25.0 – 100.0 mV", "50 – 200 pC", "<span style='color:#f59e0b; font-weight:bold;'>ŚREDNIO</span>", "Rozwinięte wyładowania w dielektryku stałym, erozja izolacji", false, dark)
                                        + tableRow("&gt; 150.0 – 500.0 mV", "&gt; 300 – 1000 pC", "<span style='color:#ef4444; font-weight:bold;'>BARDZO DUŻO / GROŹNE</span>", "Silne wyładowania powierzchniowe, drzewienie kablowe, groźba przebicia", false, dark)
                                        + tableRow("~ 71.6 mV (stałe)", "Zależy od kcal", "<span style='color:#38bdf8; font-weight:bold;'>SYGNAŁ SYNTETYCZNY</span>", "Generator funkcyjny AWG (prostokąt 300 mVpp / tłumik)", false, dark)
                                        + "</table>", dark),
                        dark)
        );
    }

    private static Entry createRiseTimeEntry() {
        return new Entry(
                "RISE_TIME",
                "Parametry czasowe",
                "Czas narastania tr (10-90%) [ns]",
                "Pulse Rise Time (10% to 90% Leading Edge)",
                "[📈]",
                "Szybkość rozwoju lawiny elektronowej, stromość czoła i odróżnienie wyładowań gazowych od dielektryka stałego.",
                dark -> page(
                        "Czas narastania impulsu tr (10-90%)",
                        "Rise Time &bull; Leading Edge Dynamics & Physical Breakdown Velocity",
                        eqSimple("<b>t<sub>r</sub></b> = t<sub>90%</sub> &minus; t<sub>10%</sub>",
                                "Czas upływający od osiągnięcia 10% do 90% wartości szczytowej na czole fali wyładowczej.", dark)
                                + sectionBox("Znaczenie fizyczne", "#00e1ff",
                                "<p>Czas narastania t<sub>r</sub> odzwierciedla <b>fizykę tworzenia się kanału plazmowego</b> w miejscu wyładowania oraz tłumienie wysokich częstotliwości w drodze od defektu do czujnika. "
                                        + "Rozwój lawiny elektronowej w gazach (np. SF6 lub powietrzu) zachodzi w czasie poniżej kilkunastu nanosekund. "
                                        + "W dielektrykach stałych oraz po propagacji w długim kablu czoło ulega znacznemu rozmyciu (dyspersja fali).</p>", dark)
                                + sectionBox("Wartości referencyjne: Dużo czy mało?", "#10b981",
                                "<table cellpadding='0' cellspacing='0' style='width: 100%; border-collapse: collapse; font-size: 11px;'>"
                                        + tableRow("Czas narastania tr", "Kategoria stromości", "Diagnoza fizyczna", "Przykład w praktyce", true, dark)
                                        + tableRow("8 – 25 ns", "Ekstremalnie strome", "Wyładowania w gazie / GIS / SF6 lub tuż przy czujniku HFCT", "Wyładowanie ulotowe w powietrzu, czujnik blisko ostrza", false, dark)
                                        + tableRow("30 – 150 ns", "Średnio strome", "Wyładowania we wnękach izolacji stałej lub olejowej", "Pęcherz gazowy w izolatorze żywicznym, wyładowanie w transformatorze", false, dark)
                                        + tableRow("200 – 800 ns", "Łagodne / stłumione", "Fala po propagacji przez kilkadziesiąt metrów kabla WN", "Wyładowanie w mufie kablowej mierzone na końcu linii 110 kV", false, dark)
                                        + tableRow("&gt; 1000 ns (np. 1224 ns)", "Nienaturalnie powolne", "<b>Sztuczny sygnał z generatora AWG</b> lub zakłócenie komutacyjne", "Plik z generatora funkcyjnego (prostokąt 1.2 &mu;s)", false, dark)
                                        + "</table>", dark),
                        dark)
        );
    }

    private static Entry createPulseWidthEntry() {
        return new Entry(
                "PULSE_WIDTH",
                "Parametry czasowe",
                "Czas trwania tw (FWHM) [ns]",
                "Full Width at Half Maximum (FWHM 50%) Pulse Duration",
                "[↔]",
                "Szerokość połówkowa impulsu na poziomie 50% amplitudy szczytowej.",
                dark -> page(
                        "Czas trwania impulsu tw (FWHM)",
                        "Full Width at Half Maximum &bull; Pulse Energy Confinement Duration",
                        eqSimple("<b>t<sub>w</sub></b> = t<sub>50% (spadek)</sub> &minus; t<sub>50% (wzrost)</sub> &nbsp; (FWHM)",
                                "Szerokość czasowa impulsu mierzona na poziomie połowy amplitudy maksymalnej (50% Qp).", dark)
                                + sectionBox("Znaczenie fizyczne", "#00e1ff",
                                "<p>Czas trwania t<sub>w</sub> (FWHM) informuje, jak długo trwa główny transfer ładunku w kanale plazmowym. "
                                        + "Naturalne wyładowania WNZ są zjawiskami wysoce ulotnymi i trwają ułamki mikrosekundy. "
                                        + "Jeżeli impuls ma szerokość powyżej 1 &mu;s, niemal zawsze świadczy to o sztucznym sygnale pomiarowym lub zakłóceniach przemysłowych.</p>", dark)
                                + sectionBox("Wartości referencyjne: Dużo czy mało?", "#10b981",
                                "<table cellpadding='0' cellspacing='0' style='width: 100%; border-collapse: collapse; font-size: 11px;'>"
                                        + tableRow("Szerokość tw (FWHM)", "Ocena", "Diagnoza zjawiska", "Typowy obiekt", true, dark)
                                        + tableRow("&lt; 25 ns", "Bardzo wąski", "Pojedynczy mikroimpuls wyładowczy", "GIS, aparatura rozdzielcza w osłonie gazowej", false, dark)
                                        + tableRow("30 – 200 ns", "Typowy dla WNZ", "Standardowy impuls wyładowania niezupełnego", "Wnęki w dielektrykach stałych, kable, uzwojenia", false, dark)
                                        + tableRow("300 – 800 ns", "Szeroki", "Wyładowanie z silnymi oscylacjami dielektryka", "Wyładowanie powierzchniowe, transformator dużej mocy", false, dark)
                                        + tableRow("&gt; 1000 ns (np. 1216 ns)", "Nienaturalnie szeroki", "<b>Sygnał testowy z generatora funkcyjnego</b>", "Sztuczny pakiet prostokątny z generatora AWG", false, dark)
                                        + "</table>", dark),
                        dark)
        );
    }

    private static Entry createFallTimeEntry() {
        return new Entry(
                "FALL_TIME",
                "Parametry czasowe",
                "Czas opadania tf (90-10%) [ns]",
                "Pulse Fall Time (90% to 10% Trailing Edge)",
                "[📉]",
                "Czas zaniku impulsu, rekombinacja nośników i tłumienie w obwodzie sprzęgającym.",
                dark -> page(
                        "Czas opadania impulsu tf (90-10%)",
                        "Fall Time &bull; Deionization, Charge Neutralization & RLC Damping",
                        eqSimple("<b>t<sub>f</sub></b> = t<sub>10%</sub> &minus; t<sub>90%</sub>",
                                "Czas spadku amplitudy z 90% do 10% na grzbiecie fali za punktem szczytowym.", dark)
                                + sectionBox("Znaczenie fizyczne", "#00e1ff",
                                "<p>Czas opadania t<sub>f</sub> jest z reguły 2 do 5 razy dłuższy niż czas narastania t<sub>r</sub>. "
                                        + "Wynika to ze zjawisk dejonizacji gazu, neutralizacji ładunków przestrzennych na ściankach wnęki oraz rozładowania pojemności czujnika pomiarowego przez rezystancję wejściową układu.</p>", dark)
                                + sectionBox("Wartości referencyjne: Dużo czy mało?", "#10b981",
                                "<ul>"
                                        + "<li><b>20 – 100 ns:</b> Bardzo szybkie opadanie, czujnik szerokopasmowy (HFCT) o małej indukcyjności własnej.</li>"
                                        + "<li><b>150 – 800 ns:</b> Typowy czas zaniku w obwodach sprzęgających IEC 60270 (czwórnik pomiarowy CD + kondensator sprzęgający CC).</li>"
                                        + "<li><b>&gt; 2000 – 6000 ns (np. 6096 ns na zrzucie):</b> Wskazuje na <b>długi płaski grzbiet fali syntetycznej z generatora</b> lub silne nasycenie wzmacniacza pomiarowego.</li>"
                                        + "</ul>", dark),
                        dark)
        );
    }

    private static Entry createEnergyEntry() {
        return new Entry(
                "ENERGY",
                "Parametry czasowe",
                "Energia całkowa impulsu E [mV²·µs]",
                "Total Pulse Energy Integral (Square of Voltage over Time)",
                "[🔋]",
                "Całka kwadratu napięcia chwilowego po czasie – odróżnienie szumu od potężnych pakietów energii.",
                dark -> page(
                        "Energia całkowa impulsu E",
                        "Energy Integral &bull; Absolute Waveform Signal Power",
                        eqSimple("<b>E</b> = &int; u<sup>2</sup>(t) dt &approx; &sum; u<sup>2</sup>(t<sub>i</sub>) &middot; &Delta;t",
                                "&Delta;t = 8.0 ns = 0.008 &mu;s. Jednostka energii całkowej: [mV<sup>2</sup>&middot;&mu;s].", dark)
                                + sectionBox("Znaczenie fizyczne", "#00e1ff",
                                "<p>Energia całkowa E określa całkowity ładunek energetyczny niesiony przez falę napięciową. "
                                        + "Stanowi jeden z najpewniejszych wskaźników odróżniających przypadkowy szum termiczny (który ma znikomą energię) od prawdziwych wyładowań WNZ oraz sztucznych sygnałów z generatora.</p>", dark)
                                + sectionBox("Wartości referencyjne: Dużo czy mało?", "#10b981",
                                "<table cellpadding='0' cellspacing='0' style='width: 100%; border-collapse: collapse; font-size: 11px;'>"
                                        + tableRow("Energia całkowa E", "Kategoria", "Charakterystyka zjawiska", "Przykład", true, dark)
                                        + tableRow("&lt; 5.0 mV&sup2;&middot;&mu;s", "<span style='color:#10b981; font-weight:bold;'>Znikoma</span>", "Szum tła przetwornika ADC i drobne oscylacje", "Szum pomiarowy przy progu 0 mV (np. 2.29 mV&sup2;&middot;&mu;s)", false, dark)
                                        + tableRow("10 – 150 mV&sup2;&middot;&mu;s", "<span style='color:#38bdf8; font-weight:bold;'>Typowa WNZ</span>", "Pojedynczy, ostry impuls wyładowania niezupełnego", "Pojedynczy przeskok iskrowy w dielektryku", false, dark)
                                        + tableRow("200 – 1500 mV&sup2;&middot;&mu;s", "<span style='color:#f59e0b; font-weight:bold;'>Wysoka</span>", "Silne wyładowanie powierzchniowe z długimi oscylacjami", "Intensywne wyładowanie ślizgowe na izolatorze", false, dark)
                                        + tableRow("&gt; 5000 mV&sup2;&middot;&mu;s (np. 16041 mV&sup2;&middot;&mu;s)", "<span style='color:#ef4444; font-weight:bold;'>EKSTREMALNIE DUŻA</span>", "<b>Potężny pakiet energii fali prostokątnej z generatora</b>", "Impuls z generatora funkcyjnego o szerokości 1.2 &mu;s", false, dark)
                                        + "</table>", dark),
                        dark)
        );
    }

    private static Entry createTeqEntry() {
        return new Entry(
                "TEQ",
                "Parametry czasowe",
                "Czas równoważny CIGRE Teq [ns]",
                "CIGRE Equivalent Time Duration (Second Spectral Moment in Time)",
                "[📐]",
                "Efektywna szerokość czasowa impulsu wg zaleceń CIGRE WG D1.33 / TF 15/33.03.05.",
                dark -> page(
                        "Czas równoważny CIGRE Teq",
                        "CIGRE Equivalent Time Length &bull; Standard CIGRE WG D1.33",
                        eqFraction("<b>T<sub>eq</sub></b> = &radic; [",
                                "&int; (t &minus; t<sub>0</sub>)<sup>2</sup> &middot; u<sup>2</sup>(t) dt",
                                "&int; u<sup>2</sup>(t) dt",
                                "]",
                                "t<sub>0</sub> – środek ciężkości czasowy rozkładu energii impulsu: t<sub>0</sub> = [ &int; t &middot; u<sup>2</sup>(t) dt ] / [ &int; u<sup>2</sup>(t) dt ].", dark)
                                + sectionBox("Znaczenie fizyczne", "#00e1ff",
                                "<p>Czas równoważny T<sub>eq</sub> stanowi statystyczny moment drugiego rzędu rozkładu energii w czasie (efektywne odchylenie standardowe szerokości fali). "
                                        + "W odróżnieniu od klasycznego czasu trwania t<sub>w</sub> (który mierzy jedynie szerokość wierzchołka na poziomie 50% piku), parametr T<sub>eq</sub> uwzględnia <b>całą energię zjawiska</b>, w tym ogony fali i wtórne oscylacje dielektryka.</p>"
                                        + "<p>Stanowi poziomą oś (X) dwuwymiarowej mapy klastrów CIGRE T-F: im mniejszy czas T<sub>eq</sub>, tym impuls jest bardziej stromy i zwarty (szybkie wyładowanie lawinowe), "
                                        + "podczas gdy wysokie T<sub>eq</sub> świadczy o długich oscylacjach tłumionych po propagacji w kablu lub fali prostokątnej z generatora.</p>", dark)
                                + sectionBox("Wartości referencyjne: Dużo czy mało?", "#10b981",
                                "<ul>"
                                        + "<li><b>&lt; 100 ns:</b> Bardzo krótki czas równoważny – ultraszybkie wyładowania w gazie (SF6 / GIS) lub wtręty próżniowe.</li>"
                                        + "<li><b>100 – 400 ns:</b> Typowe wyładowania ulotowe (Corona) w powietrzu.</li>"
                                        + "<li><b>400 – 1500 ns:</b> Wyładowania powierzchniowe (Surface) oraz wyładowania w kablach elektroenergetycznych.</li>"
                                        + "<li><b>&gt; 2000 ns (np. 2363 ns):</b> Bardzo duży czas równoważny – impulsy o długim trwaniu z generatora funkcyjnego lub wolne zakłócenia przemysłowe.</li>"
                                        + "</ul>", dark),
                        dark)
        );
    }

    // =========================================================================
    // 6. FREQUENCY DOMAIN METRICS
    // =========================================================================

    private static Entry createFreqMetricsEntry() {
        return new Entry(
                "FREQ_METRICS",
                "Parametry widmowe (FFT)",
                "Parametry widmowe FFT – przegląd i definicje",
                "Frequency Domain FFT Metrics Overview & Spectral Moments",
                "[📊]",
                "Zestawienie parametrów widmowych (f_peak, f_cent, BW, Feq, fs) wraz ze wzorami i interpretacją.",
                dark -> page(
                        "Parametry widmowe FFT: Definicje i znaczenie diagnostyczne",
                        "Frequency Domain Spectral Moments &bull; IEC 62478 / CIGRE WG D1.33",
                        sectionBox("Wprowadzenie do metryk częstotliwościowych", "#00e1ff",
                                "<p>Prawy środkowy panel okna Scope przedstawia parametry obliczane z transformaty Fouriera zarejestrowanego impulsu. "
                                        + "Pozwalają one natychmiast określić, w którym segmencie pasma (HF: 0.5–30 MHz czy VHF: 30–62.5 MHz) zachodzi zjawisko oraz odróżnić wąskopasmowe zakłócenia NBI od szerokopasmowych wyładowań WNZ.</p>", dark)
                                + sectionBox("Zestawienie parametrów widmowych", dark ? "#38bdf8" : "#0284c7",
                                "<table cellpadding='0' cellspacing='0' style='width: 100%; border-collapse: collapse; font-size: 11px;'>"
                                        + tableRow("Parametr", "Symbol i Jednostka", "Wzór matematyczny / Definicja", "Znaczenie fizyczne i diagnostyczne", true, dark)
                                        + tableRow("Częstotliwość piku", "f_peak [MHz]", "argmax |U(f)|", "Częstotliwość, dla której amplituda widma FFT osiąga maksimum (dominująca harmoniczna energii). Wskazuje na główny rezonans obwodu wyładowczego. Wartości &lt; 1 MHz wskazują na generator funkcyjny lub zakłócenia falowników; 2–10 MHz to pasmo kabli elektroenergetycznych i transformatorów; powyżej 20 MHz to wyładowania w powietrzu (ulot) lub rozdzielnicach gazowych SF6.", false, dark)
                                        + tableRow("Środek ciężkości", "f_cent [MHz]", "&int; f |U(f)| df / &int; |U(f)| df", "Średnia ważona częstotliwość całego rozkładu widmowego (tzw. 'środek masy' widma). Bardzo stabilny statystycznie wskaźnik dynamiki: wysokie wartości (&gt; 20 MHz) oznaczają zjawiska ultraszybkie i strome wyładowania iskrowe; niskie wartości (&lt; 5 MHz) świadczą o fali spowolnionej, mocno stłumionej przez dielektryk kabla lub o zakłóceniach sieciowych.", false, dark)
                                        + tableRow("Pasmo równoważne", "BW [MHz]", "&radic;[ &int; (f &minus; f_cent)&sup2; |U(f)|&sup2; df / &int; |U(f)|&sup2; df ]", "Efektywne rozproszenie energii wokół środka ciężkości widma. Kluczowy parametr do eliminacji zakłóceń: wąskie pasmo (&lt; 2–3 MHz) jednoznacznie wskazuje na sztuczne zakłócenia wąskopasmowe (stacje radiowe AM/FM, przetwornice DC/DC), podczas gdy szerokie pasmo (&gt; 10–25 MHz) jest dowodem na fizyczne, szerokopasmowe wyładowanie plazmowe WNZ.", false, dark)
                                        + tableRow("Częstotliwość CIGRE", "F_eq [MHz]", "&radic;[ &int; f&sup2; |U(f)|&sup2; df / &int; |U(f)|&sup2; df ]", "Wskaźnik wysokoczęstotliwościowości impulsu (pionowa oś Y na mapie klasyfikacyjnej klastrów CIGRE T-F). Określa, jak duża część energii skupia się w paśmie wysokich częstotliwości radiowych. Umożliwia rozróżnienie wyładowań w pęcherzykach gazu i SF6 (Feq &gt; 15 MHz) od wyładowań powierzchniowych i drzewienia w kablach (3–12 MHz) oraz od zakłóceń zasilania (&lt; 3 MHz).", false, dark)
                                        + tableRow("Częstotliwość próbkowania", "f_s [MS/s]", "125.0 MS/s", "Częstotliwość taktowania przetwornika analogowo-cyfrowego (ADC) Red Pitaya STEMlab 125-14. Przetwornik pobiera 125 milionów próbek na sekundę (jedna próbka co 8.0 ns). Zgodnie z twierdzeniem Nyquista-Shannona pozwala to na bezbłędną analizę składowych częstotliwościowych aż do 62.5 MHz.", false, dark)
                                        + "</table>", dark),
                        dark)
        );
    }

    private static Entry createFPeakEntry() {
        return new Entry(
                "F_PEAK",
                "Parametry widmowe (FFT)",
                "Częstotliwość piku f_peak [MHz]",
                "Dominant Peak Frequency in Pulse Spectrum",
                "[🎯]",
                "Częstotliwość dominującej harmonicznej w widmie fourierowskim.",
                dark -> page(
                        "Częstotliwość piku f_peak",
                        "Dominant Peak Frequency &bull; Maximum Spectral Amplitude Bin",
                        eqSimple("<b>f<sub>peak</sub></b> = argmax<sub>f</sub> |U(f)|",
                                "|U(f)| – moduł transformaty Fouriera zarejestrowanego wycinka fali impulsu.", dark)
                                + sectionBox("Znaczenie fizyczne", "#00e1ff",
                                "<p>Częstotliwość piku f<sub>peak</sub> wskazuje, która składowa harmoniczna niesie największą część energii impulsu. "
                                        + "W układach rzeczywistych zależy ona od indukcyjności i pojemności układu probierczego oraz rezonansów własnych transformatora lub kabla.</p>", dark)
                                + sectionBox("Wartości referencyjne: Dużo czy mało?", "#10b981",
                                "<ul>"
                                        + "<li><b>&lt; 0.5 – 1.0 MHz (np. 0.24 MHz na zrzucie):</b> Typowe dla <b>impulsów z generatora funkcyjnego</b> (przebieg schodkowy o okresie 4 &mu;s) oraz zakłóceń sieciowych i przetwornic DC/DC.</li>"
                                        + "<li><b>2.0 – 10.0 MHz:</b> Wyładowania w długich kablach elektroenergetycznych oraz transformatorach olejowych (tłumienie dielektryka obcina wysokie częstotliwości).</li>"
                                        + "<li><b>15.0 – 35.0 MHz (np. 28.44 MHz na zrzucie szumu):</b> Wyładowania ulotowe (Corona) w powietrzu oraz wyładowania w silnikach i generatorach WN.</li>"
                                        + "<li><b>&gt; 40.0 – 60.0 MHz:</b> Wyładowania w rozdzielnicach gazowych (GIS), izolatorach próżniowych oraz aparaturze wnętrzowej.</li>"
                                        + "</ul>", dark),
                        dark)
        );
    }

    private static Entry createFCentEntry() {
        return new Entry(
                "F_CENT",
                "Parametry widmowe (FFT)",
                "Środek ciężkości widma f_cent [MHz]",
                "Spectral Centroid (First Spectral Moment)",
                "[⚖]",
                "Średnia ważona częstotliwości – określa jasność widma i przesunięcie ku wyższym pasmom.",
                dark -> page(
                        "Środek ciężkości widma f_cent",
                        "Spectral Centroid &bull; Center of Mass of Frequency Spectrum",
                        eqFraction("<b>f<sub>cent</sub></b> =",
                                "&int; f &middot; |U(f)| df",
                                "&int; |U(f)| df",
                                "",
                                "Całkowanie odbywa się w paśmie od 0 Hz do częstotliwości Nyquista (62.5 MHz).", dark)
                                + sectionBox("Znaczenie fizyczne", "#00e1ff",
                                "<p>Środek ciężkości widma f<sub>cent</sub> określa 'środek masy' rozkładu częstotliwości. "
                                        + "Podczas gdy f<sub>peak</sub> może skakać między prążkami przy drobnych zmianach szumu, f<sub>cent</sub> jest parametrem <b>wysoce stabilnym statystycznie</b>. "
                                        + "Wysoka wartość f<sub>cent</sub> świadczy o obecności ostrych, szybkich zjawisk nanosekundowych.</p>", dark)
                                + sectionBox("Wartości referencyjne: Dużo czy mało?", "#10b981",
                                "<ul>"
                                        + "<li><b>&lt; 5.0 MHz:</b> Zjawiska wolnozmienne, zakłócenia falowników, transformatory z mocno stłumioną falą.</li>"
                                        + "<li><b>8.0 – 15.0 MHz (np. 8.88 MHz na zrzucie generatora):</b> Przebiegi o umiarkowanym widmie, sztuczne pakiety impulsów z dużą liczbą harmonicznych sinc.</li>"
                                        + "<li><b>20.0 – 45.0 MHz (np. 32.04 MHz na zrzucie szumu):</b> Wyładowania ulotowe (korona), wyładowania w gazie oraz szum termiczny toru wysokiej częstotliwości Red Pitaya.</li>"
                                        + "</ul>", dark),
                        dark)
        );
    }

    private static Entry createBwEntry() {
        return new Entry(
                "BW",
                "Parametry widmowe (FFT)",
                "Pasmo równoważne BW [MHz]",
                "Equivalent Bandwidth (Spectral Spread around Centroid)",
                "[↔]",
                "Efektywna szerokość pasma częstotliwościowego – odróżnienie zakłóceń NBI od WNZ.",
                dark -> page(
                        "Pasmo równoważne widma BW",
                        "Equivalent Bandwidth &bull; Second Central Moment of Spectrum",
                        eqFraction("<b>BW</b> = &radic; [",
                                "&int; (f &minus; f<sub>cent</sub>)<sup>2</sup> &middot; |U(f)|<sup>2</sup> df",
                                "&int; |U(f)|<sup>2</sup> df",
                                "]",
                                "Mierzy rozproszenie energii widmowej wokół środka ciężkości f_cent.", dark)
                                + sectionBox("Znaczenie fizyczne", "#00e1ff",
                                "<p>Pasmo równoważne BW opisuje szerokość przedziału częstotliwości, w którym skupiona jest energia zjawiska. "
                                        + "Jest to najważniejszy wskaźnik pozwalający <b>odróżnić zakłócenia wąskopasmowe (NBI – Narrowband Interference)</b>, takie jak stacje radiowe, od naturalnych wyładowań WNZ.</p>", dark)
                                + sectionBox("Wartości referencyjne: Dużo czy mało?", "#10b981",
                                "<ul>"
                                        + "<li><b>&lt; 1.0 – 3.0 MHz:</b> Wąskopasmowe zakłócenie sinusoidalne (stacja radiowa AM/FM, nadajnik, przetwornica). Sygnał nie jest wyładowaniem WNZ!</li>"
                                        + "<li><b>5.0 – 15.0 MHz:</b> Umiarkowanie szerokie pasmo, typowe dla wyładowań w kablach i transformatorach.</li>"
                                        + "<li><b>&gt; 15.0 – 25.0 MHz:</b> Bardzo szerokie pasmo szumowe, charakterystyczne dla naturalnych zjawisk plazmowych WNZ oraz wyładowań ulotowych.</li>"
                                        + "</ul>", dark),
                        dark)
        );
    }

    private static Entry createFeqEntry() {
        return new Entry(
                "FEQ",
                "Parametry widmowe (FFT)",
                "Częstotliwość równoważna CIGRE Feq [MHz]",
                "CIGRE Equivalent Frequency (RMS Spectral Moment)",
                "[📐]",
                "Częstotliwość równoważna wg CIGRE WG D1.33 – oś pionowa mapy klasyfikacyjnej T-F.",
                dark -> page(
                        "Częstotliwość równoważna CIGRE Feq",
                        "CIGRE Equivalent Frequency &bull; Standard CIGRE WG D1.33",
                        eqFraction("<b>F<sub>eq</sub></b> = &radic; [",
                                "&int; f<sup>2</sup> &middot; |U(f)|<sup>2</sup> df",
                                "&int; |U(f)|<sup>2</sup> df",
                                "]",
                                "Pierwiastek ze średniej ważonej kwadratu częstotliwości w znormalizowanym rozkładzie mocy.", dark)
                                + sectionBox("Znaczenie fizyczne", "#00e1ff",
                                "<p>Częstotliwość równoważna F<sub>eq</sub> stanowi pierwiastek ze średniej kwadratowej częstotliwości ważonej gęstością widmową mocy. "
                                        + "Mierzy ogólną zawartość wysokich częstotliwości w zarejestrowanym zjawisku. "
                                        + "W połączeniu z czasem równoważnym T<sub>eq</sub> tworzy parę współrzędnych (T<sub>eq</sub>, F<sub>eq</sub>) na dwuwymiarowej mapie klastrów wyładowań CIGRE.</p>"
                                        + "<p>Stanowi pionową oś klasyfikacyjną: wysokie wartości wskazują na bardzo szybkie procesy plazmowe bez tłumienia (np. w gazie SF6 lub małych wnękach próżniowych), "
                                        + "podczas gdy niskie wartości wskazują na sygnały silnie stłumione po propagacji w długim kablu, zakłócenia falowników lub sztuczne przebiegi testowe z generatora funkcyjnego.</p>", dark)
                                + sectionBox("Wartości referencyjne: Dużo czy mało?", "#10b981",
                                "<ul>"
                                        + "<li><b>&lt; 3.0 MHz (np. 2.64 MHz na zrzucie generatora):</b> Zjawiska wolnozmienne, zakłócenia niskoczęstotliwościowe (falowniki, sieć 50 Hz) i generatory testowe.</li>"
                                        + "<li><b>3.0 – 15.0 MHz:</b> Średnie pasmo CIGRE – wyładowania powierzchniowe (Surface) oraz drzewienie dielektryka w kablach (Cable Treeing).</li>"
                                        + "<li><b>&gt; 15.0 – 45.0 MHz (np. 37.55 MHz na zrzucie szumu):</b> Pasmo wysokich częstotliwości radiowych (powyżej 15 MHz) – wyładowania w gazie (SF6 / powietrze) oraz mikroszczeliny w izolacji stałej (Internal Void).</li>"
                                        + "</ul>", dark),
                        dark)
        );
    }

    // =========================================================================
    // 7. CIGRE T-F CLASSIFICATION
    // =========================================================================

    private static Entry createCigreTfEntry() {
        return new Entry(
                "CIGRE_TF",
                "Diagnostyka i klasyfikacja",
                "Klasyfikacja CIGRE T-F – analiza klastrów czas-częstotliwość",
                "CIGRE WG D1.33 Time-Frequency Cluster Map Classification",
                "[🔬]",
                "Zasada działania mapy (Teq, Feq), podział na klastry defektów: Internal Void, Corona, Surface, Cable, Noise.",
                dark -> page(
                        "Klasyfikacja CIGRE T-F: Mapa czasowo-częstotliwościowa wyładowań",
                        "Time-Frequency Classification Map &bull; Standard CIGRE WG D1.33 / TF 15/33.03.05",
                        sectionBox("Na czym polega metoda CIGRE T-F?", "#00e1ff",
                                "<p>Międzynarodowa grupa robocza <b>CIGRE WG D1.33</b> (oraz TF 15/33.03.05) opracowała zaawansowaną metodę separacji wielu źródeł WNZ bez konieczności stosowania skomplikowanych sieci neuronowych. "
                                        + "Metoda polega na rzutowaniu każdego pojedynczego zarejestrowanego impulsu na dwuwymiarową płaszczyznę parametrów:</p>"
                                        + "<ul>"
                                        + "<li><b>Oś pozioma X:</b> Czas równoważny T<sub>eq</sub> [ns] (odzwierciedla efektywną długość trwania impulsu).</li>"
                                        + "<li><b>Oś pionowa Y:</b> Częstotliwość równoważna F<sub>eq</sub> [MHz] (odzwierciedla zawartość wysokich częstotliwości).</li>"
                                        + "</ul>"
                                        + "<p>W tej przestrzeni impulsy pochodzące z różnych zjawisk fizycznych grupują się w <b>wyraźnie odseparowane klastry (chmury punktów)</b>.</p>", dark)
                                + sectionBox("Strefy klasyfikacyjne mapy CIGRE", dark ? "#38bdf8" : "#0284c7",
                                "<table cellpadding='0' cellspacing='0' style='width: 100%; border-collapse: collapse; font-size: 11px;'>"
                                        + tableRow("Kategoria defektu", "Koordynaty Teq i Feq", "Mechanizm fizyczny", "Objawy i zagrożenie", true, dark)
                                        + tableRow("Wyładowania wysokoczęstotliwościowe (szczelina gazowa / wnęka void / SF6)", "Teq &lt; 200 ns<br>Feq &gt; 15 MHz", "Ultraszybkie wyładowanie lawinowe w gazie lub w małej wnęce w dielektryku stałym", "Brak tłumienia dielektryka, strome czoło fali, wysokie ryzyko degradacji chemicznej", false, dark)
                                        + tableRow("Wyładowania ulotowe (Corona)", "Teq: 100 – 400 ns<br>Feq: 10 – 30 MHz", "Jonizacja powietrza wokół ostrych krawędzi przewodzących pod wysokim napięciem", "Regularne pakiety impulsów skupione symetrycznie wokół szczytów sinusoidy (90&deg; i 270&deg;)", false, dark)
                                        + tableRow("Wyładowania średnioczęstotliwościowe (powierzchniowe / drzewienie kabla)", "Teq: 300 – 1500 ns<br>Feq: 2 – 12 MHz", "Wyładowania ślizgowe po powierzchni izolatora lub drzewienie elektryczne w kablu XLPE", "Dłuższy czas trwania, tłumione oscylacje dielektryka, silna degradacja cieplna", false, dark)
                                        + tableRow("Zakłócenia niskoczęstotliwościowe / Generator AWG", "Teq &gt; 1500 ns<br>Feq &lt; 3 MHz", "Sztuczne impulsy testowe z generatora funkcyjnego lub zakłócenia komutacyjne sieci", "Brak cech fizycznego wyładowania plazmowego, sztuczny kształt, niska częstotliwość", false, dark)
                                        + "</table>", dark)
                                + alertBox("Interpretacja w Twojej aplikacji",
                                "W prawym dolnym rogu okna Scope pole <b>Interpretacja CIGRE T-F</b> automatycznie analizuje parametry bieżącego impulsu i wyświetla werdykt słowny (np. <i>'Wyładowanie średnioczęstotliwościowe (powierzchniowe / drzewienie kabla) – Pasmo 3–12 MHz i tłumione oscylacje dielektryka'</i>).",
                                "SUCCESS", dark),
                        dark)
        );
    }

    // =========================================================================
    // 8. BENCHMARKS ("DUŻO CZY MAŁO")
    // =========================================================================

    private static Entry createBenchmarksEntry() {
        return new Entry(
                "BENCHMARKS",
                "Diagnostyka i klasyfikacja",
                "Tabela wartości referencyjnych (Czy dana wartość to dużo czy mało?)",
                "Comprehensive Diagnostic Benchmarks & Numerical Reference Guide",
                "[📋]",
                "Zbiorcze porównanie liczbowe: szum tła, wyładowania wtrętowe, ulotowe, powierzchniowe, kable i generator AWG.",
                dark -> page(
                        "Punkty odniesienia do analizy: Czy dana wartość to dużo czy mało?",
                        "Diagnostic Thresholds, Reference Ranges & Case Study Benchmarks",
                        sectionBox("Jak korzystać z tej tabeli?", "#00e1ff",
                                "<p>Podczas diagnostyki obiektów elektroenergetycznych pojedyncza liczba bez punktu odniesienia bywa trudna w interpretacji. "
                                        + "Poniższa tabela stanowi <b>praktyczny kompas inżynierski</b>, zestawiający typowe wartości parametrów mierzonych przez system Red Pitaya w różnych scenariuszach pomiarowych.</p>", dark)
                                + sectionBox("Zbiorcza tabela porównawcza zjawisk", dark ? "#38bdf8" : "#0284c7",
                                "<table cellpadding='0' cellspacing='0' style='width: 100%; border-collapse: collapse; font-size: 11px;'>"
                                        + tableRow("Parametr impulsu", "Szum tła ADC (brak WNZ)", "Typowe WNZ (Corona / Void)", "Groźne WNZ (Surface / Treeing)", true, dark)
                                        + tableRow("Amplituda Q [mV]", "&lt; 1.5 – 3.0 mV", "10.0 – 50.0 mV", "&gt; 100.0 – 500.0 mV", false, dark)
                                        + tableRow("Ładunek pozorny [pC]", "&lt; 5 pC (zależy od kcal)", "20 – 100 pC", "&gt; 300 – 2000 pC", false, dark)
                                        + tableRow("Czas narastania tr [ns]", "8 – 16 ns (losowy)", "15 – 80 ns", "100 – 400 ns", false, dark)
                                        + tableRow("Czas trwania tw [ns]", "8 – 24 ns", "30 – 150 ns", "200 – 600 ns", false, dark)
                                        + tableRow("Czas opadania tf [ns]", "8 – 24 ns", "50 – 300 ns", "500 – 1500 ns", false, dark)
                                        + tableRow("Energia całkowa E [mV&sup2;&middot;&mu;s]", "&lt; 3.0 mV&sup2;&middot;&mu;s", "20 – 150 mV&sup2;&middot;&mu;s", "&gt; 500 – 2500 mV&sup2;&middot;&mu;s", false, dark)
                                        + tableRow("Częstotliwość f_peak [MHz]", "20 – 35 MHz (szum ADC)", "15 – 30 MHz", "2 – 10 MHz", false, dark)
                                        + tableRow("Środek ciężkości f_cent [MHz]", "25 – 35 MHz", "18 – 32 MHz", "5 – 15 MHz", false, dark)
                                        + tableRow("Pasmo równoważne BW [MHz]", "15 – 20 MHz", "10 – 20 MHz", "5 – 12 MHz", false, dark)
                                        + tableRow("Czas CIGRE Teq [ns]", "1500 – 2400 ns (płaskie tło)", "100 – 400 ns", "400 – 1200 ns", false, dark)
                                        + tableRow("Częstotliwość CIGRE Feq [MHz]", "30 – 40 MHz", "12 – 30 MHz", "3 – 10 MHz", false, dark)
                                        + "</table>", dark)
                                + sectionBox("Studium przypadku: Sygnał z generatora funkcyjnego AWG", "#f59e0b",
                                "<p>W pliku testowym <code>Generator_funkcyjny_WNZ_50Hz_Corona_300mV_Voltage_1V_faza_0.rppr.bin</code> wartości parametrów wynoszą:</p>"
                                        + "<ul>"
                                        + "<li><b>Amplituda Q = 71.6 mV:</b> Odpowiada tłumieniu sygnału testowego 300 mVpp na dzielniku wejściowym.</li>"
                                        + "<li><b>Czas narastania tr = 1224 ns oraz czas trwania tw = 1216 ns:</b> Są <b>10 do 50 razy dłuższe niż w rzeczywistym wyładowaniu WNZ</b>! Wynika to z wygenerowania schodkowej fali prostokątnej o stałym czasie trwania 1.2 &mu;s.</li>"
                                        + "<li><b>Energia całkowa E = 16041 mV&sup2;&middot;&mu;s:</b> Ponad <b>100-krotnie większa niż w naturalnym wyładowaniu WNZ</b> z powodu wielkiej powierzchni prostokątnego plateau napięciowego.</li>"
                                        + "<li><b>Częstotliwość f_peak = 0.24 MHz:</b> Widmo FFT ma postać prążków typu <i>sin(x)/x</i> (sinc), charakterystycznych dla fali prostokątnej, z dominującą częstotliwością powtarzania zboczy w pobliżu DC.</li>"
                                        + "</ul>"
                                        + "<p>Powyższe zestawienie jednoznacznie dowodzi, że narzędzie Scope bezbłędnie demaskuje syntetyczny charakter sygnału testowego i odróżnia go od prawdziwego wyładowania niezupełnego.</p>", dark),
                        dark)
        );
    }

    // =========================================================================
    // 9. BIBLIOGRAPHY & STANDARDS REFERENCES
    // =========================================================================

    private static Entry createBibliographyEntry() {
        return new Entry(
                "BIBLIOGRAPHY",
                "Literatura i normy",
                "Wykaz norm i publikacji źródłowych (Normy & Artykuły)",
                "Authoritative Standards, CIGRE Technical Brochures & Academic Foundations",
                "[📚]",
                "Szczegółowy wykaz norm IEC/PN-EN, biuletynów CIGRE i publikacji źródłowych zaimplementowanych w aplikacji.",
                dark -> page(
                        "Wykaz norm i publikacji źródłowych aplikacji PRPD Tool",
                        "International Standards, CIGRE Technical Brochures & Scientific Papers",
                        sectionBox("1. Normy Międzynarodowe i Krajowe (IEC / PN-EN)", "#00e1ff",
                                "<ul>"
                                        + "<li><b>PN-EN 60270:2003 + A1:2016 (IEC 60270:2000):</b> <i>Wysokonapięciowa technika probiercza – Pomiary wyładowań niezupełnych</i>.<br>"
                                        + "Podstawa definicji: ładunku pozornego <i>q</i> [pC] (rozdz. 3.3.1), częstości repetycji impulsów <i>n</i> [1/s] (rozdz. 3.3.2), kąta fazowego &phi;<sub>i</sub> = 360&deg; &middot; (t<sub>i</sub>/T) (rozdz. 3.3.4), obwodów probierczych (kondensator sprzęgający C<sub>k</sub>, czwórnik pomiarowy CD / Z<sub>m</sub>) oraz filtrów szerokopasmowych (30–100 kHz do 500 kHz).</li>"
                                        + "<li><b>IEC TS 62478:2016:</b> <i>High voltage test techniques – Measurement of partial discharges by electromagnetic and acoustic methods</i>.<br>"
                                        + "Podstawa nienormatywnych pomiarów wysokoczęstotliwościowych: podział pasm na zakresy wysokich częstotliwości HF (3–30 MHz), VHF (30–300 MHz) i UHF (300–3000 MHz); analiza parametrów kształtu impulsu: czas narastania t<sub>r</sub> (10–90%), czas trwania t<sub>w</sub> (FWHM 50%), czas opadania t<sub>f</sub> (90–10%); szerokopasmowe przetwarzanie w dziedzinie czasu i częstotliwości (rozdz. 4.3.4).</li>"
                                        + "<li><b>PN-EN 62271-200:2012 (IEC 62271-200:2011) & PN-EN 62271-1:</b> <i>Wysokonapięciowa aparatura rozdzielcza i sterownicza – Rozdzielnice prądu przemiennego w osłonach metalowych na napięcia znamionowe powyżej 1 kV do 52 kV</i>.<br>"
                                        + "Załącznik BB (normatywny): Badania wyładowań niezupełnych rozdzielnic SN. Punkt BB.5 określa dopuszczalny poziom wyładowań dla izolacji stałej na poziomie <b>&le; 20 pC</b> przy 1,1 U<sub>r</sub>/&radic;3 oraz 100 pC dla sieci z izolowanym punktem neutralnym.</li>"
                                        + "</ul>", dark)
                                + sectionBox("2. Biuletyny Techniczne CIGRE (Conseil International des Grands Réseaux Électriques)", dark ? "#38bdf8" : "#0284c7",
                                "<ul>"
                                        + "<li><b>CIGRE Technical Brochure 226 (TF 15.11/33.03.02, Kwiecień 2003, Convenor Prof. E. Gulski):</b><br>"
                                        + "<i>Knowledge Rules for Partial Discharge Diagnosis in Service</i> (93 strony).<br>"
                                        + "Fundament systemu regułowego PRPD Tool: podstawowe i pochodne wielkości WNZ (rozdz. 1.5.2), rozkłady fazowo-rozdzielcze H<sub>n</sub>(&phi;), H<sub>qn</sub>(&phi;), H<sub>qmax</sub>(&phi;), operatory statystyczne (skośność Sk, kurtoza Ku, korelacja krzyżowa CC, asymetria Q i N) oraz szczegółowe tablice wiedzy dla transformatorów (Part II, Tab. 2-1), kabli (Part III, Tab. 3.4–3.6), rozdzielnic GIS (Part IV, Tab. 4.2) oraz uzwojeń generatorów (Part V, Rys. 5.18).</li>"
                                        + "<li><b>CIGRE Technical Brochure 444 (WG D1.33, Grudzień 2010):</b><br>"
                                        + "<i>Guidelines for Unconventional Partial Discharge Measurements</i> (58 stron).<br>"
                                        + "Podstawa doboru pasm pomiarowych (Tabela 1: kable HF/VHF, transformatory VHF/UHF, GIS VHF/UHF, generatory HF/VHF), procedury weryfikacji czułości (sensitivity verification) zamiast tradycyjnego wzorcowania IEC, transmitancje torów i czujniki (HFCT, Rogowski coil, sensory pojemnościowe TEV, anteny UHF).</li>"
                                        + "</ul>", dark)
                                + sectionBox("3. Metodyka Map Czasowo-Częstotliwościowych T-F (Klasyfikacja Klastrów WNZ)", "#10b981",
                                "<ul>"
                                        + "<li><b>A. Cavallini, G. C. Montanari et al. (IEEE Trans. DEI, Vol. 12, No. 2, 2005):</b><br>"
                                        + "<i>A New Methodology for the Identification of PD in Electrical Apparatus: Properties and Applications</i>.<br>"
                                        + "Źródło implementacji współrzędnych czasowo-częstotliwościowych (T<sub>eq</sub>, F<sub>eq</sub>): definicja czasu równoważnego T<sub>eq</sub> jako pierwiastka z drugiego momentu centralnego rozkładu energii w czasie oraz częstotliwości równoważnej F<sub>eq</sub> jako pierwiastka ze średniej kwadratowej częstotliwości widma mocy. Umożliwia bezbłędną separację wielu źródeł WNZ i odfiltrowanie szumu.</li>"
                                        + "<li><b>A. Cavallini, G. C. Montanari, F. Ciani et al. (IEEE Trans. Power Delivery, Vol. 25, No. 2, 2010):</b><br>"
                                        + "<i>Diagnosis of EHV and HV Transformers Through an Innovative Partial-Discharge-Based Technique</i>.<br>"
                                        + "Praktyczne zastosowanie klasyfikatorów T-F i baz regułowych w diagnostyce aparatury wysokiego napięcia.</li>"
                                        + "</ul>", dark)
                                + sectionBox("4. Badania Naukowe Politechniki Warszawskiej i Sieci Badawczej Łukasiewicz", "#f59e0b",
                                "<ul>"
                                        + "<li><b>dr inż. Rafał Buczko (Rozprawa doktorska, Politechnika Warszawska, 2018):</b><br>"
                                        + "<i>Bezinwazyjna detekcja defektów wysokonapięciowej aparatury rozdzielczej</i>.<br>"
                                        + "Podstawa modułu ekstrakcji cech statystycznych: Tabela 7.1 i 7.2 – definicja 16 typów deskryptorów (kwantyle Q1–Q3, średnia, moda, wariancja, odchylenie std, odchylenie przeciętne d, współczynniki zmienności Vs/Vd, odchylenie ćwiartkowe, skośność, wskaźniki Pearsona, kurtoza) tworzących 66-elementowy wektor cech w podziale na fazę i amplitudę dla obu polaryzacji, wraz z normalizacją Z-score.</li>"
                                        + "<li><b>mgr inż. Paweł Kluge, dr hab. inż. Jacek Starzyński, dr inż. Andrzej Łasica (IEEE, 2020):</b><br>"
                                        + "<i>Comparison of methods of recognition and classification of insulation defects of power equipment</i>.<br>"
                                        + "Porównanie efektywności klasyfikatorów SVM, KNN i CNN dla cech statystycznych i obrazów fazowo-rozdzielczych defektów rozdzielnic.</li>"
                                        + "<li><b>mgr inż. Paweł Kluge, dr inż. Andrzej Łasica, dr hab. inż. Jacek Starzyński, dr hab. inż. Wojciech Kołtunowicz (Przegląd Elektrotechniczny, R. 100, Nr 11/2024):</b><br>"
                                        + "<i>Metody klasyfikacji obrazów fazowo-rozdzielczych wnz</i>.<br>"
                                        + "Klasyfikacja defektów w rozdzielnicy SN (ostrze WN, uziemione, pływające, izolacja) z użyciem hybrydowych sieci konwolucyjnych (CNN, GoogleNet, SqueezeNet).</li>"
                                        + "<li><b>mgr inż. Mariusz Kucharek, mgr inż. Paweł Kluge, mgr inż. Bartosz Owczarczuk, mgr inż. Jerzy Chudorliński (Przegląd Elektrotechniczny, R. 101, Nr 6/2025):</b><br>"
                                        + "<i>Analiza sygnałów WNZ rejestrowanych metodami alternatywnymi</i> (Łukasiewicz-ITR & PW).<br>"
                                        + "Porównanie sensorów TEV, HFCT oraz anten VHF/UHF w rozdzielnicach średniego napięcia na stanowisku laboratoryjnym.</li>"
                                        + "</ul>", dark),
                        dark)
        );
    }
}
