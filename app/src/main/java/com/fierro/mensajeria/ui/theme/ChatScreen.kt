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
import coil.compose.AsyncImage
import com.fierro.mensajeria.data.FirebaseMessage
import com.fierro.mensajeria.data.MessageViewModel
import com.fierro.mensajeria.data.User
import java.text.SimpleDateFormat
import java.util.*

// NUEVA PALETA DE COLORES "AURA" (Sincronizada con MainActivity)
val ChatBackground = Color(0xFF0D0B1F) // Púrpura muy profundo
val SentBubbleColor = Color(0xFF1C1A2E) // Púrpura oscuro
val ReceivedBubbleColor = Color(0xFF2D2A4A) // Púrpura medio para contraste
val AccentColorAura = Color(0xFF8A2BE2) // Violeta eléctrico
val InputBarColor = Color(0xFF1C1A2E).copy(alpha = 0.8f)

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
    val ownUser by viewModel.ownUser.collectAsState()
    
    var textState by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showParticipantsDialog by remember { mutableStateOf(false) }

    BackHandler {
        viewModel.deselectUser()
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            viewModel.sendMessage("📷 FOTO_MSG")
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

    Scaffold(
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
                                .background(SentBubbleColor),
                            contentAlignment = Alignment.Center
                        ) {
                            val profilePic = if (selectedGroup != null) null else selectedUser?.profilePicUrl
                            if (profilePic != null) {
                                AsyncImage(
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
                                color = AccentColorAura.copy(alpha = 0.7f)
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
                            containerColor = SentBubbleColor
                        ) {
                            DropdownMenuItem(
                                text = { Text("Vaciar chat", color = Color.White) },
                                onClick = {
                                    viewModel.clearChat()
                                    showMenu = false
                                    Toast.makeText(context, "Chat vaciado", Toast.LENGTH_SHORT).show()
                                },
                                leadingIcon = { Icon(Icons.Default.DeleteSweep, null, tint = Color.White) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ChatBackground,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        containerColor = ChatBackground
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize().background(
            Brush.verticalGradient(listOf(ChatBackground, Color(0xFF1A1635)))
        )) {
            Column(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages) { msg ->
                        ChatBubble(message = msg, myId = viewModel.myId)
                    }
                }

                Surface(
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(8.dp)
                            .navigationBarsPadding()
                            .imePadding(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            shape = RoundedCornerShape(24.dp),
                            color = InputBarColor,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
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
                                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                                        cursorBrush = SolidColor(AccentColorAura),
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
                            containerColor = AccentColorAura,
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
    }

    if (showParticipantsDialog && selectedGroup != null) {
        AlertDialog(
            onDismissRequest = { showParticipantsDialog = false },
            containerColor = SentBubbleColor,
            title = { Text("Integrantes", color = Color.White) },
            text = {
                Column {
                    selectedGroup?.members?.forEach { memberId ->
                        val memberName = if (memberId == viewModel.myId) "Tú" 
                                        else users.find { it.uid == memberId }?.displayName ?: "Usuario"
                        Text("- $memberName", modifier = Modifier.padding(vertical = 4.dp), color = Color.White)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showParticipantsDialog = false }) {
                    Text("Cerrar", color = AccentColorAura)
                }
            }
        )
    }
}

@Composable
fun ChatBubble(message: FirebaseMessage, myId: String) {
    val isMe = message.senderId == myId
    val alignment = if (isMe) Alignment.End else Alignment.Start
    val bubbleColor = if (isMe) AccentColorAura.copy(alpha = 0.2f) else SentBubbleColor
    val borderColor = if (isMe) AccentColorAura.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.05f)
    
    val time = remember(message.timestamp) {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        sdf.format(Date(message.timestamp))
    }
    
    val shape = if (isMe) {
        RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
    }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Surface(
            color = bubbleColor, 
            shape = shape,
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                when (message.content) {
                    "📷 FOTO_MSG" -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.PhotoCamera, null, Modifier.size(80.dp), tint = Color.White.copy(alpha = 0.5f))
                            Text("Foto enviada", color = Color.White, fontSize = 12.sp)
                        }
                    }
                    "🎤 AUDIO_MSG" -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            repeat(10) { Box(Modifier.width(2.dp).height((10..25).random().dp).background(Color.White.copy(alpha = 0.3f))) ; Spacer(Modifier.width(2.dp)) }
                        }
                    }
                    else -> { Text(text = message.content, color = Color.White, fontSize = 15.sp) }
                }
                Text(text = time, color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp, modifier = Modifier.align(Alignment.End).padding(top = 4.dp))
            }
        }
    }
}
