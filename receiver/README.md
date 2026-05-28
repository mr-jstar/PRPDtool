# Red Pitaya PRPD Receiver

Graficzny program w Pythonie do wyzwalanej akwizycji ADC z Red Pitaya, ramkowej transmisji TCP, zapisu binarnego `RPPR` oraz interaktywnej wizualizacji przebiegu czasowego.

Docelowa płytka:

```text
STEMlab 125-14 Pro Z7020 Gen 2
```

Odebrane pliki są zapisywane w:

```text
data/received_bin
```

## Windows

W PowerShell:

```powershell
cd E:\Kod\Java\PRPDtool
.\receiver\setup_venv.ps1
.\receiver\.venv\Scripts\python.exe .\receiver\rp_prpd_receive_to_bin.py
```

Jeśli PowerShell blokuje uruchamianie skryptów, wykonaj jednorazowo w tym samym terminalu:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
```

Następnie uruchom ponownie:

```powershell
.\receiver\setup_venv.ps1
```

## macOS

W Terminalu:

```bash
cd /sciezka/do/PRPDtool
sh receiver/setup_venv.sh
receiver/.venv/bin/python receiver/rp_prpd_receive_to_bin.py
```

Jeśli Python nie jest zainstalowany:

```bash
brew install python
```

Potem uruchom ponownie:

```bash
sh receiver/setup_venv.sh
```

## Linux

W terminalu:

```bash
cd /sciezka/do/PRPDtool
sh receiver/setup_venv.sh
receiver/.venv/bin/python receiver/rp_prpd_receive_to_bin.py
```

Na Debianie/Ubuntu w razie braku obsługi `venv`:

```bash
sudo apt update
sudo apt install python3 python3-venv python3-pip
```

Potem uruchom ponownie:

```bash
sh receiver/setup_venv.sh
```

## Agent Na Red Pitaya

Skopiuj agenta na Red Pitaya:

```bash
scp redpitaya_server/rp_prpd_agent.py root@rp-f0f84e.local:/root/rp_prpd_agent.py
```

Uruchom go na Red Pitaya:

```bash
python3 /root/rp_prpd_agent.py --host 0.0.0.0 --port 9999
```

Program GUI łączy się z tym agentem i wysyła ustawienia akwizycji ADC/DMA.
Zakres wejściowy `LV/HV` jest ustawiany oddzielnie dla `IN1` i `IN2`.

## Automatyczne Wyznaczanie Triggera IN1

W głównym GUI użyj przycisku `Wyznacz Trigger automatycznie` przy polu `Poziom Triggera [V]`.

Program uruchomi osobny subprogram:

```text
receiver/rp_trigger_calibrator.py
```

Subprogram zbiera dwie tury danych:

- referencję bez defektu,
- pomiar z defektem.

Na podstawie `abs(IN1)` zaproponuje poziom Triggera. Żółta linia na obu wykresach pokazuje aktualnie wybraną wartość i aktualizuje się po zmianie pola. Po kliknięciu `OK` wartość wraca do głównego GUI.

## Test Lokalny Bez Red Pitaya

Uruchom mock agenta w jednym terminalu.

Windows:

```powershell
.\receiver\.venv\Scripts\python.exe .\receiver\rp_prpd_receive_to_bin.py --mock-agent --host 127.0.0.1 --port 9999
```

macOS/Linux:

```bash
receiver/.venv/bin/python receiver/rp_prpd_receive_to_bin.py --mock-agent --host 127.0.0.1 --port 9999
```

Następnie uruchom GUI w drugim terminalu i ustaw host na `127.0.0.1`.

## Eksport Dla Java PRPDtool

Windows:

```powershell
.\receiver\.venv\Scripts\python.exe .\receiver\rp_prpd_receive_to_bin.py --export data\received_bin\capture.rppr.bin data\received_bin\capture.ch1.prpdtool.bin --channel 1
```

macOS/Linux:

```bash
receiver/.venv/bin/python receiver/rp_prpd_receive_to_bin.py --export data/received_bin/capture.rppr.bin data/received_bin/capture.ch1.prpdtool.bin --channel 1
```

## Uwagi

- Środowisko wirtualne jest lokalne dla katalogu `receiver/.venv`.
- Zależności GUI instaluj na komputerze PC, nie na Red Pitaya.
- Agent na Red Pitaya korzysta z modułów dostępnych w obrazie Red Pitaya oraz z `numpy`.
- GUI zapisuje surowe próbki ADC jako `int16` w plikach `*.rppr.bin`.
- Eksport tworzy starszy format zgodny z czytnikiem Java PRPDtool: pary `double t, double u`.
