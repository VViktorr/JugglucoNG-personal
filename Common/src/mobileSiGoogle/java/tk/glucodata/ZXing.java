package tk.glucodata;

import static tk.glucodata.Applic.Toaster;
import static tk.glucodata.Applic.isWearable;
import static tk.glucodata.Applic.useZXing;
import static tk.glucodata.PhotoScan.connectSensor;

import android.content.Intent;

import com.journeyapps.barcodescanner.ScanIntentResult;
import com.journeyapps.barcodescanner.ScanOptions;

class ZXing {
    private static final String LOG_ID = "ZXing";
    private static long pendingSensorPtr;

    static void scanZXingAlg(MainActivity activity, int type, long sensorPtr) {
        if (isWearable || !useZXing) return;

        ScanOptions options = new ScanOptions()
                .setPrompt(activity.getString(R.string.photomessage))
                .setOrientationLocked(true)
                .setDesiredBarcodeFormats(ScanOptions.DATA_MATRIX, ScanOptions.QR_CODE);
        pendingSensorPtr = sensorPtr;
        activity.launchBarcodePicker(options.createScanIntent(activity), type);
    }

    static void zXingResult(int resultCode, Intent data, MainActivity activity, int type) {
        if (isWearable || !useZXing) return;

        long sensorPtr = pendingSensorPtr;
        pendingSensorPtr = 0L;
        try {
            String scan = data == null ? null : ScanIntentResult.parseActivityResult(resultCode, data).getContents();
            if (scan != null) {
                Log.i(LOG_ID, "Scan: " + scan);
                Toaster(scan);
                connectSensor(scan, activity, type, sensorPtr);
                return;
            }
            Toaster("Cancelled");
            if (sensorPtr != 0L) PhotoScan.transmitterScanCancelled(sensorPtr);
        } catch (Throwable error) {
            Log.stack(LOG_ID, "zXingResult", error);
        }
    }
}
