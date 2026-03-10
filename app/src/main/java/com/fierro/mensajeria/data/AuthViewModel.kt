package com.fierro.mensajeria.data

import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _currentUser = MutableStateFlow(auth.currentUser)
    val currentUser = _currentUser.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError = _authError.asStateFlow()

    fun loginOrRegister(email: String, password: String, name: String, onSuccess: () -> Unit) {
        _authError.value = null
        
        if (email.isBlank() || password.isBlank()) {
            _authError.value = "Por favor, completa todos los campos"
            return
        }

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                _currentUser.value = auth.currentUser
                onSuccess()
            }
            .addOnFailureListener { loginError ->
                if (loginError.message?.contains("no user", ignoreCase = true) == true || 
                    loginError.message?.contains("record", ignoreCase = true) == true) {
                    
                    if (name.isBlank()) {
                        _authError.value = "Ingresa un nombre para registrarte"
                        return@addOnFailureListener
                    }

                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnSuccessListener { res ->
                            val user = User(res.user!!.uid, email, name)
                            
                            // Enviar correo de verificación
                            res.user?.sendEmailVerification()
                                ?.addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        Log.d("AUTH", "Correo de verificación enviado")
                                    }
                                }

                            db.collection("users").document(user.uid).set(user)
                                .addOnSuccessListener {
                                    _currentUser.value = auth.currentUser
                                    onSuccess()
                                }
                        }
                        .addOnFailureListener { regError ->
                            _authError.value = "Error al registrar: ${regError.localizedMessage}"
                        }
                } else {
                    _authError.value = "Credenciales incorrectas o error de red"
                }
            }
    }

    fun signInWithGoogle(idToken: String, onSuccess: () -> Unit) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnSuccessListener { res ->
                val firebaseUser = res.user
                if (firebaseUser != null) {
                    // Verificar si el usuario ya existe en Firestore
                    db.collection("users").document(firebaseUser.uid).get()
                        .addOnSuccessListener { document ->
                            if (!document.exists()) {
                                // Crear nuevo usuario automáticamente si no existe
                                val newUser = User(
                                    uid = firebaseUser.uid,
                                    email = firebaseUser.email ?: "",
                                    displayName = firebaseUser.displayName ?: "Usuario de Google",
                                    profilePicUrl = firebaseUser.photoUrl?.toString()
                                )
                                db.collection("users").document(firebaseUser.uid).set(newUser)
                                    .addOnSuccessListener {
                                        _currentUser.value = firebaseUser
                                        onSuccess()
                                    }
                            } else {
                                _currentUser.value = firebaseUser
                                onSuccess()
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                _authError.value = "Error con Google: ${e.localizedMessage}"
            }
    }

    fun clearError() {
        _authError.value = null
    }

    fun logout() {
        auth.signOut()
        _currentUser.value = null
    }
}
