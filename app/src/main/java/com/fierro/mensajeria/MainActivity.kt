package com.fierro.mensajeria

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import android.view.SurfaceView
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
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
import androidx.compose.ui.graphics.ColorFilter
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
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.video.VideoCanvas
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : FragmentActivity() {
    private var rtcEngine: RtcEngine? = null
    private var remoteUidState = mutableIntStateOf(0)
    private var isSpeakerphoneState = mutableStateOf(true)
    private var isMutedState = mutableStateOf(false)
    
    private var onAuthSuccessCallback: (() -> Unit)? = null

    private val authLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            onAuthSuccessCallback?.invoke()
            onAuthSuccessCallback = null
        }
    }

    private val callPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (!audioGranted) {
            Toast.makeText(this, "Se requiere permiso de micrófono para llamadas", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
            requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            var isDarkMode by rememberSaveable { mutableStateOf(true) }
            var isAppUnlocked by rememberSaveable { mutableStateOf(false) }

            MensajeriaTheme(darkTheme = isDarkMode) {
                val authViewModel: AuthViewModel = viewModel()
                val chatViewModel: MessageViewModel = viewModel()
                val currentUser by authViewModel.currentUser.collectAsState()
                val selectedUser by chatViewModel.selectedUser.collectAsState()
                val selectedGroup by chatViewModel.selectedGroup.collectAsState()
                val currentCall by chatViewModel.currentCall.collectAsState()
                val isBiometricEnabled by chatViewModel.isBiometricEnabled.collectAsState()

                val lifecycleOwner = LocalLifecycleOwner.current
                val context = LocalContext.current

                DisposableEffect(lifecycleOwner, currentUser) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_START -> {
                                if (currentUser != null) chatViewModel.updateOnlineStatus(true)
                            }
                            Lifecycle.Event.ON_STOP -> {
                                if (currentUser != null) chatViewModel.updateOnlineStatus(false)
                                isAppUnlocked = false 
                            }
                            else -> {}
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                // VIBRACION PARA LLAMADA ENTRANTE
                LaunchedEffect(currentCall?.status) {
                    if (currentCall?.status == "RINGING" && currentCall?.callerId != chatViewModel.myId) {
                        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                            vibratorManager.defaultVibrator
                        } else {
                            @Suppress("DEPRECATION")
                            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                        }
                        
                        val pattern = longArrayOf(0, 500, 500)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator.vibrate(pattern, 0)
                        }
                        
                        snapshotFlow { currentCall?.status }.collect { status ->
                            if (status != "RINGING") vibrator.cancel()
                        }
                    }
                }

                LaunchedEffect(currentCall?.status) {
                    val call = currentCall
                    if (call != null && (call.status == "CALLING" || call.status == "RINGING" || call.status == "ONGOING")) {
                        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
                        if (call.type == "VIDEO") permissions.add(Manifest.permission.CAMERA)
                        
                        val allGranted = permissions.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
                        
                        if (!allGranted) {
                            callPermissionLauncher.launch(permissions.toTypedArray())
                        } else {
                            setupAgora(call.receiverId, call.type == "VIDEO")
                        }

                        // INICIAR SERVICIO EN SEGUNDO PLANO
                        if (call.status == "ONGOING") {
                            val serviceIntent = Intent(context, CallService::class.java)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(serviceIntent)
                            } else {
                                context.startService(serviceIntent)
                            }
                        }
                    } else {
                        leaveChannel()
                        // DETENER SERVICIO
                        context.stopService(Intent(context, CallService::class.java))
                    }
                }

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
                        if (call.status != "IDLE" && call.status != "ENDED") {
                            CallOverlay(
                                call = call,
                                isSpeakerOn = isSpeakerphoneState.value,
                                isMuted = isMutedState.value,
                                onAccept = { chatViewModel.acceptCall() },
                                onReject = { chatViewModel.endCall() },
                                onToggleSpeaker = {
                                    val newState = !isSpeakerphoneState.value
                                    isSpeakerphoneState.value = newState
                                    rtcEngine?.setEnableSpeakerphone(newState)
                                },
                                onToggleMute = {
                                    val newState = !isMutedState.value
                                    isMutedState.value = newState
                                    rtcEngine?.muteLocalAudioStream(newState)
                                },
                                rtcEngine = rtcEngine,
                                remoteUid = remoteUidState.intValue
                            )
                        }
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

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            try {
                onAuthSuccessCallback = onSuccess
                val intent = keyguardManager.createConfirmDeviceCredentialIntent("Seguridad", "Confirma tu identidad")
                if (intent != null) authLauncher.launch(intent) else onSuccess()
            } catch (e: Exception) { onSuccess() }
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode == BiometricPrompt.ERROR_HW_NOT_PRESENT || errorCode == BiometricPrompt.ERROR_HW_UNAVAILABLE || errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS) onSuccess()
                else if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON && errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_CANCELED) Toast.makeText(this@MainActivity, errString, Toast.LENGTH_SHORT).show()
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Seguridad")
            .setSubtitle("Confirma tu identidad")
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()

        try { biometricPrompt.authenticate(promptInfo) } catch (e: Exception) { onSuccess() }
    }

    private fun setupAgora(channelName: String, isVideo: Boolean) {
        if (rtcEngine != null) return
        if (channelName.isEmpty()) return
        
        try {
            val config = RtcEngineConfig().apply {
                mContext = applicationContext
                mAppId = AgoraConfig.APP_ID
                mEventHandler = object : IRtcEngineEventHandler() {
                    override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) { 
                        Log.d("AGORA", "Unido con éxito: $channel") 
                    }
                    override fun onUserJoined(uid: Int, elapsed: Int) { 
                        remoteUidState.intValue = uid 
                    }
                    override fun onUserOffline(uid: Int, reason: Int) { 
                        remoteUidState.intValue = 0 
                    }
                }
            }
            rtcEngine = RtcEngine.create(config).apply {
                setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)
                if (isVideo) {
                    enableVideo()
                    startPreview()
                }
                enableAudio() 
                enableLocalAudio(true)
                setEnableSpeakerphone(isSpeakerphoneState.value)
                muteLocalAudioStream(isMutedState.value)
                
                val options = io.agora.rtc2.ChannelMediaOptions().apply {
                    autoSubscribeAudio = true
                    autoSubscribeVideo = isVideo
                    publishMicrophoneTrack = true
                    publishCameraTrack = isVideo
                    clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
                }
                joinChannel(AgoraConfig.TOKEN, channelName, 0, options)
            }
        } catch (e: Exception) { 
            Log.e("AGORA", "Error: ${e.message}") 
        }
    }

    private fun leaveChannel() {
        rtcEngine?.leaveChannel()
        RtcEngine.destroy()
        rtcEngine = null
        remoteUidState.intValue = 0
        isMutedState.value = false
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

    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestIdToken(context.getString(R.string.default_web_client_id)).requestEmail().build()
    val googleSignInClient = GoogleSignIn.getClient(context, gso)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            account.idToken?.let { viewModel.signInWithGoogle(it, onLoginSuccess) }
        } catch (e: ApiException) { Toast.makeText(context, "Error Google: ${e.statusCode}", Toast.LENGTH_LONG).show() }
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
            if (authError != null) Text(authError!!, color = Color.Red, modifier = Modifier.padding(vertical = 8.dp), textAlign = TextAlign.Center)
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
            TextButton(onClick = { isLoginMode = !isLoginMode; viewModel.clearError() }) { Text(if (isLoginMode) "¿No tienes cuenta? Regístrate aquí" else "¿Ya tienes cuenta? Inicia sesión", color = primaryColor) }
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showNicknameDialog by remember { mutableStateOf(false) }
    var nicknameText by remember { mutableStateOf("") }
    
    var showAddContactDialog by remember { mutableStateOf(false) }
    var beeCodeText by remember { mutableStateOf("") }

    var selectedContactForMenu by remember { mutableStateOf<User?>(null) }
    var showEditAliasDialog by remember { mutableStateOf(false) }
    var aliasText by remember { mutableStateOf("") }

    var userToMenu by remember { mutableStateOf<User?>(null) }
    var groupToLeave by remember { mutableStateOf<Group?>(null) }

    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var groupNameState by remember { mutableStateOf("") }
    val selectedMembers = remember { mutableStateListOf<String>() }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { if (it != null) viewModel.uploadProfilePicture(context, it) }
    val colorScheme = MaterialTheme.colorScheme
    val primaryColor = colorScheme.primary

    BackHandler(enabled = startDrawerState.isOpen || endDrawerState.isOpen) { scope.launch { startDrawerState.close(); endDrawerState.close() } }

    ModalNavigationDrawer(
        drawerState = startDrawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = colorScheme.background) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                    Box(Modifier.size(60.dp).clip(CircleShape).background(colorScheme.surface), contentAlignment = Alignment.Center) {
                        if (!ownUser?.profilePicUrl.isNullOrEmpty()) AsyncImage(model = ownUser?.profilePicUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else Icon(Icons.Default.Person, null, modifier = Modifier.size(40.dp), tint = Color.Gray)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(ownUser?.displayName ?: "Mi Perfil", color = colorScheme.onBackground, style = MaterialTheme.typography.titleLarge)
                        Text("VeeCode: ${ownUser?.beeCode ?: "Generando..."}", color = primaryColor, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
                HorizontalDivider(color = colorScheme.onBackground.copy(alpha = 0.1f))
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
                            Box(modifier = Modifier.fillMaxSize()) {
                                Column {
                                    Text("Contactos", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                                    HorizontalDivider()
                                    val myContacts = users.filter { ownUser?.contacts?.contains(it.uid) == true }
                                    if (myContacts.isEmpty()) {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text("Aún no tienes contactos", color = Color.Gray)
                                        }
                                    } else {
                                        LazyColumn {
                                            items(myContacts.sortedBy { ownUser?.contactAliases?.get(it.uid) ?: it.displayName }) { contact ->
                                                val displayName = ownUser?.contactAliases?.get(contact.uid) ?: contact.displayName
                                                Box(modifier = Modifier.fillMaxWidth().combinedClickable(
                                                    onClick = { viewModel.selectUser(contact); scope.launch { endDrawerState.close() } },
                                                    onLongClick = { selectedContactForMenu = contact }
                                                )) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                                        Box(Modifier.size(40.dp).clip(CircleShape).background(Color.LightGray), contentAlignment = Alignment.Center) {
                                                            if (!contact.profilePicUrl.isNullOrEmpty()) {
                                                                AsyncImage(model = contact.profilePicUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                                            } else {
                                                                Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(24.dp))
                                                            }
                                                        }
                                                        Spacer(Modifier.width(16.dp))
                                                        Text(displayName, style = MaterialTheme.typography.bodyLarge)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                FloatingActionButton(
                                    onClick = { showAddContactDialog = true },
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                                    containerColor = primaryColor,
                                    contentColor = Color.White
                                ) {
                                    Icon(painter = painterResource(id = R.drawable.agregar), contentDescription = "Agregar contacto", modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Scaffold(
                        bottomBar = { BottomNavigationBar(selectedItem = selectedTab, onItemSelected = { selectedTab = it }) },
                        floatingActionButton = {
                            if (selectedTab == 1) {
                                FloatingActionButton(
                                    onClick = { showCreateGroupDialog = true },
                                    containerColor = primaryColor,
                                    contentColor = Color.White
                                ) {
                                    Icon(Icons.Default.GroupAdd, "Crear grupo")
                                }
                            }
                        }
                    ) { padding ->
                        Column(modifier = Modifier.padding(padding)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { scope.launch { startDrawerState.open() } }) { Icon(Icons.Default.Menu, null) }
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { 
                                    Box(modifier = Modifier.size(50.dp).drawBehind {
                                        val glowColor = primaryColor.copy(alpha = 0.4f)
                                        drawCircle(brush = Brush.radialGradient(colors = listOf(glowColor, Color.Transparent), center = Offset(size.width / 2f, size.height * 0.75f), radius = size.width * 0.8f))
                                    }.clip(CircleShape), contentAlignment = Alignment.Center) {
                                        Image(
                                            painter = painterResource(id = R.drawable.vee), 
                                            contentDescription = null, 
                                            modifier = Modifier.size(40.dp).clip(CircleShape).border(1.dp, primaryColor.copy(alpha = 0.2f), CircleShape),
                                            contentScale = ContentScale.Fit
                                        ) 
                                    }
                                }
                                IconButton(onClick = { scope.launch { endDrawerState.open() } }) { Icon(Icons.Default.Contacts, null) }
                            }
                            Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(28.dp), color = colorScheme.surface) {
                                TextField(value = searchText, onValueChange = { searchText = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Buscar...") }, trailingIcon = { Icon(Icons.Default.Search, null) }, colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
                            }
                            when (selectedTab) {
                                0 -> {
                                    val filtered = users.filter { (ownUser?.contactAliases?.get(it.uid) ?: it.displayName).contains(searchText, true) && (if (isViewingArchived) archivedUserIds.contains(it.uid) else !archivedUserIds.contains(it.uid)) && lastMessages.containsKey(it.uid) }
                                    val sorted = filtered.sortedWith(compareByDescending<User> { pinnedUserIds.contains(it.uid) }.thenByDescending { lastMessages[it.uid]?.timestamp ?: 0L })
                                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        items(sorted) { user ->
                                            val lastMsg = lastMessages[user.uid]
                                            val timeStr = lastMsg?.let { SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(it.timestamp)) } ?: ""
                                            val displayName = ownUser?.contactAliases?.get(user.uid) ?: user.displayName
                                            ChatItem(user, displayName, lastMsg?.content ?: "", timeStr, unreadCounts[user.uid] ?: 0, pinnedUserIds.contains(user.uid), { viewModel.selectUser(user) }, { userToMenu = user })
                                        }
                                    }
                                }
                                1 -> {
                                    val filtered = groups.filter { it.name.contains(searchText, true) }
                                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        items(filtered) { group -> GroupChatItem(group, lastMessages[group.id]?.content ?: "", "", unreadCounts[group.id] ?: 0, false, { viewModel.selectGroup(group) }, { groupToLeave = group }) }
                                    }
                                }
                                2 -> { LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) { items(callLogs) { log -> CallLogItem(log) } } }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateGroupDialog) {
        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false },
            title = { Text("Nuevo Grupo") },
            text = {
                Column {
                    OutlinedTextField(
                        value = groupNameState,
                        onValueChange = { groupNameState = it },
                        label = { Text("Nombre del grupo") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Seleccionar integrantes:", style = MaterialTheme.typography.labelLarge)
                    LazyColumn(modifier = Modifier.height(200.dp)) {
                        val contacts = users.filter { ownUser?.contacts?.contains(it.uid) == true }
                        items(contacts) { contact ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                                if (selectedMembers.contains(contact.uid)) selectedMembers.remove(contact.uid)
                                else selectedMembers.add(contact.uid)
                            }.padding(vertical = 4.dp)) {
                                Checkbox(checked = selectedMembers.contains(contact.uid), onCheckedChange = null)
                                Text(ownUser?.contactAliases?.get(contact.uid) ?: contact.displayName)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (groupNameState.isNotBlank() && selectedMembers.isNotEmpty()) {
                        viewModel.createGroup(groupNameState, selectedMembers.toList())
                        showCreateGroupDialog = false
                        groupNameState = ""
                        selectedMembers.clear()
                    }
                }) { Text("Crear") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroupDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showAddContactDialog) {
        AlertDialog(
            onDismissRequest = { showAddContactDialog = false },
            title = { Text("Agregar contacto") },
            text = {
                Column {
                    Text("Ingresa el VeeCode de tu amigo:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = beeCodeText,
                        onValueChange = { beeCodeText = it.uppercase() },
                        label = { Text("VeeCode") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (beeCodeText.isNotBlank()) {
                        viewModel.addContactByBeeCode(beeCodeText) { result ->
                            Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
                            if (result.startsWith("Contacto agregado")) {
                                showAddContactDialog = false
                                beeCodeText = ""
                            }
                        }
                    }
                }) { Text("Agregar") }
            },
            dismissButton = {
                TextButton(onClick = { showAddContactDialog = false; beeCodeText = "" }) { Text("Cancelar") }
            }
        )
    }

    if (selectedContactForMenu != null) {
        AlertDialog(
            onDismissRequest = { selectedContactForMenu = null },
            title = { Text(ownUser?.contactAliases?.get(selectedContactForMenu!!.uid) ?: selectedContactForMenu!!.displayName) },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text("Editar apodo") },
                        leadingContent = { Icon(Icons.Default.Edit, null) },
                        modifier = Modifier.clickable {
                            aliasText = ownUser?.contactAliases?.get(selectedContactForMenu!!.uid) ?: selectedContactForMenu!!.displayName
                            showEditAliasDialog = true
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Eliminar contacto", color = Color.Red) },
                        leadingContent = { Icon(Icons.Default.Delete, null, tint = Color.Red) },
                        modifier = Modifier.clickable {
                            viewModel.removeContact(selectedContactForMenu!!.uid)
                            selectedContactForMenu = null
                        }
                    )
                }
            },
            confirmButton = { TextButton(onClick = { selectedContactForMenu = null }) { Text("Cerrar") } }
        )
    }

    if (showEditAliasDialog && selectedContactForMenu != null) {
        AlertDialog(
            onDismissRequest = { showEditAliasDialog = false },
            title = { Text("Editar apodo") },
            text = {
                OutlinedTextField(
                    value = aliasText,
                    onValueChange = { aliasText = it },
                    label = { Text("Apodo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (aliasText.isNotBlank()) {
                        viewModel.updateContactAlias(selectedContactForMenu!!.uid, aliasText)
                        showEditAliasDialog = false
                        selectedContactForMenu = null
                    }
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { showEditAliasDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showNicknameDialog) {
        AlertDialog(
            onDismissRequest = { showNicknameDialog = false },
            title = { Text("Cambiar apodo") },
            text = {
                OutlinedTextField(
                    value = nicknameText,
                    onValueChange = { nicknameText = it },
                    label = { Text("Nuevo apodo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (nicknameText.isNotBlank()) {
                        viewModel.updateDisplayName(nicknameText)
                        showNicknameDialog = false
                    }
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { showNicknameDialog = false }) { Text("Cancelar") }
            }
        )
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
                    
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { 
                        nicknameText = ownUser?.displayName ?: ""
                        showNicknameDialog = true 
                    }) {
                        Text("Cambiar apodo: ${ownUser?.displayName}")
                    }

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
        val isPinned = pinnedUserIds.contains(userToMenu!!.uid)
        AlertDialog(
            onDismissRequest = { userToMenu = null },
            confirmButton = { TextButton(onClick = { userToMenu = null }) { Text("Cerrar") } },
            title = { Text("Opciones de chat") },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text(if (isPinned) "Desfijar" else "Fijar") },
                        leadingContent = { Icon(if (isPinned) Icons.Default.PushPin else Icons.Default.PushPin, null, tint = if (isPinned) primaryColor else Color.Gray) },
                        modifier = Modifier.clickable { viewModel.togglePin(userToMenu!!.uid); userToMenu = null }
                    )
                    ListItem(
                        headlineContent = { Text("Archivar") },
                        leadingContent = { Icon(Icons.Default.Archive, null) },
                        modifier = Modifier.clickable { viewModel.toggleArchive(userToMenu!!.uid); userToMenu = null }
                    )
                    ListItem(
                        headlineContent = { Text("Bloquear") },
                        leadingContent = { Icon(Icons.Default.Block, null, tint = Color.Red) },
                        modifier = Modifier.clickable { viewModel.toggleBlock(userToMenu!!.uid); userToMenu = null }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Vaciar chat", color = Color.Red) },
                        leadingContent = { Icon(Icons.Default.DeleteSweep, null, tint = Color.Red) },
                        modifier = Modifier.clickable { 
                            viewModel.selectUser(userToMenu!!)
                            viewModel.clearChat()
                            viewModel.deselectUser()
                            userToMenu = null 
                        }
                    )
                }
            }
        )
    }

    if (groupToLeave != null) {
        AlertDialog(
            onDismissRequest = { groupToLeave = null },
            title = { Text("Salir del grupo") },
            text = { Text("¿Estás seguro de que quieres salir del grupo \"${groupToLeave?.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    groupToLeave?.let { viewModel.leaveGroup(it.id) }
                    groupToLeave = null
                }) { Text("Salir", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { groupToLeave = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun CallOverlay(
    call: CallInfo, 
    isSpeakerOn: Boolean,
    isMuted: Boolean,
    onAccept: () -> Unit, 
    onReject: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleMute: () -> Unit,
    rtcEngine: RtcEngine?,
    remoteUid: Int
) {
    var secondsElapsed by remember { mutableIntStateOf(0) }
    
    LaunchedEffect(call.status) {
        if (call.status == "ONGOING") {
            while (true) {
                delay(1000)
                secondsElapsed++
            }
        }
    }

    val timeText = remember(secondsElapsed) {
        val mins = secondsElapsed / 60
        val secs = secondsElapsed % 60
        String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f)) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (call.type == "VIDEO" && call.status == "ONGOING") {
                // Video Remoto
                if (remoteUid != 0) {
                    AndroidView(
                        factory = { context ->
                            val view = SurfaceView(context)
                            rtcEngine?.setupRemoteVideo(VideoCanvas(view, VideoCanvas.RENDER_MODE_HIDDEN, remoteUid))
                            view
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                // Video Local (Miniatura)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 64.dp, end = 16.dp)
                        .size(120.dp, 160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black)
                        .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                ) {
                    AndroidView(
                        factory = { context ->
                            val view = SurfaceView(context)
                            // Crucial: poner la miniatura por encima del video remoto
                            view.setZOrderMediaOverlay(true)
                            rtcEngine?.setupLocalVideo(VideoCanvas(view, VideoCanvas.RENDER_MODE_HIDDEN, 0))
                            view
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (call.type != "VIDEO" || call.status != "ONGOING") {
                    val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                    val myId = auth.currentUser?.uid ?: ""
                    val isOutgoing = call.callerId == myId

                    val displayPic = if (isOutgoing) call.receiverProfilePicUrl else call.callerProfilePicUrl
                    val displayName = if (isOutgoing) call.receiverName else call.callerName

                    Box(Modifier.size(120.dp).clip(CircleShape).background(Color.Gray), contentAlignment = Alignment.Center) {
                        if (!displayPic.isNullOrEmpty()) {
                            AsyncImage(model = displayPic, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Icon(Icons.Default.Person, null, modifier = Modifier.size(80.dp), tint = Color.White)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(displayName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = if (call.type == "VIDEO") Color.White else MaterialTheme.colorScheme.onBackground)
                }
                
                val statusLabel = when(call.status) {
                    "RINGING" -> "Llamada entrante..."
                    "CALLING" -> "Llamando..."
                    "ONGOING" -> "En llamada - $timeText"
                    else -> ""
                }
                Text(statusLabel, color = if (call.type == "VIDEO" && call.status == "ONGOING") Color.White else MaterialTheme.colorScheme.primary)
                
                Spacer(Modifier.height(if (call.type == "VIDEO" && call.status == "ONGOING") 320.dp else 64.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleMute, modifier = Modifier.background(Color.Black.copy(alpha = 0.2f), CircleShape)) {
                        Icon(imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = null, tint = if (isMuted) Color.Red else Color.White)
                    }

                    IconButton(onClick = onToggleSpeaker, modifier = Modifier.background(Color.Black.copy(alpha = 0.2f), CircleShape)) {
                        Icon(imageVector = if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff, contentDescription = null, tint = if (isSpeakerOn) MaterialTheme.colorScheme.primary else Color.White)
                    }

                    if (call.status == "RINGING") {
                        FloatingActionButton(onClick = onAccept, containerColor = Color.Green, contentColor = Color.White, shape = CircleShape) {
                            Icon(Icons.Default.Call, null)
                        }
                    }

                    FloatingActionButton(onClick = onReject, containerColor = Color.Red, contentColor = Color.White, shape = CircleShape) {
                        Icon(Icons.Default.CallEnd, null)
                    }
                }
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
fun ChatItem(user: User, displayName: String, subtitle: String, time: String, unreadCount: Int, isPinned: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(Color.Gray), contentAlignment = Alignment.Center) {
                if (!user.profilePicUrl.isNullOrEmpty()) {
                    AsyncImage(model = user.profilePicUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(displayName, fontWeight = FontWeight.Bold, maxLines = 1, style = MaterialTheme.typography.bodyLarge)
                    if (isPinned) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.PushPin, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(subtitle, maxLines = 1, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(time, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                if (unreadCount > 0) {
                    Spacer(Modifier.height(4.dp))
                    Badge(containerColor = MaterialTheme.colorScheme.primary) { Text(unreadCount.toString(), color = Color.White) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupChatItem(group: Group, subtitle: String, time: String, unreadCount: Int, isPinned: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(50.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                if (!group.profilePicUrl.isNullOrEmpty()) {
                    AsyncImage(model = group.profilePicUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Default.Groups, null, modifier = Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(group.name, fontWeight = FontWeight.Bold)
                Text(subtitle, maxLines = 1, color = Color.Gray)
            }
            if (unreadCount > 0) Badge { Text(unreadCount.toString()) }
        }
    }
}

@Composable
fun BottomNavigationBar(selectedItem: Int, onItemSelected: (Int) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = selectedItem == 0, 
            onClick = { onItemSelected(0) }, 
            icon = { Icon(painter = painterResource(id = R.drawable.chats), contentDescription = null, modifier = Modifier.size(24.dp)) }, 
            label = { Text("Chats") }
        )
        NavigationBarItem(
            selected = selectedItem == 1, 
            onClick = { onItemSelected(1) }, 
            icon = { Icon(painter = painterResource(id = R.drawable.grupos), contentDescription = null, modifier = Modifier.size(24.dp)) }, 
            label = { Text("Grupos") }
        )
        NavigationBarItem(
            selected = selectedItem == 2, 
            onClick = { onItemSelected(2) }, 
            icon = { Icon(painter = painterResource(id = R.drawable.llamadas), contentDescription = null, modifier = Modifier.size(24.dp)) }, 
            label = { Text("Llamadas") }
        )
    }
}
