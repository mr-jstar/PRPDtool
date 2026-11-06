package prpdtool;

/**
 * Diagnostic Cheat Sheet Knowledge Provider for High-Voltage Partial Discharge Engineers.
 * Synthesizes normative criteria from CIGRE 226, IEC 60270, and IEEE Trans. DEI.
 */
public class DiagnosticCheatSheetDoc {

    public static String getHtmlContent(boolean isDarkMode) {
        String bgCard = isDarkMode ? "#1a202c" : "#ffffff";
        String borderCard = isDarkMode ? "#2d3748" : "#e2e8f0";
        String textC = isDarkMode ? "#e2e8f0" : "#2d3748";
        String subTextC = isDarkMode ? "#a0aec0" : "#718096";
        String tableHeadBg = isDarkMode ? "#242d3d" : "#edf2f7";
        String rowAltBg = isDarkMode ? "#171c26" : "#f7fafc";

        return "<html><body style='font-family:Segoe UI, sans-serif; font-size:12px; color:" + textC + "; margin:12px;'>"

                // HEADER
                + "<div style='margin-bottom:16px;'>"
                + "<div style='font-size:16px; font-weight:bold; color:#00d2ff;'>ŚCIĄGAWKA DIAGNOSTYCZNA INŻYNIERA WNZ (CIGRE 226 / IEC 60270)</div>"
                + "<div style='font-size:11px; color:" + subTextC + "; margin-top:2px;'>"
                + "Kompleksowe kompendium kryteriów identyfikacji defektów, normatywnych wskaźników asymetrii i zasad interpretacji modeli AI."
                + "</div>"
                + "</div>"

                // SECTION 1: QUICK CLASSIFICATION TABLE
                + "<div style='font-size:13px; font-weight:bold; color:#ffb703; margin-bottom:6px;'>1. Tabela Identyfikacji Defektów WNZ (Normatywne Kryteria Fizyczne)</div>"
                + "<table cellpadding='6' cellspacing='0' border='0' style='width:100%; border-collapse:collapse; margin-bottom:16px; font-size:11px;'>"
                + "<tr style='background-color:" + tableHeadBg + "; font-weight:bold; text-align:left;'>"
                + "<th style='border:1px solid " + borderCard + ";'>Typ Defektu</th>"
                + "<th style='border:1px solid " + borderCard + ";'>Kąty Fazowe &phi;</th>"
                + "<th style='border:1px solid " + borderCard + ";'>Asymetria Q<sup>+</sup>/Q<sup>-</sup></th>"
                + "<th style='border:1px solid " + borderCard + ";'>Korelacja cc</th>"
                + "<th style='border:1px solid " + borderCard + ";'>Wpływ Napięcia U</th>"
                + "<th style='border:1px solid " + borderCard + ";'>Krytyczność & Akcja</th>"
                + "</tr>"

                // Row 1: Corona +
                + "<tr>"
                + "<td style='border:1px solid " + borderCard + "; font-weight:bold; color:#ef4444;'>Ulot Dodatni (Corona +)</td>"
                + "<td style='border:1px solid " + borderCard + ";'>35&deg; - 85&deg; (szczyt +)</td>"
                + "<td style='border:1px solid " + borderCard + "; font-weight:bold;'>&gt; 2.2 (często &gt; 4.0)</td>"
                + "<td style='border:1px solid " + borderCard + ";'>&lt; 0.25 (skrajna asymetria)</td>"
                + "<td style='border:1px solid " + borderCard + ";'>Stały ładunek; rośnie liczba impulsów</td>"
                + "<td style='border:1px solid " + borderCard + ";'><span style='color:#f59e0b; font-weight:bold;'>ŚREDNIA:</span> Erozja i ozonowanie. Zaplanować czyszczenie ostrzy na przeglądzie.</td>"
                + "</tr>"

                // Row 2: Corona -
                + "<tr style='background-color:" + rowAltBg + ";'>"
                + "<td style='border:1px solid " + borderCard + "; font-weight:bold; color:#0284c7;'>Ulot Ujemny (Corona -)</td>"
                + "<td style='border:1px solid " + borderCard + ";'>215&deg; - 265&deg; (szczyt -)</td>"
                + "<td style='border:1px solid " + borderCard + "; font-weight:bold;'>&lt; 0.45 (dominuje dół)</td>"
                + "<td style='border:1px solid " + borderCard + ";'>&lt; 0.25 (impulsy Trichela)</td>"
                + "<td style='border:1px solid " + borderCard + ";'>Wysoka częstotliwość powtarzania</td>"
                + "<td style='border:1px solid " + borderCard + ";'><span style='color:#f59e0b; font-weight:bold;'>ŚREDNIA:</span> Wyładowania z uziemionej konstrukcji w stronę fazy.</td>"
                + "</tr>"

                // Row 3: Surface
                + "<tr>"
                + "<td style='border:1px solid " + borderCard + "; font-weight:bold; color:#10b981;'>Powierzchniowe (Surface Creeping)</td>"
                + "<td style='border:1px solid " + borderCard + ";'>15&deg;-90&deg; oraz 195&deg;-270&deg;</td>"
                + "<td style='border:1px solid " + borderCard + "; font-weight:bold;'>1.4 - 2.8 (asymetryczne)</td>"
                + "<td style='border:1px solid " + borderCard + ";'>0.40 - 0.75 (szerokie pasma)</td>"
                + "<td style='border:1px solid " + borderCard + ";'>Gwałtowny wzrost ładunku ze wzrostem U</td>"
                + "<td style='border:1px solid " + borderCard + ";'><span style='color:#ef4444; font-weight:bold;'>BARDZO WYSOKA:</span> Ryzyko przeskoku iskrowego (flashover). Pilna inspekcja izolatorów!</td>"
                + "</tr>"

                // Row 4: Internal Void
                + "<tr style='background-color:" + rowAltBg + ";'>"
                + "<td style='border:1px solid " + borderCard + "; font-weight:bold; color:#a855f7;'>Pęcherz w Izolacji (Internal Void)</td>"
                + "<td style='border:1px solid " + borderCard + ";'>10&deg;-45&deg; oraz 190&deg;-225&deg; (tuż po zerze!)</td>"
                + "<td style='border:1px solid " + borderCard + "; font-weight:bold;'>0.80 - 1.25 (idealna symetria)</td>"
                + "<td style='border:1px solid " + borderCard + "; font-weight:bold;'>&gt; 0.85 (lustrzane półokresy)</td>"
                + "<td style='border:1px solid " + borderCard + ";'>Niski próg zapłonu U_inc; ładunek nasyca się</td>"
                + "<td style='border:1px solid " + borderCard + ";'><span style='color:#ef4444; font-weight:bold;'>KRYTYCZNA:</span> Rozwój drzewienia elektrycznego (electrical treeing). Ryzyko przebicia skrośnego!</td>"
                + "</tr>"

                // Row 5: Floating
                + "<tr>"
                + "<td style='border:1px solid " + borderCard + "; font-weight:bold; color:#eab308;'>Potencjał Pływający (Floating Particle)</td>"
                + "<td style='border:1px solid " + borderCard + ";'>Bardzo wąskie prążki fazowe (np. 50&deg;, 230&deg;)</td>"
                + "<td style='border:1px solid " + borderCard + "; font-weight:bold;'>0.85 - 1.30</td>"
                + "<td style='border:1px solid " + borderCard + ";'>&gt; 0.70 (stała amplituda)</td>"
                + "<td style='border:1px solid " + borderCard + ";'>Niezależny od U po osiągnięciu progu zapłonu</td>"
                + "<td style='border:1px solid " + borderCard + ";'><span style='color:#ef4444; font-weight:bold;'>WYSOKA:</span> Poluzowana śruba/ekran. Ryzyko zapłonu łuku zwarciowego w GIS/rozdzielnicy.</td>"
                + "</tr>"

                // Row 6: Noise
                + "<tr style='background-color:" + rowAltBg + ";'>"
                + "<td style='border:1px solid " + borderCard + "; font-weight:bold; color:#94a3b8;'>Szum Tła / Zakłócenia (Noise)</td>"
                + "<td style='border:1px solid " + borderCard + ";'>Rozproszone losowo 0&deg; - 360&deg;</td>"
                + "<td style='border:1px solid " + borderCard + ";'>Dowolna (zależna od fazy szumu)</td>"
                + "<td style='border:1px solid " + borderCard + ";'>&sim; 0.00 (brak korelacji)</td>"
                + "<td style='border:1px solid " + borderCard + ";'>Brak zależności od napięcia toru WNZ</td>"
                + "<td style='border:1px solid " + borderCard + ";'><span style='color:#10b981; font-weight:bold;'>BEZPIECZNE:</span> Tło stacji elektroenergetycznej, PLC, przekształtniki. Nie wymaga wyłączenia.</td>"
                + "</tr>"
                + "</table>"

                // SECTION 2: 4 GOLDEN DIAGNOSTIC RULES
                + "<div style='font-size:13px; font-weight:bold; color:#00d2ff; margin-bottom:8px;'>2. Cztery Złote Zasady Diagnosty WNZ (CIGRE WG 21.03)</div>"
                + "<div style='background-color:" + bgCard + "; border:1px solid " + borderCard + "; border-radius:6px; padding:12px; margin-bottom:16px; line-height:1.6;'>"
                + "<b>Reguła 1 (Kąt zapłonu &phi;_1):</b> Prawdziwe wyładowania wewnętrzne w dielektryku stałym (Void) zapalają się zawsze w pobliżu przejścia napięcia przez zero (<b>&phi; &in; [10&deg;, 45&deg;]</b>), ponieważ ładunek szczątkowy na ściankach pęcherza dodaje się do napięcia zasilającego. Ulot w powietrzu (Corona) wymaga maksymalnego natężenia pola, stąd zapala się dopiero wokół szczytu sinusoidy (<b>&phi; &in; [45&deg;, 90&deg;]</b>).<br><br>"
                + "<b>Reguła 2 (Wskaźnik symetrii Q_max+ / Q_max-):</b> Wartość asymetrii bliska 1.0 (zakres 0.85-1.15) to niemal niezawodny dowód na wyładowanie symetryczne (Void lub cząstka pływająca). Asymetria &gt; 2.5 jednoznacznie wskazuje na wyładowania ulotowe (Corona) lub powierzchniowe.<br><br>"
                + "<b>Reguła 3 (Korelacja fazowa cc):</b> Współczynnik korelacji <i>cc &gt; 0.85</i> oznacza, że rozkłady fazowe w obu połówkach okresu są swoimi lustrzanymi odbiciami. W zjawiskach ulotowych <i>cc &lt; 0.20</i>, co chroni przed błędną klasyfikacją.<br><br>"
                + "<b>Reguła 4 (Zachowanie przy zmianie napięcia probierczego U):</b> Przy podnoszeniu napięcia probierczego wyładowania powierzchniowe wykazują lawinowy wzrost ładunku pozornego Q (zjawisko ślizgowe). Z kolei w pęcherzach gazowych ładunek Q nasyca się i rośnie jedynie liczba impulsów N na sekundę."
                + "</div>"

                // SECTION 3: HOW TO INTERPRET AI VALIDATION CHARTS
                + "<div style='font-size:13px; font-weight:bold; color:#10b981; margin-bottom:8px;'>3. Jak Interpretować Wykresy i Metryki AI w Aplikacji?</div>"
                + "<div style='background-color:" + bgCard + "; border:1px solid " + borderCard + "; border-radius:6px; padding:12px; line-height:1.6;'>"
                + "• <b>Macierz Pomyłek (Confusion Matrix):</b> Przekątna reprezentuje poprawne trafienia (True Positives). Komórki poza przekątną to błędy modelu. Zwróć uwagę, czy dany model myli wyładowania powierzchniowe z ulotem – są to fizycznie zbliżone rozkłady ładunku, w których sieci 2D-CNN (ResNet, YOLO) radzą sobie znacznie lepiej niż klasyczne k-NN.<br>"
                + "• <b>Krzywe ROC i Wskaźnik AUC:</b> Pole powierzchni pod krzywą (AUC) powyżej <b>0.950</b> oznacza model klasy badawczo-przemysłowej. Wskazuje, że model potrafi wykryć defekt przy skrajnie niskim odsetku fałszywych alarmów (wysoka swoistość).<br>"
                + "• <b>Krzywe Precision-Recall:</b> Gdy zbiór danych zawiera miliony impulsów szumowych i zaledwie setki impulsów WNZ, krzywa PR jest bardziej miarodajna niż ROC. Im wyższa powierzchnia (AP), tym większa pewność, że alarm wygenerowany przez model nie jest fałszywym alarmem zakłóceniowym.<br>"
                + "• <b>Przestrzeń Decyzyjna (XAI):</b> Pozycja neonowego punktu pomiarowego względem elips ufności 95% CIGRE pozwala inżynierowi natychmiast zweryfikować fizyczną wiarygodność predykcji sieci głębokich bez efektu 'czarnej skrzynki'."
                + "</div>"

                + "</body></html>";
    }
}
