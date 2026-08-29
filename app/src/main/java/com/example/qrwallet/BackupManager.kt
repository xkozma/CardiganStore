package com.example.qrwallet

import android.content.Context
import com.example.qrwallet.data.Card
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupCard(val title: String?, val code: String?, val imageFilename: String?)

object BackupManager {
    private val gson = Gson()

    fun writeBackup(cards: List<Card>, output: OutputStream) {
        ZipOutputStream(output).use { zip ->
            // prepare cards JSON with image file names
            val backupCards = cards.map { card ->
                val imageFilename = card.imagePath?.let { path ->
                    java.io.File(path).name
                }
                BackupCard(card.title, card.code, imageFilename)
            }
            val json = gson.toJson(backupCards)
            val jsonBytes = json.toByteArray(Charsets.UTF_8)
            zip.putNextEntry(ZipEntry("cards.json"))
            zip.write(jsonBytes)
            zip.closeEntry()

            // include image files
            for (card in cards) {
                val imagePath = card.imagePath ?: continue
                val file = java.io.File(imagePath)
                if (!file.exists()) continue
                val entryName = "images/${file.name}"
                zip.putNextEntry(ZipEntry(entryName))
                file.inputStream().use { fis ->
                    fis.copyTo(zip)
                }
                zip.closeEntry()
            }
        }
    }

    fun readBackup(input: InputStream): Pair<List<BackupCard>, Map<String, ByteArray>> {
        val images = mutableMapOf<String, ByteArray>()
        var cardsJson: String? = null
        ZipInputStream(input).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name == "cards.json") {
                    val bytes = zis.readBytes()
                    cardsJson = bytes.toString(Charsets.UTF_8)
                } else if (name.startsWith("images/")) {
                    val filename = name.substringAfter("images/")
                    val bytes = zis.readBytes()
                    images[filename] = bytes
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val type = object : TypeToken<List<BackupCard>>() {}.type
        val cards: List<BackupCard> = if (cardsJson != null) gson.fromJson(cardsJson, type) else emptyList()
        return Pair(cards, images)
    }
}
