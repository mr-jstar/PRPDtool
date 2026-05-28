# Red Pitaya PRPD Data Acquisition Agent

Ten projekt zawiera agenta napisanego w języku Python do akwizycji danych RAW z urządzenia Red Pitaya (PRPD). Serwer nasłuchuje komend w formacie JSON i odsyła ramki z danymi ADC za pośrednictwem protokołu TCP.

W repozytorium znajduje się również konfiguracja usługi `systemd`, która zapewnia automatyczny start serwera po uruchomieniu zasilania (autostart) oraz automatyczny restart w przypadku awarii.

##  Struktura plików w systemie Red Pitaya

Docelowo pliki z tego repozytorium powinny znaleźć się na urządzeniu w następujących lokalizacjach:

```text
/
├── root/
│   └── wnz/
│       └── rp_prpd_agent.py             <-- Główny skrypt serwera
│
└── etc/
    └── systemd/
        └── system/
            └── rp-prpd-agent.service    <-- Definicja usługi 
```

##  Instalacja i wdrożenie na urządzeniu

Aby wgrać pliki z komputera (PC) na Red Pitayę i uruchomić usługę, wykonaj poniższe kroki. Pamiętaj, aby podmienić `<IP_RED_PITAYY>` na rzeczywisty adres IP swojej płytki.

### 1. Przesłanie plików na płytkę (przez SCP)

Otwórz terminal na swoim komputerze i wyślij pliki za pomocą `scp`:

```bash
# Utworzenie katalogu roboczego na Red Pitayi
ssh root@<IP_RED_PITAYY> "mkdir -p /root/wnz"

# Wgranie skryptu Pythona
scp rp_prpd_agent.py root@<IP_RED_PITAYY>:/root/wnz/

# Wgranie pliku usługi systemd
scp rp-prpd-agent.service root@<IP_RED_PITAYY>:/etc/systemd/system/
```

*(Domyślne hasło dla użytkownika root to  `root`)*

### 2. Aktywacja usługi `systemd`

Zaloguj się na Red Pitayę przez SSH:

```bash
ssh root@<IP_RED_PITAYY>
```

Następnie załaduj nową konfigurację i włącz usługę:

```bash
# 1. Przeładowanie demona (aby systemd zauważył nowy plik)
systemctl daemon-reload

# 2. Włączenie autostartu (usługa wstanie po restarcie zasilania)
systemctl enable rp-prpd-agent.service

# 3. Natychmiastowe uruchomienie serwera
systemctl start rp-prpd-agent.service

```

## Diagnostyka i logi

Jeśli chcesz sprawdzić, czy serwer działa poprawnie, lub zdiagnozować problemy, użyj wbudowanych narzędzi systemowych:

* **Sprawdzenie statusu usługi:**
```bash
systemctl status rp-prpd-agent.service
```


* **Podgląd logów na żywo :**
```bash
journalctl -u rp-prpd-agent.service -f
```



##  Parametry sieciowe

Serwer domyślnie nasłuchuje na wszystkich interfejsach (`0.0.0.0`) na porcie TCP `9999`.
Możesz to zmienić edytując parametry `--bind-address` oraz `--listen-port` w pliku `/etc/systemd/system/rp-prpd-agent.service`. Po każdej edycji tego pliku pamiętaj o wykonaniu `systemctl daemon-reload` i zrestartowaniu usługi (`systemctl restart rp-prpd-agent.service`).

