package prpdtool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Baza wiedzy i dokumentacja matematyczno-inżynierska
 * dla okna Przestrzeni Decyzyjnej AI i Inspektora XAI (AI Decision Space).
 * 
 * Zgodność z wytycznymi CIGRE WG 21.03 (CIGRE 226) oraz IEC 60270 / IEC 62478.
 */
public class AIDecisionSpaceDoc {

    public static class Entry {
        public final String id;
        public final String titlePl;
        public final String titleEn;
        public final String category;
        public final String formulaHtml;
        public final String mathNote;
        public final String desc;
        public final String applicationNote;
        public final String diagnosticInterpretation;

        public Entry(String id, String titlePl, String titleEn, String category,
                     String formulaHtml, String mathNote, String desc,
                     String applicationNote, String diagnosticInterpretation) {
            this.id = id;
            this.titlePl = titlePl;
            this.titleEn = titleEn;
            this.category = category;
            this.formulaHtml = formulaHtml;
            this.mathNote = mathNote;
            this.desc = desc;
            this.applicationNote = applicationNote;
            this.diagnosticInterpretation = diagnosticInterpretation;
        }

        public String getFullHtml(boolean dark) {
            String bgCard = dark ? "#222730" : "#ffffff";
            String bgCode = dark ? "#181c22" : "#f1f4f8";
            String textPrimary = dark ? "#e6edf3" : "#1f2328";
            String textMuted = dark ? "#8b949e" : "#57606a";
            String accent = dark ? "#00d2ff" : "#0066cc";
            String borderC = dark ? "#30363d" : "#d0d7de";
            String formulaBorder = dark ? "#0088cc" : "#0066cc";

            return "<html><body style='font-family: Segoe UI, sans-serif; font-size: 11pt; color: " + textPrimary + "; margin: 16px;'>" +
                    "<div style='border-bottom: 2px solid " + accent + "; padding-bottom: 8px; margin-bottom: 16px;'>" +
                    "<h2 style='margin: 0; color: " + accent + "; font-size: 15pt;'>" + titlePl + "</h2>" +
                    "<div style='color: " + textMuted + "; font-size: 9.5pt; margin-top: 4px;'>" + titleEn + " | Paradygmat: <b>" + category + "</b></div>" +
                    "</div>" +

                    "<div style='background-color: " + bgCard + "; border: 1px solid " + borderC + "; border-radius: 6px; padding: 12px; margin-bottom: 14px;'>" +
                    "<h4 style='margin: 0 0 6px 0; color: #ffb703; font-size: 11pt;'>1. Zasada rzutowania i interpretacja przestrzeni:</h4>" +
                    "<p style='margin: 0; line-height: 1.5; text-align: justify;'>" + desc + "</p>" +
                    "<div style='margin-top: 10px;'>" +
                    "<b style='color: " + textPrimary + ";'>Sformułowanie matematyczne / Wzór:</b>" +
                    "<div style='background-color: " + bgCode + "; border-left: 3px solid " + formulaBorder + "; padding: 8px 12px; margin: 8px 0; font-family: Consolas, monospace;'>" +
                    formulaHtml +
                    "</div>" +
                    (mathNote.isEmpty() ? "" : "<div style='color: " + textMuted + "; font-size: 9pt;'>" + mathNote + "</div>") +
                    "</div>" +
                    "</div>" +

                    "<div style='background-color: " + bgCard + "; border: 1px solid " + borderC + "; border-radius: 6px; padding: 12px; margin-bottom: 14px;'>" +
                    "<h4 style='margin: 0 0 6px 0; color: #00e1ff; font-size: 11pt;'>2. Rola w Wyjaśnialnym AI (XAI) i rozbijaniu 'czarnej skrzynki':</h4>" +
                    "<p style='margin: 0; line-height: 1.5; text-align: justify;'>" + applicationNote + "</p>" +
                    "</div>" +

                    "<div style='background-color: " + bgCard + "; border: 1px solid " + borderC + "; border-radius: 6px; padding: 12px;'>" +
                    "<h4 style='margin: 0 0 6px 0; color: #ff5964; font-size: 11pt;'>3. Korelacja z wytycznymi normatywnymi CIGRE 226 i IEC 60270:</h4>" +
                    "<p style='margin: 0; line-height: 1.5; text-align: justify;'>" + diagnosticInterpretation + "</p>" +
                    "</div>" +
                    "</body></html>";
        }
    }

    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();

    static {
        // 1. Wielowymiarowa Przestrzeń Cech i Standaryzacja Z-Score
        add("feature_space",
                "Wielowymiarowa Przestrzeń Cech CIGRE (16D -> 2D)",
                "Multidimensional Feature Space & Z-Score Standardization",
                "Reprezentacja Danych",
                "<table cellpadding='0' cellspacing='0' border='0'><tr>" +
                        "<td valign='middle'>z<sub>i</sub> = </td>" +
                        "<td valign='middle'>" +
                        "<table cellpadding='0' cellspacing='0' border='0' style='margin: 0 4px; text-align: center;'>" +
                        "<tr><td style='border-bottom: 1px solid #888888; padding: 1px 4px;'>x<sub>i</sub> - &mu;<sub>i</sub></td></tr>" +
                        "<tr><td style='padding: 1px 4px;'>&sigma;<sub>i</sub></td></tr>" +
                        "</table>" +
                        "</td>" +
                        "<td valign='middle'>, &nbsp;&nbsp; x &isin; &real;<sup>16</sup> &nbsp;&rarr;&nbsp; z &isin; &real;<sup>2</sup></td>" +
                        "</tr></table>",
                "Standaryzacja Z-score: zerowa średnia (μ=0) i jednostkowa wariancja (σ=1) dla każdej z 16 cech CIGRE.",
                "Przestrzeń cech tworzy 16 znormalizowanych deskryptorów statystycznych wyznaczanych z rozkładów fazowych H_n(φ), H_qn(φ) oraz impulsów u(t). Rzutowanie na dowolną parę osi 2D (np. asymetria ładunku Q_max+/Q_max- vs faza zapłonu φ_1) pozwala zweryfikować separowalność geometryczną klas defektów.",
                "Zamiast ufać nieprzeniknionej sieci neuronowej, inżynier widzi dokładnie, w którym klastrze wzorcowym wylądował bieżący pomiar oraz jak duży margines dzieli go od sąsiednich form uszkodzeń izolacji.",
                "Norma CIGRE 226 definiuje ramy liczbowe dla wyładowań koronowych (asymetria Q > 3.0), wtrąceniowych (Q+/Q- w okolicach 1.0) oraz powierzchniowych (asymetria 1.5 - 2.8).");

        // 2. Granica Decyzyjna Maszyny Wektorów Nośnych (SVM Margin)
        add("svm_boundary",
                "Granica Decyzyjna i Margines SVM (Decision Boundary)",
                "Support Vector Machine Hyperplane & Margin Geometry",
                "Granice Decyzyjne",
                "<table cellpadding='0' cellspacing='0' border='0'><tr>" +
                        "<td valign='middle'>w<sup>T</sup> &phi;(x) + b = 0, &nbsp;&nbsp; Margines: &gamma; = </td>" +
                        "<td valign='middle'>" +
                        "<table cellpadding='0' cellspacing='0' border='0' style='margin: 0 4px; text-align: center;'>" +
                        "<tr><td style='border-bottom: 1px solid #888888; padding: 1px 4px;'>2</td></tr>" +
                        "<tr><td style='padding: 1px 4px;'>||w||</td></tr>" +
                        "</table>" +
                        "</td>" +
                        "</tr></table>",
                "Hiperpłaszczyzna rozdzielająca w przestrzeni cech z maksymalizacją marginesu geometrycznego γ.",
                "Model SVM wyznacza optymalną hiperpłaszczyznę separującą poszczególne klasy wyładowań. Na wykresie 2D granica ta jest rzutowana jako linia lub krzywa (dla jądra wielomianowego/RBF). Odległość punktu pomiarowego od granicy bezpośrednio określa pewność diagnozy.",
                "Pozwala zidentyfikować przypadki graniczne (blisko linii rozgraniczającej), w których zachodzi ryzyko fałszywej klasyfikacji, np. na styku wyładowań powierzchniowych i wtrąceniowych.",
                "Odpowiada inżynierskiemu rozgraniczeniu stref stabilnej pracy od stanów awaryjnych rozdzielnic SN wg PN-EN 62271-200.");

        // 3. Wektory Metryczne k-Najbliższych Sąsiadów (k-NN Vectors)
        add("knn_vectors",
                "Wektory Odległości k-Najbliższych Sąsiadów (k-NN)",
                "k-Nearest Neighbors Metric Vectors & Euclidean Distance",
                "Metryka Odległości",
                "<table cellpadding='0' cellspacing='0' border='0'><tr>" +
                        "<td valign='middle'>d(x, x<sup>(i)</sup>) = </td>" +
                        "<td valign='middle'>&radic;( &sum;<sub>j=1</sub><sup>D</sup> w<sub>j</sub> (x<sub>j</sub> - x<sub>j</sub><sup>(i)</sup>)<sup>2</sup> )</td>" +
                        "</tr></table>",
                "Ważona odległość euklidesowa w znormalizowanej przestrzeni cech CIGRE.",
                "Klasyfikator k-NN jest w 100% modelem białej skrzynki (White-Box). Decyzja podejmowana jest przez głosowanie większościowe k najbliższych wzorców laboratoryjnych w bazie. Na wykresie rysowane są bezpośrednie wektory od badanego punktu do k wzorców wraz z ich odległościami numerycznymi.",
                "Diagnosta może kliknąć każdego z sąsiadów i zobaczyć, z jakiego konkretnie testu laboratoryjnego (lub stacji transformatorowej) pochodzi dany wzorzec.",
                "Gwarantuje pełną zgodność z procedurami audytowymi i ekspertyzami biegłych w energetyce.");

        // 4. Ważność Cech (Feature Importance / Gini & SHAP)
        add("feature_importance",
                "Ważność Cech i Ranking Wpływu na Decyzję (Feature Importance)",
                "Feature Importance Ranking (Gini Impurity & Gradient Weights)",
                "Wyjaśnialne AI (XAI)",
                "<table cellpadding='0' cellspacing='0' border='0'><tr>" +
                        "<td valign='middle'>I(f<sub>j</sub>) = </td>" +
                        "<td valign='middle'>" +
                        "<table cellpadding='0' cellspacing='0' border='0' style='margin: 0 4px; text-align: center;'>" +
                        "<tr><td style='border-bottom: 1px solid #888888; padding: 1px 4px;'>&sum;<sub>t &isin; T, v(t)=j</sub> p(t) &Delta;I<sub>G</sub>(t)</td></tr>" +
                        "<tr><td style='padding: 1px 4px;'>&sum;<sub>all</sub> &Delta;I<sub>G</sub></td></tr>" +
                        "</table>" +
                        "</td>" +
                        "<td valign='middle'>&times; 100%</td>" +
                        "</tr></table>",
                "Średni spadek nieczystości Giniego (MDI) w zespole drzew decyzyjnych Random Forest.",
                "Wykres słupkowy ważności cech porządkuje 16 deskryptorów wg ich rzeczywistego wkładu w wygenerowaną diagnozę. Pokazuje, czy o werdykcie zadecydowała asymetria ładunku, stromość czoła impulsu u(t), czy korelacja krzyżowa rozkładów fazowych.",
                "Ostatecznie eliminuje wątpliwości: użytkownik dokładnie wie, który parametr fizyczny zmusił model do zaklasyfikowania wady jako niebezpiecznej.",
                "Wskazuje inżynierom, na których fizycznych parametrach pomiaru WNZ należy skupić uwagę podczas przeglądu komory rozdzielnicy.");

        // 5. Elipsy Ufności Klastrów Wzorcowych (Confidence Ellipses)
        add("cluster_ellipses",
                "Elipsy Ufności Klastrów Wzorcowych (Mahalanobis Distance)",
                "Reference Cluster Confidence Ellipses & Mahalanobis Metric",
                "Modelowanie Statystyczne",
                "<table cellpadding='0' cellspacing='0' border='0'><tr>" +
                        "<td valign='middle'>D<sub>M</sub><sup>2</sup>(x, &mu;) = (x - &mu;)<sup>T</sup> &Sigma;<sup>-1</sup> (x - &mu;) &le; &chi;<sub>2</sub><sup>2</sup>(p)</td>" +
                        "</tr></table>",
                "Elipsa ufności na poziomie ufności p=95% wyznaczona z macierzy kowariancji Σ klastra.",
                "Każda forma wyładowań (Ulot, Wtrącenie, Powierzchniowe, Pływający) posiada w przestrzeni dwuwymiarowej charakterystyczny obszar rozproszenia. Elipsy ufności 95% wizualizują gęstość prawdopodobieństwa wystąpienia danego zjawiska na podstawie bazy danych pomiarów laboratoryjnych.",
                "Pozwala ocenić, czy badany sygnał jest typowym, podręcznikowym przypadkiem wady, czy anomalią leżącą na obrzeżach klastra.",
                "Podstawa oceny wiarygodności pomiarów WNZ zgodnie z CIGRE 444 i wytycznymi diagnostycznymi.");
    }

    private static void add(String id, String titlePl, String titleEn, String category,
                            String formulaHtml, String mathNote, String desc,
                            String applicationNote, String diagnosticInterpretation) {
        ENTRIES.put(id, new Entry(id, titlePl, titleEn, category, formulaHtml, mathNote, desc, applicationNote, diagnosticInterpretation));
    }

    public static List<Entry> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(ENTRIES.values()));
    }

    public static Entry get(String id) {
        return ENTRIES.get(id);
    }
}
