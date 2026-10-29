package prpdtool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Knowledge base and documentation for IEC 60270 / CIGRE WG 21.03 statistical descriptors.
 * Explains mathematical definitions, formal textbook formulas,
 * and practical defect interpretations.
 */
public class StatisticalDescriptorDoc {

    @FunctionalInterface
    public interface FormulaRenderer {
        String render(boolean dark);
    }

    public static class Entry {
        public final String key;
        public final String nameEn;
        public final String namePl;
        public final String unit;
        public final String definition;
        public final String mathFormula;
        public final String mathNote;
        public final String whatItSays;
        public final String defectDiagnosis;
        private final FormulaRenderer renderer;

        public Entry(String key, String nameEn, String namePl, String unit,
                     String definition, String mathFormula, String mathNote,
                     String whatItSays, String defectDiagnosis) {
            this(key, nameEn, namePl, unit, definition, mathFormula, mathNote, whatItSays, defectDiagnosis, null);
        }

        public Entry(String key, String nameEn, String namePl, String unit,
                     String definition, String mathFormula, String mathNote,
                     String whatItSays, String defectDiagnosis, FormulaRenderer renderer) {
            this.key = key;
            this.nameEn = nameEn;
            this.namePl = namePl;
            this.unit = unit;
            this.definition = definition;
            this.mathFormula = mathFormula;
            this.mathNote = mathNote;
            this.whatItSays = whatItSays;
            this.defectDiagnosis = defectDiagnosis;
            this.renderer = renderer;
        }

        public String getMathBoxHtml(boolean dark) {
            if (renderer != null) {
                return renderer.render(dark);
            }
            return mathBox(mathFormula, mathNote, dark);
        }

        public String getShortTooltipHtml() {
            return "<html><body style='width: 380px; font-family: Segoe UI, sans-serif; font-size: 11px; padding: 6px; color: #e6e6e6; background-color: #222428;'>"
                    + "<b style='color: #00d4ff; font-size: 12px;'>" + namePl + "</b> "
                    + "<span style='color: #aaa;'>[" + nameEn + "]</span><br><br>"
                    + "<b style='color: #ffe066;'>Definicja:</b><br>" + definition + "<br><br>"
                    + "<b style='color: #ffffff;'>Wzór formalny:</b><br>"
                    + getMathBoxHtml(true) + "<br>"
                    + "<b style='color: #66ff99;'>Co mówi o sygnale:</b><br>" + whatItSays + "<br><br>"
                    + "<i style='color: #88ccee;'>Kliknij dwukrotnie wiersz lub [?], aby otworzyć pełny opis i wzory.</i>"
                    + "</body></html>";
        }

        public String getFullHtml(boolean dark) {
            String bg = dark ? "#22252a" : "#ffffff";
            String text = dark ? "#dcdcdc" : "#222222";
            String titleColor = dark ? "#00e1ff" : "#0066cc";
            String hColor = dark ? "#ffe066" : "#b38600";
            String diagColor = dark ? "#66ff99" : "#008833";
            String boxBg = dark ? "#2c3036" : "#f4f6f9";
            String boxBorder = dark ? "#454b54" : "#d0d4dc";

            return "<html><body style='font-family: Segoe UI, Arial, sans-serif; font-size: 11px; color: " + text + "; background-color: " + bg + "; margin: 12px;'>"
                    + "<div style='font-size: 15px; font-weight: bold; color: " + titleColor + "; margin-bottom: 2px;'>"
                    + namePl + "</div>"
                    + "<div style='font-size: 12px; color: #888; margin-bottom: 12px;'>" + nameEn + " | Jednostka: <b>" + unit + "</b></div>"
                    + "<div style='background-color: " + boxBg + "; border: 1px solid " + boxBorder + "; padding: 10px; border-radius: 4px; margin-bottom: 10px;'>"
                    + "<b style='color: " + hColor + "; font-size: 12px;'>1. Definicja parametru i wzór matematyczny:</b><br>"
                    + "<p style='margin-top: 5px; margin-bottom: 8px; line-height: 1.45;'>" + definition + "</p><br>"
                    + "<b style='color: " + (dark ? "#ffffff" : "#111827") + "; font-size: 11px;'>Wzór formalny:</b><br>"
                    + getMathBoxHtml(dark)
                    + "</div>"
                    + "<div style='background-color: " + boxBg + "; border: 1px solid " + boxBorder + "; padding: 10px; border-radius: 4px; margin-bottom: 10px;'>"
                    + "<b style='color: " + diagColor + "; font-size: 12px;'>2. Znaczenie fizyczne i interpretacja sygnału:</b><br>"
                    + "<p style='margin-top: 5px; line-height: 1.45;'>" + whatItSays + "</p>"
                    + "</div>"
                    + "<div style='background-color: " + boxBg + "; border: 1px solid " + boxBorder + "; padding: 10px; border-radius: 4px;'>"
                    + "<b style='color: #ff9966; font-size: 12px;'>3. Interpretacja rodzaju defektu w izolacji:</b><br>"
                    + "<p style='margin-top: 5px; line-height: 1.45;'>" + defectDiagnosis + "</p>"
                    + "</div>"
                    + "</body></html>";
        }
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
          .append("; border-left: 3px solid #00d4ff; padding: 7px 12px; border-radius: 4px; margin-top: 4px;'>");

        sb.append("<table cellpadding='0' cellspacing='0' border='0'>");
        sb.append("<tr>");
        if (lhs != null && !lhs.isEmpty()) {
            sb.append("<td style='vertical-align: middle; color: ").append(textColor)
              .append("; font-family: Cambria, Georgia, serif; font-size: 12px; padding-right: 6px;'>")
              .append(lhs).append("</td>");
        }
        sb.append("<td style='vertical-align: middle; text-align: center;'>");
        sb.append("<table cellpadding='0' cellspacing='0' border='0'>");
        sb.append("<tr><td style='border-bottom: 1px solid ").append(lineColor)
          .append("; color: ").append(textColor)
          .append("; font-family: Cambria, Georgia, serif; font-size: 11px; text-align: center; padding: 1px 6px;'>")
          .append(num).append("</td></tr>");
        sb.append("<tr><td style='color: ").append(textColor)
          .append("; font-family: Cambria, Georgia, serif; font-size: 11px; text-align: center; padding: 1px 6px;'>")
          .append(den).append("</td></tr>");
        sb.append("</table>");
        sb.append("</td>");

        if (extra != null && !extra.isEmpty()) {
            sb.append("<td style='vertical-align: middle; color: ").append(textColor)
              .append("; font-family: Cambria, Georgia, serif; font-size: 12px; padding-left: 6px;'>")
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
                + "; border-left: 3px solid #00d4ff; padding: 7px 12px; border-radius: 4px; margin-top: 4px;'>"
                + "<div style='color: " + textColor + "; font-family: Cambria, Georgia, serif; font-size: 12px;'>"
                + mathHtml + "</div>"
                + noteHtml
                + "</div>";
    }

    public static String mathBox(String mathHtml, String note, boolean dark) {
        return eqSimple(mathHtml, note, dark);
    }

    public static String frac(String num, String den) {
        return "<table cellpadding='0' cellspacing='0' style='display:inline; vertical-align:middle; text-align:center; font-family: Cambria, Georgia, serif;'>"
                + "<tr><td style='border-bottom: 1px solid #38bdf8; padding: 0 4px; font-size: 11px; text-align:center; color:#e2f1ff;'>" + num + "</td></tr>"
                + "<tr><td style='padding: 0 4px; font-size: 11px; text-align:center; color:#e2f1ff;'>" + den + "</td></tr>"
                + "</table>";
    }

    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();

    static {
        // 1. N
        add("N",
                "Pulse Count (N)",
                "Liczba impulsów w półokresie (N)",
                "szt. [counts]",
                "Całkowita liczba wyładowań zarejestrowanych powyżej progu detekcji w dodatnim (0°-180°) lub ujemnym (180°-360°) półokresie sinusoidy 50 Hz.",
                "gdzie <i>H<sub>n</sub>(i)</i> to liczba impulsów zarejestrowanych w <i>i</i>-tym przedziale (binie) fazowym.",
                "Pokazuje intensywność jonizacji przy danej polaryzacji pola elektrycznego. Umożliwia natychmiastową ocenę, czy wyładowania występują symetrycznie w obu kierunkach prądu, czy faworyzują jeden kierunek pola.",
                "• <b>Pęcherze gazowe wewnątrz izolacji (Void / Internal):</b> Liczba wyładowań jest bardzo zbliżona w obu półokresach (N+ &asymp; N-), ponieważ pęcherz ma symetryczną geometrię.<br>"
                + "• <b>Ulot / Korona (Corona):</b> Skrajna asymetria! Wyładowania pojawiają się niemal wyłącznie w jednym półokresie (np. 95% w ujemnym przy ujemnym ostrzu).",
                dark -> eqSimple("<i>N</i><sup>+</sup> = &sum; <i>H</i><sub><i>n</i></sub>(<i>i</i>) &nbsp;&nbsp;<span style='font-size:11px; color:#94a3b8;'>[dla &phi; &isin; [0&deg;, 180&deg;)]</span>"
                        + " &nbsp;&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;&nbsp;&nbsp; "
                        + "<i>N</i><sup>&minus;</sup> = &sum; <i>H</i><sub><i>n</i></sub>(<i>i</i>) &nbsp;&nbsp;<span style='font-size:11px; color:#94a3b8;'>[dla &phi; &isin; [180&deg;, 360&deg;)]</span>",
                        "gdzie <i>H<sub>n</sub>(i)</i> to liczba impulsów zarejestrowanych w <i>i</i>-tym przedziale fazowym.", dark));

        // 2. Qmax
        add("Qmax",
                "Peak Magnitude (Qmax)",
                "Maksymalna amplituda ładunku (Qmax)",
                "mV",
                "Najwyższa wartość szczytowa impulsu zarejestrowana w danym półokresie napięcia zasilania (odpowiada ładunkowi pozornemu q_max wg IEC 60270).",
                "dla impulsów zakwalifikowanych w danym półokresie napięcia (&phi;<sub><i>k</i></sub> &isin; [0&deg;, 180&deg;) lub [180&deg;, 360&deg;)).",
                "Określa maksymalną energię pojedynczego uderzenia wyładowania w dielektryk. Mówi o doraźnym zagrożeniu i niszczącej sile zjawiska.",
                "• <b>Wysokie Qmax:</b> Świadczy o dużych inkluzjach gazowych, silnych przeskokach iskrowych lub zaawansowanych drzewieniach (treeing). Stwarza bezpośrednie ryzyko przebicia.<br>"
                + "• <b>Niskie, stabilne Qmax:</b> Typowe dla początkowej fazy ulotu w powietrzu lub mikro-pęcherzyków.",
                dark -> eqSimple("<i>Q</i><sub>max</sub> = max { |<i>u</i><sub><i>k</i></sub>| }",
                        "dla impulsów zakwalifikowanych w danym półokresie napięcia (&phi;<sub><i>k</i></sub> &isin; [0&deg;, 180&deg;) lub [180&deg;, 360&deg;)).", dark));

        // 3. u_Q
        add("u_Q",
                "Mean Magnitude (μ_Q)",
                "Średnia amplituda ładunku (μ_Q)",
                "mV",
                "Wartość oczekiwana (średnia arytmetyczna) ze wszystkich amplitud impulsów zakwalifikowanych w danym półokresie (Buczko 2018, Tabela 7.1).",
                "wartość oczekiwana (średnia arytmetyczna) amplitud wyładowań w rozpatrywanym półokresie.",
                "Pozwala ocenić typowy poziom energetyczny wyładowań. Odróżnia sygnał zdominowany przez rzadkie, pojedyncze „szpilki” od ciągłej chmury impulsów o stałej energii.",
                "• Jeśli średnia &mu;_Q jest bardzo bliska wartości szczytowej Qmax, wszystkie impulsy mają zbliżoną energię (bardzo stabilny proces jonizacji, np. czysty ulot na ostrzu).<br>"
                + "• Duża różnica między &mu;_Q a Qmax oznacza obecność nielicznych silnych wyładowań na tle drobnego tła.",
                dark -> eqFraction("<i>&mu;</i><sub><i>Q</i></sub> =", "1", "<i>N</i>", "&middot; &sum;<sub><i>k</i>=1</sub><sup><i>N</i></sup> |<i>u</i><sub><i>k</i></sub>|",
                        "wartość oczekiwana (średnia arytmetyczna) amplitud wyładowań w rozpatrywanym półokresie.", dark));

        // 4. sigma_Q
        add("sigma_Q",
                "Std Deviation (σ_Q)",
                "Odchylenie standardowe amplitud (σ_Q)",
                "mV",
                "Klasyczna miara rozrzutu (zróżnicowania) wielkości impulsów wokół ich wartości średniej (Buczko 2018, wzór 7.1).",
                "odchylenie standardowe amplitud wyładowań wokół wartości średniej w danym półokresie.",
                "Informuje o stabilności i powtarzalności zjawiska wyładowczego. Mówi, czy każde wyładowanie ma podobną siłę, czy proces jest chaotyczny.",
                "• <b>Niska wariancja (małe &sigma;_Q):</b> Wyładowania są bardzo powtarzalne, 'monotonne' — typowe dla korony (stała geometria ostrza).<br>"
                + "• <b>Wysoka wariancja (duże &sigma;_Q):</b> Duży chaos i niestabilność zjawiska — charakterystyczne dla wyładowań powierzchniowych ślizgowych (zmienna droga po zanieczyszczonym dielektryku) oraz degradacji muf kablowych.",
                dark -> eqFraction("<i>&sigma;</i><sub><i>Q</i></sub> = &radic; [", "1", "<i>N</i>", "&middot; &sum;<sub><i>k</i>=1</sub><sup><i>N</i></sup> (|<i>u</i><sub><i>k</i></sub>| &minus; <i>&mu;</i><sub><i>Q</i></sub>)<sup>2</sup> ]",
                        "odchylenie standardowe amplitud wyładowań wokół wartości średniej w danym półokresie.", dark));

        // 5. phi_inc
        add("phi_inc",
                "Inception Phase (φ_inc)",
                "Kąt fazowy zapłonu wyładowań (φ_inc)",
                "stopnie [°]",
                "Kąt fazowy fali napięcia 50 Hz, przy którym pojawia się pierwsze zarejestrowane wyładowanie w danym półokresie (Mikrut 2024).",
                "kąt fazowy pierwszego zarejestrowanego wyładowania o amplitudzie przekraczającej próg detekcji.",
                "Zgodnie z prawami fizyki wyładowanie zapala się, gdy chwilowa wartość napięcia przekracza napięcie zapłonu defektu (U_inc). Kąt zapłonu mówi o wielkości ładunku resztkowego zmagazynowanego w defekcie po poprzednim półokresie.",
                "• <b>Wczesny zapłon (20°-40° oraz 200°-220°):</b> Typowy dla pęcherzy wewnętrznych (Void). Ładunek osiadły na ściankach pęcherza dodaje się do napięcia sieci, wymuszając wczesny przeskok.<br>"
                + "• <b>Późny zapłon (60°-85° oraz 240°-265°):</b> Typowy dla wyładowań koronowych w otaczającym gazie, gdzie brak ścianek kumulujących ładunek — przeskok wymaga wysokiego napięcia blisko szczytu sinusoidy.",
                dark -> eqSimple("&phi;<sub>inc</sub> = min { &phi;<sub><i>k</i></sub> : |<i>u</i><sub><i>k</i></sub>| &ge; <i>U</i><sub>prog</sub> }",
                        "kąt fazowy pierwszego zarejestrowanego wyładowania o amplitudzie przekraczającej próg detekcji.", dark));

        // 6. mu_phi
        add("mu_phi",
                "Mean Phase (μ_φ)",
                "Środek ciężkości fazy (μ_φ)",
                "stopnie [°]",
                "Średni ważony kąt fazowy, wokół którego skupia się chmura zarejestrowanych wyładowań w danym półokresie.",
                "środek ciężkości (średnia ważona kąta fazowego) rozkładu wyładowań.",
                "Określa punkt kulminacyjny natężenia zjawiska na fali napięcia. Pozwala sprawdzić przesunięcie fazowe wyładowań względem napięcia zasilania.",
                "Dla symetrycznych uszkodzeń wewnętrznych odległość między środkami ciężkości w obu półokresach powinna wynosić dokładnie 180° (|&mu;_&phi;(-) - &mu;_&phi;(+)| = 180°). Odchyłki wskazują na asymetrię układu izolacyjnego lub nakładanie się zakłóceń.",
                dark -> eqFraction("<i>&mu;</i><sub>&phi;</sub> = &sum; <i>&phi;</i><sub><i>i</i></sub> &middot; <i>p</i><sub><i>i</i></sub> &nbsp;&nbsp;&nbsp;&nbsp;<span style='font-size:11px;'>(gdzie <i>p</i><sub><i>i</i></sub> =</span>",
                        "<i>H</i><sub><i>n</i></sub>(<i>i</i>)", "&sum; <i>H</i><sub><i>n</i></sub>", "<span style='font-size:11px;'>)</span>",
                        "środek ciężkości (średnia ważona kąta fazowego) rozkładu wyładowań.", dark));

        // 7. Sk_phi
        add("Sk_phi",
                "Phase Skewness (Sk_φ)",
                "Skośność rozkładu fazowego (Sk_φ)",
                "bezwymiarowy [-]",
                "Znormalizowany trzeci moment centralny profilu zliczeń Hn(&phi;) względem średniej fazy (wg metodyki E. Gulskiego i CIGRE WG 21.03; Buczko 2018, wzór 7.2).",
                "miara asymetrii kształtu obwiedni fazowej wyładowań wokół środka ciężkości &mu;<sub>&phi;</sub>.",
                "Mówi, czy wyładowania wolą pojawiać się przy narastaniu napięcia, czy przy jego opadaniu.",
                "• <b>Sk_&phi; > 0 (skośność dodatnia):</b> Gwałtowny wybuch wyładowań na początku półokresu z długim ogonem wygasającym w stronę szczytu. Znak rozpoznawczy pęcherzy gazowych (Internal Void).<br>"
                + "• <b>Sk_&phi; &asymp; 0:</b> Rozkład jest idealnie symetryczny wokół szczytu.<br>"
                + "• <b>Sk_&phi; < 0 (skośność ujemna):</b> Aktywność wyładowcza narasta powoli i osiąga maksimum dopiero przy szczycie lub opadaniu napięcia.",
                dark -> eqFraction("<i>Sk</i><sub>&phi;</sub> =", "&sum; (&phi;<sub><i>i</i></sub> &minus; <i>&mu;</i><sub>&phi;</sub>)<sup>3</sup> &middot; <i>p</i><sub><i>i</i></sub>", "<i>&sigma;</i><sub>&phi;</sub><sup>3</sup>", null,
                        "miara asymetrii kształtu obwiedni fazowej wyładowań wokół środka ciężkości &mu;<sub>&phi;</sub>.", dark));

        // 8. Ku_phi
        add("Ku_phi",
                "Phase Kurtosis (Ku_φ)",
                "Kurtoza rozkładu fazowego (Ku_φ)",
                "bezwymiarowy [-]",
                "Znormalizowany czwarty moment centralny profilu Hn(&phi;) pomniejszony o 3 (tzw. excess kurtosis względem rozkładu Gaussa, gdzie dla Gaussa = 0; Kluge 2021).",
                "współczynnik ekscesu (miara spiczastości i koncentracji wyładowań w oknie fazowym).",
                "Informuje, czy wyładowania są ściśnięte w bardzo wąskim 'okienku' fazowym, czy rozlane szeroko po całej połówce sinusoidy.",
                "• <b>Ku_&phi; > 1.5 (wysoka kurtoza):</b> Bardzo ostre skupisko impulsów w wąskim paśmie fazowym — typowe dla korony na ostrzu lub wyładowań ślizgowych na krawędzi elektrody.<br>"
                + "• <b>Ku_&phi; < 0 (niska/ujemna kurtoza):</b> Płaski rozkład impulsów — typowy dla szumu tła, luźnych cząstek metalowych lub rozproszonych mikropęcherzy.",
                dark -> eqFraction("<i>Ku</i><sub>&phi;</sub> =", "&sum; (&phi;<sub><i>i</i></sub> &minus; <i>&mu;</i><sub>&phi;</sub>)<sup>4</sup> &middot; <i>p</i><sub><i>i</i></sub>", "<i>&sigma;</i><sub>&phi;</sub><sup>4</sup>", "&minus; 3",
                        "współczynnik ekscesu (miara spiczastości i koncentracji wyładowań w oknie fazowym).", dark));

        // 9. Sk_Q
        add("Sk_Q",
                "Amp Skewness (Sk_Q)",
                "Skośność rozkładu amplitud (Sk_Q)",
                "bezwymiarowy [-]",
                "Asymetria rozkładu wielkości (energii) impulsów wokół średniej amplitudy &mu;_Q (Buczko 2018, Tabela 7.1).",
                "asymetria rozkładu gęstości energii pojedynczych impulsów wyładowczych.",
                "Mówi o strukturze energetycznej wyładowań — czy dominuje drobnica, czy silne impulsy.",
                "• <b>Sk_Q > 0:</b> Przewaga setek drobnych impulsów z nielicznymi silnymi wyładowaniami (prawostronny asymetryczny ogon).<br>"
                + "• <b>Sk_Q &asymp; 0:</b> Amplitudy rozkładają się symetrycznie wokół wartości średniej.",
                dark -> eqFraction("<i>Sk</i><sub><i>Q</i></sub> =", "&sum;<sub><i>k</i>=1</sub><sup><i>N</i></sup> (|<i>u</i><sub><i>k</i></sub>| &minus; <i>&mu;</i><sub><i>Q</i></sub>)<sup>3</sup>", "<i>N</i> &middot; <i>&sigma;</i><sub><i>Q</i></sub><sup>3</sup>", null,
                        "asymetria rozkładu gęstości energii pojedynczych impulsów wyładowczych.", dark));

        // 10. Ku_Q
        add("Ku_Q",
                "Amp Kurtosis (Ku_Q)",
                "Kurtoza rozkładu amplitud (Ku_Q)",
                "bezwymiarowy [-]",
                "Czwarty moment centralny rozkładu amplitud impulsów pomniejszony o 3 (współczynnik ekscesu; Buczko 2018, Tabela 7.1).",
                "miara obecności rzadkich, incydentalnych impulsów o potężnej energii na tle spokojnego sygnału.",
                "Pozwala zidentyfikować, czy w układzie dochodzi do rzadkich, incydentalnych przeskoków o potężnej energii niszczącej.",
                "• <b>Bardzo wysokie Ku_Q (> 3.0):</b> Obecność rzadkich, pojedynczych potężnych impulsów na tle względnie spokojnego sygnału.",
                dark -> eqFraction("<i>Ku</i><sub><i>Q</i></sub> =", "&sum;<sub><i>k</i>=1</sub><sup><i>N</i></sup> (|<i>u</i><sub><i>k</i></sub>| &minus; <i>&mu;</i><sub><i>Q</i></sub>)<sup>4</sup>", "<i>N</i> &middot; <i>&sigma;</i><sub><i>Q</i></sub><sup>4</sup>", "&minus; 3",
                        "miara obecności rzadkich, incydentalnych impulsów o potężnej energii na tle spokojnego sygnału.", dark));

        // 11. CC
        add("CC",
                "Cross-Correlation (CC)",
                "Współczynnik korelacji krzyżowej (CC)",
                "bezwymiarowy [-1 do +1]",
                "Współczynnik korelacji Pearsona pomiędzy profilem średniego ładunku w półokresie dodatnim Hqn+(&phi;) a ujemnym Hqn-(&phi;+180°) przesuniętym o 180° (CIGRE WG 21.03).",
                "gdzie <i>&mu;</i><sup>+</sup> i <i>&mu;</i><sup>&minus;</sup> oznaczają wartości średnie profili w odpowiednich półokresach.",
                "Fundamentalny wskaźnik symetrii procesów fizycznych zachodzących w defekcie w obu kierunkach prądu przemiennego.",
                "• <b>CC &ge; 0.75 (Wysoka korelacja):</b> Wyładowania przebiegają identycznie przy dodatniej i ujemnej polaryzacji. Jednoznaczny dowód wyładowań wewnętrznych (Void) w dielektryku stałym.<br>"
                + "• <b>CC &le; 0.35 (Niska korelacja / brak):</b> Całkowita niesymetryczność zjawiska. Klasyczny objaw korony (ulotu) w powietrzu lub zakłóceń komutacyjnych falowników.",
                dark -> eqFraction("<i>CC</i> =",
                        "&sum;<sub><i>i</i></sub> [ <i>H</i><sub><i>qn</i></sub><sup>+</sup>(<i>i</i>) &minus; <i>&mu;</i><sup>+</sup> ] &middot; [ <i>H</i><sub><i>qn</i></sub><sup>&minus;</sup>(<i>i</i>) &minus; <i>&mu;</i><sup>&minus;</sup> ]",
                        "&radic; [ &sum; ( <i>H</i><sub><i>qn</i></sub><sup>+</sup>(<i>i</i>) &minus; <i>&mu;</i><sup>+</sup> )<sup>2</sup> &middot; &sum; ( <i>H</i><sub><i>qn</i></sub><sup>&minus;</sup>(<i>i</i>) &minus; <i>&mu;</i><sup>&minus;</sup> )<sup>2</sup> ]",
                        null,
                        "gdzie <i>&mu;</i><sup>+</sup> i <i>&mu;</i><sup>&minus;</sup> oznaczają wartości średnie profili w odpowiednich półokresach.", dark));

        // 12. n
        add("n",
                "Repetition Rate (n)",
                "Częstotliwość repetycji wyładowań (n)",
                "impulsów / s [pps]",
                "Średnia liczba impulsów WNZ zarejestrowana w ciągu jednej sekundy (zgodnie z normą IEC 60270).",
                "częstotliwość repetycji impulsów WNZ w analizowanym oknie czasowym (norma IEC 60270).",
                "Informuje o intensywności i dynamice uszkodzenia izolacji.",
                "• Przy częstotliwości sieci 50 Hz, n = 100 pps oznacza dokładnie 1 impuls na każdy półokres napięcia.<br>"
                + "• Wartości n rzędu 1 000 - 10 000 pps świadczą o bardzo silnej jonizacji i obecności wielu ognisk wyładowczych jednocześnie.",
                dark -> eqFraction("<i>n</i> =", "<i>N</i><sub>total</sub>", "&Delta;<i>t</i>", "&nbsp;&nbsp;<span style='font-size:11px; color:#94a3b8;'>[impulsów / s]</span>",
                        "częstotliwość repetycji impulsów WNZ w analizowanym oknie czasowym (norma IEC 60270).", dark));

        // 13. total_charge
        add("total_charge",
                "Total Apparent Charge",
                "Sumaryczny ładunek pozorny",
                "mV",
                "Całka dyskretna (suma amplitud szczytowych) ze wszystkich zakwalifikowanych impulsów w oknie rejestracji.",
                "w torze skalibrowanym ładunkiem probierczym odpowiada całkowitemu ładunkowi w pC lub nC.",
                "Odzwierciedla całkowitą energię elektryczną wydzieloną w miejscu defektu, która zamienia się w ciepło, promieniowanie UV oraz reakcje chemiczne (np. wydzielanie kwasu azotowego i ozonu niszczącego żywicę lub XLPE).",
                "Służy do oceny tempa degradacji i szacowania pozostałego czasu życia eksploatacyjnego kabla lub rozdzielnicy.",
                dark -> eqSimple("<i>Q</i><sub>total</sub> = &sum;<sub><i>k</i>=1</sub><sup><i>N</i></sup> |<i>u</i><sub><i>k</i></sub>| &nbsp;&nbsp;[mV] &nbsp;&nbsp;&nbsp;&nbsp;&rarr;&nbsp;&nbsp;&nbsp;&nbsp; <i>Q</i><sub>total(IEC)</sub> = <i>k</i><sub>cal</sub> &middot; &sum; |<i>u</i><sub><i>k</i></sub>| &nbsp;&nbsp;[pC / nC]",
                        "w torze skalibrowanym ładunkiem probierczym odpowiada całkowitemu ładunkowi w pC lub nC.", dark));

        // 14. total_pulses
        add("total_pulses",
                "Total Pulses (N_total)",
                "Całkowita liczba impulsów",
                "szt. [pulses]",
                "Łączna liczba impulsów WNZ zarejestrowanych i zakwalifikowanych od początku pomiaru lub w wczytanym pliku.",
                "całkowita liczba zakwalifikowanych impulsów w całym oknie pomiarowym.",
                "Określa wiarygodność statystyczną wyliczonych parametrów.",
                "Normy zalecają, aby zbiór zawierał co najmniej 100-200 impulsów, aby wskaźniki skośności, kurtozy i korelacji osiągnęły pełną zbieżność i pewność diagnostyczną.",
                dark -> eqSimple("<i>N</i><sub>total</sub> = <i>N</i><sup>+</sup> + <i>N</i><sup>&minus;</sup>",
                        "całkowita liczba zakwalifikowanych impulsów w całym oknie pomiarowym.", dark));

        // 15. Q_asym
        add("Q_asym",
                "Charge Asymmetry (Q = Q+/Q-)",
                "Wskaźnik asymetrii ładunku (Q = Qmax+ / Qmax-)",
                "iloraz [-]",
                "Stosunek maksymalnej amplitudy wyładowań w dodatnim półokresie do maksymalnej amplitudy w ujemnym.",
                "iloraz szczytowych amplitud ładunku w obu półokresach (CIGRE WG 21.03).",
                "Wskazuje na geometryczną symetrię lub asymetrię elektrod tworzących defekt.",
                "• <b>Q &asymp; 1.0 (0.8 - 1.25):</b> Defekt symetryczny (np. pęcherz gazowy zamknięty w jednorodnym dielektryku).<br>"
                + "• <b>Q > 2.0 lub Q < 0.5:</b> Silna asymetria pola — ostrze pod wysokim napięciem względem uziemienia (ulot / korona).",
                dark -> eqFraction("<i>Q</i> =", "<i>Q</i><sub>max</sub><sup>+</sup>", "<i>Q</i><sub>max</sub><sup>&minus;</sup>", null,
                        "iloraz szczytowych amplitud ładunku w obu półokresach (CIGRE WG 21.03).", dark));

        // 16. N_asym
        add("N_asym",
                "Count Asymmetry (N = N+/N-)",
                "Wskaźnik asymetrii liczności (N = N+ / N-)",
                "iloraz [-]",
                "Stosunek liczby wyładowań w dodatnim półokresie do liczby wyładowań w ujemnym.",
                "iloraz liczności wyładowań pomiędzy połówkami sinusoidy napięcia zasilania.",
                "Podobnie jak wskaźnik Q, pozwala błyskawicznie odróżnić symetryczne wtrącenia wewnętrzne od jednostronnego ulotu.",
                "Wartości bliskie 1.0 świadczą o pustkach wewnętrznych, a wartości silnie odbiegające (> 3.0 lub < 0.33) o ulotach lub wadach montażowych głowic kablowych.",
                dark -> eqFraction("<i>N</i><sub>ratio</sub> =", "<i>N</i><sup>+</sup>", "<i>N</i><sup>&minus;</sup>", null,
                        "iloraz liczności wyładowań pomiędzy połówkami sinusoidy napięcia zasilania.", dark));
    }

    private static void add(String key, String nameEn, String namePl, String unit,
                            String definition, String mathNote,
                            String whatItSays, String defectDiagnosis,
                            FormulaRenderer renderer) {
        ENTRIES.put(key, new Entry(key, nameEn, namePl, unit, definition, "", mathNote, whatItSays, defectDiagnosis, renderer));
    }

    public static Entry get(String key) {
        return ENTRIES.get(key);
    }

    public static List<Entry> getAll() {
        return new ArrayList<>(ENTRIES.values());
    }

    public static Entry findByRowName(String rowName) {
        if (rowName == null) return null;
        String lower = rowName.toLowerCase();
        if (lower.contains("repetition rate") || lower.contains("[pps]")) {
            return ENTRIES.get("n");
        }
        if (lower.contains("pulse count") || lower.contains("(n)")) {
            if (lower.contains("total")) return ENTRIES.get("total_pulses");
            if (lower.contains("n+/n-") || lower.contains("asymmetry")) return ENTRIES.get("N_asym");
            return ENTRIES.get("N");
        }
        if (lower.contains("peak magnitude") || lower.contains("qmax")) {
            if (lower.contains("q+/q-") || lower.contains("asymmetry")) return ENTRIES.get("Q_asym");
            return ENTRIES.get("Qmax");
        }
        if (lower.contains("mean magnitude") || lower.contains("μ_q") || lower.contains("mu_q")) {
            return ENTRIES.get("u_Q");
        }
        if (lower.contains("std deviation") || lower.contains("σ_q") || lower.contains("sigma_q")) {
            return ENTRIES.get("sigma_Q");
        }
        if (lower.contains("inception phase") || lower.contains("φ_inc") || lower.contains("phi_inc")) {
            return ENTRIES.get("phi_inc");
        }
        if (lower.contains("mean phase") || lower.contains("μ_φ") || lower.contains("mu_phi")) {
            return ENTRIES.get("mu_phi");
        }
        if (lower.contains("phase skewness") || lower.contains("sk_φ") || lower.contains("sk_phi")) {
            return ENTRIES.get("Sk_phi");
        }
        if (lower.contains("phase kurtosis") || lower.contains("ku_φ") || lower.contains("ku_phi")) {
            return ENTRIES.get("Ku_phi");
        }
        if (lower.contains("amp skewness") || lower.contains("sk_q")) {
            return ENTRIES.get("Sk_Q");
        }
        if (lower.contains("amp kurtosis") || lower.contains("ku_q")) {
            return ENTRIES.get("Ku_Q");
        }
        if (lower.contains("cross-correlation") || lower.contains("(cc)")) {
            return ENTRIES.get("CC");
        }
        if (lower.contains("total apparent")) {
            return ENTRIES.get("total_charge");
        }
        if (lower.contains("total pulses")) {
            return ENTRIES.get("total_pulses");
        }
        return null;
    }
}
