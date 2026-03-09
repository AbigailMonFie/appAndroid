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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fierro.mensajeria.data.FirebaseMessage
import com.fierro.mensajeria.data.MessageViewModel
import com.fierro.mensajeria.data.User
import java.text.SimpleDateFormat
import java.util.*

// Colores basados en la imagen
val ChatBackground = Color(0xFF12162B)
val SentBubbleColor = Color(0xFF1E2445)
val ReceivedBubbleColor = Color(0xFF6A3D9A)
val InputBarColor = Color(0xFF1E2445)

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

    // BOTÓN DE ATRÁS DEL TELÉFONO
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
                                .background(Color.White),
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
                                text = (selectedGroup?.name ?: selectedUser?.displayName ?: "Chat").uppercase(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (selectedGroup != null) {
                                Text("Toca para ver integrantes", fontSize = 11.sp, color = Color.LightGray)
                            }
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
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Vaciar chat") },
                                onClick = {
                                    viewModel.clearChat()
                                    showMenu = false
                                    Toast.makeText(context, "Chat vaciado", Toast.LENGTH_SHORT).show()
                                },
                                leadingIcon = { Icon(Icons.Default.DeleteSweep, null) }
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
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Surface(color = Color(0xFF1E2445).copy(alpha = 0.5f), shape = RoundedCornerShape(16.dp)) {
                    Text("Hoy", color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                }
            }

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
                color = ChatBackground,
                modifier = Modifier.fillMaxWidth()
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
                        color = InputBarColor
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            IconButton(onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA)) }) {
                                Icon(Icons.Default.PhotoCamera, null, tint = Color.LightGray)
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
                                    cursorBrush = SolidColor(Color.White),
                                    maxLines = 4
                                )
                            }

                            IconButton(onClick = { focusRequester.requestFocus() }) {
                                Icon(Icons.Default.SentimentSatisfiedAlt, null, tint = Color.LightGray)
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
                        containerColor = Color(0xFF6A3D9A),
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

    // DIÁLOGO DE INTEGRANTES DEL GRUPO
    if (showParticipantsDialog && selectedGroup != null) {
        AlertDialog(
            onDismissRequest = { showParticipantsDialog = false },
            title = { Text("Integrantes del grupo") },
            text = {
                Column {
                    selectedGroup?.members?.forEach { memberId ->
                        val memberName = if (memberId == viewModel.myId) "Tú" 
                                        else users.find { it.uid == memberId }?.displayName ?: "Usuario desconocido"
                        Text("- $memberName", modifier = Modifier.padding(vertical = 4.dp), color = Color.Black)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showParticipantsDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }
}

@Composable
fun ChatBubble(message: FirebaseMessage, myId: String) {
    val isMe = message.senderId == myId
    val alignment = if (isMe) Alignment.End else Alignment.Start
    val bubbleColor = if (isMe) SentBubbleColor else ReceivedBubbleColor
    val time = remember(message.timestamp) {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        sdf.format(Date(message.timestamp))
    }
    val shape = if (isMe) {
        RoundedCornerShape(16.dp, 16.dp, 0.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 0.dp)
    }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Surface(color = bubbleColor, shape = shape, tonalElevation = 2.dp) {
            Column(modifier = Modifier.padding(12.dp)) {
                when (message.content) {
                    "📷 FOTO_MSG" -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.PhotoCamera, null, Modifier.size(80.dp), tint = Color.White.copy(alpha = 0.7f))
                            Text("Foto", color = Color.White, fontSize = 12.sp)
                        }
                    }
                    "🎤 AUDIO_MSG" -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            repeat(10) { Box(Modifier.width(2.dp).height((10..25).random().dp).background(Color.White.copy(alpha = 0.5f))) ; Spacer(Modifier.width(2.dp)) }
                        }
                    }
                    else -> { Text(text = message.content, color = Color.White, fontSize = 15.sp) }
                }
                Text(text = time, color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, modifier = Modifier.align(Alignment.End))
            }
        }
    }
}
