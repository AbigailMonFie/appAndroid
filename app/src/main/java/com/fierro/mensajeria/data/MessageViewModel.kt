package com.fierro.mensajeria.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class MessageViewModel(private val repository: MessageDao) : ViewModel() {

    val allMessages: Flow<List<MessageEntity>> = repository.getAllMessages()

    fun sendMessage(content: String) {
        viewModelScope.launch {
            val newMessage = MessageEntity(
                senderId = "me",
                receiverId = "other",
                content = content,
                timestamp = System.currentTimeMillis(),
                isSentByMe = true
            )
            repository.insertMessage(newMessage)
        }
    }
}

class MessageViewModelFactory(private val repository: MessageDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MessageViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MessageViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
