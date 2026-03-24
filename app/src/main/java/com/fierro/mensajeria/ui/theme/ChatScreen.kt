package com.fierro.mensajeria.ui.theme

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import com.fierro.mensajeria.data.AudioPlayer
import com.fierro.mensajeria.data.AudioRecorder
import com.fierro.mensajeria.data.FirebaseMessage
import com.fierro.mensajeria.data.MessageViewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.delay
import java.io.File
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
    val blockedUserIds by viewModel.blockedUserIds.collectAsState()
    
    var textState by remember { mutableStateOf("") }
    var localSearchText by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showParticipantsDialog by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var enlargedImageUrl by remember { mutableStateOf<String?>(null) }
    var messageToOptions by remember { mutableStateOf<FirebaseMessage?>(null) }
    var selectedTimer by remember { mutableStateOf<Int?>(null) }

    // Group management states
    var showGroupOptions by remember { mutableStateOf(false) }
    var showChangeGroupNameDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }

    val isBlocked = selectedUser?.let { blockedUserIds.contains(it.uid) } ?: false

    // Obtener el usuario seleccionado actualizado de la lista global de usuarios
    val currentUserStatus = users.find { it.uid == selectedUser?.uid }

    // Indicador de "Escribiendo..."
    val typingUser = remember(users, selectedUser) {
        users.find { it.typingTo == viewModel.myId && it.uid == selectedUser?.uid }
    }

    // Audio states
    val recorder = remember { AudioRecorder(context) }
    val audioPlayer = remember { AudioPlayer(context) }
    var isRecording by remember { mutableStateOf(false) }
    var audioFile by remember { mutableStateOf<File?>(null) }
    val amplitudes = remember { mutableStateListOf<Float>() }
    var currentlyPlayingUrl by remember { mutableStateOf<String?>(null) }

    val filteredMessages = remember(messages, localSearchText, isSearching) {
        if (isSearching && localSearchText.isNotBlank()) {
            messages.filter { it.content.contains(localSearchText, ignoreCase = true) }
        } else {
            messages
        }
    }

    // URI temporal para la imagen de alta calidad
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempPhotoUri != null) {
            viewModel.sendImageMessageFromUri(tempPhotoUri!!, selectedTimer)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.sendImageMessageFromUri(uri, selectedTimer)
    }

    val groupImagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && selectedGroup != null) {
            viewModel.updateGroupPhoto(selectedGroup!!.id, uri)
        }
    }

    val recordPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            audioFile = File(context.cacheDir, "audio_${System.currentTimeMillis()}.mp4")
            recorder.start(audioFile!!)
            isRecording = true
        } else {
            Toast.makeText(context, "Permiso de micrófono denegado", Toast.LENGTH_SHORT).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) {
            // Crear un archivo en MediaStore para la foto de alta calidad
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.jpg")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            }
            tempPhotoUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            tempPhotoUri?.let { cameraLauncher.launch(it) }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            try {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { location: Location? ->
                        if (location != null) viewModel.sendLocationMessage(location.latitude, location.longitude, selectedTimer)
                        else {
                            fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc: Location? ->
                                if (lastLoc != null) viewModel.sendLocationMessage(lastLoc.latitude, lastLoc.longitude, selectedTimer)
                            }
                        }
                    }
            } catch (e: SecurityException) { e.printStackTrace() }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recorder.stop()
            audioPlayer.stop()
        }
    }

    BackHandler {
        if (showEmojiPicker) showEmojiPicker = false
        else if (isSearching) {
            isSearching = false
            localSearchText = ""
        }
        else viewModel.deselectUser()
    }

    LaunchedEffect(textState) {
        viewModel.setTyping(if (textState.isNotEmpty()) (selectedUser?.uid ?: selectedGroup?.id) else null)
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isRecording) {
                amplitudes.add(recorder.getMaxAmplitude().toFloat())
                if (amplitudes.size > 30) amplitudes.removeAt(0)
                delay(100)
            }
        } else {
            amplitudes.clear()
        }
    }

    LaunchedEffect(filteredMessages.size) {
        if (filteredMessages.isNotEmpty()) {
            listState.animateScrollToItem(filteredMessages.size - 1)
        }
    }

    val isKeyboardVisible = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp
    LaunchedEffect(isKeyboardVisible) {
        if (isKeyboardVisible) {
            showEmojiPicker = false
            if (filteredMessages.isNotEmpty()) {
                listState.animateScrollToItem(filteredMessages.size - 1)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    if (isSearching) {
                        BasicTextField(
                            value = localSearchText,
                            onValueChange = { localSearchText = it },
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                            textStyle = TextStyle(color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { innerTextField ->
                                if (localSearchText.isEmpty()) Text("Buscar en el chat...", color = Color.Gray)
                                innerTextField()
                            }
                        )
                        LaunchedEffect(Unit) { focusRequester.requestFocus() }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { 
                            if (selectedGroup != null) showGroupOptions = true 
                        }) {
                            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                                val profilePic = if (selectedGroup != null) selectedGroup?.profilePicUrl else selectedUser?.profilePicUrl
                                if (profilePic != null) SubcomposeAsyncImage(model = profilePic, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                else Icon(if (selectedGroup != null) Icons.Default.Groups else Icons.Default.Person, null, tint = Color.Gray)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(text = selectedGroup?.name ?: selectedUser?.displayName ?: "Chat", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                if (selectedGroup == null) {
                                    val statusText = when {
                                        typingUser != null -> "Escribiendo..."
                                        currentUserStatus?.online == true -> "En línea"
                                        else -> ""
                                    }
                                    if (statusText.isNotEmpty()) {
                                        Text(
                                            text = statusText,
                                            fontSize = 11.sp,
                                            color = if (typingUser != null) Color.Green else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "${selectedGroup?.members?.size} miembros",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (isSearching) { isSearching = false; localSearchText = "" } else viewModel.deselectUser() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (!isSearching) {
                        IconButton(onClick = { isSearching = true }) { Icon(Icons.Default.Search, null) }
                        IconButton(onClick = { viewModel.startCall("VIDEO") }) { Icon(Icons.Default.VideoCall, null) }
                        IconButton(onClick = { viewModel.startCall("AUDIO") }) { Icon(Icons.Default.Call, null) }
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, null) }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, containerColor = MaterialTheme.colorScheme.surface) {
                            if (selectedUser != null) {
                                DropdownMenuItem(
                                    text = { Text(if (isBlocked) "Desbloquear" else "Bloquear") },
                                    onClick = { viewModel.toggleBlock(selectedUser!!.uid); showMenu = false },
                                    leadingIcon = { Icon(Icons.Default.Block, null, tint = if (isBlocked) Color.Green else Color.Red) }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Mensajes temporales: ${selectedTimer?.let { "$it seg" } ?: "Off"}") },
                                onClick = { /* Nested menu could go here, for simplicity toggling or show sub-options */ },
                                leadingIcon = { Icon(Icons.Default.Timer, null) },
                                trailingIcon = {
                                    Row {
                                        listOf(null, 10, 60).forEach { time ->
                                            Text(
                                                text = time?.toString() ?: "Off",
                                                modifier = Modifier.padding(horizontal = 4.dp).clickable { selectedTimer = time; showMenu = false },
                                                color = if (selectedTimer == time) MaterialTheme.colorScheme.primary else Color.Gray,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            )
                            DropdownMenuItem(text = { Text("Vaciar chat") }, onClick = { viewModel.clearChat(); showMenu = false }, leadingIcon = { Icon(Icons.Default.DeleteSweep, null) })
                            DropdownMenuItem(text = { Text("Compartir Ubicación") }, onClick = { locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)); showMenu = false }, leadingIcon = { Icon(Icons.Default.LocationOn, null) })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background, titleContentColor = MaterialTheme.colorScheme.onBackground)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding()).background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface))).navigationBarsPadding().imePadding()) {
            LazyColumn(state = listState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(filteredMessages.size) { index ->
                    val msg = filteredMessages[index]
                    val prevMsg = if (index > 0) filteredMessages[index - 1] else null
                    if (shouldShowDateSeparator(msg.timestamp, prevMsg?.timestamp)) DateSeparator(msg.timestamp)
                    ChatBubble(
                        message = msg, 
                        myId = viewModel.myId, 
                        isPlaying = currentlyPlayingUrl == msg.content.removePrefix("🎤 AUDIO_MSG:"),
                        onImageClick = { url -> enlargedImageUrl = url },
                        onLongClick = { messageToOptions = it },
                        onPlayAudio = { url ->
                            if (currentlyPlayingUrl == url) {
                                audioPlayer.stop()
                                currentlyPlayingUrl = null
                            } else {
                                currentlyPlayingUrl = url
                                audioPlayer.play(url) { currentlyPlayingUrl = null }
                            }
                        }
                    )
                }
            }

            if (isBlocked) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "Has bloqueado a este usuario. Desbloquéalo para enviar mensajes.",
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 14.sp
                    )
                }
            } else {
                Surface(color = Color.Transparent, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                                if (isRecording) {
                                    WaveformAnimation(amplitudes = amplitudes)
                                    Spacer(Modifier.weight(1f))
                                    Text("Grabando...", color = Color.Red, fontSize = 14.sp)
                                    IconButton(onClick = {
                                        isRecording = false
                                        recorder.stop()
                                        audioFile?.let { viewModel.sendAudioMessage(Uri.fromFile(it), selectedTimer) }
                                    }) { Icon(Icons.Default.Check, null, tint = Color.Green) }
                                    IconButton(onClick = { isRecording = false; recorder.stop(); audioFile?.delete() }) { Icon(Icons.Default.Close, null, tint = Color.Red) }
                                } else {
                                    IconButton(onClick = { galleryLauncher.launch("image/*") }) { Icon(Icons.Default.Photo, null, tint = Color.Gray) }
                                    IconButton(onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA)) }) { Icon(Icons.Default.PhotoCamera, null, tint = Color.Gray) }
                                    Box(modifier = Modifier.weight(1f).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
                                        if (textState.isEmpty()) Text("Mensaje...", color = Color.Gray, fontSize = 16.sp)
                                        BasicTextField(value = textState, onValueChange = { textState = it }, modifier = Modifier.fillMaxWidth().focusRequester(focusRequester), textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp), cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), maxLines = 4)
                                    }
                                    IconButton(onClick = { showEmojiPicker = !showEmojiPicker }) { Icon(if (showEmojiPicker) Icons.Default.Keyboard else Icons.Default.SentimentSatisfiedAlt, null, tint = if (showEmojiPicker) MaterialTheme.colorScheme.primary else Color.Gray) }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        FloatingActionButton(
                            onClick = {
                                if (textState.isNotBlank()) {
                                    viewModel.sendMessage(textState, selectedTimer)
                                    textState = ""
                                } else if (!isRecording) {
                                    recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    isRecording = false
                                    recorder.stop()
                                    audioFile?.let { viewModel.sendAudioMessage(Uri.fromFile(it), selectedTimer) }
                                }
                            },
                            shape = CircleShape, containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White, modifier = Modifier.size(48.dp), elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
                        ) {
                            Icon(if (textState.isNotBlank()) Icons.AutoMirrored.Filled.Send else if (isRecording) Icons.Default.Stop else Icons.Default.Mic, null, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
            if (showEmojiPicker) EmojiPickerPanel(onEmojiSelect = { textState += it })
        }
    }

    if (messageToOptions != null) {
        MessageOptionsDialog(
            message = messageToOptions!!,
            myId = viewModel.myId,
            onDismiss = { messageToOptions = null },
            onReactionSelected = { reaction ->
                viewModel.addReaction(messageToOptions!!.id, reaction)
                messageToOptions = null
            },
            onDeleteSelected = {
                viewModel.deleteMessage(messageToOptions!!.id)
                messageToOptions = null
            }
        )
    }

    if (enlargedImageUrl != null) {
        Dialog(onDismissRequest = { enlargedImageUrl = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black).clickable { enlargedImageUrl = null }, contentAlignment = Alignment.Center) {
                SubcomposeAsyncImage(model = enlargedImageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                IconButton(onClick = { enlargedImageUrl = null }, modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) { Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
            }
        }
    }

    if (showGroupOptions && selectedGroup != null) {
        AlertDialog(
            onDismissRequest = { showGroupOptions = false },
            title = { Text("Opciones del grupo") },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text("Ver integrantes") },
                        leadingContent = { Icon(Icons.Default.People, null) },
                        modifier = Modifier.clickable { showParticipantsDialog = true; showGroupOptions = false }
                    )
                    ListItem(
                        headlineContent = { Text("Cambiar nombre") },
                        leadingContent = { Icon(Icons.Default.Edit, null) },
                        modifier = Modifier.clickable { 
                            newGroupName = selectedGroup?.name ?: ""
                            showChangeGroupNameDialog = true
                            showGroupOptions = false 
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Cambiar foto de perfil") },
                        leadingContent = { Icon(Icons.Default.PhotoCamera, null) },
                        modifier = Modifier.clickable { 
                            groupImagePickerLauncher.launch("image/*")
                            showGroupOptions = false 
                        }
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showGroupOptions = false }) { Text("Cancelar") } }
        )
    }

    if (showChangeGroupNameDialog && selectedGroup != null) {
        AlertDialog(
            onDismissRequest = { showChangeGroupNameDialog = false },
            title = { Text("Cambiar nombre del grupo") },
            text = {
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    label = { Text("Nuevo nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newGroupName.isNotBlank()) {
                        viewModel.updateGroupName(selectedGroup!!.id, newGroupName)
                        showChangeGroupNameDialog = false
                    }
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { showChangeGroupNameDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showParticipantsDialog && selectedGroup != null) {
        AlertDialog(onDismissRequest = { showParticipantsDialog = false }, containerColor = MaterialTheme.colorScheme.surface, title = { Text("Integrantes") }, text = { Column { selectedGroup?.members?.forEach { memberId -> val name = if (memberId == viewModel.myId) "Tú" else users.find { it.uid == memberId }?.displayName ?: "Usuario"; Text("- $name", modifier = Modifier.padding(vertical = 4.dp)) } } }, confirmButton = { TextButton(onClick = { showParticipantsDialog = false }) { Text("Cerrar") } })
    }
}

@Composable
fun MessageOptionsDialog(
    message: FirebaseMessage,
    myId: String,
    onDismiss: () -> Unit,
    onReactionSelected: (String) -> Unit,
    onDeleteSelected: () -> Unit
) {
    val reactions = listOf("❤️", "😂", "😮", "😢", "😡", "👍")

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
            Column(modifier = Modifier.padding(16.dp).width(IntrinsicSize.Max)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    reactions.forEach { reaction ->
                        Text(
                            text = reaction,
                            fontSize = 28.sp,
                            modifier = Modifier.clickable { onReactionSelected(reaction) }
                        )
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                ListItem(
                    headlineContent = { Text("Eliminar mensaje", color = Color.Red) },
                    leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                    modifier = Modifier.clickable { onDeleteSelected() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}

@Composable
fun WaveformAnimation(amplitudes: List<Float>) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp).height(30.dp)) {
        amplitudes.forEach { amp ->
            val height = (amp / 32767f * 30f).coerceIn(2f, 30f)
            Box(Modifier.width(2.dp).height(height.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.dp)))
            Spacer(Modifier.width(2.dp))
        }
    }
}

@Composable
fun PlayingWaveform(isPlaying: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val heights = List(10) { index ->
        if (isPlaying) {
            infiniteTransition.animateFloat(
                initialValue = 5f,
                targetValue = 25f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 400 + (index * 50), easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "h_$index"
            )
        } else {
            remember { mutableFloatStateOf(10f) }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.height(30.dp).padding(horizontal = 4.dp)
    ) {
        heights.forEach { heightState ->
            Box(
                Modifier
                    .width(2.dp)
                    .height(heightState.value.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(1.dp))
            )
        }
    }
}

@Composable
fun EmojiPickerPanel(onEmojiSelect: (String) -> Unit) {
    val emojis = listOf("😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚", "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🤩", "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣", "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬", "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤔", "🤭", "🤫", "🤥", "😶", "😐", "😑", "😬", "🙄", "😯", "😦", "😧", "😮", "😲", "🥱", "😴", "🤤", "😪", "😵", "🤐", "🥴", "🤢", "🤮", "🤧", "😷", "🤒", "🤕", "🤑", "🤠", "😈", "👿", "👹", "👺", "🤡", "💩", "👻", "💀", "☠️", "👽", "👾", "🤖", "🎃", "😺", "😸", "😹", "😻", "😼", "😽", "🙀", "😿", "😾", "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟")
    Surface(modifier = Modifier.fillMaxWidth().height(250.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
        LazyVerticalGrid(columns = GridCells.Adaptive(48.dp), contentPadding = PaddingValues(8.dp)) {
            items(emojis) { emoji ->
                Box(modifier = Modifier.size(48.dp).clickable { onEmojiSelect(emoji) }, contentAlignment = Alignment.Center) { Text(text = emoji, fontSize = 24.sp) }
            }
        }
    }
}

fun shouldShowDateSeparator(currentTimestamp: Long, previousTimestamp: Long?): Boolean {
    if (previousTimestamp == null) return true
    val cal1 = Calendar.getInstance().apply { timeInMillis = currentTimestamp }
    val cal2 = Calendar.getInstance().apply { timeInMillis = previousTimestamp }
    return cal1.get(Calendar.YEAR) != cal2.get(Calendar.YEAR) || cal1.get(Calendar.DAY_OF_YEAR) != cal2.get(Calendar.DAY_OF_YEAR)
}

@Composable
fun DateSeparator(timestamp: Long) {
    val dateText = remember(timestamp) {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        val now = Calendar.getInstance()
        when {
            calendar.get(Calendar.YEAR) == now.get(Calendar.YEAR) && calendar.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) -> "Hoy"
            calendar.get(Calendar.YEAR) == now.get(Calendar.YEAR) && calendar.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) - 1 -> "Ayer"
            else -> SimpleDateFormat("d 'de' MMMM", Locale("es", "ES")).format(Date(timestamp))
        }
    }
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        Surface(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f), shape = RoundedCornerShape(12.dp)) {
            Text(text = dateText, color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    message: FirebaseMessage, 
    myId: String, 
    isPlaying: Boolean = false,
    onImageClick: (String) -> Unit = {},
    onLongClick: (FirebaseMessage) -> Unit = {},
    onPlayAudio: (String) -> Unit = {}
) {
    val isMe = message.senderId == myId
    val alignment = if (isMe) Alignment.End else Alignment.Start
    val bubbleColor = if (isMe) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
    val borderColor = if (isMe) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    val time = remember(message.timestamp) { SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.timestamp)) }
    val shape = if (isMe) RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
    val context = LocalContext.current
    
    val isDarkMode = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val checkColor = if (isDarkMode) Color.Yellow else Color(0xFF9C27B0) // Amarillo en oscuro, Morado en claro

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalAlignment = alignment) {
        Box(contentAlignment = if (isMe) Alignment.BottomEnd else Alignment.BottomStart) {
            Surface(
                color = bubbleColor, 
                shape = shape, 
                border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                modifier = Modifier.combinedClickable(
                    onClick = { /* normal click */ },
                    onLongClick = { onLongClick(message) }
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (message.expiresAt != null && message.expiresAt != 0L) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                            Icon(Icons.Default.Timer, null, modifier = Modifier.size(12.dp), tint = Color.Gray)
                            Spacer(Modifier.width(4.dp))
                            Text("Mensaje temporal", color = Color.Gray, fontSize = 10.sp)
                        }
                    }
                    when {
                        message.content.startsWith("📷 FOTO_MSG:") -> {
                            val imageUrl = message.content.removePrefix("📷 FOTO_MSG:")
                            Box(modifier = Modifier.width(240.dp).height(320.dp).clip(RoundedCornerShape(12.dp)).background(Color.Gray.copy(alpha = 0.1f)).clickable { onImageClick(imageUrl) }) {
                                SubcomposeAsyncImage(model = imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            }
                        }
                        message.content.startsWith("🎤 AUDIO_MSG:") -> {
                            val audioUrl = message.content.removePrefix("🎤 AUDIO_MSG:")
                            Row(
                                verticalAlignment = Alignment.CenterVertically, 
                                modifier = Modifier.clickable { onPlayAudio(audioUrl) }.padding(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow, 
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                PlayingWaveform(isPlaying = isPlaying)
                            }
                        }
                        message.content.startsWith("📍 LOCATION_MSG:") -> {
                            val coords = message.content.removePrefix("📍 LOCATION_MSG:").split(",")
                            if (coords.size == 2) {
                                Column(modifier = Modifier.clickable {
                                    val gmmIntentUri = Uri.parse("geo:${coords[0]},${coords[1]}?q=${coords[0]},${coords[1]}")
                                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                                    mapIntent.setPackage("com.google.android.apps.maps")
                                    context.startActivity(mapIntent)
                                }) {
                                    Icon(Icons.Default.Map, null, modifier = Modifier.size(100.dp).align(Alignment.CenterHorizontally), tint = MaterialTheme.colorScheme.primary)
                                    Text("Ver ubicación en el mapa", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                                }
                            }
                        }
                        else -> Text(text = message.content, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                    ) {
                        Text(text = time, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), fontSize = 10.sp)
                        if (isMe) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = if (message.read) Icons.Default.DoneAll else Icons.Default.Done,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (message.read) checkColor else Color.Gray
                            )
                        }
                    }
                }
            }
            
            // Mostrar reacciones
            if (message.reactions.isNotEmpty()) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                    modifier = Modifier.offset(y = 10.dp, x = if (isMe) (-10).dp else 10.dp)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
                        message.reactions.values.distinct().take(3).forEach { emoji ->
                            Text(text = emoji, fontSize = 12.sp)
                        }
                        if (message.reactions.size > 1) {
                            Text(text = message.reactions.size.toString(), fontSize = 10.sp, modifier = Modifier.padding(start = 2.dp))
                        }
                    }
                }
            }
        }
    }
}
