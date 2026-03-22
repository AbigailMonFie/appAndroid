package com.fierro.mensajeria

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : FragmentActivity() {
    private var rtcEngine: RtcEngine? = null
    private var remoteUidState = mutableIntStateOf(0)
    
    // Callback para el resultado de autenticación en dispositivos antiguos
    private var onAuthSuccessCallback: (() -> Unit)? = null

    private val authLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            onAuthSuccessCallback?.invoke()
            onAuthSuccessCallback = null
        }
    }

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
                                    Text("Desbloquear aplicación")
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
                        CallOverlay(call, rtcEngine, remoteUidState.intValue, 
                            onAccept = { setupAgora(call.receiverId); chatViewModel.acceptCall() },
                            onReject = { leaveChannel(); chatViewModel.endCall() }
                        )
                    }
                }
            }
        }
    }

    private fun showBiometricPrompt(onSuccess: () -> Unit) {
        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        
        if (!keyguardManager.isDeviceSecure) {
            onSuccess()
            return
        }

        // DISPOSITIVOS ANTIGUOS (Android 8.1 o inferior como el S7 Edge)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            try {
                onAuthSuccessCallback = onSuccess
                val intent = keyguardManager.createConfirmDeviceCredentialIntent(
                    "Seguridad de Mensajería", 
                    "Confirma tu identidad para continuar"
                )
                if (intent != null) {
                    authLauncher.launch(intent)
                } else {
                    onSuccess()
                }
            } catch (e: Exception) {
                Log.e("BIOMETRIC", "Error con Keyguard: ${e.message}")
                onSuccess()
            }
            return
        }

        // DISPOSITIVOS MODERNOS (Android 9+)
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode == BiometricPrompt.ERROR_HW_NOT_PRESENT || 
                    errorCode == BiometricPrompt.ERROR_HW_UNAVAILABLE ||
                    errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS) {
                    onSuccess()
                } else if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON && 
                    errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_CANCELED) {
                    Toast.makeText(this@MainActivity, errString, Toast.LENGTH_SHORT).show()
                }
            }
        })

        val promptBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Seguridad")
            .setSubtitle("Confirma tu identidad")
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)

        try {
            biometricPrompt.authenticate(promptBuilder.build())
        } catch (e: Exception) {
            Log.e("BIOMETRIC", "Fallo BiometricPrompt: ${e.message}")
            onSuccess()
        }
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
                drawCircle(brush = Brush.radialGradient(colors = listOf(glowColor, Color.Transparent), center = Offset(size.width / 2f, size.height * 0.75f), radius = size.width * 0.8f))
            }.clip(CircleShape), contentAlignment = Alignment.Center) {
                Image(painter = painterResource(id = R.drawable.vee), contentDescription = "Logo", modifier = Modifier.size(120.dp).clip(CircleShape).border(1.dp, primaryColor.copy(alpha = 0.2f), CircleShape), contentScale = ContentScale.Fit)
            }
            Spacer(Modifier.height(24.dp))
            Text(text = if (isLoginMode) "Bienvenido de nuevo" else "Crea tu cuenta", color = colorScheme.onBackground, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            if (authError != null) {
                Text(authError!!, color = Color.Red, modifier = Modifier.padding(vertical = 8.dp), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(24.dp))
            if (!isLoginMode) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Usuario", color = Color.Gray) }, textStyle = TextStyle(color = colorScheme.onSurface), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = TextFieldDefaults.colors(unfocusedContainerColor = colorScheme.surface, focusedContainerColor = colorScheme.surface, focusedIndicatorColor = primaryColor, unfocusedIndicatorColor = Color.Transparent))
                Spacer(Modifier.height(12.dp))
            }
            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email", color = Color.Gray) }, textStyle = TextStyle(color = colorScheme.onSurface), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = TextFieldDefaults.colors(unfocusedContainerColor = colorScheme.surface, focusedContainerColor = colorScheme.surface, focusedIndicatorColor = primaryColor, unfocusedIndicatorColor = Color.Transparent))
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Contraseña", color = Color.Gray) }, textStyle = TextStyle(color = colorScheme.onSurface), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), visualTransformation = PasswordVisualTransformation(), colors = TextFieldDefaults.colors(unfocusedContainerColor = colorScheme.surface, focusedContainerColor = colorScheme.surface, focusedIndicatorColor = primaryColor, unfocusedIndicatorColor = Color.Transparent))
            Spacer(Modifier.height(24.dp))
            Button(onClick = { if (isLoginMode) viewModel.login(email, password, onLoginSuccess) else viewModel.register(email, password, name, onLoginSuccess) }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = primaryColor)) {
                Text(if (isLoginMode) "Iniciar Sesión" else "Registrarse", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { isLoginMode = !isLoginMode; viewModel.clearError() }) {
                Text(if (isLoginMode) "¿No tienes cuenta? Regístrate aquí" else "¿Ya tienes cuenta? Inicia sesión", color = primaryColor)
            }
            Spacer(Modifier.height(24.dp))
            Text("— O —", color = Color.Gray)
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = { launcher.launch(googleSignInClient.signInIntent) }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.onBackground), border = ButtonDefaults.outlinedButtonBorder(true).copy(width = 1.dp)) {
                Icon(Icons.Default.AccountCircle, null, Modifier.padding(end = 8.dp))
                Text("Continuar con Google")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserListScreen(viewModel: MessageViewModel, authViewModel: AuthViewModel, isDarkMode: Boolean, onThemeToggle: () -> Unit, onBiometricVerify: (onSuccess: () -> Unit) -> Unit = {}) {
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
        scope.launch { startDrawerState.close(); endDrawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = startDrawerState,
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
                NavigationDrawerItem(label = { Text("Nuevo Grupo") }, selected = false, onClick = { showCreateGroupDialog = true; scope.launch { startDrawerState.close() } }, icon = { Icon(Icons.Default.GroupAdd, null) })
                NavigationDrawerItem(label = { Text("Ajustes") }, selected = false, onClick = { showSettingsDialog = true; scope.launch { startDrawerState.close() } }, icon = { Icon(Icons.Default.Settings, null) })
                NavigationDrawerItem(label = { Text(if (isDarkMode) "Modo Claro" else "Modo Oscuro") }, selected = false, onClick = { onThemeToggle() }, icon = { Icon(if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode, null) })
                Spacer(Modifier.weight(1f))
                NavigationDrawerItem(label = { Text("Cerrar Sesión", color = Color.Red) }, selected = false, onClick = { authViewModel.logout(); viewModel.clearData(); scope.launch { startDrawerState.close() } }, icon = { Icon(Icons.AutoMirrored.Filled.Logout, null, tint = Color.Red) })
                Spacer(Modifier.height(16.dp))
            }
        }
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            ModalNavigationDrawer(
                drawerState = endDrawerState,
                drawerContent = {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        ModalDrawerSheet(drawerContainerColor = colorScheme.background) {
                            Text("Contactos", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                            HorizontalDivider()
                            LazyColumn {
                                items(users.sortedBy { it.displayName }) { contact ->
                                    NavigationDrawerItem(label = { Text(contact.displayName) }, selected = false, onClick = { viewModel.selectUser(contact); scope.launch { endDrawerState.close() } })
                                }
                            }
                        }
                    }
                }
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Scaffold(
                        bottomBar = { BottomNavigationBar(selectedItem = selectedTab, onItemSelected = { selectedTab = it }) }
                    ) { padding ->
                        Column(modifier = Modifier.padding(padding)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { scope.launch { startDrawerState.open() } }) { Icon(Icons.Default.Menu, null) }
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    Image(painter = painterResource(id = R.drawable.vee), contentDescription = null, modifier = Modifier.size(40.dp).clip(CircleShape))
                                }
                                IconButton(onClick = { scope.launch { endDrawerState.open() } }) { Icon(Icons.Default.Contacts, null) }
                            }
                            Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(28.dp), color = colorScheme.surface) {
                                TextField(value = searchText, onValueChange = { searchText = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Buscar...") }, trailingIcon = { Icon(Icons.Default.Search, null) }, colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
                            }
                            when (selectedTab) {
                                0 -> {
                                    val filtered = users.filter { it.displayName.contains(searchText, true) && (if (isViewingArchived) archivedUserIds.contains(it.uid) else !archivedUserIds.contains(it.uid)) && lastMessages.containsKey(it.uid) }
                                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        items(filtered) { user ->
                                            ChatItem(user, lastMessages[user.uid]?.content ?: "", "", unreadCounts[user.uid] ?: 0, pinnedUserIds.contains(user.uid), { viewModel.selectUser(user) }, { userToMenu = user })
                                        }
                                    }
                                }
                                1 -> {
                                    val filtered = groups.filter { it.name.contains(searchText, true) }
                                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        items(filtered) { group ->
                                            GroupChatItem(group, lastMessages[group.id]?.content ?: "", "", unreadCounts[group.id] ?: 0, false, { viewModel.selectGroup(group) }, { showGroupMembersDialog = group })
                                        }
                                    }
                                }
                                2 -> {
                                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                                        items(callLogs) { log -> CallLogItem(log) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Ajustes") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.size(80.dp).clip(CircleShape).background(colorScheme.surface), contentAlignment = Alignment.Center) {
                        if (!ownUser?.profilePicUrl.isNullOrEmpty()) AsyncImage(model = ownUser?.profilePicUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else Icon(Icons.Default.Person, null, modifier = Modifier.size(40.dp))
                    }
                    Button(onClick = { galleryLauncher.launch("image/*") }) { Text("Cambiar foto") }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Bloqueo de aplicación", modifier = Modifier.weight(1f))
                        Switch(checked = isBiometricEnabled ?: false, onCheckedChange = { enabled ->
                            viewModel.toggleBiometric(enabled, onPromptRequired = { onBiometricVerify { viewModel.updateBiometricSettings(true) } })
                        })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSettingsDialog = false }) { Text("Cerrar") } }
        )
    }

    if (userToMenu != null) {
        AlertDialog(
            onDismissRequest = { userToMenu = null },
            confirmButton = {},
            title = { Text("Opciones") },
            text = {
                Column {
                    ListItem(headlineContent = { Text("Archivar") }, modifier = Modifier.clickable { viewModel.toggleArchive(userToMenu!!.uid); userToMenu = null })
                    ListItem(headlineContent = { Text("Fijar") }, modifier = Modifier.clickable { viewModel.togglePin(userToMenu!!.uid); userToMenu = null })
                    ListItem(headlineContent = { Text("Bloquear") }, modifier = Modifier.clickable { viewModel.toggleBlock(userToMenu!!.uid); userToMenu = null })
                }
            }
        )
    }
}

@Composable
fun CallOverlay(call: CallInfo, rtcEngine: RtcEngine?, remoteUid: Int, onAccept: () -> Unit, onReject: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background.copy(alpha = 0.9f)) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(call.callerName, style = MaterialTheme.typography.headlineLarge)
            Text(call.status)
            Row {
                if (call.status == "RINGING") Button(onClick = onAccept) { Text("Aceptar") }
                Button(onClick = onReject) { Text("Colgar") }
            }
        }
    }
}

@Composable
fun CallLogItem(log: CallLog) {
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(log.timestamp))
    ListItem(headlineContent = { Text(log.partnerName) }, supportingContent = { Text(time) }, leadingContent = { Icon(if (log.isOutgoing) Icons.AutoMirrored.Filled.CallMade else Icons.AutoMirrored.Filled.CallReceived, null) })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatItem(user: User, subtitle: String, time: String, unreadCount: Int, isPinned: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(50.dp).clip(CircleShape).background(Color.Gray))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(user.displayName, fontWeight = FontWeight.Bold)
                Text(subtitle, maxLines = 1)
            }
            if (unreadCount > 0) Badge { Text(unreadCount.toString()) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupChatItem(group: Group, subtitle: String, time: String, unreadCount: Int, isPinned: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Groups, null, modifier = Modifier.size(50.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(group.name, fontWeight = FontWeight.Bold)
                Text(subtitle, maxLines = 1)
            }
            if (unreadCount > 0) Badge { Text(unreadCount.toString()) }
        }
    }
}

@Composable
fun BottomNavigationBar(selectedItem: Int, onItemSelected: (Int) -> Unit) {
    NavigationBar {
        NavigationBarItem(selected = selectedItem == 0, onClick = { onItemSelected(0) }, icon = { Icon(Icons.AutoMirrored.Filled.Chat, null) }, label = { Text("Chats") })
        NavigationBarItem(selected = selectedItem == 1, onClick = { onItemSelected(1) }, icon = { Icon(Icons.Default.Groups, null) }, label = { Text("Grupos") })
        NavigationBarItem(selected = selectedItem == 2, onClick = { onItemSelected(2) }, icon = { Icon(Icons.Default.Call, null) }, label = { Text("Llamadas") })
    }
}
