package prpdtool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Knowledge Base and Documentation for High-Voltage PD Filtration and Denoising Techniques
 * (IEC 62478 / CIGRE WG D1.33 / IEEE Trans. DEI).
 * Explains mathematical definitions, filter mechanics, and practical defect impact.
 */
public class FiltrationLabDoc {

    public static class Entry {
        public final String key;
        public final String nameEn;
        public final String namePl;
        public final String domain;
        public final String definition;
        public final String mathFormulaHtml;
        public final String mathNote;
        public final String physicalRole;
        public final String aiImpact;

        public Entry(String key, String nameEn, String namePl, String domain,
                     String definition, String mathFormulaHtml, String mathNote,
                     String physicalRole, String aiImpact) {
            this.key = key;
            this.nameEn = nameEn;
            this.namePl = namePl;
            this.domain = domain;
            this.definition = definition;
            this.mathFormulaHtml = mathFormulaHtml;
            this.mathNote = mathNote;
            this.physicalRole = physicalRole;
            this.aiImpact = aiImpact;
        }

        public String getFullHtml(boolean dark) {
            String bg = dark ? "#1e2228" : "#ffffff";
            String text = dark ? "#dcdcdc" : "#222222";
            String titleColor = dark ? "#00e1ff" : "#0066cc";
            String hColor = dark ? "#ffe066" : "#b38600";
            String diagColor = dark ? "#66ff99" : "#008833";
            String boxBg = dark ? "#262b32" : "#f4f6f9";
            String boxBorder = dark ? "#3d444f" : "#d0d4dc";

            return "<html><body style='font-family: Segoe UI, Arial, sans-serif; font-size: 11px; color: " + text + "; background-color: " + bg + "; margin: 12px;'>"
                    + "<div style='font-size: 15px; font-weight: bold; color: " + titleColor + "; margin-bottom: 2px;'>"
                    + namePl + "</div>"
                    + "<div style='font-size: 12px; color: #888; margin-bottom: 12px;'>" + nameEn + " | Domena: <b>" + domain + "</b></div>"
                    
                    + "<div style='background-color: " + boxBg + "; border: 1px solid " + boxBorder + "; padding: 10px; border-radius: 4px; margin-bottom: 10px;'>"
                    + "<b style='color: " + hColor + "; font-size: 12px;'>1. Zasada działania i wzór matematyczny:</b><br>"
                    + "<p style='margin-top: 5px; margin-bottom: 8px; line-height: 1.45;'>" + definition + "</p><br>"
                    + "<b style='color: " + (dark ? "#ffffff" : "#111827") + "; font-size: 11px;'>Model matematyczny / Wzór:</b><br>"
                    + "<div style='background-color: " + (dark ? "#16191d" : "#ffffff") + "; border: 1px solid " + (dark ? "#4a5360" : "#c0c6d0") + "; border-left: 3px solid #00d4ff; padding: 10px 12px; margin-top: 6px; margin-bottom: 6px;'>"
                    + mathFormulaHtml + "</div>"
                    + "<span style='font-size: 10px; color: #888;'>" + mathNote + "</span>"
                    + "</div>"

                    + "<div style='background-color: " + boxBg + "; border: 1px solid " + boxBorder + "; padding: 10px; border-radius: 4px; margin-bottom: 10px;'>"
                    + "<b style='color: " + diagColor + "; font-size: 12px;'>2. Rola fizyczna w pomiarach WNZ na stacji / Hali WN:</b><br>"
                    + "<p style='margin-top: 5px; margin-bottom: 0px; line-height: 1.45;'>" + physicalRole + "</p>"
                    + "</div>"

                    + "<div style='background-color: " + boxBg + "; border: 1px solid " + boxBorder + "; padding: 10px; border-radius: 4px;'>"
                    + "<b style='color: #66d4ff; font-size: 12px;'>3. Wpływ filtracji na modele sztucznej inteligencji (AI):</b><br>"
                    + "<p style='margin-top: 5px; margin-bottom: 0px; line-height: 1.45;'>" + aiImpact + "</p>"
                    + "</div>"
                    + "</body></html>";
        }
    }

    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();

    static {
        add(new Entry(
                "raw",
                "Raw Signal (Unfiltered Baseline)",
                "Sygnał Surowy (Bez filtracji)",
                "Czasowa (Time Domain)",
                "Sygnał pobierany bezpośrednio z przetwornika ADC 14-bit karty Red Pitaya STEMlab 125-14 bez żadnej obróbki cyfrowej. Stanowi bazę odniesienia (Baseline) dla wszystkich pozostałych filtrów.",
                "<table cellpadding='0' cellspacing='0' border='0'><tr><td style='font-family:Cambria,Georgia,serif; font-size:13px; color:#e2f1ff;'>y[n] = x[n]</td></tr></table>",
                "Brak transformacji; sygnał zawiera pełne pasmo do częstotliwości Nyquista fs/2 = 62.5 MHz.",
                "W warunkach stacyjnych i laboratoryjnych sygnał surowy jest zdominowany przez przydźwięk sieciowy 50 Hz, zakłócenia impulsowe z falowników PWM oraz stacjonarny szum termiczny. Na wykresie PRPD powoduje powstanie gęstego, poziomego pasma zliczeń przy osi ładunku.",
                "Większość modeli AI (zwłaszcza klasyfikatory wizyjne 2D) w sygnale surowym wykazuje niską pewność predykcji (<40%) lub błędnie klasyfikuje szum jako wyładowania powierzchniowe (Surface) z powodu szerokiego rozkładu fałszywych impulsów."
        ));

        add(new Entry(
                "bandpass",
                "IIR Butterworth Bandpass Filter",
                "Cyfrowy Filtr Pasmo-Przepustowy (IIR Butterworth)",
                "Częstotliwościowa (IIR)",
                "Klasyczny filtr cyfrowy o płaskiej charakterystyce w paśmie przepustowym (maksymalnie płaski Butterworth). Odcina składowe niskoczęstotliwościowe (przydźwięk sieciowy 50 Hz, harmoniczne zasilania) oraz szum w.cz. powyżej pasma czujnika WNZ.",
                "<table cellpadding='0' cellspacing='0' border='0'>"
                + "<tr>"
                + "<td valign='middle' style='font-family:Cambria,serif; font-size:13px; color:#e2f1ff; padding-right:8px;'>|H(j&omega;)|<sup>2</sup> &nbsp;=&nbsp;</td>"
                + "<td valign='middle' align='center'>"
                + "<table cellpadding='0' cellspacing='0' border='0'>"
                + "<tr><td align='center' style='border-bottom:1px solid #38bdf8; font-family:Cambria,serif; font-size:12px; padding:1px 8px; color:#e2f1ff;'>1</td></tr>"
                + "<tr><td align='center' style='font-family:Cambria,serif; font-size:12px; padding:1px 8px; color:#e2f1ff;'>1 + (&omega; / &omega;<sub>c</sub>)<sup>2N</sup></td></tr>"
                + "</table>"
                + "</td>"
                + "</tr>"
                + "</table>",
                "Rząd filtru N = 2..4; pasmo przepustowe standardowo od f_low = 100 kHz do f_high = 20 MHz.",
                "Podstawowy wymóg normy PN-EN 60270 oraz IEC 62478. Skutecznie usuwa zakłócenia przemysłowe m.cz., umożliwiając wstępne wyodrębnienie impulsów wyładowań niezupełnych.",
                "Znacząco poprawia stosunek sygnału do szumu (SNR o 8-12 dB), eliminując fałszywe zliczenia w pobliżu zera. Pozwala sieciom CNN na stabilniejsze rozpoznawanie kątów zapłonu fazowego."
        ));

        add(new Entry(
                "notch",
                "Multi-Notch NBI Rejection Bank",
                "Bank Filtrów Notch (Eliminacja Nośnych NBI)",
                "Częstotliwościowa (Biquad IIR)",
                "Kaskada filtrów pasmowozaporowych o wysokiej dobroci Q (wąskie pasmo wycięcia). Służy do usuwania zakłóceń wąskopasmowych NBI (Narrowband Interference), takich jak fale nośne stacji radiowych AM/SW, radiolinii przemysłowych i systemów PLC.",
                "<table cellpadding='0' cellspacing='0' border='0'>"
                + "<tr>"
                + "<td valign='middle' style='font-family:Cambria,serif; font-size:13px; color:#e2f1ff; padding-right:8px;'>H<sub>notch</sub>(z) &nbsp;=&nbsp;</td>"
                + "<td valign='middle' align='center'>"
                + "<table cellpadding='0' cellspacing='0' border='0'>"
                + "<tr><td align='center' style='border-bottom:1px solid #38bdf8; font-family:Cambria,serif; font-size:12px; padding:1px 8px; color:#e2f1ff;'>1 &minus; 2 cos(&omega;<sub>0</sub>) z<sup>-1</sup> + z<sup>-2</sup></td></tr>"
                + "<tr><td align='center' style='font-family:Cambria,serif; font-size:12px; padding:1px 8px; color:#e2f1ff;'>1 &minus; 2 r cos(&omega;<sub>0</sub>) z<sup>-1</sup> + r<sup>2</sup> z<sup>-2</sup></td></tr>"
                + "</table>"
                + "</td>"
                + "</tr>"
                + "</table>",
                "&omega;<sub>0</sub> = 2&pi; f<sub>0</sub> / f<sub>s</sub>; promień biegunów r = 1 - (&pi; BW / f<sub>s</sub>); dobroć Q = f<sub>0</sub> / BW.",
                "Zakłócenia NBI wnikają przez antenę UHF lub cewkę HFCT i tworzą w sygnale ciągłą sinusoidę. Filtr wycina wyłącznie częstotliwość nośną, zachowując 99% szerokopasmowej energii impulsu WNZ.",
                "Zapobiega tworzeniu periodycznych pasm szumowych na obrazie PRPD, które modele AI mogłyby błędnie uznać za wyładowania ulotowe (Corona) o wysokiej częstotliwości powtarzania."
        ));

        add(new Entry(
                "fft_spectral",
                "FFT Spectral Subtraction & Masking",
                "Filtracja Widmowa FFT (Odejmowanie Widmowe)",
                "Częstotliwościowa (DFT/FFT)",
                "Nieliniowe przetwarzanie w dziedzinie częstotliwości przy użyciu szybkiej transformaty Fouriera. Wyznacza estymatę tła stacjonarnego |N(f)| i odejmuje je od widma sygnału |Y(f)| z zachowaniem oryginalnej fazy czasowej &theta;(f).",
                "<div style='font-family:Cambria,serif; font-size:13px; color:#e2f1ff; margin-bottom:6px;'>"
                + "|S(f)| &nbsp;=&nbsp; max( &beta; |Y(f)|, &nbsp;&nbsp; |Y(f)| &minus; &alpha; |N(f)| )"
                + "</div>"
                + "<div style='font-family:Cambria,serif; font-size:13px; color:#e2f1ff;'>"
                + "s[n] &nbsp;=&nbsp; IFFT{ |S(f)| &middot; e<sup>j &theta;(f)</sup> }"
                + "</div>",
                "&alpha; &ge; 1 (współczynnik nadmiarowego odejmowania), &beta; &approx; 0.05 (podłoga widmowa chroniąca przed tzw. szumem muzycznym).",
                "Idealna metoda w stacjach elektroenergetycznych, gdzie tło zakłóceń ma charakter stacjonarny o stałym widmie częstotliwościowym (np. pracujące wentylatory transformatora, przetwornice).",
                "Daje bardzo czysty kontur PRPD, co dramatycznie podnosi pewność klasyfikatorów YOLOv8 (wyraźniejsze krawędzie chmur wyładowań i brak fałszywych detekcji w tle)."
        ));

        add(new Entry(
                "noise_gate",
                "Dynamic Noise Gate",
                "Bramka Szumowa (Dynamic Noise Gating)",
                "Czasowo-Amplitudowa",
                "Adaptacyjne tłumienie szumu tła w pauzach między impulsami WNZ. Jeśli amplituda obwiedni sygnału spada poniżej progu adaptacyjnego T_gate (ustalanego z wielokrotności szumu RMS), sygnał jest tłumiony o zadaną wartość dB (np. -18 dB).",
                "<table cellpadding='0' cellspacing='0' border='0'>"
                + "<tr>"
                + "<td valign='middle' style='font-family:Cambria,serif; font-size:13px; color:#e2f1ff; padding-right:8px;'>y[n] = x[n] &middot; G(e[n]), &nbsp;&nbsp;&nbsp; G(e) &nbsp;=&nbsp;</td>"
                + "<td valign='middle'>"
                + "<table cellpadding='0' cellspacing='0' border='0'>"
                + "<tr><td style='font-family:Cambria,serif; font-size:12px; color:#e2f1ff; padding:2px 4px;'>1.0, &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; dla e &ge; T<sub>gate</sub></td></tr>"
                + "<tr><td style='font-family:Cambria,serif; font-size:12px; color:#e2f1ff; padding:2px 4px;'>&alpha;<sub>att</sub> + (1 &minus; &alpha;<sub>att</sub>) &middot; knee, &nbsp; dla e &lt; T<sub>gate</sub></td></tr>"
                + "</table>"
                + "</td>"
                + "</tr>"
                + "</table>",
                "e[n] = (1 - &lambda;) |x[n]| + &lambda; e[n-1] (śledzenie obwiedni sygnału); &alpha;<sub>att</sub> = 0.125 (-18 dB).",
                "WNZ trwają zaledwie ułamki mikrosekund (np. 50-200 ns), podczas gdy okres 50 Hz trwa 20 000 &mu;s. Przez 99.9% czasu w linii panuje cisza, w której sumuje się szum tła. Bramka usuwa ten szum, nie dotykając właściwych impulsów.",
                "Drastycznie redukuje liczbę fałszywych impulsów w histogramie N(&phi;). Sprawia, że wskaźniki asymetrii CIGRE (Q+/Q-, N+/N-) osiągają wartości zgodne z teorią laboratoryjną."
        ));

        add(new Entry(
                "dwt_wavelet",
                "Discrete Wavelet Transform (DWT - Daubechies db4)",
                "Dyskretna Transformata Falkowa (DWT Daubechies / Symlets)",
                "Czas-Skala (Wielorozdzielcza)",
                "Dekompozycja wielorozdzielcza sygnału algorytmem piramidalnym Mallata z adaptacyjnym progowaniem współczynników szczegółów Donoho-Johnstone VisuShrink. Szum szacowany jest estymatorem medianowym MAD z najwyższego pasma d_1.",
                "<table cellpadding='0' cellspacing='0' border='0'>"
                + "<tr>"
                + "<td nowrap='nowrap' valign='middle' style='font-family:Cambria,serif; font-size:13px; color:#e2f1ff; padding-right:18px;'><nobr>&lambda; &nbsp;=&nbsp; &sigma;<sub>MAD</sub> &middot; &radic;(2 ln N),</nobr></td>"
                + "<td nowrap='nowrap' valign='middle' style='font-family:Cambria,serif; font-size:13px; color:#e2f1ff; padding-right:8px;'><nobr>&sigma;<sub>MAD</sub> &nbsp;=&nbsp;</nobr></td>"
                + "<td nowrap='nowrap' valign='middle' align='center'>"
                + "<table cellpadding='0' cellspacing='0' border='0'>"
                + "<tr><td nowrap='nowrap' align='center' style='border-bottom:1px solid #38bdf8; font-family:Cambria,serif; font-size:12px; padding:1px 10px; color:#e2f1ff;'><nobr>median( |d<sub>1</sub>| )</nobr></td></tr>"
                + "<tr><td nowrap='nowrap' align='center' style='font-family:Cambria,serif; font-size:12px; padding:1px 10px; color:#e2f1ff;'><nobr>0.6745</nobr></td></tr>"
                + "</table>"
                + "</td>"
                + "</tr>"
                + "</table>"
                + "<div style='font-family:Cambria,serif; font-size:13px; color:#e2f1ff; margin-top:8px;'>"
                + "<nobr>d<sub>j</sub><sup>*</sup> &nbsp;=&nbsp; sgn(d<sub>j</sub>) &middot; max( 0, &nbsp; |d<sub>j</sub>| &minus; &lambda;<sub>j</sub> )</nobr>"
                + "</div>",
                "Baza falkowa: Daubechies db4 (8 współczynników) lub Symlet sym6. Dekompozycja na J = 4..5 poziomów.",
                "Uznawana w literaturze naukowej (IEEE Trans. DEI, IEC 62478) za najskuteczniejszą metodę dla WNZ, ponieważ falka Daubechies db4 ma kształt fizycznie zbliżony do tłumionej fali wyładowania w dielektryku.",
                "Zapewnia najwyższy zysk SNR (często >15 dB) przy zerowym rozmyciu stromego czoła impulsu. Daje najwyższą powtarzalność wyników dla modeli 1D-CNN i sieci głębokich ResNet."
        ));

        add(new Entry(
                "emd",
                "Empirical Mode Decomposition (EMD)",
                "Empiryczna Dekompozycja Modów (EMD - Funkcje IMF)",
                "Adaptacyjna Czasowa (Nieliniowa)",
                "Algorytm przesiewania (Huang sifting process) rozkładający niestacjonarny i nieliniowy sygnał WNZ na sumę funkcji składowych IMF (Intrinsic Mode Functions) oraz trend residuum bez narzucania stałej bazy funkcyjnej.",
                "<div style='font-family:Cambria,serif; font-size:13px; color:#e2f1ff; margin-bottom:8px;'>"
                + "<nobr>x(t) &nbsp;=&nbsp; &sum;<sub>k=1..K</sub> IMF<sub>k</sub>(t) &nbsp;+&nbsp; r(t)</nobr>"
                + "</div>"
                + "<table cellpadding='0' cellspacing='0' border='0'>"
                + "<tr>"
                + "<td nowrap='nowrap' valign='middle' style='font-family:Cambria,serif; font-size:13px; color:#e2f1ff; padding-right:8px;'><nobr>h<sub>1</sub>(t) &nbsp;=&nbsp; x(t) &minus;</nobr></td>"
                + "<td nowrap='nowrap' valign='middle' align='center'>"
                + "<table cellpadding='0' cellspacing='0' border='0'>"
                + "<tr><td nowrap='nowrap' align='center' style='border-bottom:1px solid #38bdf8; font-family:Cambria,serif; font-size:12px; padding:1px 10px; color:#e2f1ff;'><nobr>e<sub>max</sub>(t) + e<sub>min</sub>(t)</nobr></td></tr>"
                + "<tr><td nowrap='nowrap' align='center' style='font-family:Cambria,serif; font-size:12px; padding:1px 10px; color:#e2f1ff;'><nobr>2</nobr></td></tr>"
                + "</table>"
                + "</td>"
                + "</tr>"
                + "</table>",
                "Interpolacja obwiedni lokalnych maksimów i minimów naturalną sklejką sześcienną (Natural Cubic Spline).",
                "W pomiarach WNZ stochastyczny szum szerokopasmowy skupia się w pierwszym modzie IMF1, podczas gdy fizyczna oscylacja wyładowania dominuje w modach IMF2 i IMF3. Odrzucenie IMF1 eliminuje szum bez tłumienia energii WNZ.",
                "Pozwala modelom analizującym przebiegi 1D (1D-CNN oraz 1D-CNN-LSTM) na odtworzenie czystego profilu gasnących oscylacji, eliminując fluktuacje powodowane niestacjonarnym szumem stacji."
        ));
    }

    private static void add(Entry e) {
        ENTRIES.put(e.key.toLowerCase(), e);
    }

    public static List<Entry> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(ENTRIES.values()));
    }

    public static Entry get(String key) {
        if (key == null) return null;
        return ENTRIES.get(key.toLowerCase());
    }
}
