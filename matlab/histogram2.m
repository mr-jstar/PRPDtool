% konwersja danych
phase_deg = phase_positions * 360;   % -1..1 → 0..360
q_pC = magnitudes * 1e12;                  % C → pC

% liczba binów
nbins_phase = 200;
nbins_charge = 200;

% zakresy
phase_edges = linspace(0,360,nbins_phase+1);

qMin = prctile(q_pC,0.5);
qMax = prctile(q_pC,99.5);
charge_edges = linspace(qMin,qMax,nbins_charge+1);

% histogram 2D
H = histcounts2(q_pC, phase_deg, charge_edges, phase_edges);

% skala logarytmiczna (jak w Omicron MPD)
H_log = log10(H + 1);

% rysowanie
figure;
imagesc(phase_edges, charge_edges, H_log);
axis xy;
shading interp;
colormap(jet);
colorbar;

xlabel('Phase (degrees)');
ylabel('Charge (pC)');
title('PRPD (MPD-style)');
