package com.fierro.mensajeria.ui.theme

import android.graphics.Bitmap
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fierro.mensajeria.data.FirebaseMessage
import com.fierro.mensajeria.data.MessageViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: MessageViewModel,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.getMessagesWithSelectedUser().collectAsState(initial = emptyList())
    val selectedUser by viewModel.selectedUser.collectAsState()
    var textState by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Lanzador para capturar foto
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            Log.d("CAMERA", "Foto capturada: ${bitmap.width}x${bitmap.height}")
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
                title = { Text(selectedUser?.displayName ?: "Chat") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.deselectUser() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(Color(0xFFF0F0F0))
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { msg ->
                    ChatBubble(message = msg, myId = viewModel.myId)
                }
            }

            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(8.dp)
                        .navigationBarsPadding()
                        .imePadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { cameraLauncher.launch(null) }) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = "Cámara",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    TextField(
                        value = textState,
                        onValueChange = { textState = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Escribe un mensaje...") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )

                    IconButton(onClick = { /* Lógica para grabar audio */ }) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Grabar Audio",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = {
                            if (textState.isNotBlank()) {
                                viewModel.sendMessage(textState)
                                textState = ""
                            }
                        },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                            .size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Enviar",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: FirebaseMessage, myId: String) {
    val isMe = message.senderId == myId
    val alignment = if (isMe) Alignment.End else Alignment.Start
    val bubbleColor = if (isMe) Color(0xFFDCF8C6) else Color.White
    val shape = if (isMe) {
        RoundedCornerShape(12.dp, 12.dp, 0.dp, 12.dp)
    } else {
        RoundedCornerShape(12.dp, 12.dp, 12.dp, 0.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            color = bubbleColor,
            shape = shape,
            shadowElevation = 1.dp
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                fontSize = 16.sp
            )
        }
    }
}
