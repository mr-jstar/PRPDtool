package prpdtool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Knowledge Base and Documentation for Advanced DSP Denoising & Notch Filters.
 * Explains architecture, physical meaning, real-world field tuning,
 * optimal parameter baselines, and step-by-step diagnostic workflow (IEC 62478 / CIGRE WG D1.33).
 */
public class AdvancedDenoisingDoc {

    public static class Chapter {
        public final String key;
        public final String titlePl;
        public final String titleEn;
        public final String subtitle;
        public final String icon;
        private final ContentRenderer renderer;

        @FunctionalInterface
        public interface ContentRenderer {
            String render(boolean dark);
        }

        public Chapter(String key, String titlePl, String titleEn, String subtitle, String icon, ContentRenderer renderer) {
            this.key = key;
            this.titlePl = titlePl;
            this.titleEn = titleEn;
            this.subtitle = subtitle;
            this.icon = icon;
            this.renderer = renderer;
        }

        public String getFullHtml(boolean dark) {
            return renderer.render(dark);
        }
    }

    private static final Map<String, Chapter> CHAPTERS = new LinkedHashMap<>();

    static {
        // 1. OVERVIEW
        add("OVERVIEW",
                "Architektura i cel modułu DSP",
                "DSP Denoising Architecture & PRPD Impact",
                "Do czego służy ten moduł i jak przekłada się na wyniki całego programu",
                "",
                dark -> buildOverviewHtml(dark));

        // 2. NOTCH
        add("NOTCH",
                "Bank Filtrów Wycinających Notch",
                "Narrowband Interference (NBI) Notch Filter Bank",
                "Częstotliwości środkowe, dobroć Q i automatyczne wykrywanie harmonicznych",
                "",
                dark -> buildNotchHtml(dark));

        // 3. GATE
        add("GATE",
                "Bramka Szumowa (Noise Gate)",
                "Soft-Knee Noise Gate & Background Suppression",
                "Zasada działania, próg T_gate, kolano soft-knee i poziomy tłumienia dB",
                "",
                dark -> buildGateHtml(dark));

        // 4. WORKFLOW
        add("WORKFLOW",
                "Praktyczny Przewodnik Krok po Kroku",
                "Step-by-Step Practical Tuning Workflow",
                "Procedura strojenia od surowego sygnału do czystego obrazu PRPD",
                "",
                dark -> buildWorkflowHtml(dark));

        // 5. OPTIMAL
        add("OPTIMAL",
                "Wartości Optymalne Parametrów",
                "Baseline Reference Table & Typical Field Values",
                "Zestawienie parametrów dla laboratorium, stacji rozdzielczej i linii kablowych",
                "",
                dark -> buildOptimalHtml(dark));

        // 6. NORMS
        add("NORMS",
                "Wytyczne Normatywne (IEC / CIGRE)",
                "Normative Standards & Technical References",
                "Międzynarodowe zalecenia dotyczące niekonwencjonalnej filtracji WNZ",
                "",
                dark -> buildNormsHtml(dark));
    }

    private static void add(String key, String titlePl, String titleEn, String subtitle, String icon, Chapter.ContentRenderer renderer) {
        CHAPTERS.put(key, new Chapter(key, titlePl, titleEn, subtitle, icon, renderer));
    }

    public static Chapter get(String key) {
        if (key == null) return CHAPTERS.get("OVERVIEW");
        String upper = key.toUpperCase();
        for (Map.Entry<String, Chapter> e : CHAPTERS.entrySet()) {
            if (e.getKey().equalsIgnoreCase(upper) || upper.contains(e.getKey())) {
                return e.getValue();
            }
        }
        if (upper.contains("NOTCH") || upper.contains("NBI")) return CHAPTERS.get("NOTCH");
        if (upper.contains("GATE") || upper.contains("BRAMK")) return CHAPTERS.get("GATE");
        if (upper.contains("WORK") || upper.contains("KROK") || upper.contains("TUTORIAL")) return CHAPTERS.get("WORKFLOW");
        if (upper.contains("OPTI") || upper.contains("WARTO") || upper.contains("PUNKT")) return CHAPTERS.get("OPTIMAL");
        if (upper.contains("NORM") || upper.contains("IEC") || upper.contains("CIGRE")) return CHAPTERS.get("NORMS");
        return CHAPTERS.get("OVERVIEW");
    }

    public static List<Chapter> getChapters() {
        return new ArrayList<>(CHAPTERS.values());
    }

    // --- HTML BUILDERS ---

    private static String pageWrapper(String title, String subtitle, String bodyContent, boolean dark) {
        String bg = dark ? "#1e2126" : "#ffffff";
        String text = dark ? "#dcdcdc" : "#1f2937";
        String cardBg = dark ? "#282c34" : "#f8fafc";
        String border = dark ? "#3e4451" : "#e2e8f0";
        String titleColor = dark ? "#00e1ff" : "#0284c7";

        return "<html><body style='font-family: Segoe UI, Arial, sans-serif; font-size: 11px; color: " + text + "; background-color: " + bg + "; margin: 14px;'>"
                + "<div style='font-size: 15px; font-weight: bold; color: " + titleColor + "; margin-bottom: 2px;'>" + title + "</div>"
                + "<div style='font-size: 11px; color: #8892b0; margin-bottom: 14px; border-bottom: 1px solid " + border + "; padding-bottom: 6px;'>" + subtitle + "</div>"
                + bodyContent
                + "<div style='margin-top: 18px; padding-top: 8px; border-top: 1px solid " + border + "; font-size: 10px; color: #888888; text-align: right;'>"
                + "PRPD Tool DSP Denoising Engine &bull; IEC 62478 / CIGRE WG D1.33 Compliant</div>"
                + "</body></html>";
    }

    private static String box(String title, String color, String content, boolean dark) {
        String cardBg = dark ? "#262a32" : "#f8fafc";
        String border = dark ? "#3b414d" : "#e2e8f0";
        return "<div style='background-color: " + cardBg + "; border: 1px solid " + border + "; border-left: 3px solid " + color + "; padding: 10px 12px; border-radius: 4px; margin-bottom: 12px;'>"
                + "<b style='color: " + color + "; font-size: 12px;'>" + title + "</b><br>"
                + "<div style='margin-top: 6px; line-height: 1.5; font-size: 11px;'>" + content + "</div>"
                + "</div>";
    }

    private static String buildOverviewHtml(boolean dark) {
        StringBuilder sb = new StringBuilder();

        sb.append(box("Do czego służy ten moduł i po co istnieje?", dark ? "#38bdf8" : "#0284c7",
                "Karta pomiarowa <b>Red Pitaya STEMlab 125-14</b> rejestruje sygnał z bardzo szerokim pasmem (do 62.5 MHz) i wysoką czułością przetwornika ADC 14-bit.<br>"
                + "W warunkach rzeczywistych (rozdzielnica SN, stacja transformatorowa, linia kablowa) do czujnika WNZ (pojemnościowego CC, cewki Rogowskiego HFCT czy anteny UHF) dociera nie tylko sygnał wyładowań, ale potężna masa zakłóceń elektromagnetycznych z otoczenia:<br>"
                + "• <b>Zakłócenia ciągłe NBI (Narrowband Interference):</b> fale nośne nadajników radiowych AM/SW, radiolinii przemysłowych, systemów PLC (Power Line Communication).<br>"
                + "• <b>Harmoniczne energoelektroniczne:</b> potężne impulsy komutacyjne z falowników napędowych PWM, przetwornic DC/DC i tyrystorów (zwykle 100 kHz, 300 kHz, 500 kHz).<br>"
                + "• <b>Stochastyczny szum tła:</b> szum termiczny i zasilania o amplitudzie rzędu 2–10 mV.<br><br>"
                + "<b>Bez odszumiania te zakłócenia całkowicie niszczą obraz PRPD i uniemożliwiają poprawną diagnozę!</b> Moduł DSP pozwala wyciąć zakłócenia w czasie rzeczywistym.", dark));

        sb.append(box("Jak ta zakładka przekłada się na cały program i inne okna?", dark ? "#34d399" : "#059669",
                "Włączenie modułu (lub zmiana jego nastaw) bezpośrednio wpływa na wszystkie elementy aplikacji:<br><br>"
                + "1. <b>Główny wykres fazowy PRPD (Phase-Resolved PD):</b><br>"
                + "• <i>Przed odszumieniem:</i> Ciągła fala zakłócenia (np. 500 kHz z falownika) wyzwala komparator detektora przez całe 360° sinusoidy. Na wykresie PRPD powstaje <b>gruba, ciągła pozioma wstęga szumu</b>, sztucznie generująca setki tysięcy fałszywych impulsów i maskująca prawdziwe uszkodzenie.<br>"
                + "• <i>Po włączeniu filtrów Notch i Bramki:</i> Wstęgi szumu zostają całkowicie zlikwidowane. Odsłaniają się czyste, ostre klastry WNZ (pęcherzyki, ulot, degradacja powierzchniowa).<br><br>"
                + "2. <b>Oscyloskop pojedynczego impulsu (Single Pulse Scope):</b><br>"
                + "Przebieg czasowy zyskuje idealnie płaską linię bazową (zero). Czasy narastania <i>t<sub>r</sub></i> i czasy trwania <i>t<sub>w</sub></i> są mierzone od właściwego impulsu, a nie od nałożonej fali zakłóceń.<br><br>"
                + "3. <b>Wskaźniki statystyczne (Hn, Hqn, Sk, Ku, CC):</b><br>"
                + "Usunięcie nieskorelowanego szumu tła pozwala uzyskać rzetelne wartości korelacji połówkowej <i>CC</i> oraz kurtozy fazowej <i>Ku</i> zgodne z normą CIGRE TB 226.<br><br>"
                + "4. <b>Klasyfikator defektów AI (ONNX) i Raport Diagnostyczny:</b><br>"
                + "Czysty, odszumiony obraz PRPD gwarantuje wysoką pewność klasyfikacji (brak fałszywych alarmów <i>False Positives</i>).", dark));

        sb.append(box("Zasada działania przycisków na dole okna", dark ? "#fbbf24" : "#d97706",
                "• <b>Zastosuj do Toru Pomiarowego:</b> Natychmiast aktywuje nowe filtry w pamięci bieżącego toru akwizycji. Główny wykres PRPD i oscyloskopy natychmiast zaczynają operować na przefiltrowanym sygnale.<br>"
                + "• <b>Zapisz jako domyślne w konfiguracji:</b> Zapisuje nastawy do pliku konfiguracyjnego na dysku. Dzięki temu po restarcie programu filtry uruchomią się z tymi samymi nastawami.", dark));

        return pageWrapper("Architektura i Cel Modułu Zaawansowanego Odszumiania DSP",
                "Rola filtracji cyfrowej w niekonwencjonalnych pomiarach WNZ wg IEC 62478 / CIGRE WG D1.33",
                sb.toString(), dark);
    }

    private static String buildNotchHtml(boolean dark) {
        StringBuilder sb = new StringBuilder();

        sb.append(box("Czym jest filtr wycinający Notch (NBI Suppression)?", dark ? "#38bdf8" : "#0284c7",
                "Filtr wycinający typu <b>Notch</b> (dwubiegunowy filtr cyfrowy IIR Biquad) posiada charakterystykę częstotliwościową z bardzo głębokim wycięciem (-40 dB do -60 dB) w ściśle określonym, wąskim punkcie częstotliwości <i>f<sub>0</sub></i>.<br>"
                + "Umożliwia całkowite 'wycięcie' zakłócenia sinusoidalnego (np. fali nośnej stacji radiowej 1.25 MHz lub harmonicznej falownika 500 kHz) <b>bez utraty energii i bez deformacji szerokopasmowego impulsu WNZ</b>.", dark));

        sb.append(box("Parametry filtru Notch i jak je ustawiać:", dark ? "#a78bfa" : "#7c3aed",
                "Każdy z 5 slotów w banku posiada 3 niezależne parametry:<br><br>"
                + "1. <b>Częstotliwość środkowa f [kHz]:</b><br>"
                + "Dokładna częstotliwość harmonicznej, którą chcemy wytłumić. Można ją odczytać z lewego wykresu FFT (gdzie szpilka zakłócenia wystaje wysoko ponad tło szumu).<br><br>"
                + "2. <b>Współczynnik dobroci Q (Q-factor):</b><br>"
                + "Określa selektywność i wąskość wycięcia filtru: <i>Q = f<sub>0</sub> / &Delta;f<sub>3dB</sub></i>.<br>"
                + "• <b>Niska dobroć (Q < 15):</b> Zbyt szerokie wycięcie pasma. Wycina również pożyteczne składowe widmowe impulsu WNZ, co zaokrągla jego czoło i spowalnia czas narastania <i>t<sub>r</sub></i>!<br>"
                + "• <b>Optymalna dobroć (Q = 20 &ndash; 35) &ndash; ZALECANA:</b> Bardzo ostre, wąskie wycięcie częstotliwości zakłócenia bez jakiegokolwiek wpływu na kształt impulsu WNZ.<br>"
                + "• <b>Bardzo wysoka dobroć (Q > 50):</b> Skrajnie wąskie pasmo, lecz przy bardzo stromych impulsach może wywołać zjawisko dzwonienia filtru (własne oscylacje resztkowe).<br><br>"
                + "3. <b>Opis (etykieta):</b><br>"
                + "Pozwala nazwać źródło zakłócenia (np. 'Falownik 100 kHz', 'Radio AM 1.25 MHz').", dark));

        sb.append(box("Automatyczny skaner zakłóceń NBI (Żółty przycisk na górze)", dark ? "#f59e0b" : "#d97706",
                "Kliknięcie przycisku <b>'Automatycznie skanuj i wykryj zakłócenia NBI'</b> uruchamia procedurę automatyczną:<br>"
                + "1. Oblicza 2048-punktowe widmo FFT surowego sygnału z oknem Blackmana-Harrisa.<br>"
                + "2. Estymuje medianę i gładką krzywą szumu tła w całym paśmie do 62.5 MHz.<br>"
                + "3. Wyszukuje ostre, stacjonarne szpilki spektralne przewyższające średnie tło o ponad <b>14 dB</b>.<br>"
                + "4. Automatycznie wpisuje wykryte częstotliwości do wolnych slotów banku Notch z bezpieczną dobrocią <i>Q=30</i>.<br>"
                + "<b>Zalecenie:</b> Po podłączeniu aparatury zawsze warto kliknąć ten przycisk jako pierwszy krok!", dark));

        return pageWrapper("Bank Filtrów Wycinających Notch (Wycinanie NBI)",
                "Precyzyjne usuwanie wąskopasmowych zakłóceń stacjonarnych wg IEC 62478",
                sb.toString(), dark);
    }

    private static String buildGateHtml(boolean dark) {
        StringBuilder sb = new StringBuilder();

        sb.append(box("Czym jest Bramka Szumowa (Noise Gate) i czym różni się od progu w oknie głównym?", dark ? "#38bdf8" : "#0284c7",
                "Wielu inżynierów myli bramkę szumową z prostym progiem dyskryminacji w oknie głównym. Różnica jest zasadnicza:<br><br>"
                + "• <b>Zwykły próg w oknie głównym (Trigger Threshold):</b> To prosty komparator. Jeśli sygnał przekroczy próg, zostaje uznany za impuls. Nie zmienia on jednak w żaden sposób samego sygnału &ndash; szum w tle nadal istnieje, a przebiegi czasowe są zaszumione.<br><br>"
                + "• <b>Bramka Szumowa DSP (Noise Gate):</b> Działa dynamicznie na cały strumień sygnału w czasie rzeczywistym. Posiada detektor obwiedni o ultraszybkim narastaniu (<i>fast attack</i> < 8 ns).<br>"
                + "&ndash; <b>W stanie ciszy</b> (gdy sygnał nie przekracza progu <i>T<sub>gate</sub></i>): bramka automatycznie tłumi cały tor o zadany współczynnik (np. -18 dB = 8-krotnie). Tło szumowe staje się idealnie ciche.<br>"
                + "&ndash; <b>Gdy nadchodzi impuls WNZ</b> (amplituda przekracza próg <i>T<sub>gate</sub></i>): bramka otwiera się natychmiast i przepuszcza 100% energii oraz rzeczywistego kształtu impulsu!", dark));

        sb.append(box("Kolano bramki (Soft-Knee) i Poziomy Tłumienia (dB)", dark ? "#34d399" : "#059669",
                "• <b>Płynne kolano (Soft-Knee):</b><br>"
                + "W klasycznych prostych bramkach przejście jest skokowe (tzw. <i>hard gating</i>), co potrafiło generować sztuczne trzaski w widmie FFT. Nasza bramka posiada łagodną strefę przejściową &plusmn;1.5 mV z <b>interpolacją Hermite'a 3. stopnia</b>. Otwieranie i zamykanie bramki jest całkowicie niesłyszalne i nie wprowadza żadnych artefaktów.<br><br>"
                + "• <b>Wybór poziomu tłumienia tła:</b><br>"
                + "• <b>-12 dB (x0.25):</b> Umiarkowane tłumienie (4-krotne zmniejszenie amplitudy szumu). Dobre do cichych laboratoriów badawczych.<br>"
                + "• <b>-18 dB (x0.125) &ndash; ZALECANE STANDARDOWE:</b> 8-krotne stłumienie szumu tła. Daje idealny kompromis między czystym tłem a zachowaniem naturalnego gasnącego ogona impulsu (rekomendacja CIGRE WG D1.33).<br>"
                + "• <b>-24 dB (x0.0625):</b> Głębokie, 16-krotne wygaszenie szumu. Stosowane na silnie zaszumionych obiektach przemysłowych.<br>"
                + "• <b>Pełne wyciszenie (0 mV - Hard Gating):</b> Całkowite zerowanie próbek w stanie ciszy.", dark));

        sb.append(box("Jak dobrać próg odcięcia szumu T_gate?", dark ? "#fbbf24" : "#d97706",
                "Złota zasada inżynierska:<br>"
                + "<div style='background-color:" + (dark ? "#17191e" : "#e2e8f0") + "; padding: 6px 10px; border-radius: 4px; font-weight:bold; font-size:12px; margin: 4px 0;'>"
                + "T_gate = (1.5 &ndash; 2.5) &times; zmierzony szum RMS tła</div>"
                + "Program mierzy na bieżąco szum tła RMS i wyświetla go w panelu bramki (np. <i>Szum RMS: 2.1 mV</i>).<br>"
                + "• Jeśli szum wynosi 2.1 mV, optymalny próg bramki to <b>3.5 &ndash; 5.0 mV</b>.<br>"
                + "• Możesz kliknąć przycisk <b>'Ustaw z RMS (x2.0)'</b>, a program ustawi próg automatycznie!", dark));

        return pageWrapper("Bramka Szumowa (Noise Gate) i Tłumienie Tła",
                "Dynamiczna eliminacja szumu stochastycznego z zachowaniem 100% kształtu impulsu WNZ",
                sb.toString(), dark);
    }

    private static String buildWorkflowHtml(boolean dark) {
        StringBuilder sb = new StringBuilder();

        sb.append(box("Procedura Strojenia Krok po Kroku (Instrukcja Praktyczna)", dark ? "#38bdf8" : "#0284c7",
                "Gdy otwierasz okno <i>Advanced DSP Denoising & Notch Filters</i>, wykonaj po kolei poniższe 6 kroków:<br><br>"
                + "<b>Krok 1: Weryfikacja sygnału surowego</b><br>"
                + "Spójrz na lewy wykres FFT oraz prawy wykres czasowy. Pomarańczowa linia to surowy sygnał z karty Red Pitaya.<br>"
                + "&ndash; Jeśli w widmie FFT widzisz ostre 'wieże' lub szpilki wystające wysoko nad poziom szumu, oznacza to obecność zakłóceń NBI (nadajniki radiowe, przetwornice).<br>"
                + "&ndash; Jeśli linia bazowa na prawym wykresie czasowym grubo oscyluje wokół zera, tor jest zaszumiony.<br><br>"
                + "<b>Krok 2: Automatyczne wykrycie zakłóceń NBI</b><br>"
                + "Kliknij żółty przycisk u góry: <b>'Automatycznie skanuj i wykryj zakłócenia NBI'</b>.<br>"
                + "Program sam przeskanuje widmo i przypisze wykryte częstotliwości do slotów Notch. Na lewym wykresie pojawią się czerwone linie przerywane oznaczające wycięte częstotliwości.<br><br>"
                + "<b>Krok 3: Wybór presetu zakłóceń (opcjonalnie)</b><br>"
                + "Jeśli pracujesz w pobliżu falowników napędowych, a automatyczny skaner nie wychwycił wszystkiego, kliknij przycisk <b>'Preset: Falowniki (100k, 500k)'</b>. Do slotów zostaną załadowane typowe częstotliwości komutacji IGBT.<br><br>"
                + "<b>Krok 4: Włączenie i zestrojenie Bramki Szumowej</b><br>"
                + "W prawym panelu zaznacz <b>'Aktywuj Bramkę Szumową (Noise Gating)'</b>.<br>"
                + "Sprawdź wyświetloną wartość <i>'Szacowany szum tła RMS'</i> i kliknij przycisk <b>'Ustaw z RMS (x2.0)'</b> lub wybierz jeden z szybkich presetów (np. <i>Standard 5.0 mV</i>).<br><br>"
                + "<b>Krok 5: Ocena zysku SNR na wykresach</b><br>"
                + "Porównaj przebiegi (pomarańczowy surowy vs błękitny odszumiony):<br>"
                + "&ndash; Na wykresie czasowym błękitny sygnał powinien mieć idealnie płaską linię bazową (zero), a sam impuls WNZ zachować pełną wysokość szczytową.<br>"
                + "&ndash; Na dolnym pasku zielony napis wskaże zysk: np. <b>Poprawa SNR: +12.4 dB</b>.<br><br>"
                + "<b>Krok 6: Aktywacja i utrwalenie w programie</b><br>"
                + "1. Kliknij <b>'Zastosuj do Toru Pomiarowego'</b> &ndash; od tego momentu główny wykres PRPD i oscyloskop będą operować na odszumionym sygnale.<br>"
                + "2. Kliknij <b>'Zapisz jako domyślne w konfiguracji'</b>, aby ustawienia nie zniknęły po zamknięciu programu.", dark));

        return pageWrapper("Praktyczny Przewodnik Krok po Kroku",
                "Jak skutecznie i bezpiecznie zestroić tor odszumiania w aparaturze wysokiego napięcia",
                sb.toString(), dark);
    }

    private static String buildOptimalHtml(boolean dark) {
        StringBuilder sb = new StringBuilder();

        sb.append(box("Tabela Punktów Odniesienia dla Typowych Środowisk Pomiarowych", dark ? "#38bdf8" : "#0284c7",
                "Poniższe zestawienie stanowi sprawdzony punkt wyjścia przy doborze nastaw w zależności od miejsca pomiaru:", dark));

        String thBg = dark ? "#2d333f" : "#e2e8f0";
        String trBg1 = dark ? "#22252b" : "#ffffff";
        String trBg2 = dark ? "#282c34" : "#f8fafc";
        String tdBorder = dark ? "#3e4451" : "#cbd5e1";

        sb.append("<table cellpadding='6' cellspacing='0' border='0' style='width:100%; border-collapse:collapse; font-size:11px; margin-bottom:12px;'>")
          .append("<tr style='background-color:").append(thBg).append("; font-weight:bold;'>")
          .append("<td style='border:1px solid ").append(tdBorder).append("; padding:6px;'>Środowisko / Obiekt</td>")
          .append("<td style='border:1px solid ").append(tdBorder).append("; padding:6px;'>Szum RMS</td>")
          .append("<td style='border:1px solid ").append(tdBorder).append("; padding:6px;'>Filtry Notch (NBI)</td>")
          .append("<td style='border:1px solid ").append(tdBorder).append("; padding:6px;'>Próg bramki T_gate</td>")
          .append("<td style='border:1px solid ").append(tdBorder).append("; padding:6px;'>Tłumienie dB</td>")
          .append("</tr>");

        // Row 1: Lab
        sb.append("<tr style='background-color:").append(trBg1).append(";'>")
          .append("<td style='border:1px solid ").append(tdBorder).append(";'><b>Laboratorium WN / Klatka Faradaya</b><br><span style='color:#8892b0;'>Czyste środowisko badawcze</span></td>")
          .append("<td style='border:1px solid ").append(tdBorder).append(";'>0.5 &ndash; 1.5 mV</td>")
          .append("<td style='border:1px solid ").append(tdBorder).append(";'>Zazwyczaj wyłączone (brak NBI)</td>")
          .append("<td style='border:1px solid ").append(tdBorder).append(";'><b>1.5 &ndash; 3.0 mV</b></td>")
          .append("<td style='border:1px solid ").append(tdBorder).append(";'>-12 dB</td>")
          .append("</tr>");

        // Row 2: Switchgear
        sb.append("<tr style='background-color:").append(trBg2).append(";'>")
          .append("<td style='border:1px solid ").append(tdBorder).append(";'><b>Rozdzielnica SN (1&ndash;36 kV) w stacji</b><br><span style='color:#8892b0;'>Falowniki, przetwornice, automatyka</span></td>")
          .append("<td style='border:1px solid ").append(tdBorder).append(";'>2.0 &ndash; 5.0 mV</td>")
          .append("<td style='border:1px solid ").append(tdBorder).append(";'>Falowniki (100k, 300k, 500k) lub auto-skan (Q=25&ndash;30)</td>")
          .append("<td style='border:1px solid ").append(tdBorder).append(";'><b>4.0 &ndash; 8.0 mV (Standard)</b></td>")
          .append("<td style='border:1px solid ").append(tdBorder).append(";'><b>-18 dB (Zalecane)</b></td>")
          .append("</tr>");

        // Row 3: Substation / Field
        sb.append("<tr style='background-color:").append(trBg1).append(";'>")
          .append("<td style='border:1px solid ").append(tdBorder).append(";'><b>Pomiary polowe / GPZ / Kable SN/WN</b><br><span style='color:#8892b0;'>Sensory HFCT na uziemieniach, linie napowietrzne</span></td>")
          .append("<td style='border:1px solid ").append(tdBorder).append(";'>5.0 &ndash; 15.0 mV</td>")
          .append("<td style='border:1px solid ").append(tdBorder).append(";'>Radio AM (1.25M, 2.5M, 10.7M) + auto-skan (Q=30&ndash;35)</td>")
          .append("<td style='border:1px solid ").append(tdBorder).append(";'><b>10.0 &ndash; 20.0 mV</b></td>")
          .append("<td style='border:1px solid ").append(tdBorder).append(";'>-18 dB lub -24 dB</td>")
          .append("</tr>");

        sb.append("</table>");

        sb.append(box("Wskazówka: Co zrobić, gdy nie wiesz co ustawić?", dark ? "#34d399" : "#059669",
                "W 90% przypadków w zupełności wystarcza prosta kombinacja domyślna:<br>"
                + "1. Kliknij <b>'Automatycznie skanuj i wykryj zakłócenia NBI'</b>.<br>"
                + "2. Włącz Bramkę Szumową z poziomem <b>-18 dB</b> i kliknij <b>'Ustaw z RMS (x2.0)'</b>.<br>"
                + "3. Kliknij <b>'Zastosuj do Toru Pomiarowego'</b>.<br>"
                + "Ta bezpieczna konfiguracja skutecznie czyści obraz PRPD bez jakiegokolwiek ryzyka obcięcia prawdziwych wyładowań niezupełnych.", dark));

        return pageWrapper("Wartości Optymalne i Punkty Odniesienia",
                "Rekomendowane nastawy toru odszumiania dla różnych obiektów elektroenergetycznych",
                sb.toString(), dark);
    }

    private static String buildNormsHtml(boolean dark) {
        StringBuilder sb = new StringBuilder();

        sb.append(box("Wytyczne Normatywne i Literatura Źródłowa", dark ? "#38bdf8" : "#0284c7",
                "Algorytmy zastosowane w niniejszym module zostały zaimplementowane ściśle według wytycznych międzynarodowych komitetów standaryzacyjnych:<br><br>"
                + "• <b>IEC TS 62478:2016:</b><br>"
                + "<i>'High voltage test techniques &ndash; Measurement of partial discharges by acoustic and electrical methods'</i><br>"
                + "Norma definiuje techniki pomiaru niekonwencjonalnego (pasma HF, VHF i UHF) oraz metody eliminacji zakłóceń NBI przy użyciu filtrów cyfrowych bez zniekształcania ładunku pozornego.<br><br>"
                + "• <b>CIGRE Technical Brochure 444 (Working Group D1.33, 2010):</b><br>"
                + "<i>'Guidelines for Unconventional Partial Discharge Measurements'</i><br>"
                + "Kluczowy dokument CIGRE określający wymagania dla filtracji pasmowej, wycinania zakłóceń radiofonicznych oraz dynamicznego bramkowania szumu (Noise Gating) w diagnostyce transformatorów i kabli WN w ruchu.<br><br>"
                + "• <b>CIGRE Technical Brochure 226 (Task Force 15.33.03.02, 2003):</b><br>"
                + "<i>'Knowledge Rules for Partial Discharge Diagnosis in Service'</i><br>"
                + "Wyznacza zasady separacji wielu źródeł wyładowań oraz metody unikania błędów diagnostycznych spowodowanych nakładaniem się zakłóceń komutacyjnych falowników.<br><br>"
                + "• <b>PN-EN 60270:2003 + A1:2016 (IEC 60270):</b><br>"
                + "Podstawowa norma pomiarów WNZ definiująca pojęcie szumu tła i wymóg zachowania odpowiedniego odstępu sygnału od szumu (SNR &ge; 2:1).", dark));

        return pageWrapper("Wytyczne Normatywne (IEC 62478 / CIGRE WG D1.33)",
                "Fundament formalno-prawny i inżynierski metod odszumiania w PRPD Tool",
                sb.toString(), dark);
    }
}
