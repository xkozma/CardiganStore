package com.example.qrwallet.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    var searchQuery by remember { mutableStateOf("") }
    val filteredCards = remember(cards, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) cards else cards.filter { card ->
            (card.title ?: "").lowercase().contains(q) || (card.code ?: "").lowercase().contains(q)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                backgroundColor = Color(0xFF0B1020),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "My Wallet",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 22.sp
                        )
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                        modifier = Modifier.defaultMinSize(minWidth = 96.dp, minHeight = 38.dp)
                    ) {
                        TextButton(
                            onClick = { showWalletShare = filteredCards.isNotEmpty() },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
                        ) {
                            Text("Share", color = Color.White, fontWeight = FontWeight.Medium)
                        }
                    }
                },
                elevation = 0.dp
            )
        },
        backgroundColor = Color(0xFF0B1020)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0B1020),
                            Color(0xFF111C2F),
                            Color(0xFFF3F6FB)
                        )
                    )
                )
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 96.dp)
            ) {
                if (cards.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = Color.White,
                            elevation = 12.dp,
                            modifier = Modifier.fillMaxWidth().padding(24.dp).widthIn(max = 640.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("No cards yet", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Scan a QR code or import a photo to build your wallet.", color = Color(0xFF475569), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .widthIn(max = 680.dp)
                    ) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Stored cards", color = Color(0xFFCBD5E1), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(cards.size.toString(), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF7C9BFF).copy(alpha = 0.18f),
                                modifier = Modifier.padding(2.dp)
                            ) {
                                Text(
                                    text = "Ready",
                                    color = Color(0xFFDBEAFE),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search cards") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            textColor = Color.White,
                            backgroundColor = Color.White.copy(alpha = 0.06f),
                            cursorColor = Color(0xFF8AB4FF),
                            focusedBorderColor = Color(0xFF8AB4FF),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedLabelColor = Color(0xFFDBEAFE),
                            unfocusedLabelColor = Color(0xFFCBD5E1)
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                        if (filteredCards.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No cards match your search", color = Color(0xFFE2E8F0), fontSize = 18.sp)
                            }
                        } else {
                            CardList(
                                cards = filteredCards,
                                onClick = { card -> viewModel.selectCard(card) },
                                onDelete = { card ->
                                    viewModel.selectCard(null)
                                    viewModel.delete(card)
                                },
                                onReplace = onPickImage
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onScan,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF5B6BFF),
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.elevation(
                            defaultElevation = 6.dp,
                            pressedElevation = 0.dp
                        )
                    ) {
                        Text("Scan QR", fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onPickImage,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF1FB7A4),
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.elevation(
                            defaultElevation = 6.dp,
                            pressedElevation = 0.dp
                        )
                    ) {
                        Text("Import file", fontWeight = FontWeight.SemiBold)
                    }
                }
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
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 88.dp)
    ) {
        items(cards, key = { it.id }) { card ->
            CardRow(card = card, onClick = onClick, onDelete = onDelete, onReplace = onReplace)
        }
    }
}

@Composable
fun CardRow(card: Card, onClick: (Card) -> Unit, onDelete: (Card) -> Unit, onReplace: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val palette = getCardPalette(card.title ?: "My Card")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .clickable { onClick(card) },
        shape = RoundedCornerShape(24.dp),
        elevation = 8.dp,
        backgroundColor = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(palette))
                .padding(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha = 0.08f))
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (card.imagePath != null) {
                        AsyncImage(
                            model = card.imagePath,
                            contentDescription = "card image",
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        val bmp = generateCardBitmap(card, 96)
                        bmp?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = if (card.format == "BARCODE") "barcode" else "qr code",
                                modifier = Modifier.size(48.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = card.title ?: "My Card",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = (card.format ?: "QR").uppercase(),
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = card.code?.take(26) ?: "No code",
                        color = Color(0xFFE2E8F0),
                        maxLines = 1,
                        fontSize = 14.sp
                    )
                }

                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.background(Color.White.copy(alpha = 0.08f), shape = CircleShape)) {
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

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 0.dp, vertical = 0.dp)
                    .widthIn(max = 2000.dp),
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFF0F172A),
                elevation = 18.dp
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = card.title ?: "My Card", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Box {
                            var showMenu by remember { mutableStateOf(false) }
                            IconButton(onClick = { showMenu = true }, modifier = Modifier.background(Color.White.copy(alpha = 0.08f), shape = CircleShape)) {
                                Text("⋮", fontSize = 24.sp, color = Color.White)
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

                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color.White,
                        elevation = 10.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White)
                                .padding(horizontal = 18.dp, vertical = 20.dp)
                        ) {
                            if (card.imagePath != null) {
                                AsyncImage(
                                    model = card.imagePath,
                                    contentDescription = "card image",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(300.dp),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                val bmp = generateCardBitmap(card, 768)
                                bmp?.let {
                                    Image(
                                        bitmap = it.asImageBitmap(),
                                        contentDescription = if (card.format == "BARCODE") "barcode" else "qr code",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(300.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (editing) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Card title") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { editing = false }) { Text("Cancel", color = Color(0xFFBFDBFE)) }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    onSaveTitle(card.copy(title = title.trim().ifEmpty { "My Card" }))
                                    editing = false
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = Color(0xFF7C9BFF),
                                    contentColor = Color.White
                                )
                            ) { Text("Save") }
                        }
                    } else {
                        Text(text = card.code ?: "No code available", color = Color(0xFFCBD5E1), modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { editing = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = Color(0xFF1E293B),
                                    contentColor = Color.White
                                )
                            ) { Text("Rename") }
                            Button(
                                onClick = onClose,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = Color(0xFF7C9BFF),
                                    contentColor = Color.White
                                )
                            ) { Text("Close") }
                        }
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
        title = { Text("Add this card") },
        text = {
            Column {
                Text("Give this loyalty card a quick name so it feels right in your wallet.")
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Card name") },
                    placeholder = { Text(card.title ?: "My Card") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name) },
                shape = RoundedCornerShape(12.dp)
            ) { Text("Save card") }
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFF0F172A),
                elevation = 18.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 0.dp, vertical = 0.dp)
                    .widthIn(max = 2000.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Share wallet", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        TextButton(onClick = onDismiss) { Text("Close", color = Color(0xFFBFDBFE)) }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Choose the cards to include in the shared QR.",
                        color = Color(0xFFCBD5E1),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White.copy(alpha = 0.04f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LazyColumn(modifier = Modifier.heightIn(max = 220.dp).padding(8.dp)) {
                            items(cards.indices.toList()) { index ->
                                val card = cards[index]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp, horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = selected[index] == true,
                                        onCheckedChange = { checked -> selected[index] = checked },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = Color(0xFF7C9BFF),
                                            uncheckedColor = Color(0xFF94A3B8),
                                            checkmarkColor = Color.White
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(card.title ?: "My Card", color = Color.White, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    if (bitmap != null && selectedCards.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color.White,
                            elevation = 10.dp,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(Color.White)
                                    .padding(18.dp)
                            ) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "wallet sharing QR",
                                    modifier = Modifier.size(240.dp)
                                )
                            }
                        }
                    } else {
                        Text(
                            "Select at least one card to generate the QR.",
                            color = Color(0xFFCBD5E1),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            enabled = selectedCards.isNotEmpty(),
                            onClick = { onShare(selectedCards) },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = Color(0xFF7C9BFF),
                                contentColor = Color.White,
                                disabledBackgroundColor = Color(0xFF334155),
                                disabledContentColor = Color(0xFF94A3B8)
                            )
                        ) {
                            Text("Save & share")
                        }
                    }
                }
            }
        }
    }
}

private fun getCardPalette(title: String): List<Color> {
    val palettes = listOf(
        listOf(Color(0xFF1D4ED8), Color(0xFF7C3AED)),
        listOf(Color(0xFF0EA5E9), Color(0xFF14B8A6)),
        listOf(Color(0xFFEA580C), Color(0xFFF59E0B)),
        listOf(Color(0xFF22C55E), Color(0xFF059669)),
        listOf(Color(0xFFEC4899), Color(0xFF8B5CF6)),
        listOf(Color(0xFFF97316), Color(0xFFEF4444))
    )
    val hash = title.hashCode()
    val safeHash = if (hash < 0) -hash else hash
    val index = safeHash % palettes.size
    return palettes[index]
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
