package com.fierro.mensajeria.data

import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _currentUser = MutableStateFlow(auth.currentUser)
    val currentUser = _currentUser.asStateFlow()

    fun loginOrRegister(email: String, password: String, name: String, onSuccess: () -> Unit) {
        if (email.isBlank() || password.isBlank()) {
            Log.e("AUTH", "Email o contraseña vacíos")
            return
        }

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                Log.d("AUTH", "Login exitoso: ${auth.currentUser?.email}")
                _currentUser.value = auth.currentUser
                onSuccess()
            }
            .addOnFailureListener { loginError ->
                Log.w("AUTH", "Login fallido, intentando registro: ${loginError.message}")
                
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener { res ->
                        Log.d("AUTH", "Registro exitoso en Auth")
                        val user = User(res.user!!.uid, email, name)
                        
                        // Guardar perfil en Firestore
                        db.collection("users").document(user.uid).set(user)
                            .addOnSuccessListener {
                                Log.d("AUTH", "Perfil guardado en Firestore")
                                _currentUser.value = auth.currentUser
                                onSuccess()
                            }
                            .addOnFailureListener { dbError ->
                                Log.e("AUTH", "Error al guardar perfil: ${dbError.message}")
                            }
                    }
                    .addOnFailureListener { regError ->
                        Log.e("AUTH", "Error en registro: ${regError.message}")
                    }
            }
    }

    fun logout() {
        auth.signOut()
        _currentUser.value = null
    }
}
