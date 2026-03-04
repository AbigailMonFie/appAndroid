package com.fierro.mensajeria.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fierro.mensajeria.data.MessageEntity
import com.fierro.mensajeria.data.MessageViewModel

@Composable
fun ChatScreen(
    viewModel: MessageViewModel,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.allMessages.collectAsState(initial = emptyList())
    var textState by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(8.dp)
        ) {
            items(messages) { msg ->
                ChatBubble(message = msg)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = textState,
                onValueChange = { textState = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Escribe un mensaje...") }
            )
            IconButton(onClick = {
                if (textState.isNotBlank()) {
                    viewModel.sendMessage(textState)
                    textState = ""
                }
            }) {
                Icon(Icons.Default.Send, contentDescription = "Enviar")
            }
        }
    }
}

@Composable
fun ChatBubble(message: MessageEntity) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (message.isSentByMe) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (message.isSentByMe) Color(0xFFDCF8C6) else Color.White,
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 2.dp
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}
