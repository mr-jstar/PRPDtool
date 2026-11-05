package prpdtool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Baza wiedzy, architektura przeplywu danych i dokumentacja techniczna
 * dla Centrum Klasyfikacji Hybrydowej AI (YOLO + CIGRE Ensemble).
 * 
 * Zawiera szczegolowa mape przetwarzania krok po kroku ze strzalkami (pipeline flowchart),
 * wyjasnienie ramienia wizyjnego (YOLO/RT-DETR), ramienia fizykalnego (odcisk statystyczny CIGRE),
 * algorytmu fuzji decyzyjnej, wyjasnialnego AI (XAI) oraz katalogu form defektow WNZ.
 * 
 * Zgodnosc z wytycznymi IEC 60270, IEC 62478 oraz CIGRE WG 21.03 / WG D1.33.
 */
public class HybridClassificationDoc {

    public static class Chapter {
        public final String key;
        public final String titlePl;
        public final String titleEn;
        public final String subtitle;
        private final ContentRenderer renderer;

        @FunctionalInterface
        public interface ContentRenderer {
            String render(boolean dark);
        }

        public Chapter(String key, String titlePl, String titleEn, String subtitle, ContentRenderer renderer) {
            this.key = key;
            this.titlePl = titlePl;
            this.titleEn = titleEn;
            this.subtitle = subtitle;
            this.renderer = renderer;
        }

        public String getFullHtml(boolean dark) {
            return renderer.render(dark);
        }
    }

    private static final Map<String, Chapter> CHAPTERS = new LinkedHashMap<>();

    static {
        // 1. PIPELINE MAP (MAPA PRZEPLYWU ZE STRZALKAMI KROK PO KROKU)
        add("PIPELINE_MAP",
                "Cykl przetwarzania i fuzji AI",
                "Full End-to-End Processing & AI Fusion Pipeline",
                "Mapa przep\u0142ywu danych od pr\u00f3bek ADC do diagnozy",
                dark -> buildPipelineMapHtml(dark));

        // 2. INTERFACE GUIDE
        add("INTERFACE_GUIDE",
                "Przewodnik po oknie i obs\u0142udze",
                "Interface Controls & Operational Guide",
                "Opis kontrolek, tryb\u00f3w pracy i wska\u017anik\u00f3w",
                dark -> buildInterfaceGuideHtml(dark));

        // 3. VISION BRANCH
        add("VISION_MODELS",
                "Rami\u0119 wizyjne: Detektory YOLO i RT-DETR",
                "Computer Vision Branch: Deep Object Detection",
                "Sieci splotowe i transformery na obrazach PRPD 448x448",
                dark -> buildVisionBranchHtml(dark));

        // 4. PHYSICS BRANCH
        add("PHYSICS_CIGRE",
                "Rami\u0119 fizykalne: Odcisk statystyczny CIGRE",
                "Physics Branch: Statistical Fingerprint & Phase Profiles",
                "Profile Hn, Hqn, Hqmax i wytyczne IEC 60270 / CIGRE",
                dark -> buildPhysicsBranchHtml(dark));

        // 5. ENSEMBLE RULES
        add("FUSION_ENGINE",
                "Silnik fuzji i ocena zgodno\u015bci modeli",
                "Hybrid Ensemble Decision Engine & Model Agreement",
                "Wagowa integracja decyzji i ocena ryzyka",
                dark -> buildFusionEngineHtml(dark));

        // 6. DEFECT CLASSES
        add("DEFECT_CLASSES",
                "Katalog form wy\u0142adowa\u0144 (6 klas WNZ)",
                "Partial Discharge Defect Classification Catalog",
                "Fizyka zjawiska, sygnatury PRPD i kryteria klas",
                dark -> buildDefectClassesHtml(dark));

        // 7. XAI & RECOMMENDATIONS
        add("XAI_AND_RECOMMENDATIONS",
                "Wyja\u015bnialne AI (XAI) i zalecenia CIGRE",
                "Explainable AI (XAI) Rationale & Maintenance Guidelines",
                "Interpretowalno\u015b\u0107 diagnozy i procedury ruchowe",
                dark -> buildXaiRecommendationsHtml(dark));
    }

    private static void add(String key, String titlePl, String titleEn, String subtitle, Chapter.ContentRenderer renderer) {
        CHAPTERS.put(key, new Chapter(key, titlePl, titleEn, subtitle, renderer));
    }

    public static List<Chapter> getChapters() {
        return Collections.unmodifiableList(new ArrayList<>(CHAPTERS.values()));
    }

    public static Chapter get(String key) {
        return CHAPTERS.get(key);
    }

    // =========================================================================
    // HTML BUILDERS
    // =========================================================================

    private static String baseStyles(boolean dark) {
        String bg = dark ? "#22252a" : "#ffffff";
        String text = dark ? "#dcdcdc" : "#222222";
        return "font-family: Segoe UI, Arial, sans-serif; font-size: 11px; color: " + text + "; background-color: " + bg + "; margin: 12px; line-height: 1.45;";
    }

    private static String cardStyle(boolean dark, String borderColor) {
        String boxBg = dark ? "#2c3036" : "#f4f6f9";
        return "background-color: " + boxBg + "; border: 1px solid " + borderColor + "; padding: 10px 12px; margin-bottom: 10px;";
    }

    private static String stepCardStyle(boolean dark, String accentCol) {
        String boxBg = dark ? "#262930" : "#ffffff";
        String borderCol = dark ? "#3e4450" : "#d0d6e2";
        return "background-color: " + boxBg + "; border-left: 4px solid " + accentCol + "; border-top: 1px solid " + borderCol + "; border-right: 1px solid " + borderCol + "; border-bottom: 1px solid " + borderCol + "; padding: 9px 12px; margin-bottom: 6px;";
    }

    private static String arrowSeparator(String label) {
        return "<div style='text-align: center; padding: 4px 0 6px 0; color: #00d4ff; font-weight: bold; font-size: 13px;'>"
                + "&darr; <span style='font-size: 10px; color: #8899aa; text-transform: uppercase; letter-spacing: 0.5px; font-weight: normal;'>" + label + "</span> &darr;</div>";
    }

    // 1. PIPELINE MAP
    private static String buildPipelineMapHtml(boolean dark) {
        String titleColor = dark ? "#00e1ff" : "#0066cc";
        String hColor = dark ? "#ffe066" : "#b38600";
        String cyan = dark ? "#38bdf8" : "#0284c7";
        String amber = dark ? "#fbbf24" : "#d97706";
        String emerald = dark ? "#34d399" : "#059669";
        String purple = dark ? "#c084fc" : "#7c3aed";

        return "<html><body style='" + baseStyles(dark) + "'>"
                + "<div style='font-size: 15px; font-weight: bold; color: " + titleColor + "; margin-bottom: 2px;'>"
                + "Cykl Przetwarzania i Fuzji AI: Od Sygna\u0142u do Diagnozy</div>"
                + "<div style='font-size: 11px; color: #8899aa; margin-bottom: 12px;'>"
                + "Kompletny przep\u0142yw danych i algorytm\u00f3w prowadz\u0105cy do wynik\u00f3w widocznych w oknie klasyfikacji</div>"

                + "<div style='" + cardStyle(dark, dark ? "#00e1ff" : "#0066cc") + "'>"
                + "<b style='color: " + hColor + "; font-size: 12px;'>Jak dochodzimy do wynik\u00f3w widocznych w tym oknie?</b><br>"
                + "Klasyfikacja w programie PRPD Tool <b>nie jest zwyk\u0142\u0105 sieci\u0105 neuronow\u0105 typu 'black box'</b>. "
                + "Zastosowano unikaln\u0105 architektur\u0119 <b>fuzji hybrydowej (Hybrid Ensemble)</b>, w kt\u00f3rej zaawansowana wizja komputerowa "
                + "(sie\u0107 YOLOv8 / RT-DETR) wsp\u00f3\u0142pracuje r\u00f3wnolegle z fizykalnym modelem analitycznym opartym na normach "
                + "<b>IEC 60270</b> oraz wytycznych <b>CIGRE WG 21.03</b>.<br><br>"
                + "Poni\u017csza mapa przedstawia ca\u0142y \u0142a\u0144cuch transformacji surowego sygna\u0142u fizycznego w ostateczn\u0105 ocen\u0119 stanu izolacji:"
                + "</div>"

                // KROK 1
                + "<div style='" + stepCardStyle(dark, cyan) + "'>"
                + "<span style='background: " + cyan + "; color: #000000; font-weight: bold; font-size: 9px; padding: 2px 6px; margin-right: 6px;'>KROK 1</span>&nbsp; "
                + "<b style='font-size: 12px; color: " + cyan + ";'>Akwizycja i digitalizacja sygna\u0142u (Red Pitaya STEMlab 125-14)</b><br>"
                + "<div style='margin-top: 5px; font-size: 11px;'>"
                + "\u2022 <b>Przetwornik ADC:</b> 14-bitowy tor pomiarowy o pr\u00f3bkowaniu <b>125 MS/s</b> rejestruje szybkie impulsy w pe\u0142nym pa\u015bmie w.cz.<br>"
                + "\u2022 <b>Kana\u0142 CH1 (IN1):</b> Fizyczny sygna\u0142 z czujnika WNZ (cewka Rogowskiego HFCT, dzielnik pojemno\u015bciowy CC lub antena UHF).<br>"
                + "\u2022 <b>Kana\u0142 CH2 (IN2):</b> Sygna\u0142 napi\u0119cia probierczego 50 Hz (s\u0142u\u017cy jako bezwzgl\u0119dna referencja fazowa zero-crossing).<br>"
                + "\u2022 <b>Bufor ko\u0142owy:</b> Ramki danych (16 384 pr\u00f3bek) przesy\u0142ane s\u0105 strumieniowo przez TCP/IP do pami\u0119ci programu."
                + "</div></div>"

                + arrowSeparator("Surowe pr\u00f3bki czasowe u(t)")

                // KROK 2
                + "<div style='" + stepCardStyle(dark, emerald) + "'>"
                + "<span style='background: " + emerald + "; color: #000000; font-weight: bold; font-size: 9px; padding: 2px 6px; margin-right: 6px;'>KROK 2</span>&nbsp; "
                + "<b style='font-size: 12px; color: " + emerald + ";'>Filtracja cyfrowa i zaawansowane odszumianie DSP</b><br>"
                + "<div style='margin-top: 5px; font-size: 11px;'>"
                + "\u2022 <b>Filtr g\u00f3rnoprzepustowy (HPF):</b> Butterworth 2-8 rz\u0119du (odci\u0119cie np. 100 kHz) odcina sk\u0142adow\u0105 sieciow\u0105 50 Hz i dryfy sta\u0142opr\u0105dowe.<br>"
                + "\u2022 <b>Bank filtr\u00f3w wycinaj\u0105cych Notch:</b> Wycina w\u0105skopasmowe no\u015bne radiowe AM/SW oraz harmoniczne falownik\u00f3w PWM.<br>"
                + "\u2022 <b>Dynamiczna bramka szumowa (Noise Gate):</b> Wycisza stochastyczny szum t\u0142a o -18 dB mi\u0119dzy impulsami, zachowuj\u0105c 100% kszta\u0142tu wy\u0142adowa\u0144."
                + "</div></div>"

                + arrowSeparator("Przefiltrowany sygna\u0142 WNZ")

                // KROK 3
                + "<div style='" + stepCardStyle(dark, amber) + "'>"
                + "<span style='background: " + amber + "; color: #000000; font-weight: bold; font-size: 9px; padding: 2px 6px; margin-right: 6px;'>KROK 3</span>&nbsp; "
                + "<b style='font-size: 12px; color: " + amber + ";'>Detekcja impuls\u00f3w i powi\u0105zanie z k\u0105tem fazowym (Peak Picker & Phase Extraction)</b><br>"
                + "<div style='margin-top: 5px; font-size: 11px;'>"
                + "\u2022 <b>Komparator progowy:</b> Kwalifikuje pr\u00f3bki przekraczaj\u0105ce zadany pr\u00f3g amplitudy <code>|u| &ge; U_prog</code>.<br>"
                + "\u2022 <b>Wyszukiwanie wierzcho\u0142ka (Dead Time, np. 10 &mu;s):</b> Algorytm lokalizuje globalne ekstremum amplitudy impulsu i blokuje dzwonienie obwodu.<br>"
                + "\u2022 <b>K\u0105t fazowy &phi;:</b> Wyznaczana jest chwila przej\u015bcia napi\u0119cia 50 Hz przez zero (t0). K\u0105t obliczany jest ze wzoru: <code>&phi; = 360 &times; f0 &times; (t &minus; t0) mod 360&deg;</code>.<br>"
                + "\u2022 <b>Zbi\u00f3r wyj\u015bciowy:</b> Ka\u017cde zdarzenie zapisywane jest jako tr\u00f3jka danych: <b>(&phi;<sub>i</sub>, q<sub>i</sub>, t<sub>i</sub>)</b>."
                + "</div></div>"

                + arrowSeparator("Zbi\u00f3r impuls\u00f3w (&phi;, q, t)")

                // KROK 4
                + "<div style='" + stepCardStyle(dark, purple) + "'>"
                + "<span style='background: " + purple + "; color: #ffffff; font-weight: bold; font-size: 9px; padding: 2px 6px; margin-right: 6px;'>KROK 4</span>&nbsp; "
                + "<b style='font-size: 12px; color: " + purple + ";'>Generowanie dwuwymiarowej mapy fazowej PRPD (&Phi;-q-n)</b><br>"
                + "<div style='margin-top: 5px; font-size: 11px;'>"
                + "\u2022 <b>Dyskretna macierz 2D:</b> Podzia\u0142 na 360 kolumn k\u0105ta fazowego (1&deg;) oraz 256 poziom\u00f3w amplitudy \u0142adunku.<br>"
                + "\u2022 <b>Histogram g\u0119sto\u015bci (Heatmap):</b> Akumulacja liczby zlicze\u0144 w ka\u017cdym binie tworzy charakterystyczne chmury wy\u0142adowa\u0144.<br>"
                + "\u2022 <b>Paleta barw i normalizacja:</b> Przekszta\u0142cenie g\u0119sto\u015bci nieliniow\u0105 skal\u0105 do palety Jet/Turbo na ciemnym tle.<br>"
                + "\u2022 <b>Tensor obrazu:</b> Rasteryzacja do dok\u0142adnego formatu wej\u015bciowego AI: <b>448 &times; 448 pikseli RGB</b>."
                + "</div></div>"

                + "<div style='text-align: center; padding: 6px 0; color: #ffe066; font-weight: bold; font-size: 12px;'>"
                + "&dArr; ROZGA\u0141\u0118ZIENIE NA DWA NIEZALE\u017bNE RAMIONA DIAGNOSTYCZNE &dArr;</div>"

                // RAMIONA A i B
                + "<table style='width: 100%; border-collapse: collapse; margin-bottom: 8px;'>"
                + "<tr>"
                + "<td style='width: 50%; vertical-align: top; padding-right: 4px;'>"
                + "<div style='" + stepCardStyle(dark, cyan) + " min-height: 190px;'>"
                + "<b style='color: " + cyan + "; font-size: 11px;'>RAMI\u0118 A: Wizja Komputerowa (YOLO / RT-DETR)</b><br>"
                + "<span style='font-size: 10px; color: #8899aa;'>G\u0142\u0119boka sie\u0107 splotowa ONNX Runtime</span><br><br>"
                + "\u2022 <b>Wej\u015bcie:</b> Obraz PRPD 448x448 RGB.<br>"
                + "\u2022 <b>Ekstrakcja cech:</b> Warstwy splotowe rozpoznaj\u0105 kszta\u0142t geometryczny chmury WNZ (pi\u00f3ropusze, symetria, pasy).<br>"
                + "\u2022 <b>Lokalizacja (BBoxes):</b> Wyznacza prostok\u0105ty otaczaj\u0105ce [xc, yc, w, h] dla ka\u017cdego skupiska.<br>"
                + "\u2022 <b>Predykcja:</b> Wektor ufno\u015bci P_vision dla ka\u017cdej z 6 klas defekt\u00f3w.<br>"
                + "\u2022 <i>Zaleta:</i> Wykrywa jednoczesn\u0105 superpozycj\u0119 kilku r\u00f3\u017cnych \u017ar\u00f3de\u0142 WNZ."
                + "</div>"
                + "</td>"
                + "<td style='width: 50%; vertical-align: top; padding-left: 4px;'>"
                + "<div style='" + stepCardStyle(dark, emerald) + " min-height: 190px;'>"
                + "<b style='color: " + emerald + "; font-size: 11px;'>RAMI\u0118 B: Odcisk Statystyczny (CIGRE / IEC)</b><br>"
                + "<span style='font-size: 10px; color: #8899aa;'>Fizyka wy\u0142adowa\u0144 wg normy IEC 60270</span><br><br>"
                + "\u2022 <b>Wej\u015bcie:</b> Surowe impulsy (&phi;, q) w 360 oknach fazowych.<br>"
                + "\u2022 <b>Profile rozk\u0142adu:</b> Estymacja Hn(&phi;), Hqn(&phi;) oraz Hqmax(&phi;).<br>"
                + "\u2022 <b>Momenty statystyczne:</b> Sko\u015bno\u015b\u0107 (Sk+, Sk-) oraz Kurtoza (Ku+, Ku-) dla obu p\u00f3\u0142okres\u00f3w.<br>"
                + "\u2022 <b>Asymetrie:</b> Relacja \u0142adunku Q+/Q- oraz liczby zlicze\u0144 N+/N-.<br>"
                + "\u2022 <b>Predykcja:</b> Prawdopodobie\u0144stwa fizykalne P_stat wg wytycznych CIGRE WG 21.03.<br>"
                + "\u2022 <i>Zaleta:</i> Odporno\u015b\u0107 na halucynacje AI."
                + "</div>"
                + "</td>"
                + "</tr>"
                + "</table>"

                + arrowSeparator("Fuzja decyzyjna (P_vision + P_stat)")

                // KROK 5
                + "<div style='" + stepCardStyle(dark, amber) + "'>"
                + "<span style='background: " + amber + "; color: #000000; font-weight: bold; font-size: 9px; padding: 2px 6px; margin-right: 6px;'>KROK 5</span>&nbsp; "
                + "<b style='font-size: 12px; color: " + amber + ";'>Silnik Fuzji Hybrydowej (Hybrid Decision Fusion Engine)</b><br>"
                + "<div style='margin-top: 5px; font-size: 11px;'>"
                + "\u2022 <b>Komitet decyzyjny:</b> Wyznaczenie zagregowanego prawdopodobie\u0144stwa ka\u017cdej klasy: <code>P_hyb(k) = w_vis &times; P_vis(k) + w_stat &times; P_stat(k)</code>.<br>"
                + "\u2022 <b>Weryfikacja zgodno\u015bci modeli (Model Agreement):</b> Por\u00f3wnanie wskaza\u0144 detektora AI i fizyki CIGRE (pe\u0142na zgodno\u015b\u0107 / niejednoznaczno\u015b\u0107 / rozbie\u017cno\u015b\u0107).<br>"
                + "\u2022 <b>Wy\u0142onienie wiod\u0105cej diagnozy:</b> Wyb\u00f3r dominuj\u0105cej formy WNZ oraz obliczenie ostatecznej pewno\u015bci fuzji (np. 94.2%)."
                + "</div></div>"

                + arrowSeparator("Wiod\u0105ca klasa + wektor cech")

                // KROK 6
                + "<div style='" + stepCardStyle(dark, dark ? "#00e1ff" : "#0066cc") + "'>"
                + "<span style='background: " + (dark ? "#00e1ff" : "#0066cc") + "; color: " + (dark ? "#000000" : "#ffffff") + "; font-weight: bold; font-size: 9px; padding: 2px 6px; margin-right: 6px;'>KROK 6</span>&nbsp; "
                + "<b style='font-size: 12px; color: " + (dark ? "#00e1ff" : "#0066cc") + ";'>Wyja\u015bnialne AI (XAI) i zalecenia eksploatacyjne CIGRE</b><br>"
                + "<div style='margin-top: 5px; font-size: 11px;'>"
                + "\u2022 <b>Generator uzasadnie\u0144 XAI:</b> T\u0142umaczy in\u017cynierowi w j\u0119zyku naturalnym, dlaczego dany defekt zosta\u0142 sklasyfikowany (np. wskazuj\u0105c asymetri\u0119 \u0142adunku, p\u00f3\u0142okres i obecno\u015b\u0107 ramki detekcji).<br>"
                + "\u2022 <b>Ocena poziomu ryzyka:</b> Automatyczna kwalifikacja stanu izolacji na poziomy: <b>NORMALNY</b> (zielony), <b>OSTRZEGAWCZY</b> (pomara\u0144czowy), <b>KRYTYCZNY</b> (czerwony).<br>"
                + "\u2022 <b>Rekomendacja serwisowa:</b> Konkretne wytyczne dla personelu stacji SN/WN (np. pilna inspekcja g\u0142owic, czyszczenie szyn, skr\u00f3cenie czasu mi\u0119dzy badaniami)."
                + "</div></div>"

                + "</body></html>";
    }

    // 2. INTERFACE GUIDE
    private static String buildInterfaceGuideHtml(boolean dark) {
        String titleColor = dark ? "#00e1ff" : "#0066cc";
        String hColor = dark ? "#ffe066" : "#b38600";
        String borderCol = dark ? "#3e4450" : "#d0d6e2";

        return "<html><body style='" + baseStyles(dark) + "'>"
                + "<div style='font-size: 15px; font-weight: bold; color: " + titleColor + "; margin-bottom: 2px;'>"
                + "Przewodnik po Oknie i Elementach Interfejsu</div>"
                + "<div style='font-size: 11px; color: #8899aa; margin-bottom: 12px;'>"
                + "Szczeg\u00f3\u0142owy opis kontrolek, tryb\u00f3w pracy oraz prezentowanych paneli diagnostycznych</div>"

                + "<div style='" + cardStyle(dark, borderCol) + "'>"
                + "<b style='color: " + hColor + "; font-size: 12px;'>Uk\u0142ad g\u0142\u00f3wny okna</b><br>"
                + "Okno zosta\u0142o podzielone na dwa g\u0142\u00f3wne obszary robocze z ruchom\u0105 przegrod\u0105 (Split Pane):<br>"
                + "\u2022 <b>Lewa strona (Canvas PRPD):</b> Prezentacja dwuwymiarowego obrazu fazowego 448&times;448 z dynamicznie nanoszonymi prostok\u0105tami detekcji obiektowej (Bounding Boxes) oraz etykietami klas i procentem ufno\u015bci.<br>"
                + "\u2022 <b>Prawa strona (Panel diagnostyczny):</b> Pi\u0119\u0107 ustrukturyzowanych kart prezentuj\u0105cych wiod\u0105c\u0105 diagnoz\u0119, rozk\u0142ady prawdopodobie\u0144stw dla wszystkich 6 klas, por\u00f3wnanie decyzji modeli, uzasadnienie XAI i rekomendacje."
                + "</div>"

                + "<div style='font-size: 13px; font-weight: bold; color: " + titleColor + "; margin: 12px 0 6px 0;'>"
                + "G\u00f3rny pasek narz\u0119dziowy (Top Toolbar)</div>"

                + "<table style='width: 100%; border-collapse: collapse; margin-bottom: 12px; font-size: 11px;'>"
                + "<tr style='background: " + (dark ? "#2c3036" : "#e9eef5") + "; text-align: left;'>"
                + "<th style='padding: 6px; border: 1px solid " + borderCol + "; width: 28%;'>Element</th>"
                + "<th style='padding: 6px; border: 1px solid " + borderCol + ";'>Funkcja i rola diagnostyczna</th>"
                + "</tr>"

                + "<tr>"
                + "<td style='padding: 6px; border: 1px solid " + borderCol + ";'><b>Model wizualny (ComboBox)</b></td>"
                + "<td style='padding: 6px; border: 1px solid " + borderCol + ";'>Wyb\u00f3r silnika detekcji obrazowej: <i>Wszystkie modele (Komitet)</i> uruchamia ewaluacj\u0119 ka\u017cdego za\u0142adowanego modelu ONNX (np. YOLOv8 Nano, YOLOv8 Small, RT-DETR) i wybiera predykcj\u0119 o najwy\u017cszej pewno\u015bci. Mo\u017cna r\u00f3wnie\u017c wymusi\u0107 testowanie konkretnego modelu.</td>"
                + "</tr>"

                + "<tr>"
                + "<td style='padding: 6px; border: 1px solid " + borderCol + ";'><b>Pr\u00f3g ufno\u015bci (Slider 10-90%)</b></td>"
                + "<td style='padding: 6px; border: 1px solid " + borderCol + ";'>Filtruje ramki detekcji na wykresie PRPD. Detekcje o pewno\u015bci ni\u017cszej ni\u017c wybrany pr\u00f3g nie s\u0105 wy\u015bwietlane na obrazie, co zapobiega za\u015bmiecaniu widoku ma\u0142o istotnymi propozycjami. Domy\u015blna warto\u015b\u0107 <b>30%</b> jest optymalna w wi\u0119kszo\u015bci bada\u0144 laboratoryjnych i terenowych.</td>"
                + "</tr>"

                + "<tr>"
                + "<td style='padding: 6px; border: 1px solid " + borderCol + ";'><b>Poka\u017c ramki detekcji (BBoxes)</b></td>"
                + "<td style='padding: 6px; border: 1px solid " + borderCol + ";'>Prze\u0142\u0105cznik widoczno\u015bci kolorowych ramek obiektowych na p\u0142\u00f3tnie PRPD. Kolor ka\u017cdej ramki odpowiada znormalizowanemu kodowi barwnemu danej klasy WNZ.</td>"
                + "</tr>"

                + "</table>"

                + "<div style='font-size: 13px; font-weight: bold; color: " + titleColor + "; margin: 12px 0 6px 0;'>"
                + "Karty analityczne prawego panelu</div>"

                + "<div style='" + cardStyle(dark, borderCol) + "'>"
                + "<b style='color: " + cyanStyle() + ";'>1. Wiod\u0105ca Diagnoza Hybrydowa:</b><br>"
                + "Najwa\u017cniejszy wska\u017anik syntetyczny. Wy\u015bwietla nazw\u0119 dominuj\u0105cej formy WNZ w j\u0119zyku polskim, zagregowany procent pewno\u015bci, odznak\u0119 poziomu ryzyka (NORMALNY, OSTRZEGAWCZY, KRYTYCZNY) oraz status koherencji mi\u0119dzy modelem wizyjnym i statystyk\u0105 CIGRE.<br><br>"

                + "<b style='color: " + cyanStyle() + ";'>2. Rozk\u0142ad Prawdopodobie\u0144stw Klas WNZ:</b><br>"
                + "Zestaw 6 poziomych pask\u00f3w post\u0119pu (Progress Bars) obrazuj\u0105cych prawdopodobie\u0144stwo a posteriori ka\u017cdej z mo\u017cliwych form wy\u0142adowa\u0144 (0.0% &ndash; 100.0%). Pozwala to wykry\u0107 obecno\u015b\u0107 defekt\u00f3w z\u0142o\u017conych lub wsp\u00f3\u0142istniej\u0105cych.<br><br>"

                + "<b style='color: " + cyanStyle() + ";'>3. Por\u00f3wnanie Decyzji Modeli Sk\u0142adowych:</b><br>"
                + "Prezentuje bezpo\u015brednie wskazanie g\u0142\u0119bokiego detektora YOLO/ONNX oraz niezale\u017cne wskazanie klasyfikatora statystycznego CIGRE wraz z ich indywidualnymi ocenami ufno\u015bci.<br><br>"

                + "<b style='color: " + cyanStyle() + ";'>4. Wyja\u015bnienie Decyzji Modeli (Explainable AI &ndash; XAI):</b><br>"
                + "Lista czytelnych przes\u0142anek in\u017cynierskich uzasadniaj\u0105cych wyrok sztucznej inteligencji (np. wskazanie konkretnych warto\u015bci sko\u015bno\u015bci, p\u00f3\u0142okresu oraz korelacji kszta\u0142tu).<br><br>"

                + "<b style='color: " + cyanStyle() + ";'>5. Rekomendacje Eksploatacyjne CIGRE:</b><br>"
                + "Wytyczne operacyjne sugeruj\u0105ce dalsze dzia\u0142ania diagnostyczne (np. natychmiastowe wy\u0142\u0105czenie obiektu spod napi\u0119cia, inspekcja wizualna komory \u0142ukowej lub zaplanowanie rewizji okresowej)."
                + "</div>"

                + "</body></html>";
    }

    private static String cyanStyle() {
        return "#00d4ff";
    }

    // 3. VISION BRANCH
    private static String buildVisionBranchHtml(boolean dark) {
        String titleColor = dark ? "#00e1ff" : "#0066cc";
        String hColor = dark ? "#ffe066" : "#b38600";
        String borderCol = dark ? "#3e4450" : "#d0d6e2";

        return "<html><body style='" + baseStyles(dark) + "'>"
                + "<div style='font-size: 15px; font-weight: bold; color: " + titleColor + "; margin-bottom: 2px;'>"
                + "Rami\u0119 Wizyjne: Detektory YOLO i RT-DETR</div>"
                + "<div style='font-size: 11px; color: #8899aa; margin-bottom: 12px;'>"
                + "Zasada dzia\u0142ania g\u0142\u0119bokich sieci neuronowych detekcji obiektowej na obrazach PRPD</div>"

                + "<div style='" + cardStyle(dark, borderCol) + "'>"
                + "<b style='color: " + hColor + "; font-size: 12px;'>Dlaczego detekcja obiektowa (Object Detection), a nie zwyk\u0142a klasyfikacja?</b><br>"
                + "W klasycznej klasyfikacji obraz\u00f3w sie\u0107 przypisuje <b>jedn\u0105 etykiet\u0119 do ca\u0142ego obrazu</b>. "
                + "W diagnostyce WNZ w warunkach rzeczywistych jest to cz\u0119sto niewystarczaj\u0105ce, poniewa\u017c na jednym obiekcie (np. rozdzielnicy SN) "
                + "mo\u017ce jednocze\u015bnie wyst\u0119powa\u0107 <b>ulot na szynach oraz wy\u0142adowania wtr\u0105cone wewn\u0105trz przek\u0142adnika</b>.<br><br>"
                + "Architektury detekcyjne takie jak <b>YOLOv8</b> (You Only Look Once) oraz <b>RT-DETR</b> (Real-Time DEtection TRansformer):<br>"
                + "\u2022 <b>Lokalizuj\u0105 ka\u017cde skupisko niezale\u017cnie:</b> Wyznaczaj\u0105 prostok\u0105tne ramki (Bounding Boxes) precyzuj\u0105ce, w kt\u00f3rym obszarze fazy (&phi;) i amplitudy (q) znajduje si\u0119 dany defekt.<br>"
                + "\u2022 <b>Obs\u0142uguj\u0105 superpozycj\u0119 defekt\u00f3w:</b> S\u0105 w stanie wykry\u0107 jednocze\u015bnie 2, 3 lub wi\u0119cej r\u00f3\u017cnych zjawisk na tym samym wykresie PRPD.<br>"
                + "\u2022 <b>Odporno\u015b\u0107 na przesuni\u0119cia fazowe:</b> Dzi\u0119ki wieloskalowym mapom cech (FPN/PAN) sie\u0107 bezb\u0142\u0119dnie identyfikuje morfologi\u0119 chmury wy\u0142adowa\u0144 nawet przy przesuni\u0119ciu k\u0105towym."
                + "</div>"

                + "<div style='font-size: 13px; font-weight: bold; color: " + titleColor + "; margin: 12px 0 6px 0;'>"
                + "Format wej\u015bciowy i optymalizacja ONNX Runtime</div>"

                + "<div style='" + cardStyle(dark, borderCol) + "'>"
                + "\u2022 <b>Tensor wej\u015bciowy:</b> Obraz macierzy PRPD o sta\u0142ym wymiarze <code>[1, 3, 448, 448]</code> w formacie RGB (warto\u015bci pikseli znormalizowane do zakresu 0.0 &ndash; 1.0).<br>"
                + "\u2022 <b>Format ONNX:</b> Modele s\u0105 skompilowane do otwartego formatu ONNX, co pozwala na bezpo\u015bredni\u0105, wysoce zoptymalizowan\u0105 inferencj\u0119 w \u015brodowisku Java za po\u015brednictwem biblioteki <b>Microsoft ONNX Runtime</b>.<br>"
                + "\u2022 <b>Czas inferencji:</b> Wykonanie pe\u0142nego przej\u015bcia w prz\u00f3d (forward pass) na standardowym procesorze CPU trwa poni\u017cej <b>25 ms</b>, co pozwala na prac\u0119 w p\u0119tli czasu rzeczywistego (na żądanie)."
                + "</div>"

                + "</body></html>";
    }

    // 4. PHYSICS BRANCH
    private static String buildPhysicsBranchHtml(boolean dark) {
        String titleColor = dark ? "#00e1ff" : "#0066cc";
        String hColor = dark ? "#ffe066" : "#b38600";
        String borderCol = dark ? "#3e4450" : "#d0d6e2";

        return "<html><body style='" + baseStyles(dark) + "'>"
                + "<div style='font-size: 15px; font-weight: bold; color: " + titleColor + "; margin-bottom: 2px;'>"
                + "Rami\u0119 Fizykalne: Odcisk Statystyczny CIGRE</div>"
                + "<div style='font-size: 11px; color: #8899aa; margin-bottom: 12px;'>"
                + "Profile fazowe Hn, Hqn, Hqmax, momenty statystyczne i kryteria normatywne</div>"

                + "<div style='" + cardStyle(dark, borderCol) + "'>"
                + "<b style='color: " + hColor + "; font-size: 12px;'>Dlaczego sztuczna inteligencja potrzebuje modelu fizykalnego?</b><br>"
                + "Same g\u0142\u0119bokie sieci neuronowe maj\u0105 tendencj\u0119 do tzw. 'nadmiernej pewno\u015bci' (overconfidence) i mog\u0105 generowa\u0107 b\u0142\u0119dne predykcje, "
                + "gdy w sygnale pojawi si\u0119 nietypowe zak\u0142\u00f3cenie, kt\u00f3rego nie by\u0142o w zbiorze ucz\u0105cym.<br><br>"
                + "Z tego wzgl\u0119du r\u00f3wnolegle z detektorem obrazowym uruchamiany jest <b>deterministyczny model fizyki WNZ</b>, "
                + "zbudowany bezpo\u015brednio w oparciu o wytyczne mi\u0119dzynarodowej grupy roboczej <b>CIGRE WG 21.03</b> oraz norm\u0119 <b>IEC 60270</b>."
                + "</div>"

                + "<div style='font-size: 13px; font-weight: bold; color: " + titleColor + "; margin: 12px 0 6px 0;'>"
                + "Kluczowe deskryptory odcisku statystycznego</div>"

                + "<div style='" + cardStyle(dark, borderCol) + "'>"
                + "\u2022 <b>Profile rozk\u0142adu fazowego:</b><br>"
                + "&nbsp;&nbsp;- <code>Hn(&phi;)</code>: rozk\u0142ad cz\u0119sto\u015bci zlicze\u0144 w funkcji k\u0105ta fazowego,<br>"
                + "&nbsp;&nbsp;- <code>Hqn(&phi;)</code>: rozk\u0142ad \u015bredniej amplitudy \u0142adunku w fazie,<br>"
                + "&nbsp;&nbsp;- <code>Hqmax(&phi;)</code>: rozk\u0142ad maksymalnego \u0142adunku w ka\u017cdym oknie fazowym.<br><br>"
                + "\u2022 <b>Sko\u015bno\u015b\u0107 (Skewness Sk+, Sk-):</b> Mierzy asymetri\u0119 kszta\u0142tu obwiedni wy\u0142adowa\u0144 wzgl\u0119dem jej \u015brodka. Warto\u015bci dodatnie oznaczaj\u0105 strome narastanie i d\u0142ugi ogon, ujemne &ndash; powolne narastanie i strome gaszenie.<br><br>"
                + "\u2022 <b>Kurtoza (Kurtosis Ku+, Ku-):</b> Okre\u015bla smuk\u0142o\u015b\u0107 i skupienie rozk\u0142adu wok\u00f3\u0142 warto\u015bci \u015bredniej. Pozwala rozr\u00f3\u017cni\u0107 w\u0105skopasmowy ulot ostrzowy od szerokich pasm wy\u0142adowa\u0144 pe\u0142zaj\u0105cych.<br><br>"
                + "\u2022 <b>Wska\u017aniki asymetrii (Q, Qa):</b> Stosunek liczby impuls\u00f3w <code>N+ / N-</code> oraz \u0142adunku maksymalnego <code>Qmax+ / Qmax-</code>. Jest to kluczowy wyznacznik fizyczny &ndash; np. ulot w uk\u0142adzie ostrze-p\u0142yta wykazuje asymetri\u0119 rz\u0119du 2&ndash;5, podczas gdy p\u0119cherzyki wtr\u0105cone s\u0105 niemal idealnie symetryczne (Q &asymp; 1.0).<br><br>"
                + "\u2022 <b>Korelacja krzy\u017cowa (Cross-Correlation CC):</b> Wsp\u00f3\u0142czynnik podobie\u0144stwa obwiedni p\u00f3\u0142okresu dodatniego i ujemnego po przesuni\u0119ciu o 180&deg;."
                + "</div>"

                + "</body></html>";
    }

    // 5. FUSION ENGINE
    private static String buildFusionEngineHtml(boolean dark) {
        String titleColor = dark ? "#00e1ff" : "#0066cc";
        String hColor = dark ? "#ffe066" : "#b38600";
        String borderCol = dark ? "#3e4450" : "#d0d6e2";

        return "<html><body style='" + baseStyles(dark) + "'>"
                + "<div style='font-size: 15px; font-weight: bold; color: " + titleColor + "; margin-bottom: 2px;'>"
                + "Silnik Fuzji Decyzyjnej i Ocena Zgodno\u015bci Modeli</div>"
                + "<div style='font-size: 11px; color: #8899aa; margin-bottom: 12px;'>"
                + "Zasada dzia\u0142ania komitetu fuzji, weryfikacja koherencji i kwalifikacja ryzyka</div>"

                + "<div style='" + cardStyle(dark, borderCol) + "'>"
                + "<b style='color: " + hColor + "; font-size: 12px;'>Matematyczna regu\u0142a fuzji decyzyjnej</b><br>"
                + "Silnik fuzji \u0142\u0105czy prawdopodobie\u0144stwa wyznaczone przez model wizyjny oraz model statystyczny za pomoc\u0105 wa\u017conego schematu aproksymacji bayesowskiej:<br>"
                + "<div style='background: " + (dark ? "#1a1d22" : "#eaeff8") + "; padding: 8px 12px; margin: 8px 0; border-left: 3px solid #00d4ff; font-family: monospace; font-size: 11px;'>"
                + "P_hybrid(k) = w_vision &times; P_vision(k) + w_stat &times; P_stat(k)"
                + "</div>"
                + "gdzie wagi <code>w_vision</code> i <code>w_stat</code> s\u0105 dynamicznie dostosowywane w zale\u017cno\u015bci od liczby zebranych impuls\u00f3w w oknie pomiarowym (dla ma\u0142ej liczby pr\u00f3bek wi\u0119ksz\u0105 wag\u0119 ma model wizyjny, dla bogatego histogramu ro\u015bnie waga statystyki CIGRE)."
                + "</div>"

                + "<div style='font-size: 13px; font-weight: bold; color: " + titleColor + "; margin: 12px 0 6px 0;'>"
                + "Trzy stany zgodno\u015bci modeli (Model Agreement)</div>"

                + "<table style='width: 100%; border-collapse: collapse; margin-bottom: 12px; font-size: 11px;'>"
                + "<tr style='background: " + (dark ? "#2c3036" : "#e9eef5") + "; text-align: left;'>"
                + "<th style='padding: 6px; border: 1px solid " + borderCol + "; width: 28%;'>Status</th>"
                + "<th style='padding: 6px; border: 1px solid " + borderCol + ";'>Znaczenie fizykalne i diagnostyczne</th>"
                + "</tr>"

                + "<tr>"
                + "<td style='padding: 6px; border: 1px solid " + borderCol + "; color: #10b981; font-weight: bold;'>Pe\u0142na zgodno\u015b\u0107 modeli (Full Agreement)</td>"
                + "<td style='padding: 6px; border: 1px solid " + borderCol + ";'>Zar\u00f3wno detektor YOLO, jak i odcisk statystyczny CIGRE wskazuj\u0105 bezsprzecznie t\u0119 sam\u0105 form\u0119 defektu. Diagnoza charakteryzuje si\u0119 najwy\u017csz\u0105 pewno\u015bci\u0105 i mo\u017ce stanowi\u0107 bezpo\u015bredni\u0105 podstaw\u0119 do decyzji ruchowych.</td>"
                + "</tr>"

                + "<tr>"
                + "<td style='padding: 6px; border: 1px solid " + borderCol + "; color: #f59e0b; font-weight: bold;'>Cz\u0119\u015bciowa zgodno\u015b\u0107 / Niejednoznaczno\u015b\u0107</td>"
                + "<td style='padding: 6px; border: 1px solid " + borderCol + ";'>G\u0142\u00f3wny defekt jest zgodny, ale drugi model wykrywa cechy wsp\u00f3\u0142istniej\u0105ce (np. ulot z nak\u0142adaj\u0105cym si\u0119 szumem otoczenia) lub liczba zarejestrowanych impuls\u00f3w jest zbyt ma\u0142a do pe\u0142nej zbie\u017cno\u015bci statystycznej.</td>"
                + "</tr>"

                + "<tr>"
                + "<td style='padding: 6px; border: 1px solid " + borderCol + "; color: #ef4444; font-weight: bold;'>Rozbie\u017cno\u015b\u0107 modeli (Discrepancy)</td>"
                + "<td style='padding: 6px; border: 1px solid " + borderCol + ";'>Modele wskazuj\u0105 r\u00f3\u017cne klasy defekt\u00f3w. Taka sytuacja \u015bwiadczy o silnych zak\u0142\u00f3ceniach zewn\u0119trznych (NBI, falowniki) zniekszta\u0142caj\u0105cych obraz lub o nietypowej kombinacji uszkodze\u0144. Zalecana jest weryfikacja filtracji w zak\u0142adce <i>Advanced DSP Denoising</i>.</td>"
                + "</tr>"
                + "</table>"

                + "<div style='font-size: 13px; font-weight: bold; color: " + titleColor + "; margin: 12px 0 6px 0;'>"
                + "Kwalifikacja poziom\u00f3w ryzyka (Risk Level)</div>"

                + "<div style='" + cardStyle(dark, borderCol) + "'>"
                + "\u2022 <b style='color: #ef4444;'>KRYTYCZNY:</b> Wykryto formy bezpo\u015brednio zagra\u017caj\u0105ce przebiciem izolacji &ndash; <b>Wy\u0142adowania wtr\u0105cone (Void)</b> oraz <b>Wy\u0142adowania powierzchniowe (Surface/Tracking)</b>. Wymaga natychmiastowych dzia\u0142a\u0144 zapobiegawczych.<br><br>"
                + "\u2022 <b style='color: #f59e0b;'>OSTRZEGAWCZY:</b> Wykryto zjawiska o charakterze post\u0119puj\u0105cym &ndash; <b>Ulot ostrzowy (Corona HV)</b> lub <b>Z\u0142y styk (Contact Sparking)</b>. Wskazuje na przyspieszone starzenie dielektryka lub obecno\u015b\u0107 ostrych kraw\u0119dzi konstrukcyjnych.<br><br>"
                + "\u2022 <b style='color: #10b981;'>NORMALNY:</b> Zarejestrowany sygna\u0142 odpowiada <b>T\u0142u szumowemu (Noise)</b> lub brakowi mierzalnych zjawisk WNZ powy\u017cej progu czu\u0142o\u015bci aparatury pomiarowej."
                + "</div>"

                + "</body></html>";
    }

    // 6. DEFECT CLASSES
    private static String buildDefectClassesHtml(boolean dark) {
        String titleColor = dark ? "#00e1ff" : "#0066cc";
        String hColor = dark ? "#ffe066" : "#b38600";
        String borderCol = dark ? "#3e4450" : "#d0d6e2";

        return "<html><body style='" + baseStyles(dark) + "'>"
                + "<div style='font-size: 15px; font-weight: bold; color: " + titleColor + "; margin-bottom: 2px;'>"
                + "Katalog Form Wy\u0142adowa\u0144 Niezupe\u0142nych (6 Klas WNZ)</div>"
                + "<div style='font-size: 11px; color: #8899aa; margin-bottom: 12px;'>"
                + "Fizyka zjawisk, morfologia obrazu PRPD i kryteria rozpoznawania defekt\u00f3w izolacji</div>"

                + "<div style='" + cardStyle(dark, borderCol) + "'>"
                + "<b style='color: #00d4ff; font-size: 12px;'>1. Ulot ostrzowy (Corona HV)</b><br>"
                + "\u2022 <b>Fizyka zjawiska:</b> Wy\u0142adowania zachodz\u0105ce w gazie (powietrzu, SF6) w obszarach silnie niejednorodnego pola elektrycznego wok\u00f3\u0142 ostrych kraw\u0119dzi, \u015brub, przewod\u00f3w fazowych lub ko\u0144c\u00f3wek szyn rozdzielnicy.<br>"
                + "\u2022 <b>Obraz PRPD:</b> Wyj\u0105tkowo charakterystyczne skupiska o kszta\u0142cie pionowych 'pi\u00f3ropuszy' skupionych \u015bci\u015ble wok\u00f3\u0142 szczyt\u00f3w napi\u0119cia (90&deg; i 270&deg;).<br>"
                + "\u2022 <b>Odcisk statystyczny:</b> Bardzo wysoka asymetria p\u00f3\u0142okres\u00f3w (Q = N+/N- lub Qmax+/Qmax- cz\u0119sto przekracza 2.5), wysoka kurtoza (smuk\u0142e rozk\u0142ady) oraz niska korelacja krzy\u017cowa CC &lt; 0.3."
                + "</div>"

                + "<div style='" + cardStyle(dark, borderCol) + "'>"
                + "<b style='color: #f59e0b; font-size: 12px;'>2. Wy\u0142adowania powierzchniowe / pe\u0142zaj\u0105ce (Surface / Tracking)</b><br>"
                + "\u2022 <b>Fizyka zjawiska:</b> Wy\u0142adowania rozwijaj\u0105ce si\u0119 wzd\u0142u\u017c granicy faz dielektryk sta\u0142y &ndash; gaz, np. po powierzchni izolator\u00f3w wsporczych, przepust\u00f3w lub g\u0142owic kablowych w warunkach zawilgocenia lub zabrudzenia.<br>"
                + "\u2022 <b>Obraz PRPD:</b> Szerokie chmury wy\u0142adowa\u0144 rozci\u0105gaj\u0105ce si\u0119 w obu p\u00f3\u0142okresach od 20&deg; do 110&deg; oraz od 200&deg; do 290&deg;. Amplitudy rosn\u0105 wraz ze wzrostem napi\u0119cia chwilowego.<br>"
                + "\u2022 <b>Odcisk statystyczny:</b> Umiarkowana asymetria, sko\u015bno\u015b\u0107 dodatnia Sk &gt; 0, obecno\u015b\u0107 impuls\u00f3w o bardzo du\u017cej energii w pobli\u017cu przej\u015bcia przez zero."
                + "</div>"

                + "<div style='" + cardStyle(dark, borderCol) + "'>"
                + "<b style='color: #ef4444; font-size: 12px;'>3. Wy\u0142adowania wtr\u0105cone / p\u0119cherzykowe (Internal / Void)</b><br>"
                + "\u2022 <b>Fizyka zjawiska:</b> Wy\u0142adowania w mikroskopijnych p\u0119cherzykach gazowych lub pustkach uwi\u0119zionych wewn\u0105trz litej izolacji sta\u0142ej (np. \u017cywicy epoksydowej, polietylenu XLPE, papieru transformatorowego). Najbardziej niebezpieczna forma defektu prowadz\u0105ca do drzewienia elektrycznego (electrical treeing).<br>"
                + "\u2022 <b>Obraz PRPD:</b> Prawie idealnie symetryczne, 'owalne' lub 'prostok\u0105tne' chmury wyprzedzaj\u0105ce szczyty napi\u0119cia (wyst\u0119puj\u0105ce na zboczach narastaj\u0105cych 0&deg;&ndash;90&deg; oraz 180&deg;&ndash;270&deg;).<br>"
                + "\u2022 <b>Odcisk statystyczny:</b> Bardzo wysoka symetria p\u00f3\u0142okres\u00f3w (Q &asymp; 1.0, Qa &asymp; 1.0), korelacja krzy\u017cowa CC &gt; 0.70 oraz identyczny k\u0105t zap\u0142onu &Delta;&phi;_inc &asymp; 180&deg;."
                + "</div>"

                + "<div style='" + cardStyle(dark, borderCol) + "'>"
                + "<b style='color: #a855f7; font-size: 12px;'>4. Cz\u0105stka zawieszona / elektroda p\u0142ywaj\u0105ca (Floating Electrode)</b><br>"
                + "\u2022 <b>Fizyka zjawiska:</b> Nieuziemiony element metalowy (np. lu\u017ana podk\u0142adka, opaska ekranuj\u0105ca, odspojony ekran kabla) przyjmuj\u0105cy potencja\u0142 p\u0142ywaj\u0105cy w polu elektrycznym.<br>"
                + "\u2022 <b>Obraz PRPD:</b> Bardzo w\u0105skie, pionowe linie impuls\u00f3w o wyj\u0105tkowo sta\u0142ej i powtarzalnej amplitudzie w ka\u017cdym okresie 50 Hz.<br>"
                + "\u2022 <b>Odcisk statystyczny:</b> Skrajnie ma\u0142a wariancja \u0142adunku, wysoka powtarzalno\u015b\u0107 mi\u0119dzyokresowa."
                + "</div>"

                + "<div style='" + cardStyle(dark, borderCol) + "'>"
                + "<b style='color: #3b82f6; font-size: 12px;'>5. Z\u0142y styk / iskrzenie (Contact / Sparking)</b><br>"
                + "\u2022 <b>Fizyka zjawiska:</b> Mikro\u0142uki na stykach szynowych, po\u0142\u0105czeniach \u015brubowych lub w z\u0142\u0105czach o zwi\u0119kszonej rezystancji przej\u015bcia.<br>"
                + "\u2022 <b>Obraz PRPD:</b> Nieregularne k\u0119pki impuls\u00f3w zale\u017cne od p\u0142yn\u0105cego pr\u0105du obci\u0105\u017cenia, cz\u0119sto przesuni\u0119te fazowo wzgl\u0119dem napi\u0119cia."
                + "</div>"

                + "<div style='" + cardStyle(dark, borderCol) + "'>"
                + "<b style='color: #64748b; font-size: 12px;'>6. Zak\u0142\u00f3cenia i szum t\u0142a (Noise / Background)</b><br>"
                + "\u2022 <b>Fizyka zjawiska:</b> Zewn\u0119trzne zak\u0142\u00f3cenia elektromagnetyczne (fale radiowe, szum termiczny, impulsy komutacyjne tyrystor\u00f3w).<br>"
                + "\u2022 <b>Obraz PRPD:</b> R\u00f3wnomierny, losowy rozk\u0142ad punkt\u00f3w w ca\u0142ym przedziale k\u0105towym 0&ndash;360&deg; bez \u017cadnej korelacji z sinusoid\u0105 50 Hz."
                + "</div>"

                + "</body></html>";
    }

    // 7. XAI & RECOMMENDATIONS
    private static String buildXaiRecommendationsHtml(boolean dark) {
        String titleColor = dark ? "#00e1ff" : "#0066cc";
        String hColor = dark ? "#ffe066" : "#b38600";
        String borderCol = dark ? "#3e4450" : "#d0d6e2";

        return "<html><body style='" + baseStyles(dark) + "'>"
                + "<div style='font-size: 15px; font-weight: bold; color: " + titleColor + "; margin-bottom: 2px;'>"
                + "Wyja\u015bnialne AI (XAI) i Zalecenia Eksploatacyjne</div>"
                + "<div style='font-size: 11px; color: #8899aa; margin-bottom: 12px;'>"
                + "Zasada dzia\u0142ania modu\u0142u transparentno\u015bci decyzyjnej i normatywne procedury CIGRE</div>"

                + "<div style='" + cardStyle(dark, borderCol) + "'>"
                + "<b style='color: " + hColor + "; font-size: 12px;'>Czym jest Explainable AI (XAI) w programie PRPD Tool?</b><br>"
                + "W zastosowaniach in\u017cynierskich i elektroenergetyce wysokonapi\u0119ciowej diagnoza 'czarnej skrzynki' jest nie do przyj\u0119cia &ndash; "
                + "in\u017cynier diagnosta musi wiedzie\u0107, <b>dlaczego algorytm podj\u0105\u0142 okre\u015blon\u0105 decyzj\u0119</b>.<br><br>"
                + "Modu\u0142 XAI analizuje wyekstrahowany wektor cech i t\u0142umaczy przes\u0142anki w postaci zrozumiałych regu\u0142 fizykalnych, np.:<br>"
                + "<i>'Dominuj\u0105ca asymetria \u0142adunku Q+/Q- = 2.516 wskazuje na jednobiegunowy charakter wy\u0142adowania, a skupienie impuls\u00f3w przy k\u0105cie 90&deg; z ujemn\u0105 sko\u015bno\u015bci\u0105 fazow\u0105 bezwzgl\u0119dnie potwierdza obecno\u015b\u0107 ulotu ostrzowego w o\u015brodku powietrznym.'</i>"
                + "</div>"

                + "<div style='font-size: 13px; font-weight: bold; color: " + titleColor + "; margin: 12px 0 6px 0;'>"
                + "Normatywne procedury eksploatacyjne CIGRE</div>"

                + "<div style='" + cardStyle(dark, borderCol) + "'>"
                + "\u2022 <b>Dla wy\u0142adowa\u0144 wtr\u0105conych (Void):</b> Wysokie ryzyko rozwoju drzewienia dielektrycznego. Zalecana natychmiastowa lokalizacja akustyczna/UHF, skr\u00f3cenie czasu mi\u0119dzy badaniami do 7 dni lub zaplanowanie wymiany elementu izolacyjnego podczas najbli\u017cszego okna odstawczego.<br><br>"
                + "\u2022 <b>Dla wy\u0142adowa\u0144 pe\u0142zaj\u0105cych (Surface/Tracking):</b> Zagro\u017cenie przeskokiem \u015blizgowym. Zalecana rewizja komory rozdzielnicy, czyszczenie i odt\u0142uszczenie powierzchni izolator\u00f3w wsporczych, weryfikacja szczelno\u015bci i wilgotno\u015bci powietrza.<br><br>"
                + "\u2022 <b>Dla ulotu ostrzowego (Corona HV):</b> Sprawdzenie geometrii po\u0142\u0105cze\u0144 szynowych, dokr\u0119cenie os\u0142on przeciwulotowych, usuni\u0119cie ostrych zadzior\u00f3w na elementach pod napi\u0119ciem."
                + "</div>"

                + "</body></html>";
    }
}
