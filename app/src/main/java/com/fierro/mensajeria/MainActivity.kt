package com.fierro.mensajeria

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fierro.mensajeria.data.AppDatabase
import com.fierro.mensajeria.data.MessageViewModel
import com.fierro.mensajeria.data.MessageViewModelFactory
import com.fierro.mensajeria.ui.theme.ChatScreen
import com.fierro.mensajeria.ui.theme.MensajeriaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Obtenemos la base de datos y el DAO
        val database = AppDatabase.getDatabase(this)
        val dao = database.messageDao()
        
        // Creamos la fábrica para el ViewModel con el DAO
        val factory = MessageViewModelFactory(dao)

        setContent {
            MensajeriaTheme {
                // Obtenemos el ViewModel usando la fábrica
                val chatViewModel: MessageViewModel = viewModel(factory = factory)
                
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Pasamos el ViewModel y aplicamos el padding del Scaffold
                    ChatScreen(
                        viewModel = chatViewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
