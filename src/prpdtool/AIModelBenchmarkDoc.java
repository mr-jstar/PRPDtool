package prpdtool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Knowledge Base and Theoretical Reference for AI Models and Classifiers in Partial Discharge Diagnosis
 * (IEEE Trans. DEI / CIGRE WG 21.03 / Kluge 2021-2023 / Łasica 2024).
 * Explains architecture, mathematical formulation, input representations, and physical diagnostic roles.
 */
public class AIModelBenchmarkDoc {

    public static class Entry {
        public final String key;
        public final String nameEn;
        public final String namePl;
        public final String family;
        public final String inputRepresentation;
        public final String architectureSummary;
        public final String mathFormulaHtml;
        public final String mathNote;
        public final String strengthAndSuitability;
        public final String diagnosticInterpretation;

        public Entry(String key, String nameEn, String namePl, String family,
                     String inputRepresentation, String architectureSummary,
                     String mathFormulaHtml, String mathNote,
                     String strengthAndSuitability, String diagnosticInterpretation) {
            this.key = key;
            this.nameEn = nameEn;
            this.namePl = namePl;
            this.family = family;
            this.inputRepresentation = inputRepresentation;
            this.architectureSummary = architectureSummary;
            this.mathFormulaHtml = mathFormulaHtml;
            this.mathNote = mathNote;
            this.strengthAndSuitability = strengthAndSuitability;
            this.diagnosticInterpretation = diagnosticInterpretation;
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
                    + "<div style='font-size: 12px; color: #888; margin-bottom: 12px;'>" + nameEn + " | Paradygmat: <b>" + family + "</b></div>"

                    + "<div style='background-color: " + boxBg + "; border: 1px solid " + boxBorder + "; padding: 10px; border-radius: 4px; margin-bottom: 10px;'>"
                    + "<b style='color: " + hColor + "; font-size: 12px;'>1. Architektura modelu i reprezentacja wejściowa:</b><br>"
                    + "<p style='margin-top: 5px; margin-bottom: 6px; line-height: 1.45;'>" + architectureSummary + "</p>"
                    + "<span style='font-size: 10px; color: #66d4ff;'>Dane wejściowe: <b>" + inputRepresentation + "</b></span><br><br>"
                    + "<b style='color: " + (dark ? "#ffffff" : "#111827") + "; font-size: 11px;'>Sformułowanie matematyczne:</b><br>"
                    + "<div style='background-color: " + (dark ? "#16191d" : "#ffffff") + "; border: 1px solid " + (dark ? "#4a5360" : "#c0c6d0") + "; border-left: 3px solid #00d4ff; padding: 10px 12px; margin-top: 6px; margin-bottom: 6px;'>"
                    + mathFormulaHtml + "</div>"
                    + "<span style='font-size: 10px; color: #888;'>" + mathNote + "</span>"
                    + "</div>"

                    + "<div style='background-color: " + boxBg + "; border: 1px solid " + boxBorder + "; padding: 10px; border-radius: 4px; margin-bottom: 10px;'>"
                    + "<b style='color: " + diagColor + "; font-size: 12px;'>2. Zalety i zastosowanie w aparaturze SN/WN:</b><br>"
                    + "<p style='margin-top: 5px; margin-bottom: 0px; line-height: 1.45;'>" + strengthAndSuitability + "</p>"
                    + "</div>"

                    + "<div style='background-color: " + boxBg + "; border: 1px solid " + boxBorder + "; padding: 10px; border-radius: 4px;'>"
                    + "<b style='color: #ffb86c; font-size: 12px;'>3. Interpretacja fizyczna w diagnostyce wyładowań:</b><br>"
                    + "<p style='margin-top: 5px; margin-bottom: 0px; line-height: 1.45;'>" + diagnosticInterpretation + "</p>"
                    + "</div>"
                    + "</body></html>";
        }
    }

    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();

    static {
        // 1. YOLOv8
        add(new Entry(
                "yolo_v8",
                "YOLOv8-PRPD Object Detection",
                "YOLOv8-PRPD (Detekcja Skupisk Fazowych)",
                "Wizja 2D (PRPD Pattern)",
                "Obraz fazowo-rozdzielczy PRPD 640x640 (RGB) z kolorową gęstością wyładowań",
                "Model detekcji obiektowej bezkotwicznej (anchor-free) z modułami C2f i decoupled head. Rozpoznaje skupiska wyładowań w przestrzeni (faza, ładunek) nakładając na nie ramki BBoxes.",
                "<div style='font-family:Cambria,serif; font-size:13px; color:#e2f1ff; margin-bottom:6px;'>"
                + "Loss &nbsp;=&nbsp; &lambda;<sub>box</sub> &middot; L<sub>CIoU</sub> &nbsp;+&nbsp; &lambda;<sub>cls</sub> &middot; L<sub>BCE</sub> &nbsp;+&nbsp; &lambda;<sub>dfl</sub> &middot; L<sub>DFL</sub>"
                + "</div>"
                + "<table cellpadding='0' cellspacing='0' border='0'>"
                + "<tr>"
                + "<td nowrap='nowrap' valign='middle' style='font-family:Cambria,serif; font-size:13px; color:#e2f1ff; padding-right:8px;'><nobr>CIoU &nbsp;=&nbsp; IoU &nbsp;&minus;</nobr></td>"
                + "<td nowrap='nowrap' valign='middle' align='center'>"
                + "<table cellpadding='0' cellspacing='0' border='0'>"
                + "<tr><td nowrap='nowrap' align='center' style='border-bottom:1px solid #38bdf8; font-family:Cambria,serif; font-size:12px; padding:1px 10px; color:#e2f1ff;'><nobr>&rho;<sup>2</sup>(b, b<sup>gt</sup>)</nobr></td></tr>"
                + "<tr><td nowrap='nowrap' align='center' style='font-family:Cambria,serif; font-size:12px; padding:1px 10px; color:#e2f1ff;'><nobr>c<sup>2</sup></nobr></td></tr>"
                + "</table>"
                + "</td>"
                + "<td nowrap='nowrap' valign='middle' style='font-family:Cambria,serif; font-size:13px; color:#e2f1ff; padding-left:8px;'><nobr>&minus;&nbsp; &alpha; &nu;</nobr></td>"
                + "</tr>"
                + "</table>",
                "IoU (Intersection over Union), &rho; (odległość środków), c (przekątna otaczająca), &nu; (spójność proporcji boków).",
                "Jedyny model zdolny do jednoczesnego wykrywania wielu współistniejących defektów w rozdzielnicy SN (np. ulot na szynie + wyładowania na izolatorze).",
                "Daje wgląd przestrzenny: wskazuje dokładne przedziały fazowe zapłonu i rozpiętość ładunku każdego wykrytego skupiska."
        ));

        // 2. SqueezeNet
        add(new Entry(
                "squeezenet",
                "SqueezeNet / MobileNetV3 (Edge CNN)",
                "SqueezeNet / MobileNetV3 (Ultralekki 2D-CNN)",
                "Wizja 2D (PRPD Pattern / Skalogram)",
                "Skalowany obraz PRPD 224x224 lub skalogram falkowy CWT pojedynczego impulsu",
                "Kompaktowa sieć konwolucyjna wykorzystująca moduły 'Fire' (warstwa squeeze 1x1 kompresująca liczbę kanałów + expand z filtrami 1x1 i 3x3).",
                "<div style='font-family:Cambria,serif; font-size:13px; color:#e2f1ff;'>"
                + "Fire(x) &nbsp;=&nbsp; Concat[ &nbsp; Conv<sub>1x1</sub>( Squeeze(x) ), &nbsp; Conv<sub>3x3</sub>( Squeeze(x) ) &nbsp; ]"
                + "</div>",
                "Parametry < 1.2 mln wag, rozmiar modelu < 2.5 MB, czas inferencji rzędu 1.5 - 2.5 ms.",
                "Zbadana i opisana przez Kluge et al. (2021). Pozwala na inferencję w czasie rzeczywistym bezpośrednio na mikrokontrolerze ARM karty Red Pitaya.",
                "Osiąga ponad 97% skuteczności w rozróżnianiu 4 głównych klas uszkodzeń rozdzielnic SN przy minimalnym obciążeniu procesora."
        ));

        // 3. ResNet-18
        add(new Entry(
                "resnet_18",
                "ResNet-18 (Deep Residual Network)",
                "ResNet-18 (Głęboka Sieć Resztkowa)",
                "Wizja 2D (PRPD Pattern)",
                "Znormalizowany obraz PRPD 224x224 (RGB) o wysokiej dynamice barw",
                "18-warstwowa sieć konwolucyjna z połączeniami skrótowymi (skip connections). Przekazuje sygnał tożsamościowy z pominięciem warstw wagowych, zapobiegając zanikaniu gradientu.",
                "<div style='font-family:Cambria,serif; font-size:13px; color:#e2f1ff; margin-bottom:6px;'>"
                + "y &nbsp;=&nbsp; &fnof;( x, {W<sub>i</sub>} ) &nbsp;+&nbsp; x"
                + "</div>"
                + "<div style='font-family:Cambria,serif; font-size:13px; color:#e2f1ff;'>"
                + "&fnof;(x, {W<sub>i</sub>}) &nbsp;=&nbsp; W<sub>2</sub> &middot; &sigma;( W<sub>1</sub> x + b<sub>1</sub> ) + b<sub>2</sub>"
                + "</div>",
                "Blok resztkowy; &sigma; oznacza funkcję aktywacji ReLU, W_i macierze wagowe splotów 3x3.",
                "Złoty standard w klasyfikacji obrazów przemysłowych. Najwyższa zdolność generalizacji na danych zniekształconych i nietypowych geometriach.",
                "Doskonale wyłapuje rozmyte, asymetryczne struktury wyładowań wewnętrznych w dielektryku stałym (Void) i odróżnia je od szumów."
        ));

        // 4. 2D-CNN-LSTM
        add(new Entry(
                "cnn_lstm_2d",
                "2D-CNN-LSTM (Spatio-Temporal Sequence)",
                "2D-CNN-LSTM (Sekwencja Czasowo-Przestrzenna)",
                "Wizja 2D + Czas (Multi-Frame)",
                "Sekwencja 5 kolejnych ramek PRPD w oknie czasowym T = 100-500 ms",
                "Hybryda sieci splotowej 2D i rekurencyjnej LSTM. Sploty 2D redukują obraz do wektora cech, a komórki LSTM modelują ewolucję wyładowań w kolejnych okresach sinusoidy.",
                "<div style='font-family:Cambria,serif; font-size:13px; color:#e2f1ff; line-height: 1.8;'>"
                + "f<sub>t</sub> = &sigma;( W<sub>f</sub> x<sub>t</sub> + U<sub>f</sub> h<sub>t-1</sub> + b<sub>f</sub> )<br>"
                + "c<sub>t</sub> = f<sub>t</sub> &middot; c<sub>t-1</sub> + i<sub>t</sub> &middot; tanh( W<sub>c</sub> x<sub>t</sub> + U<sub>c</sub> h<sub>t-1</sub> + b<sub>c</sub> )<br>"
                + "h<sub>t</sub> = o<sub>t</sub> &middot; tanh( c<sub>t</sub> )"
                + "</div>",
                "Bramki: f_t (zapominania), i_t (wejściowa), o_t (wyjściowa), c_t (komórka pamięci), h_t (stan ukryty).",
                "Niezastąpiona przy próbach starzeniowych na Hali WN PW podczas obserwacji ewolucji defektu (przejście wyładowań powierzchniowych w stan łukowy).",
                "Wykrywa dynamikę zmian intensywności wyładowań w czasie i migrację kątów zapłonu, ostrzegając przed przeskokiem."
        ));

        // 5. 1D-CNN
        add(new Entry(
                "cnn_1d",
                "1D-CNN (Temporal Pulse Morphology)",
                "1D-CNN (Jednowymiarowa Sieć Konwolucyjna)",
                "Czasowa 1D (Przebieg u(t))",
                "Surowy wektor próbek impulsu u(t) (1024 punkty, próbkowanie 125 MS/s)",
                "Sieć operująca bezpośrednio na wektorze czasowym sygnału napięciowego. Filtry splotowe 1D przesuwają się po przebiegu impulsu wyładowania, dokonując detekcji mikro-cech kształtu.",
                "<div style='font-family:Cambria,serif; font-size:13px; color:#e2f1ff;'>"
                + "y[i] &nbsp;=&nbsp; &sigma;&Big( &sum;<sub>k=1</sub><sup>K</sup> x[i &middot; s + k] &middot; w[k] &nbsp;+&nbsp; b &Big)"
                + "</div>",
                "s (krok splotu), K (rozmiar jądra 1D, np. 5-15 próbek), &sigma; (aktywacja ReLU/LeakyReLU).",
                "Nie wymaga akumulacji setek okresów napięcia do zbudowania PRPD – potrafi sklasyfikować typ defektu z pojedynczego impulsu w czasie <3 ms.",
                "Fizycznie odróżnia bardzo szybkie wyładowania ulotowe (czas narastania 5-15 ns) od wolniejszych wyładowań powierzchniowych i wewnętrznych."
        ));

        // 6. 1D-CNN-LSTM
        add(new Entry(
                "cnn_lstm_1d",
                "1D-CNN-LSTM (Pulse Cascade Recurrence)",
                "1D-CNN-LSTM (Kaskada Impulsów w Okresie 20 ms)",
                "Czasowa 1D + Sekwencja Fazowa",
                "Ciąg kolejnych pojedynczych impulsów 1D uporządkowanych według kąta fazowego &phi;",
                "Połączenie splotów 1D (do ekstrakcji morfologii fali) z warstwą rekurencyjną LSTM modelującą zjawisko pamięci ładunku przestrzennego (space charge memory effect).",
                "<div style='font-family:Cambria,serif; font-size:13px; color:#e2f1ff;'>"
                + "h<sub>m</sub> &nbsp;=&nbsp; LSTM( &Phi;<sub>1D-CNN</sub>( u<sub>m</sub>(t) ), &nbsp;&nbsp; h<sub>m-1</sub> )"
                + "</div>",
                "u_m(t) (m-ty impuls w cyklu), &Phi; (wektor cech kształtu), h_m (akumulowany stan dynamiczny ładunku przestrzennego).",
                "Rozwiązuje problem klasyfikacji zjawisk, w których wystąpienie jednego impulsu zmienia lokalne pole elektryczne i opóźnia kolejne wyładowanie.",
                "Skuteczna przy badaniu defektów głowic kablowych i wtrąceń gazowych w przekładnikach prądowych/napięciowych SN."
        ));

        // 7. SVM
        add(new Entry(
                "svm_quadratic",
                "Support Vector Machine (Quadratic Kernel)",
                "SVM (Support Vector Machine - Jądro Wielomianowe)",
                "Cechy Statystyczne CIGRE/IEC",
                "Znormalizowany wektor 16 deskryptorów statystycznych (Sk, Ku, Q+/Q-, N+/N-, CC, &phi;_inc)",
                "Algorytm maksymalizacji marginesu geometrycznego oddzielającego klasy defektów. Wykorzystuje nieliniowe jądro wielomianowe stopnia d=2 do odwzorowania cech w przestrzeni wyższego wymiaru.",
                "<div style='font-family:Cambria,serif; font-size:13px; color:#e2f1ff; margin-bottom:6px;'>"
                + "&fnof;(x) &nbsp;=&nbsp; sgn&Big( &sum;<sub>i=1</sub><sup>M</sup> &alpha;<sub>i</sub> y<sub>i</sub> &middot; K(x<sub>i</sub>, x) &nbsp;+&nbsp; b &Big)"
                + "</div>"
                + "<div style='font-family:Cambria,serif; font-size:13px; color:#e2f1ff;'>"
                + "K(x<sub>i</sub>, x) &nbsp;=&nbsp; ( x<sub>i</sub><sup>T</sup> x + c )<sup>2</sup>"
                + "</div>",
                "&alpha;_i (mnożniki Lagrange'a), y_i &isin; {-1, +1}, c &ge; 0 (parametr swobodny jądra).",
                "Publikowany przez Kluge (2021) jako najdokładniejszy klasyfikator cech manualnych (95.7% skuteczności w rozdzielnicy AIS). Czas obliczeń <0.2 ms.",
                "Idealnie separuje wyładowania ulotowe od wewnętrznych na podstawie asymetrii ładunku Q+/Q- (>1.8) oraz korelacji fazowej CC (<0.2)."
        ));

        // 8. Random Forest
        add(new Entry(
                "random_forest",
                "Random Forest (Decision Tree Ensemble)",
                "Random Forest (RF - Zespół Drzew Decyzyjnych)",
                "Cechy Statystyczne CIGRE/IEC",
                "Wektor deskryptorów rozkładu fazowego Hn(&phi;), Hqn(&phi;) i Hqmax(&phi;)",
                "Komitet 20 drzew decyzyjnych trenowanych metodą bootstrap aggregation z losowym doborem cech. Decyzja zapada w drodze głosowania większościowego.",
                "<div style='font-family:Cambria,serif; font-size:13px; color:#e2f1ff; margin-bottom:6px;'>"
                + "I<sub>G</sub>(p) &nbsp;=&nbsp; 1 &minus; &sum;<sub>i=1</sub><sup>C</sup> p<sub>i</sub><sup>2</sup>"
                + "</div>"
                + "<div style='font-family:Cambria,serif; font-size:13px; color:#e2f1ff;'>"
                + "&fnof;<sub>RF</sub>(x) &nbsp;=&nbsp; argmax<sub>c</sub> &sum;<sub>b=1</sub><sup>B</sup> I( T<sub>b</sub>(x) == c )"
                + "</div>",
                "I_G (indeks Giniego), p_i (udział klasy i), B (liczba drzew), T_b (predykcja b-tego drzewa).",
                "Całkowicie odporny na punkty odstające i zakłócenia pojedynczych pomiarów. Generuje automatyczną ocenę ważności cech (Feature Importance).",
                "Klasyfikuje wyładowania powierzchniowe z wysoką pewnością dzięki wielopoziomowej analizie skośności fazowej Sk i szerokości okna wyładowań."
        ));

        // 9. k-NN
        add(new Entry(
                "knn",
                "k-Nearest Neighbors (k-NN Metric Classifier)",
                "k-NN (k-Najbliższych Sąsiadów)",
                "Cechy Statystyczne CIGRE/IEC",
                "Znormalizowana przestrzeń 16-wymiarowa metryki euklidesowej",
                "Klasyfikator geometryczny typu 'lazy learning'. Oblicza odległość bieżącego pomiaru od wzorców w bazie laboratoryjnej i przypisuje klasę dominującą wśród k sąsiadów (k=5).",
                "<table cellpadding='0' cellspacing='0' border='0'>"
                + "<tr>"
                + "<td nowrap='nowrap' valign='middle' style='font-family:Cambria,serif; font-size:13px; color:#e2f1ff; padding-right:8px;'><nobr>d(x, y) &nbsp;=&nbsp; &radic;&Big( &sum;<sub>j=1..D</sub></nobr></td>"
                + "<td nowrap='nowrap' valign='middle' align='center'>"
                + "<table cellpadding='0' cellspacing='0' border='0'>"
                + "<tr><td nowrap='nowrap' align='center' style='border-bottom:1px solid #38bdf8; font-family:Cambria,serif; font-size:12px; padding:1px 10px; color:#e2f1ff;'><nobr>(x<sub>j</sub> &minus; y<sub>j</sub>)<sup>2</sup></nobr></td></tr>"
                + "<tr><td nowrap='nowrap' align='center' style='font-family:Cambria,serif; font-size:12px; padding:1px 10px; color:#e2f1ff;'><nobr>&sigma;<sub>j</sub><sup>2</sup></nobr></td></tr>"
                + "</table>"
                + "</td>"
                + "<td nowrap='nowrap' valign='middle' style='font-family:Cambria,serif; font-size:13px; color:#e2f1ff; padding-left:8px;'><nobr>&Big)</nobr></td>"
                + "</tr>"
                + "</table>",
                "D = 16 (liczba deskryptorów CIGRE), &sigma;_j (odchylenie standardowe j-tej cechy).",
                "W pełni przejrzysty – pozwala inżynierowi wskazać, do których konkretnie badań z Hali WN PW badany obiekt jest najbardziej podobny.",
                "Doskonale sprawdza się przy weryfikacji powtarzalności prób laboratoryjnych na identycznych rozdzielnicach SN."
        ));

        // 10. ANN / MLP
        add(new Entry(
                "ann_mlp",
                "Artificial Neural Network (Multi-Layer Perceptron)",
                "ANN / MLP (Wielowarstwowy Perceptron)",
                "Cechy Statystyczne CIGRE/IEC",
                "Wektor 16 deskryptorów CIGRE (wejście) -> 32 -> 16 -> 6 (wyjście Softmax)",
                "Gęsta sieć jednokierunkowa. Złożona z warstw liniowych, aktywacji ReLU oraz warstwy wyjściowej Softmax generującej rozkład prawdopodobieństw.",
                "<div style='font-family:Cambria,serif; font-size:13px; color:#e2f1ff; margin-bottom:8px;'>"
                + "a<sup>(l)</sup> &nbsp;=&nbsp; ReLU( W<sup>(l)</sup> a<sup>(l-1)</sup> &nbsp;+&nbsp; b<sup>(l)</sup> )"
                + "</div>"
                + "<table cellpadding='0' cellspacing='0' border='0'>"
                + "<tr>"
                + "<td nowrap='nowrap' valign='middle' style='font-family:Cambria,serif; font-size:13px; color:#e2f1ff; padding-right:8px;'><nobr>P(y = c | x) &nbsp;=&nbsp;</nobr></td>"
                + "<td nowrap='nowrap' valign='middle' align='center'>"
                + "<table cellpadding='0' cellspacing='0' border='0'>"
                + "<tr><td nowrap='nowrap' align='center' style='border-bottom:1px solid #38bdf8; font-family:Cambria,serif; font-size:12px; padding:1px 12px; color:#e2f1ff;'><nobr>exp(z<sub>c</sub>)</nobr></td></tr>"
                + "<tr><td nowrap='nowrap' align='center' style='font-family:Cambria,serif; font-size:12px; padding:1px 12px; color:#e2f1ff;'><nobr>&sum;<sub>j=1..C</sub> exp(z<sub>j</sub>)</nobr></td></tr>"
                + "</table>"
                + "</td>"
                + "</tr>"
                + "</table>",
                "a^(l) (aktywacja warstwy l), z_c (logit wyjściowy dla klasy c), ReLU(z) = max(0, z).",
                "Klasyczna architektura sieci neuronowej, szeroko opisana w literaturze wysokich napięć. Błyskawiczny czas odpowiedzi i małe użycie pamięci RAM.",
                "Płynnie łączy subtelne korelacje między kątami zapłonu a asymetrią ładunku, dając stabilne predykcje nawet przy zakłóceniach."
        ));

        // 11. CIGRE Rules
        add(new Entry(
                "cigre_rules",
                "CIGRE 226 Deterministic Expert Rule Engine",
                "CIGRE 226 Rule Engine (Deterministyczny System Ekspercki)",
                "System Ekspercki (White-Box)",
                "Parametry statystyczne rozkładu fazowego wg IEC 60270 i CIGRE TF 15-33-03-02",
                "Silnik wnioskowania regułowego. Wykorzystuje sformalizowaną bazę wiedzy inżynierskiej CIGRE 226 łączącą progi ładunku pozornego, symetrię fazową oraz kąty zapłonu bez użycia czarnych skrzynek (XAI).",
                "<div style='font-family:Cambria,serif; font-size:12px; color:#e2f1ff; line-height: 1.9;'>"
                + "JEŻELI ( Q<sub>max</sub><sup>+</sup> / Q<sub>max</sub><sup>-</sup> &gt; 1.8 &nbsp; ORAZ &nbsp; CC &lt; 0.2 &nbsp; ORAZ &nbsp; &Delta;&phi; &approx; 180&deg; ) &nbsp; &rArr; &nbsp; <b style='color:#ff4d4d;'>CORONA +</b><br>"
                + "JEŻELI ( |Q<sub>max</sub><sup>+</sup> / Q<sub>max</sub><sup>-</sup> &minus; 1.0| &lt; 0.25 &nbsp; ORAZ &nbsp; CC &gt; 0.6 ) &nbsp; &rArr; &nbsp; <b style='color:#bf55ec;'>INTERNAL (VOID)</b>"
                + "</div>",
                "Zero 'czarnych skrzynek'; każda decyzja posiada formalne uzasadnienie fizyczne i normatywne.",
                "Stanowi niezależny arbiter fizyczny (Ground Truth) weryfikujący, czy modele uczenia maszynowego nie podejmują decyzji sprzecznych z prawami elektrotechniki.",
                "Gwarantuje zgodność raportu diagnostycznego z procedurami odbiorczymi i normą PN-EN 62271-200 dla rozdzielnic prądu przemiennego w obudowach metalowych."
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
