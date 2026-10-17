package com.quantumvikings.weightloss.data

import android.content.Context
import com.quantumvikings.weightloss.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Holds the bearer token and the API client. One per process, created in MainActivity. */
class Session(context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    private val _token = MutableStateFlow(prefs.getString(KEY_TOKEN, null))
    val token: StateFlow<String?> = _token

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(Interceptor { chain ->
            val request = _token.value
                ?.let { chain.request().newBuilder().header("Authorization", "Bearer $it").build() }
                ?: chain.request()
            val response = chain.proceed(request)
            // An expired or revoked token sends the user back to the login screen.
            if (response.code == 401 && request.url.encodedPath != "/api/v1/login") signOut()
            response
        })
        .build()

    val api: Api = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(Api::class.java)

    suspend fun signIn(email: String, password: String) {
        val res = api.login(LoginRequest(email.trim(), password))
        prefs.edit().putString(KEY_TOKEN, res.token).apply()
        _token.value = res.token
    }

    fun signOut() {
        prefs.edit().remove(KEY_TOKEN).apply()
        _token.value = null
    }

    /** A message fit for the screen, from whatever an API call threw. */
    fun describe(e: Throwable): String = when (e) {
        is HttpException -> e.response()?.errorBody()?.string()
            ?.let { runCatching { json.decodeFromString<ErrorBody>(it).error }.getOrNull() }
            ?: "Serverfout (${e.code()})"
        is IOException -> "Geen verbinding met de server"
        else -> e.message ?: "Onbekende fout"
    }

    @kotlinx.serialization.Serializable
    private data class ErrorBody(val error: String)

    private companion object {
        const val KEY_TOKEN = "token"
    }
}
