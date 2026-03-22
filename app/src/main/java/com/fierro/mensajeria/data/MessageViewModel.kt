package com.fierro.mensajeria.data

import android.content.Context
import android.graphics.Bitmap
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.UUID

class MessageViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()
    
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

    private val _blockedUserIds = MutableStateFlow<Set<String>>(emptySet())
    val blockedUserIds = _blockedUserIds.asStateFlow()

    private val _lastMessages = MutableStateFlow<Map<String, FirebaseMessage>>(emptyMap())
    val lastMessages = _lastMessages.asStateFlow()

    private val _unreadCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val unreadCounts = _unreadCounts.asStateFlow()

    private val _callLogs = MutableStateFlow<List<CallLog>>(emptyList())
    val callLogs = _callLogs.asStateFlow()

    private val _isBiometricEnabled = MutableStateFlow<Boolean?>(null)
    val isBiometricEnabled = _isBiometricEnabled.asStateFlow()

    init {
        if (auth.currentUser != null) {
            loadInitialData()
        }
        startDisappearingMessagesWorker()
    }

    private fun startDisappearingMessagesWorker() {
        viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                try {
                    val expired = messagesCollection.whereLessThan("expiresAt", now).get().await()
                    if (!expired.isEmpty) {
                        val batch = db.batch()
                        expired.documents.forEach { batch.delete(it.reference) }
                        batch.commit().await()
                    }
                } catch (e: Exception) { Log.e("WORKER", "Error cleanup: ${e.message}") }
                delay(10000) // Revisar cada 10 segundos
            }
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
        _blockedUserIds.value = emptySet()
        _lastMessages.value = emptyMap()
        _unreadCounts.value = emptyMap()
        _callLogs.value = emptyList()
        _isBiometricEnabled.value = null
    }

    private fun loadInitialData() {
        fetchOwnProfile()
        fetchUsers()
        fetchGroups()
        listenForCalls()
        listenForArchivedChats()
        listenForPinnedChats()
        listenForBlockedChats()
        listenForLastMessages()
        listenForCallLogs()
        checkBiometricSettings()
    }

    private fun checkBiometricSettings() {
        if (myId == "anonimo") return
        usersCollection.document(myId).get().addOnSuccessListener { 
            _isBiometricEnabled.value = it.getBoolean("biometricEnabled") ?: false
        }
    }

    fun toggleBiometric(enabled: Boolean, onPromptRequired: () -> Unit = {}) {
        if (myId == "anonimo") return
        if (enabled) {
            onPromptRequired()
        } else {
            updateBiometricSettings(false)
        }
    }

    fun updateBiometricSettings(enabled: Boolean) {
        usersCollection.document(myId).update("biometricEnabled", enabled).addOnSuccessListener {
            _isBiometricEnabled.value = enabled
        }
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
        
        val listener = combine(
            messagesCollection.orderBy("timestamp", Query.Direction.DESCENDING).limit(100).snapshots(),
            _blockedUserIds
        ) { snapshot, blocked -> snapshot to blocked }
            .onEach { (snapshot, blocked) ->
                val allMessages = snapshot.toObjects(FirebaseMessage::class.java)
                val lastMsgsMap = mutableMapOf<String, FirebaseMessage>()
                val unreadCountsMap = mutableMapOf<String, Int>()
                
                val myGroupIds = _groups.value.map { it.id }
                
                allMessages.forEach { msg ->
                    // Filter messages from blocked users
                    if (msg.senderId != myId && blocked.contains(msg.senderId)) return@forEach

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
            }.launchIn(viewModelScope)
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

    fun setTyping(targetId: String?) {
        if (myId == "anonimo") return
        usersCollection.document(myId).update("typingTo", targetId)
    }

    fun addReaction(messageId: String, reaction: String) {
        viewModelScope.launch {
            try {
                val msgRef = messagesCollection.document(messageId)
                db.runTransaction { transaction ->
                    val snapshot = transaction.get(msgRef)
                    val reactions = snapshot.get("reactions") as? Map<String, String> ?: emptyMap()
                    val updatedReactions = reactions.toMutableMap()
                    updatedReactions[myId] = reaction
                    transaction.update(msgRef, "reactions", updatedReactions)
                }.await()
            } catch (e: Exception) { Log.e("FIRESTORE", "Error reaction: ${e.message}") }
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

    private fun listenForBlockedChats() {
        if (myId == "anonimo") return
        val listener = db.collection("users").document(myId).collection("blocked")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    _blockedUserIds.value = snapshot.documents.map { it.id }.toSet()
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

    fun toggleBlock(userId: String) {
        viewModelScope.launch {
            val isBlocked = _blockedUserIds.value.contains(userId)
            val docRef = db.collection("users").document(myId).collection("blocked").document(userId)
            if (isBlocked) docRef.delete() else docRef.set(mapOf("active" to true))
        }
    }

    fun uploadProfilePicture(context: Context, uri: Uri) {
        if (myId == "anonimo") return
        viewModelScope.launch {
            try {
                val fileRef = storage.reference.child("profile_pics/$myId.jpg")
                fileRef.putFile(uri).await()
                val downloadUrl = fileRef.downloadUrl.await()
                usersCollection.document(myId).update("profilePicUrl", downloadUrl.toString()).await()
            } catch (e: Exception) { Log.e("STORAGE", "Error en perfil: ${e.message}") }
        }
    }

    fun sendImageMessage(bitmap: Bitmap, disappearingSeconds: Int? = null) {
        val messageId = UUID.randomUUID().toString()
        
        viewModelScope.launch {
            try {
                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                val data = baos.toByteArray()
                val imageRef = storage.reference.child("chat_images/$messageId.jpg")
                imageRef.putBytes(data).await()
                val imageUrl = imageRef.downloadUrl.await().toString()
                sendMessage("📷 FOTO_MSG:$imageUrl", disappearingSeconds)
            } catch (e: Exception) { Log.e("STORAGE", "Error: ${e.message}") }
        }
    }

    fun sendImageMessageFromUri(uri: Uri, disappearingSeconds: Int? = null) {
        val messageId = UUID.randomUUID().toString()
        
        viewModelScope.launch {
            try {
                val imageRef = storage.reference.child("chat_images/$messageId.jpg")
                imageRef.putFile(uri).await()
                val imageUrl = imageRef.downloadUrl.await().toString()
                sendMessage("📷 FOTO_MSG:$imageUrl", disappearingSeconds)
            } catch (e: Exception) { Log.e("STORAGE", "Error: ${e.message}") }
        }
    }

    fun sendLocationMessage(lat: Double, lng: Double, disappearingSeconds: Int? = null) {
        sendMessage("📍 LOCATION_MSG:$lat,$lng", disappearingSeconds)
    }

    fun sendAudioMessage(uri: Uri, disappearingSeconds: Int? = null) {
        val messageId = UUID.randomUUID().toString()
        
        viewModelScope.launch {
            try {
                val audioRef = storage.reference.child("chat_audios/$messageId.mp4")
                audioRef.putFile(uri).await()
                val audioUrl = audioRef.downloadUrl.await().toString()
                sendMessage("🎤 AUDIO_MSG:$audioUrl", disappearingSeconds)
            } catch (e: Exception) { Log.e("STORAGE", "Error: ${e.message}") }
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
            // Check if I am blocked by the receiver before calling
            viewModelScope.launch {
                try {
                    val isBlockedByReceiver = db.collection("users").document(targetId)
                        .collection("blocked").document(myId).get().await().exists()
                    if (!isBlockedByReceiver) {
                        callsCollection.document(targetId).set(call)
                        _currentCall.value = call.copy(status = "CALLING")
                        addCallToLog(CallLog(UUID.randomUUID().toString(), targetName, type, System.currentTimeMillis(), true))
                    } else {
                        Log.d("BLOCK", "No se puede iniciar llamada: El destinatario te ha bloqueado.")
                    }
                } catch (e: Exception) {
                    Log.e("BLOCK", "Error al verificar bloqueo en llamada: ${e.message}")
                    // Ante la duda (error de permisos), podríamos optar por no permitir la llamada o reintentar
                }
            }
            return
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

    fun leaveGroup(groupId: String) {
        viewModelScope.launch {
            try {
                val groupRef = groupsCollection.document(groupId)
                db.runTransaction { transaction ->
                    val snapshot = transaction.get(groupRef)
                    val members = snapshot.get("members") as? List<String> ?: emptyList()
                    val updatedMembers = members.filter { it != myId }
                    if (updatedMembers.isEmpty()) {
                        transaction.delete(groupRef)
                    } else {
                        transaction.update(groupRef, "members", updatedMembers)
                    }
                }.await()
                _selectedGroup.value = null
            } catch (e: Exception) { Log.e("FIRESTORE", "Error al salir del grupo: ${e.message}") }
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
    fun getMessages(): Flow<List<FirebaseMessage>> = combine(_selectedUser, _selectedGroup, _blockedUserIds) { user, group, blocked -> 
        Triple(user, group, blocked) 
    }.flatMapLatest { (user, group, blocked) ->
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
                                val isOurChat = (it.senderId == myId && it.receiverId == user!!.uid) || 
                                              (it.senderId == user!!.uid && it.receiverId == myId)
                                // Only show if it's our chat AND sender is not blocked
                                isOurChat && !blocked.contains(it.senderId)
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

    fun sendMessage(content: String, disappearingSeconds: Int? = null) {
        val receiverId = _selectedGroup.value?.id ?: _selectedUser.value?.uid ?: return
        val currentUserId = myId
        viewModelScope.launch {
            try {
                // Si es un mensaje directo, verificamos si el destinatario nos tiene bloqueados
                if (_selectedUser.value != null) {
                    val blockedDoc = db.collection("users").document(receiverId)
                        .collection("blocked").document(currentUserId).get().await()
                    
                    if (blockedDoc.exists()) {
                        Log.d("BLOCK", "No se puede enviar: Estás bloqueado por el destinatario.")
                        return@launch
                    }
                }

                val expiresAt = disappearingSeconds?.let { System.currentTimeMillis() + it * 1000 }
                val messageData = hashMapOf(
                    "senderId" to currentUserId,
                    "receiverId" to receiverId,
                    "content" to content,
                    "timestamp" to System.currentTimeMillis(),
                    "read" to false,
                    "reactions" to emptyMap<String, String>(),
                    "expiresAt" to expiresAt
                )
                messagesCollection.add(messageData).await()
            } catch (e: Exception) {
                Log.e("FIRESTORE", "Error al enviar mensaje: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        activeListeners.forEach { it.remove() }
        activeListeners.clear()
    }

    private fun addCallToLog(log: CallLog) {
        db.collection("users").document(myId).collection("calls").add(log)
    }

    private fun Query.snapshots(): Flow<com.google.firebase.firestore.QuerySnapshot> = callbackFlow {
        val registration = addSnapshotListener { snapshot, error ->
            if (error != null) close(error)
            else if (snapshot != null) trySend(snapshot)
        }
        awaitClose { registration.remove() }
    }
}
