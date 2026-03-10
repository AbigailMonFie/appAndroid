package com.fierro.mensajeria

import android.Manifest
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.fierro.mensajeria.data.*
import com.fierro.mensajeria.ui.theme.ChatScreen
import com.fierro.mensajeria.ui.theme.MensajeriaTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.video.VideoCanvas
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

val DarkBg = Color(0xFF12162B)
val CardBg = Color(0xFF1E2445)
val AccentColor = Color(0xFF6200EE)
val GreenBadge = Color(0xFF4CAF50)

class MainActivity : ComponentActivity() {
    private var rtcEngine: RtcEngine? = null
    private var remoteUidState = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MensajeriaTheme {
                val authViewModel: AuthViewModel = viewModel()
                val chatViewModel: MessageViewModel = viewModel()
                val currentUser by authViewModel.currentUser.collectAsState()
                val selectedUser by chatViewModel.selectedUser.collectAsState()
                val selectedGroup by chatViewModel.selectedGroup.collectAsState()
                val currentCall by chatViewModel.currentCall.collectAsState()

                Box(modifier = Modifier.fillMaxSize()) {
                    if (currentUser == null) {
                        LoginScreen(
                            onLoginSuccess = { chatViewModel.onUserAuthenticated() }, 
                            viewModel = authViewModel
                        )
                    } else if (selectedUser == null && selectedGroup == null) {
                        UserListScreen(viewModel = chatViewModel, authViewModel = authViewModel)
                    } else {
                        ChatScreen(viewModel = chatViewModel, modifier = Modifier.fillMaxSize())
                    }

                    currentCall?.let { call ->
                        CallOverlay(
                            call = call,
                            rtcEngine = rtcEngine,
                            remoteUid = remoteUidState.intValue,
                            onAccept = {
                                setupAgora(call.receiverId)
                                chatViewModel.acceptCall()
                            },
                            onReject = {
                                leaveChannel()
                                chatViewModel.endCall()
                            }
                        )
                    }
                }
            }
        }
    }

    private fun setupAgora(channelName: String) {
        if (rtcEngine != null) return
        try {
            val config = RtcEngineConfig()
            config.mContext = applicationContext
            config.mAppId = AgoraConfig.APP_ID
            config.mEventHandler = object : IRtcEngineEventHandler() {
                override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) { Log.d("AGORA", "Unido") }
                override fun onUserJoined(uid: Int, elapsed: Int) { remoteUidState.intValue = uid }
                override fun onUserOffline(uid: Int, reason: Int) { remoteUidState.intValue = 0 }
            }
            rtcEngine = RtcEngine.create(config)
            rtcEngine?.enableVideo()
            rtcEngine?.joinChannel(AgoraConfig.TOKEN, channelName, "", 0)
        } catch (e: Exception) { Log.e("AGORA", "Error: ${e.message}") }
    }

    private fun leaveChannel() {
        rtcEngine?.leaveChannel()
        RtcEngine.destroy()
        rtcEngine = null
        remoteUidState.intValue = 0
    }
}

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit, viewModel: AuthViewModel) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val authError by viewModel.authError.collectAsState()

    // CONFIGURACIÓN DE GOOGLE SIGN-IN
    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken("913198340441-afbrpdti0apkhqsfhqv56hsb1hv4te0s.apps.googleusercontent.com")
        .requestEmail()
        .build()
    val googleSignInClient = GoogleSignIn.getClient(context, gso)

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            account.idToken?.let { viewModel.signInWithGoogle(it, onLoginSuccess) }
        } catch (e: ApiException) {
            Log.e("AUTH", "Error Google: ${e.message}")
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Vee", style = MaterialTheme.typography.headlineLarge, color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Si no tienes cuenta, se creará una automáticamente", color = Color.Gray, fontSize = 14.sp, textAlign = TextAlign.Center)
            
            if (authError != null) {
                Text(authError!!, color = Color.Red, modifier = Modifier.padding(vertical = 8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre", color = Color.Gray) }, textStyle = TextStyle(color = Color.White), modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email", color = Color.Gray) }, textStyle = TextStyle(color = Color.White), modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password", color = Color.Gray) }, textStyle = TextStyle(color = Color.White), modifier = Modifier.fillMaxWidth())
            
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { viewModel.loginOrRegister(email, password, name, onLoginSuccess) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor)
            ) {
                Text("Entrar / Registrarse", color = Color.White)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("— O —", color = Color.Gray)
            Spacer(modifier = Modifier.height(16.dp))

            // BOTÓN DE GOOGLE
            OutlinedButton(
                onClick = { launcher.launch(googleSignInClient.signInIntent) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Icon(Icons.Default.AccountCircle, null, Modifier.padding(end = 8.dp))
                Text("Continuar con Google")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserListScreen(viewModel: MessageViewModel, authViewModel: AuthViewModel) {
    val users by viewModel.users.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val archivedUserIds by viewModel.archivedUserIds.collectAsState()
    val pinnedUserIds by viewModel.pinnedUserIds.collectAsState()
    val lastMessages by viewModel.lastMessages.collectAsState()
    val unreadCounts by viewModel.unreadCounts.collectAsState()
    val callLogs by viewModel.callLogs.collectAsState()
    val ownUser by viewModel.ownUser.collectAsState()
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var searchText by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var isViewingArchived by remember { mutableStateOf(false) }
    
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showGroupMembersDialog by remember { mutableStateOf<Group?>(null) }
    var userToMenu by remember { mutableStateOf<User?>(null) }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) viewModel.uploadProfilePicture(context, uri)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = DarkBg) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                    Box(modifier = Modifier.size(60.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                        if (!ownUser?.profilePicUrl.isNullOrEmpty()) {
                            AsyncImage(model = ownUser?.profilePicUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Icon(Icons.Default.Person, null, modifier = Modifier.size(40.dp), tint = Color.Gray)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(ownUser?.displayName ?: "Mi Perfil", color = Color.White, style = MaterialTheme.typography.titleLarge)
                }
                HorizontalDivider(color = Color.Gray)
                NavigationDrawerItem(label = { Text("Nuevo Grupo", color = Color.White) }, selected = false, onClick = { showCreateGroupDialog = true ; scope.launch { drawerState.close() } }, icon = { Icon(Icons.Default.GroupAdd, null, tint = Color.White) }, colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent))
                NavigationDrawerItem(label = { Text("Ajustes", color = Color.White) }, selected = false, onClick = { showSettingsDialog = true ; scope.launch { drawerState.close() } }, icon = { Icon(Icons.Default.Settings, null, tint = Color.White) }, colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent))
                
                Spacer(modifier = Modifier.weight(1f))
                NavigationDrawerItem(label = { Text("Cerrar Sesión", color = Color.Red) }, selected = false, onClick = { authViewModel.logout() ; scope.launch { drawerState.close() } }, icon = { Icon(Icons.AutoMirrored.Filled.Logout, null, tint = Color.Red) }, colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent))
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    ) {
        Scaffold(
            bottomBar = { BottomNavigationBar(selectedItem = selectedTab, onItemSelected = { selectedTab = it }) },
            containerColor = DarkBg
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, null, tint = Color.White) }
                    Text("Vee", modifier = Modifier.weight(1f).padding(end = 48.dp), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
                Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(56.dp), shape = RoundedCornerShape(28.dp), color = Color.White) {
                    TextField(value = searchText, onValueChange = { searchText = it }, modifier = Modifier.fillMaxSize(), placeholder = { Text("Buscar...", color = Color.Gray, fontSize = 16.sp) }, trailingIcon = { Icon(Icons.Default.Search, null, tint = AccentColor) }, colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = Color.Black, unfocusedTextColor = Color.Black), textStyle = TextStyle(fontSize = 16.sp), singleLine = true)
                }
                if (selectedTab == 0) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { isViewingArchived = false }) { Text("Activo", color = if (!isViewingArchived) Color.White else Color.Gray, fontWeight = FontWeight.Medium) ; if (!isViewingArchived) { Spacer(Modifier.height(4.dp)) ; Box(Modifier.width(40.dp).height(2.dp).background(Color.White)) } }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { isViewingArchived = true }) { Text("Archivado", color = if (isViewingArchived) Color.White else Color.Gray, fontWeight = FontWeight.Medium) ; if (isViewingArchived) { Spacer(Modifier.height(4.dp)) ; Box(Modifier.width(40.dp).height(2.dp).background(Color.White)) } }
                    }
                } else { Spacer(Modifier.height(16.dp)) }
                when (selectedTab) {
                    0 -> {
                        val filteredUsers = users.filter { it.displayName.contains(searchText, ignoreCase = true) && (if (isViewingArchived) archivedUserIds.contains(it.uid) else !archivedUserIds.contains(it.uid)) }.sortedWith(compareByDescending<User> { pinnedUserIds.contains(it.uid) }.thenByDescending { lastMessages[it.uid]?.timestamp ?: 0L })
                        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { items(filteredUsers) { user -> val lastMsg = lastMessages[user.uid] ; ChatItem(user = user, subtitle = lastMsg?.content ?: "Sin mensajes", time = if (lastMsg != null) SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(lastMsg.timestamp)) else "", unreadCount = unreadCounts[user.uid] ?: 0, isPinned = pinnedUserIds.contains(user.uid), onClick = { viewModel.selectUser(user) }, onLongClick = { userToMenu = user }) } }
                    }
                    1 -> {
                        val filteredGroups = groups.filter { it.name.contains(searchText, ignoreCase = true) }.sortedByDescending { lastMessages[it.id]?.timestamp ?: 0L }
                        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { items(filteredGroups) { group -> GroupChatItem(group = group, subtitle = lastMessages[group.id]?.content ?: "${group.members.size} miembros", time = if (lastMessages[group.id] != null) SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(lastMessages[group.id]!!.timestamp)) else "", unreadCount = unreadCounts[group.id] ?: 0, isPinned = false, onClick = { viewModel.selectGroup(group) }, onLongClick = { showGroupMembersDialog = group }) } }
                    }
                    2 -> { LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { items(callLogs) { log -> CallLogItem(log = log) } } }
                }
            }
        }
    }
    if (showGroupMembersDialog != null) {
        val group = showGroupMembersDialog!!
        AlertDialog(onDismissRequest = { showGroupMembersDialog = null }, title = { Text("Miembros: ${group.name}") }, text = { Column { group.members.forEach { memberId -> val name = if (memberId == viewModel.myId) "Tú" else users.find { it.uid == memberId }?.displayName ?: "Usuario" ; Text("- $name", color = Color.Black) } } }, confirmButton = { if (group.adminId == viewModel.myId) TextButton(onClick = { viewModel.dissolveGroup(group.id) ; showGroupMembersDialog = null }) { Text("Disolver", color = Color.Red) } }, dismissButton = { TextButton(onClick = { showGroupMembersDialog = null }) { Text("Cerrar") } })
    }
    if (showCreateGroupDialog) {
        var groupName by remember { mutableStateOf("") } ; val selectedMembers = remember { mutableStateListOf<String>() }
        AlertDialog(onDismissRequest = { showCreateGroupDialog = false }, title = { Text("Crear Nuevo Grupo") }, text = { Column { OutlinedTextField(value = groupName, onValueChange = { groupName = it }, label = { Text("Nombre del grupo") }) ; Spacer(Modifier.height(8.dp)) ; LazyColumn(modifier = Modifier.height(200.dp)) { items(users) { user -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { if (selectedMembers.contains(user.uid)) selectedMembers.remove(user.uid) else selectedMembers.add(user.uid) }.padding(8.dp)) { Checkbox(checked = selectedMembers.contains(user.uid), onCheckedChange = null) ; Text(user.displayName) } } } } }, confirmButton = { Button(onClick = { if (groupName.isNotBlank()) { viewModel.createGroup(groupName, selectedMembers.toList()) ; showCreateGroupDialog = false ; Toast.makeText(context, "Grupo creado", Toast.LENGTH_SHORT).show() } }) { Text("Crear") } })
    }
    if (showSettingsDialog) {
        AlertDialog(onDismissRequest = { showSettingsDialog = false }, title = { Text("Ajustes de Perfil") }, text = { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) { Box(modifier = Modifier.size(100.dp).clip(CircleShape).background(Color.Gray), contentAlignment = Alignment.Center) { if (!ownUser?.profilePicUrl.isNullOrEmpty()) AsyncImage(model = ownUser?.profilePicUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) else Icon(Icons.Default.Person, null, modifier = Modifier.size(40.dp), tint = Color.White) } ; Button(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.padding(top = 16.dp)) { Text("Cambiar Foto de Perfil") } } }, confirmButton = { TextButton(onClick = { showSettingsDialog = false }) { Text("Cerrar") } })
    }
    if (userToMenu != null) {
        val isArchived = archivedUserIds.contains(userToMenu?.uid) ; val isPinned = pinnedUserIds.contains(userToMenu?.uid)
        AlertDialog(onDismissRequest = { userToMenu = null }, confirmButton = { TextButton(onClick = { userToMenu = null }) { Text("Cerrar") } }, title = { Text("Opciones de chat") }, text = { Column { ListItem(headlineContent = { Text(if (isArchived) "Desarchivar" else "Archivar") }, leadingContent = { Icon(Icons.Default.Archive, null) }, modifier = Modifier.clickable { viewModel.toggleArchive(userToMenu!!.uid) ; userToMenu = null }) ; ListItem(headlineContent = { Text(if (isPinned) "Desfijar" else "Fijar") }, leadingContent = { Icon(Icons.Default.PushPin, null) }, modifier = Modifier.clickable { viewModel.togglePin(userToMenu!!.uid) ; userToMenu = null }) ; ListItem(headlineContent = { Text("Eliminar contenido", color = Color.Red) }, leadingContent = { Icon(Icons.Default.Delete, null, tint = Color.Red) }, modifier = Modifier.clickable { viewModel.clearChat() ; userToMenu = null }) } })
    }
}

@Composable
fun CallOverlay(call: CallInfo, rtcEngine: RtcEngine?, remoteUid: Int, onAccept: () -> Unit, onReject: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = DarkBg.copy(alpha = 0.98f)) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (remoteUid != 0 && call.type == "VIDEO") { AndroidView(factory = { context -> SurfaceView(context).apply { rtcEngine?.setupRemoteVideo(VideoCanvas(this, VideoCanvas.RENDER_MODE_HIDDEN, remoteUid)) } }, modifier = Modifier.fillMaxSize()) }
            Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(64.dp))
                    if ((call.status == "ONGOING" || call.status == "CALLING") && call.type == "VIDEO") { AndroidView(factory = { context -> SurfaceView(context).apply { rtcEngine?.setupLocalVideo(VideoCanvas(this, VideoCanvas.RENDER_MODE_HIDDEN, 0)) } }, modifier = Modifier.size(150.dp, 200.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black)) }
                    else { Box(modifier = Modifier.size(120.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, modifier = Modifier.size(80.dp), tint = Color.Gray) } }
                    Spacer(modifier = Modifier.height(24.dp)) ; Text(call.callerName, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold) ; Text(text = when(call.status) { "RINGING" -> "Llamada entrante..." ; "CALLING" -> "Llamando..." ; else -> "En llamada activa" }, color = Color.LightGray, fontSize = 18.sp)
                }
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 64.dp), horizontalArrangement = Arrangement.SpaceEvenly) { if (call.status == "RINGING") { FloatingActionButton(onClick = onAccept, containerColor = Color.Green, contentColor = Color.White, shape = CircleShape) { Icon(Icons.Default.Call, null) } } ; FloatingActionButton(onClick = onReject, containerColor = Color.Red, contentColor = Color.White, shape = CircleShape) { Icon(Icons.Default.CallEnd, null) } }
            }
        }
    }
}

@Composable
fun CallLogItem(log: CallLog) {
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(log.timestamp))
    Surface(color = CardBg, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (log.isOutgoing) Icons.AutoMirrored.Filled.CallMade else Icons.AutoMirrored.Filled.CallReceived, null, tint = if (log.isOutgoing) Color.Green else Color.Red)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) { Text(log.partnerName, color = Color.White, fontWeight = FontWeight.Bold) ; Text(time, color = Color.Gray, fontSize = 12.sp) }
            Icon(Icons.Default.Call, null, tint = Color.White)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatItem(user: User, subtitle: String, time: String, unreadCount: Int, isPinned: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick), color = CardBg, shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(60.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { if (!user.profilePicUrl.isNullOrEmpty()) AsyncImage(model = user.profilePicUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) else Icon(Icons.Default.Person, null, modifier = Modifier.size(40.dp), tint = Color.Gray) }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(text = user.displayName.uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) ; if (isPinned) { Spacer(Modifier.width(8.dp)) ; Icon(Icons.Default.PushPin, null, Modifier.size(14.dp), tint = Color.LightGray) } } ; Text(text = subtitle, color = Color.LightGray, fontSize = 14.sp, maxLines = 1) }
            Column(horizontalAlignment = Alignment.End) { if (time.isNotEmpty()) { Text(time, color = Color.Gray, fontSize = 12.sp) } ; if (unreadCount > 0) { Spacer(modifier = Modifier.height(4.dp)) ; Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(GreenBadge), contentAlignment = Alignment.Center) { Text(unreadCount.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) } } }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupChatItem(group: Group, subtitle: String, time: String, unreadCount: Int, isPinned: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick), color = CardBg, shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(60.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { Icon(Icons.Default.Groups, null, modifier = Modifier.size(40.dp), tint = Color.Gray) }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(text = group.name.uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) ; if (isPinned) { Spacer(Modifier.width(8.dp)) ; Icon(Icons.Default.PushPin, null, Modifier.size(14.dp), tint = Color.LightGray) } } ; Text(text = subtitle, color = Color.LightGray, fontSize = 14.sp, maxLines = 1) }
            Column(horizontalAlignment = Alignment.End) { if (time.isNotEmpty()) { Text(time, color = Color.Gray, fontSize = 12.sp) } ; if (unreadCount > 0) { Spacer(modifier = Modifier.height(4.dp)) ; Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(GreenBadge), contentAlignment = Alignment.Center) { Text(unreadCount.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) } } }
        }
    }
}

@Composable
fun BottomNavigationBar(selectedItem: Int, onItemSelected: (Int) -> Unit) {
    NavigationBar(containerColor = Color(0xFF0A1D37), tonalElevation = 8.dp) {
        val items = listOf(
            Triple("Chats", Icons.AutoMirrored.Filled.Chat, 0),
            Triple("Grupos", Icons.Default.Groups, 1),
            Triple("Llamadas", Icons.Default.Call, 2)
        )
        items.forEach { (label, icon, index) -> 
            NavigationBarItem(
                selected = selectedItem == index, 
                onClick = { onItemSelected(index) }, 
                icon = { Icon(icon, contentDescription = label) }, 
                label = { Text(label, fontSize = 10.sp) }, 
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White, 
                    unselectedIconColor = Color.Gray, 
                    selectedTextColor = Color.White, 
                    unselectedTextColor = Color.Gray, 
                    indicatorColor = AccentColor.copy(alpha = 0.3f)
                )
            ) 
        }
    }
}
