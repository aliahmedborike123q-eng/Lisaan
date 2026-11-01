package com.lisaan.app.core.network

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val currentUser = firebaseAuth.currentUser

        val token = try {
            if (currentUser != null) {
                Tasks.await(currentUser.getIdToken(false)).token
            } else null
        } catch (e: Exception) {
            null
        }

        val requestBuilder = originalRequest.newBuilder()
            .header("Accept", "application/json")
            .header("X-Client-Platform", "Android-Lisaan")

        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        return chain.proceed(requestBuilder.build())
    }
}
