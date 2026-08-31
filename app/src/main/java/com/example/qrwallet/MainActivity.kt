package com.example.qrwallet

import androidx.annotation.Keep

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.example.qrwallet.data.AppDatabase
import com.example.qrwallet.data.Card
import com.example.qrwallet.repo.CardRepository
import com.example.qrwallet.ui.QRApp
import com.google.gson.Gson
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
// Camera scanning now handled by ScannerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: com.example.qrwallet.viewmodel.CardViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge drawing so Compose `statusBarsPadding()` / `navigationBarsPadding()` work
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Make status and navigation bars transparent so the app can draw behind them
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        val db = AppDatabase.getInstance(applicationContext)
        val repo = CardRepository(db.cardDao())
        viewModel = ViewModelProvider(this, com.example.qrwallet.viewmodel.CardViewModelFactory(repo))
            .get(com.example.qrwallet.viewmodel.CardViewModel::class.java)

        setContent {
            MaterialTheme(
                colors = darkColors(
                    primary = Color(0xFF7C9BFF),
                    primaryVariant = Color(0xFF4F7CFF),
                    secondary = Color(0xFF2DD4BF),
                    background = Color(0xFF090F1C),
                    surface = Color(0xFF101A2B),
                    onPrimary = Color.White,
                    onBackground = Color.White,
                    onSurface = Color.White
                ),
                typography = Typography(
                    h4 = androidx.compose.material.Typography().h4.copy(color = Color.White),
                    h5 = androidx.compose.material.Typography().h5.copy(color = Color.White),
                    body1 = androidx.compose.material.Typography().body1.copy(color = Color(0xFFE2E8F0)),
                    body2 = androidx.compose.material.Typography().body2.copy(color = Color(0xFFE2E8F0))
                )
            ) {
                QRApp(
                    viewModel = viewModel,
                    onScan = { startCameraScan() },
                    onPickImage = { pickImage() },
                    onBackup = { startExport() },
                    onRestore = { startImport() },
                    onShareWalletQr = { cards -> shareWalletQr(cards) }
                )
            }
        }
    }

    private fun shareWalletQr(cards: List<Card>) {
        if (cards.isEmpty()) return

        val payload = SharedWalletPayload(
            cards = cards.map { SharedWalletCard(title = it.title ?: "My Card", code = it.code ?: "", format = it.format) }
        )
        val qrPayload = "walletshare:v1:" + Gson().toJson(payload)
        val bitmap = com.example.qrwallet.ui.generateQRCodeBitmap(qrPayload, 1200) ?: return

        val walletDir = File(cacheDir, "wallet_share").apply { mkdirs() }
        val imageFile = File(walletDir, "wallet-share-${System.currentTimeMillis()}.png")
        FileOutputStream(imageFile).use { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) }

        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", imageFile)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Wallet cards to import")
            putExtra(Intent.EXTRA_TEXT, "Shared wallet cards")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(shareIntent, "Share wallet QR"))
    }

    private fun startExport() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, "qrwallet_backup.zip")
        }
        startActivityForResult(intent, EXPORT_REQUEST)
    }

    private fun startImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, IMPORT_REQUEST)
    }

    private fun startCameraScan() {
        // Ensure camera permission
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
            return
        }
        val intent = Intent(this, ScannerActivity::class.java)
        startActivityForResult(intent, SCAN_REQUEST)
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        startActivityForResult(intent, PICK_IMAGE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE && resultCode == Activity.RESULT_OK) {
            val uri: Uri? = data?.data
            uri?.let { handleImageUri(it) }
            return
        }

        if (requestCode == SCAN_REQUEST && resultCode == Activity.RESULT_OK) {
            val text = data?.getStringExtra(ScannerActivity.EXTRA_SCAN_RESULT)
            val format = data?.getStringExtra(ScannerActivity.EXTRA_SCAN_FORMAT)
            text?.trim()?.takeIf { it.isNotEmpty() }?.let { handleScannedText(it, format) }
        }

        if (requestCode == EXPORT_REQUEST && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            // write zip
            try {
                contentResolver.openOutputStream(uri)?.use { os ->
                    val cards = viewModel.allCards.value
                    BackupManager.writeBackup(cards, os)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (requestCode == IMPORT_REQUEST && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.openInputStream(uri)?.use { ins ->
                    val (backupCards, images) = BackupManager.readBackup(ins)
                    // save images and insert cards
                    for (b in backupCards) {
                        var path: String? = null
                        if (b.imageFilename != null && images.containsKey(b.imageFilename)) {
                            val bytes = images[b.imageFilename]!!
                            val file = File(filesDir, b.imageFilename)
                            file.outputStream().use { it.write(bytes) }
                            path = file.absolutePath
                        }
                        CoroutineScope(Dispatchers.IO).launch {
                            viewModel.insert(Card(title = b.title, code = b.code, imagePath = path))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCameraScan()
            }
        }
    }

    private fun handleImageUri(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bitmap = contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input)
                } ?: run {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Could not read that image. Please try another photo.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val decoded = decodeScannedValue(bitmap)
                if (decoded == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "No QR code found in that photo. Try a clear image of the QR itself.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    handleScannedText(decoded.first, decoded.second)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Could not process the selected image.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handleScannedText(text: String, detectedFormat: String? = null) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val format = detectBarcodeFormat(trimmed, detectedFormat)
        val walletPayloadText = when {
            trimmed.startsWith(WALLET_SHARE_PREFIX) -> trimmed.removePrefix(WALLET_SHARE_PREFIX)
            trimmed.startsWith("{") || trimmed.startsWith("[") -> trimmed
            else -> null
        }

        if (walletPayloadText != null) {
            try {
                val payload = Gson().fromJson(walletPayloadText, SharedWalletPayload::class.java)
                val existingCodes = viewModel.allCards.value.mapNotNull { it.code?.trim() }.map { it.lowercase() }
                val newCards = payload.cards.orEmpty()
                    .filter { !it.code.isNullOrBlank() }
                    .filter { !existingCodes.contains(it.code!!.trim().lowercase()) }
                    .map {
                        val code = it.code!!.trim()
                        // Preserve any explicit symbology name provided in the share payload
                        // (e.g., CODE_128, UPC_A, EAN_13, QR). Fall back to QR when missing.
                        val importedFormat = it.format?.trim()?.uppercase().takeUnless { it.isNullOrBlank() } ?: "QR"
                        Card(
                            title = it.title?.takeIf { title -> title.isNotBlank() } ?: "Shared Card",
                            code = code,
                            format = importedFormat
                        )
                    }

                if (newCards.isEmpty()) {
                    Toast.makeText(this, "You already have all cards in this wallet.", Toast.LENGTH_SHORT).show()
                    return
                }

                viewModel.queueImportCards(newCards)
                return
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "This QR does not contain a valid wallet export.", Toast.LENGTH_SHORT).show()
                return
            }
        }

        val normalizedExistingCode = trimmed.lowercase()
        val alreadyExists = viewModel.allCards.value.any { card ->
            card.code?.trim()?.lowercase() == normalizedExistingCode
        }

        if (alreadyExists) {
            viewModel.discardQueuedCard()
            Toast.makeText(this, "You already have this card in your wallet.", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.queueNewCard(trimmed, defaultTitle = "Scanned Card", format = format)
    }

    private fun detectBarcodeFormat(value: String, detectedFormat: String? = null): String {
        val clean = value.trim()
        if (clean.isEmpty()) return "QR"

        // If scanner provided an exact format, preserve it (e.g., CODE_128, UPC_A, EAN_13)
        if (!detectedFormat.isNullOrBlank()) {
            return detectedFormat.uppercase()
        }

        // Fallback: guess QR vs barcode based on content
        return "QR"
    }

    private fun decodeScannedValue(bitmap: Bitmap): Pair<String, String>? {
        val hints = mapOf(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.POSSIBLE_FORMATS to listOf(
                BarcodeFormat.QR_CODE,
                BarcodeFormat.CODE_128,
                BarcodeFormat.CODE_39,
                BarcodeFormat.CODE_93,
                BarcodeFormat.CODABAR,
                BarcodeFormat.EAN_13,
                BarcodeFormat.EAN_8,
                BarcodeFormat.UPC_A,
                BarcodeFormat.UPC_E,
                BarcodeFormat.ITF,
                BarcodeFormat.PDF_417,
                BarcodeFormat.DATA_MATRIX
            ),
            DecodeHintType.CHARACTER_SET to "UTF-8"
        )

        val orientations = listOf(
            bitmap,
            rotateBitmap(bitmap, 90f),
            rotateBitmap(bitmap, 180f),
            rotateBitmap(bitmap, 270f)
        )

        val candidates = mutableListOf<Bitmap>()
        for (image in orientations) {
            candidates += image

            val scaleFactors = listOf(0.5f, 1f, 1.5f, 2f, 3f)
            for (scale in scaleFactors) {
                val scaledW = maxOf(1, (image.width * scale).toInt())
                val scaledH = maxOf(1, (image.height * scale).toInt())
                candidates += Bitmap.createScaledBitmap(image, scaledW, scaledH, false)
            }

            val cropRatios = listOf(0.25f, 0.4f, 0.6f, 0.8f)
            for (ratio in cropRatios) {
                val cropW = maxOf(1, (image.width * ratio).toInt())
                val cropH = maxOf(1, (image.height * ratio).toInt())
                val regions = listOf(
                    Pair(0, 0),
                    Pair((image.width - cropW) / 2, (image.height - cropH) / 2),
                    Pair(image.width - cropW, 0),
                    Pair(0, image.height - cropH),
                    Pair(image.width - cropW, image.height - cropH)
                )

                for ((x, y) in regions) {
                    val clippedX = x.coerceIn(0, (image.width - cropW).coerceAtLeast(0))
                    val clippedY = y.coerceIn(0, (image.height - cropH).coerceAtLeast(0))
                    if (clippedX + cropW <= image.width && clippedY + cropH <= image.height) {
                        candidates += Bitmap.createBitmap(image, clippedX, clippedY, cropW, cropH)
                    }
                }
            }
        }

        for (candidate in candidates) {
            val result = tryDecodeBitmap(candidate, hints)
            if (result != null) return result.text to (result.barcodeFormat?.name ?: "QR")
        }

        return null
    }

    private fun decodeQrFromBitmap(bitmap: Bitmap): String? {
        return decodeScannedValue(bitmap)?.first
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun tryDecodeBitmap(bitmap: Bitmap, hints: Map<DecodeHintType, Any>): com.google.zxing.Result? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            if (width <= 0 || height <= 0) return null

            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val reader = MultiFormatReader()
            reader.decode(binaryBitmap, hints)
        } catch (_: Exception) {
            null
        }
    }

    private fun saveBitmapToInternalStorage(bitmap: android.graphics.Bitmap): String? {
        return try {
            val filename = "qr_" + UUID.randomUUID().toString() + ".png"
            val file = File(filesDir, filename)
            val out = FileOutputStream(file)
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, out)
            out.flush()
            out.close()
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    companion object {
        private const val PICK_IMAGE = 201
        private const val SCAN_REQUEST = 202
        private const val CAMERA_PERMISSION_REQUEST = 301
        private const val EXPORT_REQUEST = 401
        private const val IMPORT_REQUEST = 402
        private const val WALLET_SHARE_PREFIX = "walletshare:v1:"
    }
}

@Keep
private data class SharedWalletPayload(
    val cards: List<SharedWalletCard>? = null
)

@Keep
private data class SharedWalletCard(
    val title: String? = null,
    val code: String? = null,
    val format: String? = null
)
