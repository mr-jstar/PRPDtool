import numpy as np

fs = 1953125.0
t = np.arange(39064) / fs
f0 = 50.0

t0_true = 0.00166
u = np.sin(2 * np.pi * f0 * (t - t0_true))

Re = np.sum(u * np.cos(2 * np.pi * f0 * t))
Im = np.sum(u * np.sin(2 * np.pi * f0 * t))

phi = np.arctan2(-Im, Re) 

t0_est = (-np.pi/2 - phi) / (2 * np.pi * f0)
T = 1.0 / f0
t0_est = t0_est % T

print(f"True t0: {t0_true}")
print(f"Est  t0: {t0_est}")
