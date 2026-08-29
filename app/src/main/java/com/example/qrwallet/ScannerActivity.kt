package com.example.qrwallet

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView

class ScannerActivity : AppCompatActivity() {
    private lateinit var barcodeView: DecoratedBarcodeView
    private var lastText: String? = null
    private var flashOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanner)

        barcodeView = findViewById(R.id.zxing_barcode_scanner)
        val btnFlash: ImageButton = findViewById(R.id.btnFlash)

        btnFlash.setOnClickListener {
            flashOn = !flashOn
            if (flashOn) {
                barcodeView.setTorchOn()
            } else {
                barcodeView.setTorchOff()
            }
        }

        barcodeView.decodeContinuous(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult?) {
                result?.text?.let { text ->
                    // Avoid duplicate callbacks
                    if (text != lastText) {
                        lastText = text
                        val intent = intent
                        intent.putExtra(EXTRA_SCAN_RESULT, text)
                        intent.putExtra(EXTRA_SCAN_FORMAT, result.barcodeFormat.name)
                        setResult(Activity.RESULT_OK, intent)
                        finish()
                    }
                }
            }

            override fun possibleResultPoints(resultPoints: MutableList<com.google.zxing.ResultPoint>?) {}
        })
    }

    override fun onResume() {
        super.onResume()
        barcodeView.resume()
    }

    override fun onPause() {
        super.onPause()
        barcodeView.pause()
    }

    companion object {
        const val EXTRA_SCAN_RESULT = "extra_scan_result"
        const val EXTRA_SCAN_FORMAT = "extra_scan_format"
    }
}
