package com.fierro.mensajeria

import android.os.Build
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
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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

class MainActivity : FragmentActivity() {
    private var rtcEngine: RtcEngine? = null
    private var remoteUidState = mutableIntStateOf(0)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, "Permiso de notificaciones denegado", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            var isDarkMode by rememberSaveable { mutableStateOf(true) }
            var isAppUnlocked by rememberSaveable { mutableStateOf(false) }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP) {
                        isAppUnlocked = false 
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            MensajeriaTheme(darkTheme = isDarkMode) {
                val authViewModel: AuthViewModel = viewModel()
                val chatViewModel: MessageViewModel = viewModel()
                val currentUser by authViewModel.currentUser.collectAsState()
                val selectedUser by chatViewModel.selectedUser.collectAsState()
                val selectedGroup by chatViewModel.selectedGroup.collectAsState()
                val currentCall by chatViewModel.currentCall.collectAsState()
                val isBiometricEnabled by chatViewModel.isBiometricEnabled.collectAsState()

                LaunchedEffect(currentUser, isBiometricEnabled, isAppUnlocked) {
                    if (currentUser != null && isBiometricEnabled == true && !isAppUnlocked) {
                        showBiometricPrompt { isAppUnlocked = true }
                    } else if (currentUser != null && isBiometricEnabled == false) {
                        isAppUnlocked = true
                    }
                }

                LaunchedEffect(selectedUser, selectedGroup) {
                    selectedUser?.let { chatViewModel.markMessagesAsRead(it.uid, false) }
                    selectedGroup?.let { chatViewModel.markMessagesAsRead(it.id, true) }
                }

                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    if (currentUser == null) {
                        LoginScreen(onLoginSuccess = { chatViewModel.onUserAuthenticated() }, viewModel = authViewModel)
                    } else if (isBiometricEnabled == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (!isAppUnlocked && isBiometricEnabled == true) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(16.dp))
                                Text("Acceso Protegido", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                                Spacer(Modifier.height(24.dp))
                                Button(onClick = { showBiometricPrompt { isAppUnlocked = true } }) {
                                    Text("Desbloquear con Biometría")
                                }
                            }
                        }
                    } else if (selectedUser == null && selectedGroup == null) {
                        UserListScreen(
                            viewModel = chatViewModel, 
                            authViewModel = authViewModel, 
                            isDarkMode = isDarkMode, 
                            onThemeToggle = { isDarkMode = !isDarkMode },
                            onBiometricVerify = { onSuccess -> showBiometricPrompt(onSuccess) }
                        )
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

    private fun showBiometricPrompt(onSuccess: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON && errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                    Toast.makeText(this@MainActivity, errString, Toast.LENGTH_SHORT).show()
                }
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Bloqueo de Mensajería")
            .setSubtitle("Usa tu huella o patrón para acceder")
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun setupAgora(channelName: String) {
        if (rtcEngine != null) return
        try {
            val config = RtcEngineConfig().apply {
                mContext = applicationContext
                mAppId = AgoraConfig.APP_ID
                mEventHandler = object : IRtcEngineEventHandler() {
                    override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) { Log.d("AGORA", "Unido") }
                    override fun onUserJoined(uid: Int, elapsed: Int) { remoteUidState.intValue = uid }
                    override fun onUserOffline(uid: Int, reason: Int) { remoteUidState.intValue = 0 }
                }
            }
            rtcEngine = RtcEngine.create(config).apply {
                enableVideo()
                joinChannel(AgoraConfig.TOKEN, channelName, "", 0)
            }
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
    var isLoginMode by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val authError by viewModel.authError.collectAsState()

    val colorScheme = MaterialTheme.colorScheme
    val primaryColor = colorScheme.primary

    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(context.getString(R.string.default_web_client_id))
        .requestEmail()
        .build()
    val googleSignInClient = GoogleSignIn.getClient(context, gso)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            account.idToken?.let { viewModel.signInWithGoogle(it, onLoginSuccess) }
        } catch (e: ApiException) {
            Toast.makeText(context, "Error Google: ${e.statusCode}", Toast.LENGTH_LONG).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(colorScheme.background, colorScheme.surface)))) {
        Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(modifier = Modifier.size(150.dp).drawBehind {
                val glowColor = primaryColor.copy(alpha = 0.4f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glowColor, Color.Transparent),
                        center = Offset(size.width / 2f, size.height * 0.75f),
                        radius = size.width * 0.8f
                    )
                )
            }.clip(CircleShape), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = R.drawable.vee),
                    contentDescription = "Logo",
                    modifier = Modifier.size(120.dp).clip(CircleShape).border(1.dp, primaryColor.copy(alpha = 0.2f), CircleShape),
                    contentScale = ContentScale.Fit
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = if (isLoginMode) "Bienvenido de nuevo" else "Crea tu cuenta",
                color = colorScheme.onBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            if (authError != null) {
                Text(authError!!, color = Color.Red, modifier = Modifier.padding(vertical = 8.dp), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(24.dp))
            if (!isLoginMode) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Usuario", color = Color.Gray) },
                    textStyle = TextStyle(color = colorScheme.onSurface),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = colorScheme.surface,
                        focusedContainerColor = colorScheme.surface,
                        focusedIndicatorColor = primaryColor,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                Spacer(Modifier.height(12.dp))
            }
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email", color = Color.Gray) },
                textStyle = TextStyle(color = colorScheme.onSurface),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = colorScheme.surface,
                    focusedContainerColor = colorScheme.surface,
                    focusedIndicatorColor = primaryColor,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña", color = Color.Gray) },
                textStyle = TextStyle(color = colorScheme.onSurface),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                visualTransformation = PasswordVisualTransformation(),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = colorScheme.surface,
                    focusedContainerColor = colorScheme.surface,
                    focusedIndicatorColor = primaryColor,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    if (isLoginMode) viewModel.login(email, password, onLoginSuccess)
                    else viewModel.register(email, password, name, onLoginSuccess)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
            ) {
                Text(if (isLoginMode) "Iniciar Sesión" else "Registrarse", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { isLoginMode = !isLoginMode; viewModel.clearError() }) {
                Text(
                    if (isLoginMode) "¿No tienes cuenta? Regístrate aquí" else "¿Ya tienes cuenta? Inicia sesión",
                    color = primaryColor
                )
            }
            Spacer(Modifier.height(24.dp))
            Text("— O —", color = Color.Gray)
            Spacer(Modifier.height(24.dp))
            OutlinedButton(
                onClick = { launcher.launch(googleSignInClient.signInIntent) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.onBackground),
                border = ButtonDefaults.outlinedButtonBorder(true).copy(width = 1.dp)
            ) {
                Icon(Icons.Default.AccountCircle, null, Modifier.padding(end = 8.dp))
                Text("Continuar con Google")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserListScreen(
    viewModel: MessageViewModel, 
    authViewModel: AuthViewModel, 
    isDarkMode: Boolean, 
    onThemeToggle: () -> Unit,
    onBiometricVerify: (onSuccess: () -> Unit) -> Unit = {}
) {
    val users by viewModel.users.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val archivedUserIds by viewModel.archivedUserIds.collectAsState()
    val pinnedUserIds by viewModel.pinnedUserIds.collectAsState()
    val blockedUserIds by viewModel.blockedUserIds.collectAsState()
    val lastMessages by viewModel.lastMessages.collectAsState()
    val unreadCounts by viewModel.unreadCounts.collectAsState()
    val callLogs by viewModel.callLogs.collectAsState()
    val ownUser by viewModel.ownUser.collectAsState()
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()

    val startDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val endDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var searchText by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var isViewingArchived by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showGroupMembersDialog by remember { mutableStateOf<Group?>(null) }
    var userToMenu by remember { mutableStateOf<User?>(null) }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { if (it != null) viewModel.uploadProfilePicture(context, it) }

    val colorScheme = MaterialTheme.colorScheme
    val primaryColor = colorScheme.primary

    BackHandler(enabled = startDrawerState.isOpen || endDrawerState.isOpen) {
        scope.launch {
            startDrawerState.close()
            endDrawerState.close()
        }
    }

    // Panel de Menú Principal (Externo + LTR para gesto desde la izquierda)
    ModalNavigationDrawer(
        drawerState = startDrawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = colorScheme.background) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                    Box(Modifier.size(60.dp).clip(CircleShape).background(colorScheme.surface), contentAlignment = Alignment.Center) {
                        if (!ownUser?.profilePicUrl.isNullOrEmpty()) {
                            AsyncImage(model = ownUser?.profilePicUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Icon(Icons.Default.Person, null, modifier = Modifier.size(40.dp), tint = Color.Gray)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(ownUser?.displayName ?: "Mi Perfil", color = colorScheme.onBackground, style = MaterialTheme.typography.titleLarge)
                }
                HorizontalDivider(color = colorScheme.onBackground.copy(alpha = 0.1f))
                NavigationDrawerItem(label = { Text("Nuevo Grupo", color = colorScheme.onBackground) }, selected = false, onClick = { showCreateGroupDialog = true; scope.launch { startDrawerState.close() } }, icon = { Icon(Icons.Default.GroupAdd, null, tint = colorScheme.onBackground) })
                NavigationDrawerItem(label = { Text("Ajustes", color = colorScheme.onBackground) }, selected = false, onClick = { showSettingsDialog = true; scope.launch { startDrawerState.close() } }, icon = { Icon(Icons.Default.Settings, null, tint = colorScheme.onBackground) })
                NavigationDrawerItem(label = { Text(if (isDarkMode) "Modo Claro" else "Modo Oscuro", color = colorScheme.onBackground) }, selected = false, onClick = { onThemeToggle() }, icon = { Icon(if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode, null, tint = colorScheme.onBackground) })
                Spacer(Modifier.weight(1f))
                NavigationDrawerItem(label = { Text("Cerrar Sesión", color = Color.Red.copy(alpha = 0.7f)) }, selected = false, onClick = { authViewModel.logout(); viewModel.clearData(); scope.launch { startDrawerState.close() } }, icon = { Icon(Icons.AutoMirrored.Filled.Logout, null, tint = Color.Red.copy(alpha = 0.7f)) })
                Spacer(Modifier.height(16.dp))
            }
        }
    ) {
        // Panel de Contactos (Interno + RTL para gesto desde la derecha)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            ModalNavigationDrawer(
                drawerState = endDrawerState,
                gesturesEnabled = true,
                drawerContent = {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        ModalDrawerSheet(drawerContainerColor = colorScheme.background) {
                            Spacer(Modifier.height(16.dp))
                            Text("Contactos", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge, color = colorScheme.onBackground)
                            HorizontalDivider(color = colorScheme.onBackground.copy(alpha = 0.1f))
                            val allContacts = users.sortedBy { it.displayName.lowercase() }
                            LazyColumn(Modifier.fillMaxSize()) {
                                items(allContacts) { contact ->
                                    NavigationDrawerItem(
                                        label = { Text(contact.displayName, color = colorScheme.onBackground) },
                                        selected = false,
                                        onClick = {
                                            viewModel.selectUser(contact)
                                            scope.launch { endDrawerState.close() }
                                        },
                                        icon = {
                                            Box(Modifier.size(32.dp).clip(CircleShape).background(colorScheme.surface), contentAlignment = Alignment.Center) {
                                                if (!contact.profilePicUrl.isNullOrEmpty()) {
                                                    AsyncImage(model = contact.profilePicUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                                } else {
                                                    Icon(Icons.Default.Person, null, modifier = Modifier.size(20.dp), tint = Color.Gray)
                                                }
                                            }
                                        },
                                        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                                    )
                                }
                            }
                        }
                    }
                }
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Scaffold(
                        bottomBar = { BottomNavigationBar(selectedItem = selectedTab, onItemSelected = { selectedTab = it }) },
                        containerColor = colorScheme.background
                    ) { padding ->
                        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { scope.launch { startDrawerState.open() } }) { Icon(Icons.Default.Menu, null, tint = colorScheme.onBackground) }
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    Box(modifier = Modifier.size(50.dp).drawBehind {
                                        val glowColor = primaryColor.copy(alpha = 0.4f)
                                        drawCircle(brush = Brush.radialGradient(colors = listOf(glowColor, Color.Transparent), center = Offset(size.width / 2f, size.height * 0.75f), radius = size.width * 0.8f))
                                    }.clip(CircleShape), contentAlignment = Alignment.Center) {
                                        Image(painter = painterResource(id = R.drawable.vee), contentDescription = "Logo", modifier = Modifier.size(38.dp).clip(CircleShape).border(1.dp, primaryColor.copy(alpha = 0.1f), CircleShape), contentScale = ContentScale.Fit)
                                    }
                                }
                                IconButton(onClick = { scope.launch { endDrawerState.open() } }) { Icon(Icons.Default.Contacts, null, tint = colorScheme.onBackground) }
                            }
                            Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(56.dp), shape = RoundedCornerShape(28.dp), color = colorScheme.surface.copy(alpha = 0.5f), border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.2f))) {
                                TextField(value = searchText, onValueChange = { searchText = it }, modifier = Modifier.fillMaxSize(), placeholder = { Text("Buscar...", color = Color.Gray, fontSize = 16.sp) }, trailingIcon = { Icon(Icons.Default.Search, null, tint = primaryColor) }, colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = colorScheme.onSurface, unfocusedTextColor = colorScheme.onSurface), textStyle = TextStyle(fontSize = 16.sp), singleLine = true)
                            }
                            when (selectedTab) {
                                0 -> {
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { isViewingArchived = false }) {
                                            Text("Chats", color = if (!isViewingArchived) colorScheme.onBackground else Color.Gray, fontWeight = FontWeight.Medium)
                                            if (!isViewingArchived) {
                                                Spacer(Modifier.height(4.dp))
                                                Box(Modifier.width(40.dp).height(2.dp).background(primaryColor))
                                            }
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { isViewingArchived = true }) {
                                            Text("Archivado", color = if (isViewingArchived) colorScheme.onBackground else Color.Gray, fontWeight = FontWeight.Medium)
                                            if (isViewingArchived) {
                                                Spacer(Modifier.height(4.dp))
                                                Box(Modifier.width(40.dp).height(2.dp).background(primaryColor))
                                            }
                                        }
                                    }
                                    val filteredUsers = users.filter {
                                        it.displayName.contains(searchText, ignoreCase = true) &&
                                                (if (isViewingArchived) archivedUserIds.contains(it.uid) else !archivedUserIds.contains(it.uid)) &&
                                                lastMessages.containsKey(it.uid)
                                    }.sortedWith(compareByDescending<User> { pinnedUserIds.contains(it.uid) }.thenByDescending { lastMessages[it.uid]?.timestamp ?: 0L })

                                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        items(filteredUsers) { user ->
                                            val lastMsg = lastMessages[user.uid]
                                            ChatItem(
                                                user = user,
                                                subtitle = lastMsg?.content ?: "Sin mensajes",
                                                time = if (lastMsg != null) SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(lastMsg.timestamp)) else "",
                                                unreadCount = unreadCounts[user.uid] ?: 0,
                                                isPinned = pinnedUserIds.contains(user.uid),
                                                onClick = { viewModel.selectUser(user) },
                                                onLongClick = { userToMenu = user }
                                            )
                                        }
                                    }
                                }
                                1 -> {
                                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("Tus Grupos", color = colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                        IconButton(onClick = { showCreateGroupDialog = true }, modifier = Modifier.background(primaryColor, CircleShape)) {
                                            Icon(Icons.Default.Add, null, tint = Color.White)
                                        }
                                    }
                                    val filteredGroups = groups.filter { it.name.contains(searchText, ignoreCase = true) }.sortedByDescending { lastMessages[it.id]?.timestamp ?: 0L }
                                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        items(filteredGroups) { group ->
                                            GroupChatItem(
                                                group = group,
                                                subtitle = lastMessages[group.id]?.content ?: "${group.members.size} miembros",
                                                time = if (lastMessages[group.id] != null) SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(lastMessages[group.id]!!.timestamp)) else "",
                                                unreadCount = unreadCounts[group.id] ?: 0,
                                                isPinned = false,
                                                onClick = { viewModel.selectGroup(group) },
                                                onLongClick = { showGroupMembersDialog = group }
                                            )
                                        }
                                    }
                                }
                                2 -> {
                                    Spacer(Modifier.height(16.dp))
                                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        items(callLogs) { log -> CallLogItem(log = log) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showGroupMembersDialog != null) {
        val group = showGroupMembersDialog!!
        AlertDialog(
            onDismissRequest = { showGroupMembersDialog = null },
            title = { Text("Miembros: ${group.name}") },
            text = {
                Column {
                    group.members.forEach { memberId ->
                        val name = if (memberId == viewModel.myId) "Tú" else users.find { it.uid == memberId }?.displayName ?: "Usuario"
                        Text("- $name", color = colorScheme.onSurface)
                    }
                }
            },
            containerColor = colorScheme.surface,
            confirmButton = {
                if (group.adminId == viewModel.myId) {
                    TextButton(onClick = { viewModel.dissolveGroup(group.id); showGroupMembersDialog = null }) {
                        Text("Disolver", color = Color.Red)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showGroupMembersDialog = null }) {
                    Text("Cerrar", color = colorScheme.onSurface)
                }
            }
        )
    }

    if (showCreateGroupDialog) {
        var groupName by remember { mutableStateOf("") }
        val selectedMembers = remember { mutableStateListOf<String>() }
        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false },
            containerColor = colorScheme.surface,
            title = { Text("Crear Nuevo Grupo", color = colorScheme.onSurface) },
            text = {
                Column {
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = { Text("Nombre del grupo") },
                        modifier = Modifier.border(1.dp, colorScheme.onSurface.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = colorScheme.background,
                            focusedContainerColor = colorScheme.background,
                            focusedTextColor = colorScheme.onBackground,
                            unfocusedTextColor = colorScheme.onBackground
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.height(200.dp)) {
                        items(users) { user ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (selectedMembers.contains(user.uid)) selectedMembers.remove(user.uid)
                                    else selectedMembers.add(user.uid)
                                }.padding(8.dp)
                            ) {
                                Checkbox(checked = selectedMembers.contains(user.uid), onCheckedChange = null)
                                Text(user.displayName, color = colorScheme.onSurface)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (groupName.isNotBlank()) {
                            viewModel.createGroup(groupName, selectedMembers.toList())
                            showCreateGroupDialog = false
                            Toast.makeText(context, "Grupo creado", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                ) {
                    Text("Crear")
                }
            }
        )
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            containerColor = colorScheme.surface,
            title = { Text("Ajustes de Perfil", color = colorScheme.onSurface) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.size(100.dp).clip(CircleShape).background(colorScheme.background), contentAlignment = Alignment.Center) {
                        if (!ownUser?.profilePicUrl.isNullOrEmpty()) {
                            AsyncImage(model = ownUser?.profilePicUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Icon(Icons.Default.Person, null, modifier = Modifier.size(40.dp), tint = colorScheme.onBackground)
                        }
                    }
                    Button(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.padding(top = 16.dp), colors = ButtonDefaults.buttonColors(containerColor = primaryColor)) {
                        Text("Cambiar Foto de Perfil")
                    }
                    Spacer(Modifier.height(24.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Icon(Icons.Default.Fingerprint, null, tint = colorScheme.onSurface)
                        Spacer(Modifier.width(16.dp))
                        Text("Bloqueo con huella o patrón", modifier = Modifier.weight(1f), color = colorScheme.onSurface)
                        Switch(
                            checked = isBiometricEnabled ?: false,
                            onCheckedChange = { enabled ->
                                viewModel.toggleBiometric(enabled, onPromptRequired = {
                                    onBiometricVerify {
                                        viewModel.updateBiometricSettings(true)
                                    }
                                })
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Cerrar", color = colorScheme.onSurface)
                }
            }
        )
    }

    if (userToMenu != null) {
        val isArchived = archivedUserIds.contains(userToMenu?.uid)
        val isPinned = pinnedUserIds.contains(userToMenu?.uid)
        val isBlocked = blockedUserIds.contains(userToMenu?.uid)
        AlertDialog(
            onDismissRequest = { userToMenu = null },
            containerColor = colorScheme.surface,
            confirmButton = {
                TextButton(onClick = { userToMenu = null }) {
                    Text("Cerrar", color = colorScheme.onSurface)
                }
            },
            title = { Text("Opciones de chat", color = colorScheme.onSurface) },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text(if (isArchived) "Desarchivar" else "Archivar", color = colorScheme.onSurface) },
                        leadingContent = { Icon(Icons.Default.Archive, null, tint = colorScheme.onSurface) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            viewModel.toggleArchive(userToMenu!!.uid)
                            userToMenu = null
                        }
                    )
                    ListItem(
                        headlineContent = { Text(if (isPinned) "Desfijar" else "Fijar", color = colorScheme.onSurface) },
                        leadingContent = { Icon(Icons.Default.PushPin, null, tint = colorScheme.onSurface) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            viewModel.togglePin(userToMenu!!.uid)
                            userToMenu = null
                        }
                    )
                    ListItem(
                        headlineContent = { Text(if (isBlocked) "Desbloquear" else "Bloquear", color = Color.Red.copy(alpha = 0.7f)) },
                        leadingContent = { Icon(Icons.Default.Block, null, tint = Color.Red.copy(alpha = 0.7f)) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            viewModel.toggleBlock(userToMenu!!.uid)
                            userToMenu = null
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Eliminar contenido", color = Color.Red.copy(alpha = 0.7f)) },
                        leadingContent = { Icon(Icons.Default.Delete, null, tint = Color.Red.copy(alpha = 0.7f)) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            viewModel.clearChat()
                            userToMenu = null
                        }
                    )
                }
            }
        )
    }
}

@Composable
fun CallOverlay(call: CallInfo, rtcEngine: RtcEngine?, remoteUid: Int, onAccept: () -> Unit, onReject: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(modifier = Modifier.fillMaxSize(), color = colorScheme.background.copy(alpha = 0.95f)) {
        Box(Modifier.fillMaxSize()) {
            if (remoteUid != 0 && call.type == "VIDEO") {
                AndroidView(factory = { SurfaceView(it).apply { rtcEngine?.setupRemoteVideo(VideoCanvas(this, VideoCanvas.RENDER_MODE_HIDDEN, remoteUid)) } }, modifier = Modifier.fillMaxSize())
            }
            Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(64.dp))
                    if ((call.status == "ONGOING" || call.status == "CALLING") && call.type == "VIDEO") {
                        AndroidView(factory = { SurfaceView(it).apply { rtcEngine?.setupLocalVideo(VideoCanvas(this, VideoCanvas.RENDER_MODE_HIDDEN, 0)) } }, modifier = Modifier.size(150.dp, 200.dp).clip(RoundedCornerShape(24.dp)).background(Color.Black))
                    } else {
                        Box(modifier = Modifier.size(120.dp).clip(CircleShape).background(colorScheme.surface), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, null, modifier = Modifier.size(80.dp), tint = Color.Gray)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(call.callerName, color = colorScheme.onBackground, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = when(call.status) {
                            "RINGING" -> "Llamada entrante..."
                            "CALLING" -> "Llamando..."
                            else -> "En llamada activa"
                        },
                        color = colorScheme.secondary,
                        fontSize = 18.sp
                    )
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 64.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    if (call.status == "RINGING") {
                        FloatingActionButton(onClick = onAccept, containerColor = Color(0xFF4CAF50), contentColor = Color.White, shape = CircleShape) { Icon(Icons.Default.Call, null) }
                    }
                    FloatingActionButton(onClick = onReject, containerColor = Color(0xFFFF5252), contentColor = Color.White, shape = CircleShape) { Icon(Icons.Default.CallEnd, null) }
                }
            }
        }
    }
}

@Composable
fun CallLogItem(log: CallLog) {
    val colorScheme = MaterialTheme.colorScheme
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(log.timestamp))
    Surface(color = colorScheme.surface, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (log.isOutgoing) Icons.AutoMirrored.Filled.CallMade else Icons.AutoMirrored.Filled.CallReceived,
                null,
                tint = if (log.isOutgoing) Color.Green else Color.Red
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(log.partnerName, color = colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Text(time, color = Color.Gray, fontSize = 12.sp)
            }
            Icon(Icons.Default.Call, null, tint = colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatItem(user: User, subtitle: String, time: String, unreadCount: Int, isPinned: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick), color = colorScheme.surface, shape = RoundedCornerShape(20.dp)) {
        Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(colorScheme.background), contentAlignment = Alignment.Center) {
                if (!user.profilePicUrl.isNullOrEmpty()) {
                    AsyncImage(model = user.profilePicUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Default.Person, null, modifier = Modifier.size(36.dp), tint = Color.Gray)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(user.displayName, color = colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (isPinned) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.PushPin, null, Modifier.size(14.dp), tint = colorScheme.secondary)
                    }
                }
                Text(subtitle, color = Color.Gray, fontSize = 14.sp, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                if (time.isNotEmpty()) Text(time, color = Color.Gray, fontSize = 12.sp)
                if (unreadCount > 0) {
                    Spacer(Modifier.height(4.dp))
                    Box(modifier = Modifier.size(22.dp).clip(CircleShape).background(colorScheme.primary), contentAlignment = Alignment.Center) {
                        Text(unreadCount.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupChatItem(group: Group, subtitle: String, time: String, unreadCount: Int, isPinned: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick), color = colorScheme.surface, shape = RoundedCornerShape(20.dp)) {
        Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(colorScheme.background), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Groups, null, modifier = Modifier.size(36.dp), tint = Color.Gray)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(group.name, color = colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (isPinned) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.PushPin, null, Modifier.size(14.dp), tint = colorScheme.secondary)
                    }
                }
                Text(subtitle, color = Color.Gray, fontSize = 14.sp, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                if (time.isNotEmpty()) Text(time, color = Color.Gray, fontSize = 12.sp)
                if (unreadCount > 0) {
                    Spacer(Modifier.height(4.dp))
                    Box(modifier = Modifier.size(22.dp).clip(CircleShape).background(colorScheme.primary), contentAlignment = Alignment.Center) {
                        Text(unreadCount.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun BottomNavigationBar(selectedItem: Int, onItemSelected: (Int) -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    NavigationBar(containerColor = colorScheme.background, tonalElevation = 0.dp) {
        val items = listOf(
            Triple("Chats", Icons.AutoMirrored.Filled.Chat, 0),
            Triple("Grupos", Icons.Default.Groups, 1),
            Triple("Llamadas", Icons.Default.Call, 2)
        )
        items.forEach { (label, icon, index) ->
            NavigationBarItem(
                selected = selectedItem == index,
                onClick = { onItemSelected(index) },
                icon = { Icon(icon, label) },
                label = { Text(label, fontSize = 10.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colorScheme.primary,
                    unselectedIconColor = Color.Gray,
                    selectedTextColor = colorScheme.primary,
                    unselectedTextColor = Color.Gray,
                    indicatorColor = colorScheme.primary.copy(alpha = 0.1f)
                )
            )
        }
    }
}
