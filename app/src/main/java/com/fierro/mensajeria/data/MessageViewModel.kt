package com.fierro.mensajeria.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class MessageViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance() // Referencia a Firebase Storage (Bucket)
    
    private val messagesCollection = db.collection("messages")
    private val usersCollection = db.collection("users")
    private val groupsCollection = db.collection("groups")
    private val callsCollection = db.collection("calls")

    private val activeListeners = mutableListOf<ListenerRegistration>()

    val myId: String get() = auth.currentUser?.uid ?: "anonimo"

    private val _ownUser = MutableStateFlow<User?>(null)
    val ownUser = _ownUser.asStateFlow()

    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users = _users.asStateFlow()

    private val _groups = MutableStateFlow<List<Group>>(emptyList())
    val groups = _groups.asStateFlow()

    private val _selectedUser = MutableStateFlow<User?>(null)
    val selectedUser = _selectedUser.asStateFlow()

    private val _selectedGroup = MutableStateFlow<Group?>(null)
    val selectedGroup = _selectedGroup.asStateFlow()

    private val _currentCall = MutableStateFlow<CallInfo?>(null)
    val currentCall = _currentCall.asStateFlow()

    private val _archivedUserIds = MutableStateFlow<Set<String>>(emptySet())
    val archivedUserIds = _archivedUserIds.asStateFlow()

    private val _pinnedUserIds = MutableStateFlow<Set<String>>(emptySet())
    val pinnedUserIds = _pinnedUserIds.asStateFlow()

    private val _lastMessages = MutableStateFlow<Map<String, FirebaseMessage>>(emptyMap())
    val lastMessages = _lastMessages.asStateFlow()

    private val _unreadCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val unreadCounts = _unreadCounts.asStateFlow()

    private val _callLogs = MutableStateFlow<List<CallLog>>(emptyList())
    val callLogs = _callLogs.asStateFlow()

    init {
        if (auth.currentUser != null) {
            loadInitialData()
        }
    }

    fun onUserAuthenticated() {
        clearData() 
        loadInitialData()
    }

    fun clearData() {
        activeListeners.forEach { it.remove() }
        activeListeners.clear()
        
        _ownUser.value = null
        _users.value = emptyList()
        _groups.value = emptyList()
        _selectedUser.value = null
        _selectedGroup.value = null
        _currentCall.value = null
        _archivedUserIds.value = emptySet()
        _pinnedUserIds.value = emptySet()
        _lastMessages.value = emptyMap()
        _unreadCounts.value = emptyMap()
        _callLogs.value = emptyList()
    }

    private fun loadInitialData() {
        fetchOwnProfile()
        fetchUsers()
        fetchGroups()
        listenForCalls()
        listenForArchivedChats()
        listenForPinnedChats()
        listenForLastMessages()
        listenForCallLogs()
    }

    private fun fetchOwnProfile() {
        if (myId == "anonimo") return
        val listener = usersCollection.document(myId).addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                _ownUser.value = snapshot.toObject(User::class.java)
            }
        }
        activeListeners.add(listener)
    }

    private fun listenForLastMessages() {
        if (myId == "anonimo") return
        
        val listener = messagesCollection.orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100) 
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val allMessages = snapshot.toObjects(FirebaseMessage::class.java)
                    val lastMsgsMap = mutableMapOf<String, FirebaseMessage>()
                    val unreadCountsMap = mutableMapOf<String, Int>()
                    
                    val myGroupIds = _groups.value.map { it.id }
                    
                    allMessages.forEach { msg ->
                        val isGroupMsg = myGroupIds.contains(msg.receiverId)
                        val isDirectMsg = msg.senderId == myId || msg.receiverId == myId
                        
                        if (isGroupMsg || isDirectMsg) {
                            val chatKey = if (isGroupMsg) msg.receiverId else (if (msg.senderId == myId) msg.receiverId else msg.senderId)
                            
                            if (!lastMsgsMap.containsKey(chatKey)) {
                                lastMsgsMap[chatKey] = msg
                            }
                            
                            if (msg.senderId != myId && !msg.read) {
                                val currentOpenChatId = _selectedUser.value?.uid ?: _selectedGroup.value?.id
                                if (chatKey != currentOpenChatId) {
                                    unreadCountsMap[chatKey] = (unreadCountsMap[chatKey] ?: 0) + 1
                                }
                            }
                        }
                    }
                    _lastMessages.value = lastMsgsMap
                    _unreadCounts.value = unreadCountsMap
                }
            }
        activeListeners.add(listener)
    }

    fun markMessagesAsRead(chatId: String, isGroup: Boolean) {
        val currentUserId = myId
        if (currentUserId == "anonimo") return

        val query = if (isGroup) {
            messagesCollection
                .whereEqualTo("receiverId", chatId)
        } else {
            messagesCollection
                .whereEqualTo("senderId", chatId)
                .whereEqualTo("receiverId", currentUserId)
        }

        query.get().addOnSuccessListener { documents ->
            if (documents != null && !documents.isEmpty) {
                val batch = db.batch()
                var updated = false
                for (document in documents) {
                    val msg = document.toObject(FirebaseMessage::class.java)
                    if (!msg.read && msg.senderId != currentUserId) {
                        batch.update(document.reference, "read", true)
                        updated = true
                    }
                }
                if (updated) {
                    batch.commit().addOnFailureListener { e ->
                        Log.e("FIRESTORE", "Error al confirmar batch read: ${e.message}")
                    }
                }
            }
        }.addOnFailureListener { e ->
            Log.e("FIRESTORE", "Error al obtener mensajes para marcar: ${e.message}")
        }
    }

    private fun listenForArchivedChats() {
        if (myId == "anonimo") return
        val listener = db.collection("users").document(myId).collection("archived")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    _archivedUserIds.value = snapshot.documents.map { it.id }.toSet()
                }
            }
        activeListeners.add(listener)
    }

    private fun listenForPinnedChats() {
        if (myId == "anonimo") return
        val listener = db.collection("users").document(myId).collection("pinned")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    _pinnedUserIds.value = snapshot.documents.map { it.id }.toSet()
                }
            }
        activeListeners.add(listener)
    }

    private fun listenForCallLogs() {
        if (myId == "anonimo") return
        val listener = db.collection("users").document(myId).collection("calls")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    _callLogs.value = snapshot.toObjects(CallLog::class.java)
                }
            }
        activeListeners.add(listener)
    }

    private fun addCallToLog(log: CallLog) {
        db.collection("users").document(myId).collection("calls").add(log)
    }

    fun toggleArchive(userId: String) {
        viewModelScope.launch {
            val isArchived = _archivedUserIds.value.contains(userId)
            val docRef = db.collection("users").document(myId).collection("archived").document(userId)
            if (isArchived) docRef.delete() else docRef.set(mapOf("active" to true))
        }
    }

    fun togglePin(userId: String) {
        viewModelScope.launch {
            val isPinned = _pinnedUserIds.value.contains(userId)
            val docRef = db.collection("users").document(myId).collection("pinned").document(userId)
            if (isPinned) docRef.delete() else docRef.set(mapOf("active" to true))
        }
    }

    /**
     * Sube la imagen a Firebase Storage (Bucket de Google Cloud)
     * y guarda la URL pública en el perfil del usuario en Firestore.
     */
    fun uploadProfilePicture(context: Context, uri: Uri) {
        if (myId == "anonimo") return
        
        viewModelScope.launch {
            try {
                // 1. Crear referencia en el Bucket (ejemplo: profile_pics/USER_ID.jpg)
                val fileRef = storage.reference.child("profile_pics/$myId.jpg")
                
                // 2. Subir el archivo directamente desde el URI
                val uploadTask = fileRef.putFile(uri).await()
                
                // 3. Obtener la URL de descarga pública
                val downloadUrl = fileRef.downloadUrl.await()
                
                // 4. Actualizar Firestore con la nueva URL (ya no Base64)
                usersCollection.document(myId).update("profilePicUrl", downloadUrl.toString())
                
                Log.d("STORAGE", "Foto subida con éxito: $downloadUrl")
            } catch (e: Exception) { 
                Log.e("STORAGE", "Error al subir foto a Storage: ${e.message}") 
            }
        }
    }

    private fun fetchUsers() {
        val listener = usersCollection.addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                _users.value = snapshot.toObjects(User::class.java).filter { it.uid != myId }
            }
        }
        activeListeners.add(listener)
    }

    private fun fetchGroups() {
        if (myId == "anonimo") return
        val listener = groupsCollection.whereArrayContains("members", myId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    _groups.value = snapshot.toObjects(Group::class.java)
                }
            }
        activeListeners.add(listener)
    }

    private fun listenForCalls() {
        if (myId == "anonimo") return
        val listener = callsCollection.document(myId).addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                val call = snapshot.toObject(CallInfo::class.java)
                if (call?.status == "RINGING") {
                    _currentCall.value = call
                    addCallToLog(CallLog(UUID.randomUUID().toString(), call.callerName, call.type, System.currentTimeMillis(), false))
                }
            }
        }
        activeListeners.add(listener)
    }

    fun startCall(type: String) {
        val targetId = _selectedGroup.value?.id ?: _selectedUser.value?.uid ?: return
        val targetName = _selectedGroup.value?.name ?: _selectedUser.value?.displayName ?: "Chat"
        val call = CallInfo(myId, ownUser.value?.displayName ?: "Alguien", targetId, type, "RINGING")
        if (_selectedGroup.value != null) {
            _selectedGroup.value?.members?.forEach { if (it != myId) callsCollection.document(it).set(call) }
        } else {
            callsCollection.document(targetId).set(call)
        }
        _currentCall.value = call.copy(status = "CALLING")
        addCallToLog(CallLog(UUID.randomUUID().toString(), targetName, type, System.currentTimeMillis(), true))
    }

    fun endCall() {
        val call = _currentCall.value ?: return
        callsCollection.document(myId).delete()
        if (_selectedGroup.value == null) callsCollection.document(call.receiverId).delete()
        _currentCall.value = null
    }

    fun acceptCall() {
        val call = _currentCall.value ?: return
        val updatedCall = call.copy(status = "ONGOING")
        _currentCall.value = updatedCall
        callsCollection.document(myId).set(updatedCall)
    }

    fun selectUser(user: User) {
        _selectedGroup.value = null
        _selectedUser.value = user
        markMessagesAsRead(user.uid, false)

        val newMap = _unreadCounts.value.toMutableMap()
        newMap.remove(user.uid)
        _unreadCounts.value = newMap
    }

    fun selectGroup(group: Group) {
        _selectedUser.value = null
        _selectedGroup.value = group
        markMessagesAsRead(group.id, true)

        val newMap = _unreadCounts.value.toMutableMap()
        newMap.remove(group.id)
        _unreadCounts.value = newMap
    }

    fun deselectUser() {
        _selectedUser.value = null
        _selectedGroup.value = null
    }

    fun createGroup(name: String, memberIds: List<String>) {
        val groupId = groupsCollection.document().id
        val newGroup = Group(groupId, name, memberIds + myId, myId)
        groupsCollection.document(groupId).set(newGroup)
    }

    fun dissolveGroup(groupId: String) {
        viewModelScope.launch {
            groupsCollection.document(groupId).delete()
            _selectedGroup.value = null
        }
    }

    fun clearChat() {
        val user = _selectedUser.value
        val group = _selectedGroup.value
        viewModelScope.launch {
            try {
                val query = if (group != null) messagesCollection.whereEqualTo("receiverId", group.id)
                else if (user != null) messagesCollection.whereIn("receiverId", listOf(myId, user.uid))
                else return@launch
                
                val snapshot = query.get().await()
                db.runBatch { batch ->
                    snapshot.documents.forEach { doc ->
                        val msg = doc.toObject(FirebaseMessage::class.java)
                        if (msg != null) {
                            if (group != null) {
                                batch.delete(doc.reference)
                            } else if (user != null) {
                                if ((msg.senderId == myId && msg.receiverId == user.uid) || (msg.senderId == user.uid && msg.receiverId == myId)) {
                                    batch.delete(doc.reference)
                                }
                            }
                        }
                    }
                }.await()
            } catch (e: Exception) { Log.e("FIRESTORE", "Error: ${e.message}") }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getMessages(): Flow<List<FirebaseMessage>> = combine(_selectedUser, _selectedGroup) { user, group -> user to group }
        .flatMapLatest { (user, group) ->
            if (user == null && group == null) return@flatMapLatest flowOf(emptyList())
            callbackFlow {
                val query = if (group != null) {
                    messagesCollection.whereEqualTo("receiverId", group.id)
                } else {
                    messagesCollection.whereIn("receiverId", listOf(myId, user!!.uid))
                }

                val subscription = query.addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        val allMsgs = snapshot.toObjects(FirebaseMessage::class.java)
                        val filtered = if (group != null) {
                            allMsgs
                        } else {
                            allMsgs.filter { 
                                (it.senderId == myId && it.receiverId == user!!.uid) || 
                                (it.senderId == user!!.uid && it.receiverId == myId) 
                            }
                        }
                        val sorted = filtered.sortedBy { it.timestamp }
                        
                        val chatId = group?.id ?: user?.uid
                        if (chatId != null) {
                            val hasUnreadFromOthers = sorted.any { it.senderId != myId && !it.read }
                            if (hasUnreadFromOthers) {
                                markMessagesAsRead(chatId, group != null)
                            }
                        }
                        
                        trySend(sorted)
                    }
                }
                awaitClose { subscription.remove() }
            }
        }

    fun sendMessage(content: String) {
        val receiverId = _selectedGroup.value?.id ?: _selectedUser.value?.uid ?: return
        val currentUserId = myId

        viewModelScope.launch {
            val messageData = hashMapOf(
                "senderId" to currentUserId,
                "receiverId" to receiverId,
                "content" to content,
                "timestamp" to System.currentTimeMillis(),
                "read" to false
            )

            messagesCollection.add(messageData)
                .addOnFailureListener { e ->
                    Log.e("FIRESTORE", "Error al enviar mensaje: ${e.message}")
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        activeListeners.forEach { it.remove() }
        activeListeners.clear()
    }
}
