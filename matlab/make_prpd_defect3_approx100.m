%% make_prpd_defect3_approx100.m
% Wymaga w Workspace: phase_positions (0..1), magnitudes (C), time_stamps (s)
% Generuje ~100 obrazów PRPD MPD-style (unipolar, normalizacja), z siatką, bez opisów osi.

%% make_prpd_defectX_approx100.m  (poprawiona wersja kroku)
outDir = fullfile(pwd, "def1");
if ~exist(outDir, "dir"); mkdir(outDir); end

targetN = 100;
winSec  = 5;

% rozmiar PNG
W = 512; Hpx = 512; dpi = 140;

% wygląd / normalizacja
yMax = 100;
minPoints = 3000;

% przygotowanie danych
phase_deg_all = mod(phase_positions, 1) * 360;
q_pC_all = magnitudes * 1e12;
q_uni_all = abs(q_pC_all);

t0 = min(time_stamps);
t1 = max(time_stamps);
T  = t1 - t0;

fprintf("Czas: %.3f .. %.3f (%.1f s)\n", t0, t1, T);

% --- AUTO KROK: tak, aby liczba kandydatów ~ targetN ---
usableT = max(0, T - winSec);          % czas, po którym możemy przesuwać okno
if targetN <= 1 || usableT == 0
    stepSec = usableT;
else
    stepSec = usableT / (targetN - 1); % idealny krok
end

% ograniczenia praktyczne (żeby nie było absurdów)
stepMin = 0.5;     % nie schodź poniżej 0.5 s
stepMax = 10.0;    % nie dawaj >10 s (możesz zmienić)
stepSec = min(max(stepSec, stepMin), stepMax);

% generuj starty
starts_t = t0 : stepSec : (t1 - winSec);

% jeśli przez zaokrąglenia wyszło za dużo, przytnij równomiernie
if numel(starts_t) > targetN
    sel = round(linspace(1, numel(starts_t), targetN));
    starts_t = starts_t(sel);
end

fprintf("Okno %.1f s, krok %.2f s, kandydatów: %d\n", winSec, stepSec, numel(starts_t));

%% pętla generacji (reszta Twojego kodu bez zmian)
idxOut = 0;

for k = 1:numel(starts_t)
    ta = starts_t(k);
    tb = ta + winSec;

    idx = (time_stamps >= ta) & (time_stamps < tb);
    if nnz(idx) < minPoints
        continue;
    end

    ph = phase_deg_all(idx);
    q  = q_uni_all(idx);

    q = log10(q + 1);

    qLo = prctile(q, 1);
    qHi = prctile(q, 99.999);
    if ~isfinite(qLo) || ~isfinite(qHi) || qHi <= qLo
        continue;
    end

    qn = (q - qLo) ./ (qHi - qLo);
    qn = max(0, min(1, qn));
    y  = qn * yMax;

    fig = figure("Visible","off","Color","w","Position",[100 100 W Hpx]);
    ax = axes(fig);
    hold(ax, "on");
    set(ax, "Color", "w", "XLim", [0 360], "YLim", [0 yMax]);

    set(ax, "XTick", 0:45:360, "YTick", 0:10:yMax);
    grid(ax, "on");
    ax.GridAlpha = 0.25;
    ax.GridLineStyle = "-";

    ax.XTickLabel = [];
    ax.YTickLabel = [];
    box(ax, "on");

    try
        s = scatter(ax, ph, y, 3, "filled");
        s.MarkerFaceAlpha = 0.06;
        s.MarkerEdgeAlpha = 0.06;
    catch
        scatter(ax, ph, y, 3, ".");
    end

    set(ax, "XColor", "none", "YColor", "none");
    axis(ax, "tight");
    set(ax, "XLim", [0 360], "YLim", [0 yMax]);

    idxOut = idxOut + 1;
    fname = sprintf("def2_prpd_%05d.png", idxOut);
    exportgraphics(ax, fullfile(outDir, fname), "Resolution", dpi);
    close(fig);
end

fprintf("Zapisano %d obrazów do: %s\n", idxOut, outDir);
