package com.fierro.mensajeria.data

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MessageViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val messagesCollection = db.collection("messages")
    private val usersCollection = db.collection("users")

    val myId: String get() = auth.currentUser?.uid ?: "anonimo"

    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users = _users.asStateFlow()

    private val _selectedUser = MutableStateFlow<User?>(null)
    val selectedUser = _selectedUser.asStateFlow()

    init {
        if (auth.currentUser != null) {
            fetchUsers()
        }
    }

    fun onUserAuthenticated() {
        Log.d("FIRESTORE", "Iniciando carga de usuarios para el ID: $myId")
        fetchUsers()
    }

    private fun fetchUsers() {
        usersCollection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("FIRESTORE", "Error al traer usuarios: ${error.message}")
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val userList = snapshot.toObjects(User::class.java).filter { it.uid != myId }
                Log.d("FIRESTORE", "Usuarios cargados: ${userList.size}")
                _users.value = userList
            }
        }
    }

    fun selectUser(user: User) {
        _selectedUser.value = user
    }

    // Función añadida para permitir volver a la lista de contactos
    fun deselectUser() {
        _selectedUser.value = null
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getMessagesWithSelectedUser(): Flow<List<FirebaseMessage>> = _selectedUser.flatMapLatest { user ->
        if (user == null) return@flatMapLatest flowOf(emptyList())
        
        callbackFlow {
            val subscription = messagesCollection
                .whereIn("senderId", listOf(myId, user.uid))
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("FIRESTORE", "Error al traer mensajes: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val allMsgs = snapshot.toObjects(FirebaseMessage::class.java)
                        val filtered = allMsgs.filter { 
                            (it.senderId == myId && it.receiverId == user.uid) || 
                            (it.senderId == user.uid && it.receiverId == myId)
                        }.sortedBy { it.timestamp }

                        trySend(filtered)
                    }
                }
            awaitClose { subscription.remove() }
        }
    }

    fun sendMessage(content: String) {
        val target = _selectedUser.value ?: return
        viewModelScope.launch {
            val newMessage = FirebaseMessage(
                senderId = myId,
                receiverId = target.uid,
                content = content,
                timestamp = System.currentTimeMillis()
            )
            messagesCollection.add(newMessage)
                .addOnSuccessListener {
                    Log.d("FIRESTORE", "Mensaje enviado exitosamente")
                }
                .addOnFailureListener {
                    Log.e("FIRESTORE", "Error al enviar mensaje: ${it.message}")
                }
        }
    }
}
