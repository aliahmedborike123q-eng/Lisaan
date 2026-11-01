package com.lisaan.app.domain.repository

import com.lisaan.app.core.common.Resource
import com.lisaan.app.core.database.entities.UserEntity
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUserFlow: Flow<UserEntity?>
    suspend fun signInWithEmail(email: String, password: String): Resource<UserEntity>
    suspend fun signUpWithEmail(displayName: String, email: String, password: String): Resource<UserEntity>
    suspend fun signInWithGoogleIdToken(idToken: String): Resource<UserEntity>
    suspend fun continueAsGuest(): Resource<UserEntity>
    suspend fun sendPasswordResetEmail(email: String): Resource<Unit>
    suspend fun signOut(): Resource<Unit>
    suspend fun deleteAccountPermanently(passwordForReauth: String? = null): Resource<Unit>
}
