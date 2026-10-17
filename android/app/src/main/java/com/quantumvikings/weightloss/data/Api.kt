package com.quantumvikings.weightloss.data

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Mirrors server/src/app/api/v1. Field names match the JSON exactly. */
interface Api {
    @POST("api/v1/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @GET("api/v1/me")
    suspend fun me(): MeResponse

    @PUT("api/v1/me")
    suspend fun saveProfile(@Body body: ProfileDto): OkResponse

    @GET("api/v1/foods/search")
    suspend fun searchFoods(@Query("q") q: String, @Query("limit") limit: Int = 30): FoodsResponse

    @GET("api/v1/foods/barcode/{code}")
    suspend fun foodByBarcode(@Path("code") code: String): FoodResponse

    @GET("api/v1/diary")
    suspend fun diary(@Query("date") date: String): DiaryResponse

    @POST("api/v1/diary")
    suspend fun addEntry(@Body body: AddEntryRequest): IdResponse

    @PATCH("api/v1/diary/{id}")
    suspend fun updateEntry(@Path("id") id: Int, @Body body: UpdateEntryRequest): OkResponse

    @DELETE("api/v1/diary/{id}")
    suspend fun deleteEntry(@Path("id") id: Int): OkResponse

    @GET("api/v1/weight")
    suspend fun weights(@Query("from") from: String? = null): WeightsResponse

    @POST("api/v1/weight")
    suspend fun saveWeight(@Body body: WeightDto): OkResponse
}

@Serializable data class LoginRequest(val email: String, val password: String)
@Serializable data class LoginResponse(val token: String, val user: UserDto)
@Serializable data class UserDto(val id: Int, val name: String, val email: String)
@Serializable data class OkResponse(val ok: Boolean = true)
@Serializable data class IdResponse(val id: Int)

@Serializable
data class ProfileDto(
    val sex: String,
    val birthDate: String,
    val heightCm: Double,
    val activity: String,
    val goalWeightKg: Double,
    val weeklyLossKg: Double,
    val locale: String,
)

@Serializable
data class TargetDto(
    val bmr: Int,
    val tdee: Int,
    val kcal: Int,
    val proteinG: Int,
    val fatG: Int,
    val carbsG: Int,
    val clamped: Boolean,
)

@Serializable data class MeResponse(val user: UserDto, val profile: ProfileDto? = null, val target: TargetDto? = null)

@Serializable
data class Per100Dto(
    val kcal: Double,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
    val sugar: Double? = null,
    val salt: Double? = null,
)

@Serializable
data class FoodDto(
    val id: Int,
    val source: String,
    val barcode: String? = null,
    val name: String,
    val brand: String? = null,
    val per100g: Per100Dto,
    val servingG: Double? = null,
)

@Serializable data class FoodsResponse(val foods: List<FoodDto>)
@Serializable data class FoodResponse(val food: FoodDto)

@Serializable
data class EntryDto(
    val id: Int,
    val meal: String,
    val foodId: Int,
    val name: String,
    val brand: String? = null,
    val grams: Double,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
)

@Serializable data class TotalsDto(val kcal: Double, val protein: Double, val carbs: Double, val fat: Double)
@Serializable data class DiaryResponse(val date: String, val entries: List<EntryDto>, val totals: TotalsDto, val target: TargetDto? = null)
@Serializable data class AddEntryRequest(val date: String, val meal: String, val foodId: Int, val grams: Double)
@Serializable data class UpdateEntryRequest(val grams: Double? = null, val meal: String? = null)
@Serializable data class WeightDto(val date: String, val weightKg: Double)
@Serializable data class WeightsResponse(val weights: List<WeightDto>)
