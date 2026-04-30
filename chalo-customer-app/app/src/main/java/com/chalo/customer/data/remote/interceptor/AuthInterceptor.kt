package com.chalo.customer.data.remote.interceptor

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor() : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = getToken(forceRefresh = false)
        val request = chain.request().newBuilder().apply {
            if (token != null) addHeader("Authorization", "Bearer $token")
        }.build()

        val response = chain.proceed(request)

        // On 401, try refreshing the Firebase ID token once
        if (response.code == 401) {
            response.close()
            val refreshedToken = getToken(forceRefresh = true)
            if (refreshedToken != null) {
                val retryRequest = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $refreshedToken")
                    .build()
                return chain.proceed(retryRequest)
            }
        }

        return response
    }

    private fun getToken(forceRefresh: Boolean): String? {
        return try {
            runBlocking {
                FirebaseAuth.getInstance().currentUser
                    ?.getIdToken(forceRefresh)
                    ?.await()
                    ?.token
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get Firebase ID token")
            null
        }
    }
}
