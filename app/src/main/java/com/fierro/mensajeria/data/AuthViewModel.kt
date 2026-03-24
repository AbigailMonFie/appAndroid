package com.fierro.mensajeria.data

import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class AuthViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _currentUser = MutableStateFlow(auth.currentUser)
    val currentUser = _currentUser.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError = _authError.asStateFlow()

    private fun generateBeeCode(): String {
        return UUID.randomUUID().toString().substring(0, 7).uppercase()
    }

    fun login(email: String, password: String, onSuccess: () -> Unit) {
        _authError.value = null
        if (email.isBlank() || password.isBlank()) {
            _authError.value = "Completa todos los campos"
            return
        }

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                _currentUser.value = auth.currentUser
                updateFcmToken()
                onSuccess()
            }
            .addOnFailureListener { e ->
                val errorCode = (e as? FirebaseAuthException)?.errorCode
                _authError.value = when(errorCode) {
                    "ERROR_WRONG_PASSWORD" -> "Contraseña incorrecta"
                    "ERROR_USER_NOT_FOUND", "INVALID_LOGIN_CREDENTIALS" -> "Usuario no encontrado o credenciales inválidas"
                    "ERROR_INVALID_EMAIL" -> "Email inválido"
                    else -> e.localizedMessage
                }
            }
    }

    fun register(email: String, password: String, name: String, onSuccess: () -> Unit) {
        _authError.value = null
        if (email.isBlank() || password.isBlank() || name.isBlank()) {
            _authError.value = "Completa todos los campos"
            return
        }
        if (password.length < 6) {
            _authError.value = "La contraseña debe tener al menos 6 caracteres"
            return
        }

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { res ->
                val user = User(
                    uid = res.user!!.uid, 
                    email = email, 
                    displayName = name,
                    beeCode = generateBeeCode()
                )
                db.collection("users").document(user.uid).set(user)
                    .addOnSuccessListener {
                        _currentUser.value = auth.currentUser
                        updateFcmToken()
                        onSuccess()
                    }
            }
            .addOnFailureListener { e ->
                val errorCode = (e as? FirebaseAuthException)?.errorCode
                _authError.value = if (errorCode == "ERROR_EMAIL_ALREADY_IN_USE") "Este email ya está registrado" 
                                 else e.localizedMessage
            }
    }

    fun signInWithGoogle(idToken: String, onSuccess: () -> Unit) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnSuccessListener { res ->
                val firebaseUser = res.user
                if (firebaseUser != null) {
                    db.collection("users").document(firebaseUser.uid).get()
                        .addOnSuccessListener { document ->
                            if (!document.exists()) {
                                val newUser = User(
                                    uid = firebaseUser.uid,
                                    email = firebaseUser.email ?: "",
                                    displayName = firebaseUser.displayName ?: "Usuario de Google",
                                    profilePicUrl = firebaseUser.photoUrl?.toString(),
                                    beeCode = generateBeeCode()
                                )
                                db.collection("users").document(firebaseUser.uid).set(newUser)
                                    .addOnSuccessListener {
                                        _currentUser.value = firebaseUser
                                        updateFcmToken()
                                        onSuccess()
                                    }
                            } else {
                                _currentUser.value = firebaseUser
                                updateFcmToken()
                                onSuccess()
                            }
                        }
                }
            }
            .addOnFailureListener { e -> _authError.value = "Error con Google: ${e.localizedMessage}" }
    }

    fun updateFcmToken() {
        val uid = auth.currentUser?.uid ?: return
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                db.collection("users").document(uid).update("fcmToken", token)
            }
        }
    }

    fun clearError() { _authError.value = null }

    fun logout() {
        val uid = auth.currentUser?.uid
        if (uid != null) db.collection("users").document(uid).update("fcmToken", null)
        auth.signOut()
        _currentUser.value = null
        _authError.value = null
    }
}
