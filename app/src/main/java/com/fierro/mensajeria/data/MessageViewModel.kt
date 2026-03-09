package com.fierro.mensajeria.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
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
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.UUID

class MessageViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val messagesCollection = db.collection("messages")
    private val usersCollection = db.collection("users")
    private val groupsCollection = db.collection("groups")
    private val callsCollection = db.collection("calls")

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
        loadInitialData()
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
        usersCollection.document(myId).addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                _ownUser.value = snapshot.toObject(User::class.java)
            }
        }
    }

    private fun listenForLastMessages() {
        messagesCollection.orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val allMessages = snapshot.toObjects(FirebaseMessage::class.java)
                    val lastMsgsMap = mutableMapOf<String, FirebaseMessage>()
                    val unreadCountsMap = mutableMapOf<String, Int>()
                    
                    allMessages.forEach { msg ->
                        val partnerId = if (msg.senderId == myId) msg.receiverId else msg.senderId
                        if (!lastMsgsMap.containsKey(partnerId)) {
                            lastMsgsMap[partnerId] = msg
                        }
                        if (msg.receiverId == myId) {
                            val currentPartner = _selectedUser.value?.uid ?: _selectedGroup.value?.id
                            if (partnerId != currentPartner) {
                                unreadCountsMap[partnerId] = (unreadCountsMap[partnerId] ?: 0) + 1
                            }
                        }
                    }
                    _lastMessages.value = lastMsgsMap
                    _unreadCounts.value = unreadCountsMap
                }
            }
    }

    private fun listenForArchivedChats() {
        if (auth.currentUser == null) return
        db.collection("users").document(myId).collection("archived")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    _archivedUserIds.value = snapshot.documents.map { it.id }.toSet()
                }
            }
    }

    private fun listenForPinnedChats() {
        if (auth.currentUser == null) return
        db.collection("users").document(myId).collection("pinned")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    _pinnedUserIds.value = snapshot.documents.map { it.id }.toSet()
                }
            }
    }

    private fun listenForCallLogs() {
        if (auth.currentUser == null) return
        db.collection("users").document(myId).collection("calls")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    _callLogs.value = snapshot.toObjects(CallLog::class.java)
                }
            }
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

    fun uploadProfilePicture(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                
                // Comprimir imagen para que quepa en Firestore
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
                val byteArray = outputStream.toByteArray()
                val base64String = "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.DEFAULT)
                
                usersCollection.document(myId).update("profilePicUrl", base64String)
                Log.d("FIRESTORE", "Foto de perfil actualizada con éxito (Base64)")
            } catch (e: Exception) {
                Log.e("FIRESTORE", "Error al procesar foto: ${e.message}")
            }
        }
    }

    private fun fetchUsers() {
        usersCollection.addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                _users.value = snapshot.toObjects(User::class.java).filter { it.uid != myId }
            }
        }
    }

    private fun fetchGroups() {
        groupsCollection.whereArrayContains("members", myId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    _groups.value = snapshot.toObjects(Group::class.java)
                }
            }
    }

    private fun listenForCalls() {
        callsCollection.document(myId).addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                val call = snapshot.toObject(CallInfo::class.java)
                if (call?.status == "RINGING") {
                    _currentCall.value = call
                    addCallToLog(CallLog(UUID.randomUUID().toString(), call.callerName, call.type, System.currentTimeMillis(), false))
                }
            }
        }
    }

    fun startCall(type: String) {
        val targetId = _selectedGroup.value?.id ?: _selectedUser.value?.uid ?: return
        val targetName = _selectedGroup.value?.name ?: _selectedUser.value?.displayName ?: "Chat"
        val call = CallInfo(myId, auth.currentUser?.displayName ?: "Alguien", targetId, type, "RINGING")
        
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
        val newMap = _unreadCounts.value.toMutableMap()
        newMap.remove(user.uid)
        _unreadCounts.value = newMap
    }

    fun selectGroup(group: Group) {
        _selectedUser.value = null
        _selectedGroup.value = group
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
                else if (user != null) messagesCollection.whereIn("senderId", listOf(myId, user.uid))
                else return@launch
                val snapshot = query.get().await()
                db.runBatch { batch ->
                    snapshot.documents.forEach { doc ->
                        if (group != null) batch.delete(doc.reference)
                        else if (user != null) {
                            val msg = doc.toObject(FirebaseMessage::class.java)
                            if (msg != null && ((msg.senderId == myId && msg.receiverId == user.uid) || (msg.senderId == user.uid && msg.receiverId == myId))) batch.delete(doc.reference)
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
                val subscription = messagesCollection.addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        val allMsgs = snapshot.toObjects(FirebaseMessage::class.java)
                        val filtered = if (group != null) allMsgs.filter { it.receiverId == group.id }
                        else allMsgs.filter { (it.senderId == myId && it.receiverId == user!!.uid) || (it.senderId == user!!.uid && it.receiverId == myId) }
                        trySend(filtered.sortedBy { it.timestamp })
                    }
                }
                awaitClose { subscription.remove() }
            }
        }

    fun sendMessage(content: String) {
        val receiverId = _selectedGroup.value?.id ?: _selectedUser.value?.uid ?: return
        viewModelScope.launch {
            messagesCollection.add(FirebaseMessage(myId, receiverId, content, System.currentTimeMillis()))
        }
    }
}
