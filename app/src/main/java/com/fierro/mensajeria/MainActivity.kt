package com.fierro.mensajeria

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fierro.mensajeria.data.AuthViewModel
import com.fierro.mensajeria.data.MessageViewModel
import com.fierro.mensajeria.data.User
import com.fierro.mensajeria.ui.theme.ChatScreen
import com.fierro.mensajeria.ui.theme.MensajeriaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            MensajeriaTheme {
                val authViewModel: AuthViewModel = viewModel()
                val chatViewModel: MessageViewModel = viewModel()
                val currentUser by authViewModel.currentUser.collectAsState()
                val selectedUser by chatViewModel.selectedUser.collectAsState()

                if (currentUser == null) {
                    LoginScreen(
                        onLoginSuccess = { chatViewModel.onUserAuthenticated() },
                        viewModel = authViewModel
                    )
                } else if (selectedUser == null) {
                    UserListScreen(viewModel = chatViewModel)
                } else {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        ChatScreen(
                            viewModel = chatViewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onLoginSuccess: () -> Unit, viewModel: AuthViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Mensajería", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(16.dp))
        TextField(value = name, onValueChange = { name = it }, label = { Text("Nombre") })
        TextField(value = email, onValueChange = { email = it }, label = { Text("Email") })
        TextField(value = password, onValueChange = { password = it }, label = { Text("Password") })
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { 
            viewModel.loginOrRegister(email, password, name, onLoginSuccess) 
        }) {
            Text("Entrar / Registrarse")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserListScreen(viewModel: MessageViewModel) {
    val users by viewModel.users.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Contactos") })
        }
    ) { padding ->
        if (users.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No hay otros usuarios registrados aún.")
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(users) { user ->
                    ListItem(
                        headlineContent = { Text(user.displayName) },
                        // Se eliminó supportingContent que mostraba el email
                        modifier = Modifier.clickable { viewModel.selectUser(user) }
                    )
                }
            }
        }
    }
}
