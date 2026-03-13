package com.tvkeyboard.phone;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.journeyapps.barcodescanner.CaptureManager;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import com.tvkeyboard.R;

/**
 * QR code scanner activity using ZXing Android Embedded.
 * Returns scanned content via Intent extra EXTRA_QR_RESULT.
 */
public class QrScanActivity extends AppCompatActivity {

    public static final String EXTRA_QR_RESULT = "qr_result";

    private CaptureManager captureManager;
    private DecoratedBarcodeView barcodeView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_scan);

        barcodeView = findViewById(R.id.barcode_scanner);

        captureManager = new CaptureManager(this, barcodeView);
        captureManager.initializeFromIntent(getIntent(), savedInstanceState);
        captureManager.decode();

        // Override result handling
        barcodeView.decodeSingle(result -> {
            if (result != null && result.getText() != null) {
                Intent data = new Intent();
                data.putExtra(EXTRA_QR_RESULT, result.getText());
                setResult(RESULT_OK, data);
                finish();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        captureManager.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        captureManager.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        captureManager.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        captureManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
