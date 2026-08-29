package com.example.qrwallet.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.qrwallet.data.Card
import com.example.qrwallet.viewmodel.CardViewModel
import com.google.gson.Gson
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.QRCodeWriter

@Composable
fun QRApp(
    viewModel: CardViewModel,
    onScan: () -> Unit,
    onPickImage: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onShareWalletQr: (List<Card>) -> Unit
) {
    val cards by viewModel.allCards.collectAsState(initial = emptyList())
    val selected by viewModel.selected.collectAsState()
    val pendingCard by viewModel.newCardDraft.collectAsState()
    val pendingImportCards by viewModel.pendingImportCards.collectAsState()
    var showWalletShare by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                backgroundColor = Color(0xFF101828),
                title = { Text("My Wallet", color = Color.White) },
                actions = {
                    TextButton(onClick = { showWalletShare = cards.isNotEmpty() }) { Text("Share wallet", color = Color.White) }
                    TextButton(onClick = onBackup) { Text("Backup", color = Color.White) }
                    TextButton(onClick = onRestore) { Text("Restore", color = Color.White) }
                }
            )
        },
        floatingActionButton = {
            Row(modifier = Modifier.padding(end = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FloatingActionButton(
                    backgroundColor = Color(0xFF2E5BFF),
                    contentColor = Color.White,
                    onClick = onScan
                ) { Text("Scan card") }
                FloatingActionButton(
                    backgroundColor = Color(0xFF14B8A6),
                    contentColor = Color.White,
                    onClick = onPickImage
                ) { Text("Import QR") }
            }
        },
        backgroundColor = Color(0xFFF3F4F6)
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (cards.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        backgroundColor = Color.White,
                        elevation = 4.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("No cards yet", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Scan a QR code or import a photo to build your wallet.", color = Color.Gray)
                        }
                    }
                }
            } else {
                CardList(
                    cards = cards,
                    onClick = { card -> viewModel.selectCard(card) },
                    onDelete = { card ->
                        viewModel.selectCard(null)
                        viewModel.delete(card)
                    },
                    onReplace = onPickImage
                )
            }

            pendingCard?.let { draft ->
                NewCardDialog(
                    card = draft,
                    onDismiss = { viewModel.discardQueuedCard() },
                    onSave = { title ->
                        viewModel.saveQueuedCard(title)
                    }
                )
            }

            selected?.let { card ->
                CardDetail(
                    card = card,
                    onClose = { viewModel.selectCard(null) },
                    onDelete = { cardToDelete ->
                        viewModel.delete(cardToDelete)
                        viewModel.selectCard(null)
                    },
                    onSaveTitle = { updatedCard -> viewModel.update(updatedCard) },
                    onReplace = onPickImage
                )
            }

            if (showWalletShare && cards.isNotEmpty()) {
                ShareWalletDialog(
                    cards = cards,
                    onDismiss = { showWalletShare = false },
                    onShare = { selectedCards ->
                        showWalletShare = false
                        onShareWalletQr(selectedCards)
                    }
                )
            }

            if (pendingImportCards.isNotEmpty()) {
                ImportWalletDialog(
                    cards = pendingImportCards,
                    onDismiss = { viewModel.dismissImportCards() },
                    onConfirm = { selected ->
                        viewModel.confirmImportCards(selected)
                    }
                )
            }
        }
    }
}

@Composable
fun CardList(cards: List<Card>, onClick: (Card) -> Unit, onDelete: (Card) -> Unit, onReplace: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(cards) { card ->
            CardRow(card = card, onClick = onClick, onDelete = onDelete, onReplace = onReplace)
        }
    }
}

@Composable
fun CardRow(card: Card, onClick: (Card) -> Unit, onDelete: (Card) -> Unit, onReplace: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick(card) },
        shape = MaterialTheme.shapes.medium,
        elevation = 6.dp,
        backgroundColor = Color.White
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(Color(0xFF0F172A), Color(0xFF1E293B))))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (card.imagePath != null) {
                    AsyncImage(
                        model = card.imagePath,
                        contentDescription = "card image",
                        modifier = Modifier.size(72.dp),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    val bmp = generateCardBitmap(card, 128)
                    bmp?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = if (card.format == "BARCODE") "barcode" else "qr code",
                            modifier = Modifier.size(72.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = card.title ?: "My Card",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = card.code?.take(30) ?: "No code",
                        color = Color(0xFFE2E8F0),
                        maxLines = 1
                    )
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Text("⋮", color = Color.White, fontSize = 22.sp)
                    }

                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(onClick = {
                            showMenu = false
                            onReplace()
                        }) {
                            Text("Replace card")
                        }
                        DropdownMenuItem(onClick = {
                            showMenu = false
                            confirmDelete = true
                        }) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete card?") },
            text = { Text("Are you sure you want to remove ${card.title ?: "this card"} from your wallet?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete(card)
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun CardDetail(card: Card, onClose: () -> Unit, onDelete: (Card) -> Unit, onSaveTitle: (Card) -> Unit, onReplace: () -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf(card.title ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onClose) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = MaterialTheme.shapes.medium,
            elevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Box {
                        var showMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showMenu = true }) {
                            Text("⋮", fontSize = 26.sp)
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(onClick = {
                                showMenu = false
                                onReplace()
                            }) {
                                Text("Replace card")
                            }
                            DropdownMenuItem(onClick = {
                                showMenu = false
                                confirmDelete = true
                            }) {
                                Text("Delete")
                            }
                        }
                    }
                }

                if (card.imagePath != null) {
                    AsyncImage(
                        model = card.imagePath,
                        contentDescription = "card image",
                        modifier = Modifier.size(260.dp),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    val bmp = generateCardBitmap(card, 512)
                    bmp?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = if (card.format == "BARCODE") "barcode" else "qr code",
                            modifier = Modifier.size(260.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (editing) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Card title") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { editing = false }) { Text("Cancel") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            onSaveTitle(card.copy(title = title.trim().ifEmpty { "My Card" }))
                            editing = false
                        }) { Text("Save") }
                    }
                } else {
                    Text(text = card.title ?: "My Card", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = card.code ?: "No code available", color = Color.Gray)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Button(onClick = { editing = true }) { Text("Rename") }
                        Button(onClick = onClose) { Text("Close") }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this card?") },
            text = { Text("This action cannot be undone. Remove ${card.title ?: "this card"} from your wallet?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete(card)
                    onClose()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun NewCardDialog(card: Card, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember(card.id) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name this card") },
        text = {
            Column {
                Text("Give this card a name so it feels like a real wallet item.")
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Card name") },
                    placeholder = { Text(card.title ?: "My Card") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(name)
            }) { Text("Save card") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ImportWalletDialog(cards: List<Card>, onDismiss: () -> Unit, onConfirm: (List<Card>) -> Unit) {
    val selected = remember(cards) {
        mutableStateMapOf<Int, Boolean>().also { map ->
            cards.indices.forEach { index -> map[index] = true }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import wallet cards") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Choose which cards to add to your wallet.")
                Spacer(modifier = Modifier.height(12.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(cards.indices.toList()) { index ->
                        val card = cards[index]
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selected[index] == true,
                                onCheckedChange = { checked -> selected[index] = checked }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(card.title ?: "My Card")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = cards.indices.any { index -> selected[index] == true },
                onClick = {
                    onConfirm(cards.filterIndexed { index, _ -> selected[index] == true })
                }
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ShareWalletDialog(cards: List<Card>, onDismiss: () -> Unit, onShare: (List<Card>) -> Unit) {
    val selected = remember(cards) {
        mutableStateMapOf<Int, Boolean>().also { map ->
            cards.indices.forEach { index -> map[index] = true }
        }
    }

    val selectedCards = cards.filterIndexed { index, _ -> selected[index] == true }
    val payload = WalletSharePayload(cards = selectedCards.map {
        WalletShareCard(
            title = it.title ?: "My Card",
            code = it.code ?: "",
            format = it.format
        )
    })
    val qrJson = Gson().toJson(payload)
    val qrPayload = "walletshare:v1:" + qrJson
    val bitmap = generateQRCodeBitmap(qrPayload, 800)

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                elevation = 10.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Share wallet", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Pick the cards to include, then save or send the QR.", color = Color.Gray)
                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                        items(cards.indices.toList()) { index ->
                            val card = cards[index]
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selected[index] == true,
                                    onCheckedChange = { checked -> selected[index] = checked }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(card.title ?: "My Card")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (bitmap != null && selectedCards.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .background(Color.White, shape = MaterialTheme.shapes.medium)
                                .padding(12.dp)
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "wallet sharing QR",
                                modifier = Modifier.size(260.dp)
                            )
                        }
                    } else {
                        Text("Select at least one card to generate the QR.", color = Color.Gray)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) { Text("Close") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            enabled = selectedCards.isNotEmpty(),
                            onClick = { onShare(selectedCards) }
                        ) {
                            Text("Save & share")
                        }
                    }
                }
            }
        }
    }
}

private data class WalletSharePayload(
    val cards: List<WalletShareCard>
)

private data class WalletShareCard(
    val title: String,
    val code: String,
    val format: String? = null
)

fun generateCardBitmap(card: Card, size: Int): android.graphics.Bitmap? {
    return if (card.format == "BARCODE") {
        generateBarcodeBitmap(card.code ?: "", size)
    } else {
        generateQRCodeBitmap(card.code ?: "", size)
    }
}

fun generateBarcodeBitmap(text: String, size: Int): android.graphics.Bitmap? {
    return try {
        val format = if (text.length == 13 && text.all { it.isDigit() }) BarcodeFormat.EAN_13 else BarcodeFormat.CODE_128
        val writer = MultiFormatWriter()
        val bitMatrix = writer.encode(text, format, size * 2, size / 2)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bmp
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun generateQRCodeBitmap(text: String, size: Int): android.graphics.Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bmp
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
