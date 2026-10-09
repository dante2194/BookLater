@file:OptIn(ExperimentalMaterial3Api::class)

package com.booklater.app

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage

private val Gold = Color(0xFFF5B400)

// ============================================================ shared pieces

@Composable
fun Cover(url: String, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Filled.MenuBook, null,
                Modifier.align(Alignment.Center),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun RatingLine(b: Book) {
    if (b.rating.isBlank()) return
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Star, null, Modifier.size(16.dp), tint = Gold)
        Text(
            " ${b.rating}" + if (b.ratingCount.isNotBlank()) "  (${b.ratingCount})" else "",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun SaveButton(saved: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
            contentDescription = if (saved) "Remove from saved" else "Save for later",
            tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun BookRow(b: Book, saved: Boolean, onOpen: (String) -> Unit, onToggle: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onOpen(b.asin) },
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(12.dp)) {
            Cover(b.imageUrl, Modifier.width(70.dp).height(105.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    b.title,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (b.author.isNotBlank()) {
                    Text(
                        b.author,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                RatingLine(b)
            }
            SaveButton(saved, onToggle)
        }
    }
}

@Composable
fun Loading() {
    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun Hint(text: String, sub: String = "") {
    Column(
        Modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.MenuBook, null,
            Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(12.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (sub.isNotBlank()) {
            Text(
                sub,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

@Composable
fun ErrorBox(f: Load.Fail, onRetry: () -> Unit, onVerify: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.CloudOff, null,
            Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(12.dp))
        Text(f.message, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRetry) { Text("Try again") }
            if (f.captcha) Button(onClick = onVerify) { Text("Verify with Amazon") }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
    )
}

// ============================================================ search

@Composable
fun SearchScreen(vm: MainVm, onOpen: (String) -> Unit, onVerify: () -> Unit) {
    val state by vm.results.collectAsState()
    val saved by vm.saved.collectAsState()
    val ids = saved.map { it.asin }.toSet()
    val kb = LocalSoftwareKeyboardController.current

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("BookLater", fontWeight = FontWeight.Bold) })
        OutlinedTextField(
            value = vm.query,
            onValueChange = { vm.query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search Amazon Books…") },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (vm.query.isNotEmpty()) {
                    IconButton(onClick = { vm.query = "" }) { Icon(Icons.Filled.Close, "Clear") }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                kb?.hide()
                vm.search()
            })
        )
        when (val s = state) {
            is Load.Idle -> Hint("Search Amazon Books", "Try “Armenian theology”")
            is Load.Loading -> Loading()
            is Load.Fail -> ErrorBox(s, { vm.search() }, onVerify)
            is Load.Ok -> {
                if (s.data.isEmpty()) {
                    Hint("No books found", "Try different words, or check Settings → Amazon store")
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                        items(s.data, key = { it.asin }) { b ->
                            BookRow(b, b.asin in ids, onOpen) { vm.toggle(b) }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================ saved

@Composable
fun SavedScreen(vm: MainVm, onOpen: (String) -> Unit) {
    val saved by vm.saved.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Saved", fontWeight = FontWeight.Bold) })
        if (saved.isEmpty()) {
            Hint("Nothing saved yet", "Tap the bookmark on any book to keep it here")
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(saved, key = { it.asin }) { b ->
                    BookRow(b, true, onOpen) { vm.toggle(b) }
                }
            }
        }
    }
}

// ============================================================ settings

@Composable
fun SettingsScreen(vm: MainVm, onVerify: () -> Unit) {
    val domain by vm.domain.collectAsState()
    val theme by vm.theme.collectAsState()
    var confirm by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Settings", fontWeight = FontWeight.Bold) })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SectionTitle("Amazon store")
            AMAZON_STORES.forEach { (label, host) ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.setDomain(host) }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = host == domain, onClick = { vm.setDomain(host) })
                    Text("$label  ·  $host", style = MaterialTheme.typography.bodyMedium)
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            SectionTitle("Theme")
            Row(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("System", "Light", "Dark").forEachIndexed { i, name ->
                    FilterChip(selected = theme == i, onClick = { vm.setTheme(i) }, label = { Text(name) })
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            SectionTitle("Troubleshooting")
            Text(
                "If pages fail with a human-check message, open Amazon once here, " +
                    "solve the check, then come back and try again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            OutlinedButton(onClick = onVerify, modifier = Modifier.padding(16.dp)) {
                Text("Verify with Amazon")
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            SectionTitle("Data")
            OutlinedButton(
                onClick = { confirm = true },
                modifier = Modifier.padding(horizontal = 16.dp)
            ) { Text("Clear all saved books") }

            Text(
                "BookLater reads public Amazon pages. It isn't affiliated with Amazon.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Clear saved books?") },
            text = { Text("This removes every book from your Saved list.") },
            confirmButton = {
                TextButton(onClick = { vm.clearSaved(); confirm = false }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
        )
    }
}

// ============================================================ detail pieces

@Composable
fun DetailHeader(b: Book, saved: Boolean, onToggle: () -> Unit) {
    Row(Modifier.padding(16.dp)) {
        Cover(b.imageUrl, Modifier.width(120.dp).height(180.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(b.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (b.author.isNotBlank()) {
                Text(
                    b.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            RatingLine(b)
            Spacer(Modifier.height(14.dp))
            if (saved) {
                FilledTonalButton(onClick = onToggle) {
                    Icon(Icons.Filled.Bookmark, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Saved")
                }
            } else {
                Button(onClick = onToggle) {
                    Icon(Icons.Outlined.BookmarkBorder, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save for later")
                }
            }
        }
    }
}

@Composable
fun FactsCard(facts: List<Fact>) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(14.dp)) {
            facts.forEachIndexed { i, f ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Row {
                    Text(
                        f.label,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(0.4f)
                    )
                    Text(
                        f.value,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(0.6f)
                    )
                }
            }
        }
    }
}

@Composable
fun TextSectionCard(body: String) {
    var open by remember(body) { mutableStateOf(false) }
    val long = body.length > 320 || body.count { it == '\n' } > 6
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(
                body,
                maxLines = if (open || !long) Int.MAX_VALUE else 8,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
            if (long) {
                TextButton(onClick = { open = !open }) { Text(if (open) "Show less" else "Read more") }
            }
        }
    }
}

@Composable
fun RatingSummary(b: Book, hist: List<StarShare>, customersSay: String) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(14.dp)) {
            if (b.rating.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, null, Modifier.size(22.dp), tint = Gold)
                    Text(
                        " ${b.rating} out of 5",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (b.ratingCount.isNotBlank()) {
                    Text(
                        b.ratingCount,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            hist.forEach { h ->
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${h.stars} star", Modifier.width(52.dp), style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(
                        progress = h.percent / 100f,
                        modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp))
                    )
                    Text(
                        "${h.percent}%",
                        Modifier.width(44.dp),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (customersSay.isNotBlank()) {
                Text(
                    "Customers say",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    customersSay,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun ReviewCard(r: Review) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    r.author.ifBlank { "Amazon customer" },
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                if (r.stars.isNotBlank()) {
                    Icon(Icons.Filled.Star, null, Modifier.size(14.dp), tint = Gold)
                    Text(" ${r.stars}", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (r.date.isNotBlank()) {
                Text(
                    r.date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (r.title.isNotBlank()) {
                Text(
                    r.title,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Text(
                r.body,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/** One recommendation row from Amazon — books stacked vertically, one below another. */
@Composable
fun GroupBlock(g: BookGroup, ids: Set<String>, onOpen: (String) -> Unit, onToggle: (Book) -> Unit) {
    var all by remember(g.title) { mutableStateOf(false) }
    val shown = if (all) g.books else g.books.take(5)
    Column {
        SectionTitle(g.title)
        shown.forEach { b -> BookRow(b, b.asin in ids, onOpen) { onToggle(b) } }
        if (g.books.size > 5) {
            TextButton(onClick = { all = !all }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(if (all) "Show fewer" else "Show all ${g.books.size}")
            }
        }
    }
}

// ============================================================ detail screen

@Composable
fun DetailScreen(
    vm: MainVm,
    asin: String,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onVerify: () -> Unit
) {
    LaunchedEffect(asin) { vm.loadDetail(asin) }
    val all by vm.details.collectAsState()
    val saved by vm.saved.collectAsState()
    val domain by vm.domain.collectAsState()
    val uri = LocalUriHandler.current

    val st: Load<BookDetail> = all[asin] ?: Load.Loading
    val d = if (st is Load.Ok) st.data else null
    val book = d?.book ?: vm.book(asin)
    val ids = saved.map { it.asin }.toSet()
    var expanded by remember(asin) { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(book?.title ?: "Book", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (book != null) SaveButton(book.asin in ids) { vm.toggle(book) }
                    IconButton(onClick = { uri.openUri("https://$domain/dp/$asin") }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open on Amazon")
                    }
                }
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            if (book != null) {
                item { DetailHeader(book, book.asin in ids) { vm.toggle(book) } }
            }
            when (st) {
                is Load.Fail -> item { ErrorBox(st, { vm.loadDetail(asin, true) }, onVerify) }
                is Load.Ok -> {
                    val det = st.data

                    if (det.description.isNotBlank()) {
                        item {
                            Column {
                                SectionTitle("Book description")
                                Text(
                                    det.description,
                                    maxLines = if (expanded) Int.MAX_VALUE else 6,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                TextButton(
                                    onClick = { expanded = !expanded },
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) { Text(if (expanded) "Show less" else "Read more") }
                            }
                        }
                    }

                    if (det.facts.isNotEmpty()) {
                        item {
                            Column {
                                SectionTitle("Book details")
                                FactsCard(det.facts)
                            }
                        }
                    }

                    det.sections.forEach { s ->
                        item {
                            Column {
                                SectionTitle(s.title)
                                TextSectionCard(s.body)
                            }
                        }
                    }

                    item {
                        Column {
                            SectionTitle("Customer reviews")
                            RatingSummary(det.book, det.histogram, det.customersSay)
                        }
                    }
                    if (det.reviews.isEmpty()) {
                        item { Note("No reviews were included in the page Amazon sent.") }
                    } else {
                        items(det.reviews) { r -> ReviewCard(r) }
                    }
                    item {
                        OutlinedButton(
                            onClick = { uri.openUri("https://$domain/product-reviews/$asin") },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        ) { Text("See all reviews on Amazon") }
                    }

                    if (det.groups.isEmpty()) {
                        item {
                            Column {
                                SectionTitle("More books")
                                Note("Amazon didn't include recommendation rows in this page.")
                            }
                        }
                    } else {
                        det.groups.forEach { g ->
                            item { GroupBlock(g, ids, onOpen) { vm.toggle(it) } }
                        }
                    }
                }
                else -> item { Loading() }
            }
        }
    }
}

// ============================================================ verify (WebView)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VerifyScreen(vm: MainVm, onBack: () -> Unit) {
    val domain by vm.domain.collectAsState()
    DisposableEffect(Unit) {
        onDispose { CookieManager.getInstance().flush() }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verify with Amazon") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = { TextButton(onClick = onBack) { Text("Done") } }
            )
        }
    ) { pad ->
        AndroidView(
            modifier = Modifier.padding(pad).fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = Amazon.UA
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = WebViewClient()
                    loadUrl("https://$domain/s?k=books&i=stripbooks")
                }
            }
        )
    }
}
