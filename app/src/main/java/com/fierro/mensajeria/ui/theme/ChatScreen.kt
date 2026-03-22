package com.fierro.mensajeria.ui.theme

import android.Manifest
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import com.fierro.mensajeria.data.FirebaseMessage
import com.fierro.mensajeria.data.MessageViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: MessageViewModel,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.getMessages().collectAsState(initial = emptyList())
    val selectedUser by viewModel.selectedUser.collectAsState()
    val selectedGroup by viewModel.selectedGroup.collectAsState()
    val users by viewModel.users.collectAsState()
    
    var textState by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showParticipantsDialog by remember { mutableStateOf(false) }
    
    var enlargedImageUrl by remember { mutableStateOf<String?>(null) }

    BackHandler {
        viewModel.deselectUser()
    }

    // Volvemos a la versión estable de miniatura
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            viewModel.sendImageMessage(bitmap)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) {
            cameraLauncher.launch(null)
        } else if (permissions[Manifest.permission.RECORD_AUDIO] == true) {
            viewModel.sendMessage("🎤 AUDIO_MSG")
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val isKeyboardVisible = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp
    LaunchedEffect(isKeyboardVisible) {
        if (isKeyboardVisible && messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { 
                            if (selectedGroup != null) showParticipantsDialog = true 
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            val profilePic = if (selectedGroup != null) null else selectedUser?.profilePicUrl
                            if (profilePic != null) {
                                SubcomposeAsyncImage(
                                    model = profilePic,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    if (selectedGroup != null) Icons.Default.Groups else Icons.Default.Person,
                                    null,
                                    tint = Color.Gray
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = selectedGroup?.name ?: selectedUser?.displayName ?: "Chat",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (selectedGroup != null) "${selectedGroup?.members?.size} miembros" else "En línea",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.deselectUser() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.startCall("VIDEO") }) { 
                        Icon(Icons.Default.VideoCall, contentDescription = "Videollamada") 
                    }
                    IconButton(onClick = { viewModel.startCall("AUDIO") }) { 
                        Icon(Icons.Default.Call, contentDescription = "Llamada") 
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) { 
                            Icon(Icons.Default.MoreVert, contentDescription = "Opciones") 
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            DropdownMenuItem(
                                text = { Text("Vaciar chat", color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    viewModel.clearChat()
                                    showMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.DeleteSweep, null, tint = MaterialTheme.colorScheme.onSurface) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .background(
                    Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface))
                )
                .navigationBarsPadding()
                .imePadding()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages.size) { index ->
                    val msg = messages[index]
                    val prevMsg = if (index > 0) messages[index - 1] else null
                    if (shouldShowDateSeparator(msg.timestamp, prevMsg?.timestamp)) {
                        DateSeparator(msg.timestamp)
                    }
                    ChatBubble(
                        message = msg, 
                        myId = viewModel.myId,
                        onImageClick = { url -> enlargedImageUrl = url }
                    )
                }
            }

            Surface(
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            IconButton(onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA)) }) {
                                Icon(Icons.Default.PhotoCamera, null, tint = Color.Gray)
                            }
                            
                            Box(
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (textState.isEmpty()) {
                                    Text("Mensaje...", color = Color.Gray, fontSize = 16.sp)
                                }
                                BasicTextField(
                                    value = textState,
                                    onValueChange = { textState = it },
                                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    maxLines = 4
                                )
                            }

                            IconButton(onClick = { /* Emoji picker */ }) {
                                Icon(Icons.Default.SentimentSatisfiedAlt, null, tint = Color.Gray)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    FloatingActionButton(
                        onClick = {
                            if (textState.isNotBlank()) {
                                viewModel.sendMessage(textState)
                                textState = ""
                            } else {
                                permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                            }
                        },
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        modifier = Modifier.size(48.dp),
                        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
                    ) {
                        Icon(
                            if (textState.isNotBlank()) Icons.AutoMirrored.Filled.Send else Icons.Default.Mic,
                            null,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }

    if (enlargedImageUrl != null) {
        Dialog(
            onDismissRequest = { enlargedImageUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { enlargedImageUrl = null },
                contentAlignment = Alignment.Center
            ) {
                SubcomposeAsyncImage(
                    model = enlargedImageUrl,
                    contentDescription = "Imagen ampliada",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { enlargedImageUrl = null },
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                ) {
                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }
    }

    if (showParticipantsDialog && selectedGroup != null) {
        AlertDialog(
            onDismissRequest = { showParticipantsDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Integrantes", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column {
                    selectedGroup?.members?.forEach { memberId ->
                        val memberName = if (memberId == viewModel.myId) "Tú" 
                                        else users.find { it.uid == memberId }?.displayName ?: "Usuario"
                        Text("- $memberName", modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showParticipantsDialog = false }) {
                    Text("Cerrar", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }
}

fun shouldShowDateSeparator(currentTimestamp: Long, previousTimestamp: Long?): Boolean {
    if (previousTimestamp == null) return true
    val cal1 = Calendar.getInstance().apply { timeInMillis = currentTimestamp }
    val cal2 = Calendar.getInstance().apply { timeInMillis = previousTimestamp }
    return cal1.get(Calendar.YEAR) != cal2.get(Calendar.YEAR) ||
           cal1.get(Calendar.DAY_OF_YEAR) != cal2.get(Calendar.DAY_OF_YEAR)
}

@Composable
fun DateSeparator(timestamp: Long) {
    val dateText = remember(timestamp) {
        val calendar = Calendar.getInstance()
        val now = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        
        when {
            calendar.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            calendar.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) -> "Hoy"
            
            calendar.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            calendar.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) - 1 -> "Ayer"
            
            else -> SimpleDateFormat("d 'de' MMMM", Locale("es", "ES")).format(Date(timestamp))
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = dateText,
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun ChatBubble(
    message: FirebaseMessage, 
    myId: String,
    onImageClick: (String) -> Unit = {}
) {
    val isMe = message.senderId == myId
    val alignment = if (isMe) Alignment.End else Alignment.Start
    val bubbleColor = if (isMe) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
    val borderColor = if (isMe) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    
    val time = remember(message.timestamp) {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        sdf.format(Date(message.timestamp))
    }
    
    val shape = if (isMe) {
        RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalAlignment = alignment) {
        Surface(
            color = bubbleColor, 
            shape = shape,
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (message.content.startsWith("📷 FOTO_MSG:")) {
                    val imageUrl = message.content.removePrefix("📷 FOTO_MSG:")
                    
                    Box(modifier = Modifier
                        .width(240.dp)
                        .height(320.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Gray.copy(alpha = 0.1f))
                        .clickable { onImageClick(imageUrl) }
                    ) {
                        SubcomposeAsyncImage(
                            model = imageUrl,
                            contentDescription = "Imagen de chat",
                            loading = {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                }
                            },
                            error = {
                                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Icon(Icons.Default.BrokenImage, null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                                    Text("Error al cargar", color = Color.Gray, fontSize = 10.sp)
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                } else if (message.content == "🎤 AUDIO_MSG") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(8.dp))
                        repeat(10) { Box(Modifier.width(2.dp).height((10..25).random().dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))) ; Spacer(Modifier.width(2.dp)) }
                    }
                } else {
                    Text(text = message.content, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                }
                Text(text = time, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), fontSize = 10.sp, modifier = Modifier.align(Alignment.End).padding(top = 4.dp))
            }
        }
    }
}
