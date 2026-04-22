%% make_prpd_clean_grid_5s_step2s.m
% Wymaga: phase_positions (0..1), magnitudes (C), time_stamps (s)

outDir = fullfile(pwd, "def4");
if ~exist(outDir, "dir"); mkdir(outDir); end

winSec  = 5;
stepSec = 2;

% rozmiar PNG
W = 512; Hpx = 512; dpi = 140;

% "umowny" zakres pionu po normalizacji (nie pC)
yMax = 100;

% minimalna liczba punktów w oknie
minPoints = 5000;

% przygotowanie danych
phase_deg_all = mod(phase_positions, 1) * 360;  % 0..360
q_pC_all = magnitudes * 1e12;
q_uni_all = abs(q_pC_all);

t0 = min(time_stamps);
t1 = max(time_stamps);
starts_t = t0 : stepSec : (t1 - winSec);

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

    % --- klucz: kompresja amplitudy, żeby uniknąć "sufitu" ---
    q = log10(q + 1);  % stabilne dla q=0, kompresuje ogon

    % percentyle w skali log
    qLo = prctile(q, 1);
    qHi = prctile(q, 99.9999);   % wyżej niż 99, mniej clippingu
    if ~isfinite(qLo) || ~isfinite(qHi) || qHi <= qLo
        continue;
    end

    qn = (q - qLo) ./ (qHi - qLo);
    qn = max(0, min(1, qn));
    y  = qn * yMax;

    % rysowanie
    fig = figure("Visible","off","Color","w","Position",[100 100 W Hpx]);
    ax = axes(fig);
    hold(ax, "on");
    set(ax, "Color", "w", "XLim", [0 360], "YLim", [0 yMax]);

    % siatka jak w MPD, ale bez liczb i opisów
    set(ax, "XTick", 0:45:360, "YTick", 0:10:yMax);
    grid(ax, "on");
    ax.GridAlpha = 0.25;
    ax.GridLineStyle = "-";

    % usuń liczby/etykiety, zostaw siatkę
    ax.XTickLabel = [];
    ax.YTickLabel = [];
    ax.LineWidth = 1;

    % usuń ramkę osi (opcjonalnie). Jak chcesz ramkę, zakomentuj:
    box(ax, "off");

    % chmura punktów
    try
        s = scatter(ax, ph, y, 3, "filled");
        s.MarkerFaceAlpha = 0.06;
        s.MarkerEdgeAlpha = 0.06;
    catch
        scatter(ax, ph, y, 3, ".");
    end

    % brak tytułu i opisów
    axis(ax, "tight");
    set(ax, "XLim", [0 360], "YLim", [0 yMax]);

    idxOut = idxOut + 1;
    fname = sprintf("prpd_%05d.png", idxOut);
    exportgraphics(ax, fullfile(outDir, fname), "Resolution", dpi);
    close(fig);
end

fprintf("Zapisano %d obrazów do: %s\n", idxOut, outDir);
