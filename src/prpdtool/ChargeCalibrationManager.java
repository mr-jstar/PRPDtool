package prpdtool;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central Apparent Charge Calibration Manager (IEC 60270 / CIGRE).
 * Converts raw ADC millivolts [mV] to apparent charge in PicoCoulombs [pC] or NanoCoulombs [nC].
 * Maintains calibration parameters, persistent configuration, and notifies UI listeners.
 */
public class ChargeCalibrationManager {

    private static final ChargeCalibrationManager INSTANCE = new ChargeCalibrationManager();

    public static ChargeCalibrationManager getInstance() {
        return INSTANCE;
    }

    public enum ChargeUnit {
        MV("mV", "Miliwolty (ADC)", 1.0),
        PC("pC", "Pikokulomby (IEC 60270)", 1.0),
        NC("nC", "Nanokulomby (1 nC = 1000 pC)", 0.001);

        private final String symbol;
        private final String description;
        private final double factorToUnit; // multiplier from pC to this unit

        ChargeUnit(String symbol, String description, double factorToUnit) {
            this.symbol = symbol;
            this.description = description;
            this.factorToUnit = factorToUnit;
        }

        public String getSymbol() {
            return symbol;
        }

        public String getDescription() {
            return description;
        }

        public double getFactorToUnit() {
            return factorToUnit;
        }

        @Override
        public String toString() {
            return symbol + " - " + description;
        }
    }

    // Config keys
    public static final String CFG_CAL_ENABLED = "PRPDMonitor.calibration.enabled";
    public static final String CFG_CAL_FACTOR = "PRPDMonitor.calibration.factor_pC_mV";
    public static final String CFG_CAL_TARGET_PC = "PRPDMonitor.calibration.target_charge_pC";
    public static final String CFG_CAL_MEASURED_MV = "PRPDMonitor.calibration.measured_mV";
    public static final String CFG_CAL_UNIT = "PRPDMonitor.calibration.active_unit";
    public static final String CFG_CAL_NAME = "PRPDMonitor.calibration.calibrator_name";
    public static final String CFG_CAL_DATE = "PRPDMonitor.calibration.date";

    private boolean isCalibrated = false;
    private ChargeUnit currentUnit = ChargeUnit.MV;
    private double scaleFactor_pC_per_mV = 1.0;
    private double targetCharge_pC = 10.0;
    private double measuredMean_mV = 10.0;
    private String calibratorName = "Wzorzec IEC 60270";
    private String calibrationDate = "-";

    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();

    private ChargeCalibrationManager() {
    }

    /**
     * Converts a raw ADC voltage in mV to the currently selected display charge unit.
     */
    public double toDisplay(double amplitudeMv) {
        if (currentUnit == ChargeUnit.MV || !isCalibrated) {
            return amplitudeMv;
        }
        double pC = amplitudeMv * scaleFactor_pC_per_mV;
        if (currentUnit == ChargeUnit.NC) {
            return pC * 0.001;
        }
        return pC;
    }

    /**
     * Converts a display charge back to raw mV.
     */
    public double fromDisplay(double displayVal) {
        if (currentUnit == ChargeUnit.MV || !isCalibrated) {
            return displayVal;
        }
        if (currentUnit == ChargeUnit.NC) {
            displayVal *= 1000.0;
        }
        if (scaleFactor_pC_per_mV <= 0.0) return displayVal;
        return displayVal / scaleFactor_pC_per_mV;
    }

    public String getUnitLabel() {
        return (isCalibrated) ? currentUnit.getSymbol() : "mV";
    }

    public ChargeUnit getUnit() {
        return currentUnit;
    }

    public void setUnit(ChargeUnit unit) {
        if (unit != null && this.currentUnit != unit) {
            this.currentUnit = unit;
            notifyListeners();
        }
    }

    public boolean isCalibrated() {
        return isCalibrated;
    }

    public double getScaleFactor() {
        return scaleFactor_pC_per_mV;
    }

    public double getTargetCharge_pC() {
        return targetCharge_pC;
    }

    public double getMeasuredMean_mV() {
        return measuredMean_mV;
    }

    public String getCalibratorName() {
        return calibratorName;
    }

    public String getCalibrationDate() {
        return calibrationDate;
    }

    /**
     * Applies new calibration parameters.
     * k_cal = target_pC / measured_mV [pC/mV]
     */
    public void calibrate(double target_pC, double measured_mV, String calibratorName, boolean switchToPc) {
        if (measured_mV <= 0.0 || target_pC <= 0.0) {
            throw new IllegalArgumentException("Wartości kalibracji muszą być większe od zera.");
        }
        this.targetCharge_pC = target_pC;
        this.measuredMean_mV = measured_mV;
        this.scaleFactor_pC_per_mV = target_pC / measured_mV;
        this.calibratorName = (calibratorName != null && !calibratorName.trim().isEmpty())
                ? calibratorName.trim()
                : "Wzorzec IEC 60270";
        this.calibrationDate = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
        this.isCalibrated = true;

        if (switchToPc) {
            this.currentUnit = ChargeUnit.PC;
        }

        notifyListeners();
    }

    /**
     * Resets calibration to uncalibrated millivolts mode.
     */
    public void resetCalibration() {
        this.isCalibrated = false;
        this.currentUnit = ChargeUnit.MV;
        this.scaleFactor_pC_per_mV = 1.0;
        this.targetCharge_pC = 10.0;
        this.measuredMean_mV = 10.0;
        this.calibratorName = "Wzorzec IEC 60270";
        this.calibrationDate = "-";
        notifyListeners();
    }

    public String getStatusSummary() {
        if (!isCalibrated) {
            return "Nieskalibrowany (tryb surowy mV)";
        }
        return String.format(Locale.US, "Skalibrowany: %.3f pC/mV [%s] (Wzorzec: %.1f pC)",
                scaleFactor_pC_per_mV, currentUnit.getSymbol(), targetCharge_pC);
    }

    public void addChangeListener(Runnable listener) {
        if (listener != null && !changeListeners.contains(listener)) {
            changeListeners.add(listener);
        }
    }

    public void removeChangeListener(Runnable listener) {
        changeListeners.remove(listener);
    }

    private void notifyListeners() {
        for (Runnable r : changeListeners) {
            try {
                r.run();
            } catch (Exception ignored) {
            }
        }
    }

    // =========================================================================
    // PERSISTENCE (CONFIG FILE INTEGRATION)
    // =========================================================================

    public void loadFromConfig(Configuration config) {
        if (config == null) return;
        try {
            String enStr = config.getValue(CFG_CAL_ENABLED);
            if (enStr != null) {
                isCalibrated = Boolean.parseBoolean(enStr);
            }

            String factorStr = config.getValue(CFG_CAL_FACTOR);
            if (factorStr != null) {
                scaleFactor_pC_per_mV = Double.parseDouble(factorStr);
            }

            String targetStr = config.getValue(CFG_CAL_TARGET_PC);
            if (targetStr != null) {
                targetCharge_pC = Double.parseDouble(targetStr);
            }

            String measStr = config.getValue(CFG_CAL_MEASURED_MV);
            if (measStr != null) {
                measuredMean_mV = Double.parseDouble(measStr);
            }

            String nameStr = config.getValue(CFG_CAL_NAME);
            if (nameStr != null && !nameStr.isEmpty()) {
                calibratorName = nameStr;
            }

            String dateStr = config.getValue(CFG_CAL_DATE);
            if (dateStr != null && !dateStr.isEmpty()) {
                calibrationDate = dateStr;
            }

            String unitStr = config.getValue(CFG_CAL_UNIT);
            if (unitStr != null) {
                for (ChargeUnit u : ChargeUnit.values()) {
                    if (u.name().equalsIgnoreCase(unitStr) || u.getSymbol().equalsIgnoreCase(unitStr)) {
                        currentUnit = u;
                        break;
                    }
                }
            }
        } catch (Exception ex) {
            System.err.println("Błąd wczytywania kalibracji z konfiguracji: " + ex.getMessage());
        }
    }

    public void saveToConfig(Configuration config) {
        if (config == null) return;
        try {
            config.saveValue(CFG_CAL_ENABLED, Boolean.toString(isCalibrated));
            config.saveValue(CFG_CAL_FACTOR, String.format(Locale.US, "%.6f", scaleFactor_pC_per_mV));
            config.saveValue(CFG_CAL_TARGET_PC, String.format(Locale.US, "%.3f", targetCharge_pC));
            config.saveValue(CFG_CAL_MEASURED_MV, String.format(Locale.US, "%.3f", measuredMean_mV));
            config.saveValue(CFG_CAL_UNIT, currentUnit.name());
            config.saveValue(CFG_CAL_NAME, calibratorName);
            config.saveValue(CFG_CAL_DATE, calibrationDate);
        } catch (Exception ex) {
            System.err.println("Błąd zapisu kalibracji do konfiguracji: " + ex.getMessage());
        }
    }
}
