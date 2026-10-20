package com.quantumvikings.weightloss.data

import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/** Mirrors server/src/app/api/v1. Field names match the JSON exactly. */
interface Api {
    @POST("api/v1/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    /** [today] is the phone's date, so the weekend part of the target follows the user's clock. */
    @GET("api/v1/me")
    suspend fun me(@Query("today") today: String = java.time.LocalDate.now().toString()): MeResponse

    @PUT("api/v1/me")
    suspend fun saveProfile(@Body body: ProfileDto): OkResponse

    @GET("api/v1/foods/search")
    suspend fun searchFoods(@Query("q") q: String, @Query("limit") limit: Int = 30): FoodsResponse

    @GET("api/v1/foods/barcode/{code}")
    suspend fun foodByBarcode(@Path("code") code: String): FoodResponse

    @GET("api/v1/foods/{id}")
    suspend fun food(@Path("id") id: Int): FoodDetailResponse

    @GET("api/v1/foods/recent")
    suspend fun recentFoods(@Query("meal") meal: String? = null): FoodsResponse

    @GET("api/v1/foods/favorites")
    suspend fun favoriteFoods(): FoodsResponse

    @POST("api/v1/foods/{id}/favorite")
    suspend fun addFavorite(@Path("id") id: Int): OkResponse

    @DELETE("api/v1/foods/{id}/favorite")
    suspend fun removeFavorite(@Path("id") id: Int): OkResponse

    @POST("api/v1/foods")
    suspend fun createFood(@Body body: CreateFoodRequest): FoodResponse

    @GET("api/v1/diary")
    suspend fun diary(@Query("date") date: String, @Query("today") today: String): DiaryResponse

    @POST("api/v1/diary/copy")
    suspend fun copyDiary(@Body body: CopyRequest): CopyResponse

    @POST("api/v1/diary")
    suspend fun addEntry(@Body body: AddEntryRequest): IdResponse

    @POST("api/v1/diary")
    suspend fun addQuickEntry(@Body body: QuickEntryRequest): IdResponse

    @POST("api/v1/diary/share")
    suspend fun shareMeal(@Body body: ShareRequest): ShareResponse

    @GET("api/v1/household")
    suspend fun household(): HouseholdResponse

    @GET("api/v1/meals")
    suspend fun savedMeals(): SavedMealsResponse

    @POST("api/v1/meals")
    suspend fun saveMeal(@Body body: SaveMealRequest): IdResponse

    @DELETE("api/v1/meals/{id}")
    suspend fun deleteSavedMeal(@Path("id") id: Int): OkResponse

    @POST("api/v1/meals/{id}/log")
    suspend fun logSavedMeal(@Path("id") id: Int, @Body body: LogMealRequest): AddedResponse

    @PATCH("api/v1/diary/{id}")
    suspend fun updateEntry(@Path("id") id: Int, @Body body: UpdateEntryRequest): OkResponse

    @DELETE("api/v1/diary/{id}")
    suspend fun deleteEntry(@Path("id") id: Int): OkResponse

    @GET("api/v1/weight")
    suspend fun weights(@Query("from") from: String? = null): WeightsResponse

    @POST("api/v1/weight")
    suspend fun saveWeight(@Body body: WeightDto): OkResponse

    @PUT("api/v1/steps")
    suspend fun saveSteps(@Body body: StepsDto): OkResponse

    @PUT("api/v1/water")
    suspend fun saveWater(@Body body: WaterDto): OkResponse

    @GET("api/v1/summary")
    suspend fun summary(@Query("from") from: String, @Query("to") to: String): SummaryResponse

    @GET("api/v1/recipes")
    suspend fun recipes(): FoodsResponse

    @GET("api/v1/recipes/{id}")
    suspend fun recipe(@Path("id") id: Int): RecipeDto

    @POST("api/v1/recipes")
    suspend fun createRecipe(@Body body: RecipeRequest): FoodResponse

    @PUT("api/v1/recipes/{id}")
    suspend fun updateRecipe(@Path("id") id: Int, @Body body: RecipeRequest): FoodResponse

    @DELETE("api/v1/recipes/{id}")
    suspend fun deleteRecipe(@Path("id") id: Int): OkResponse

    @GET("api/v1/fasting")
    suspend fun fasting(): FastingResponse

    @POST("api/v1/fasting")
    suspend fun startFast(@Body body: StartFastRequest): FastResponse

    @PATCH("api/v1/fasting/{id}")
    suspend fun updateFast(@Path("id") id: Int, @Body body: UpdateFastRequest): FastResponse

    @DELETE("api/v1/fasting/{id}")
    suspend fun deleteFast(@Path("id") id: Int): OkResponse

    @POST("api/v1/foods/{id}/units")
    suspend fun addUnit(@Path("id") foodId: Int, @Body body: NewUnitRequest): FoodUnitDto

    @DELETE("api/v1/foods/{id}/units/{unitId}")
    suspend fun deleteUnit(@Path("id") foodId: Int, @Path("unitId") unitId: Int): OkResponse

    @GET("api/v1/measurements")
    suspend fun measurements(@Query("from") from: String? = null): MeasurementsResponse

    @PUT("api/v1/measurements")
    suspend fun saveMeasurement(@Body body: MeasurementDto): OkResponse

    @GET("api/v1/photos")
    suspend fun photos(): PhotosResponse

    @Multipart
    @POST("api/v1/photos")
    suspend fun uploadPhoto(@Part("date") date: RequestBody, @Part photo: MultipartBody.Part): PhotoDto

    @Streaming
    @GET("api/v1/photos/{id}")
    suspend fun photo(@Path("id") id: Int): ResponseBody

    @DELETE("api/v1/photos/{id}")
    suspend fun deletePhoto(@Path("id") id: Int): OkResponse

    /** The Monday-to-Sunday week holding [date], compared with the week before. */
    @GET("api/v1/week")
    suspend fun week(@Query("date") date: String, @Query("today") today: String = java.time.LocalDate.now().toString()): WeekResponse
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
    // No defaults: kotlinx leaves out values equal to their default, and the server keeps what it
    // does not get, so going back to 25/30/45 or switching the fixed goal off must be sent.
    val proteinPct: Int,
    val fatPct: Int,
    val carbsPct: Int,
    /** A kcal goal set by hand; null = computed from the plan. */
    val fixedKcal: Int?,
    /** Extra on Saturday and Sunday, taken from the weekdays. */
    val weekendExtraKcal: Int,
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
    val steps: Int? = null,
    /** Already included in [kcal]. */
    val stepsKcal: Int = 0,
    val fixed: Boolean = false,
    /** What the weekend setting added (weekend) or took (weekday) today; included in [kcal]. */
    val weekendKcal: Int = 0,
    val proteinPct: Int = 25,
    val fatPct: Int = 30,
    val carbsPct: Int = 45,
    /** At least this much fiber; at most this much sugar, saturated fat and salt. */
    val fiberG: Int = 0,
    val sugarMaxG: Int = 0,
    val saturatedFatMaxG: Int = 0,
    val saltMaxG: Int = 0,
)

@Serializable
data class MeResponse(
    val user: UserDto,
    val profile: ProfileDto? = null,
    val target: TargetDto? = null,
    /** The other members of the household, to share a meal with. */
    val household: List<MemberDto> = emptyList(),
)

@Serializable data class MemberDto(val id: Int, val name: String)

@Serializable
data class Per100Dto(
    val kcal: Double,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val saturatedFat: Double? = null,
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
    /** What the pack calls that portion ("1 slice"); null = just a portion. */
    val servingLabel: String? = null,
    val unit: String = "g",
    val category: String? = null,
    /** Only in the recent list: the amount used last time. */
    val lastGrams: Double? = null,
)

@Serializable data class FoodsResponse(val foods: List<FoodDto>)
@Serializable data class FoodResponse(val food: FoodDto)

@Serializable
data class EntryDto(
    val id: Int,
    val meal: String,
    /** Null for a quick entry: typed-in kcal, no food and no amount. */
    val foodId: Int? = null,
    val name: String,
    val brand: String? = null,
    val category: String? = null,
    val unit: String = "g",
    val grams: Double,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double? = null,
    val sugar: Double? = null,
    val saturatedFat: Double? = null,
    val salt: Double? = null,
) {
    val quick get() = foodId == null
}

@Serializable
data class TotalsDto(
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double = 0.0,
    val sugar: Double = 0.0,
    val saturatedFat: Double = 0.0,
    val salt: Double = 0.0,
)
@Serializable data class DiaryResponse(
    val date: String,
    val entries: List<EntryDto>,
    val totals: TotalsDto,
    val target: TargetDto? = null,
    val waterMl: Int = 0,
    /** Days in a row with something logged, up to today. */
    val streak: Int = 0,
)
@Serializable data class AddEntryRequest(val date: String, val meal: String, val foodId: Int, val grams: Double)
@Serializable
data class QuickEntryRequest(
    val date: String,
    val meal: String,
    val name: String,
    val kcal: Double,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
)

/** grams for a food entry; name and kcal for a quick entry. */
@Serializable
data class UpdateEntryRequest(
    val grams: Double? = null,
    val meal: String? = null,
    val name: String? = null,
    val kcal: Double? = null,
)
@Serializable data class WeightDto(val date: String, val weightKg: Double)
@Serializable data class WeightsResponse(val weights: List<WeightDto>)

@Serializable data class NutrientDto(val code: String, val name: String, val value: Double, val unit: String)
@Serializable data class NutrientGroupDto(val name: String, val nutrients: List<NutrientDto>)
@Serializable data class IngredientDto(val foodId: Int, val name: String, val share: Double)

@Serializable
data class FoodDetailResponse(
    val food: FoodDto,
    val favorite: Boolean = false,
    val units: List<FoodUnitDto> = emptyList(),
    val nutrientGroups: List<NutrientGroupDto> = emptyList(),
    val ingredients: List<IngredientDto> = emptyList(),
    val synonyms: List<String> = emptyList(),
    val remark: String? = null,
    val traces: List<String> = emptyList(),
    val fortified: List<String> = emptyList(),
)

@Serializable
data class CreateFoodRequest(
    val name: String,
    val brand: String? = null,
    val kcal: Double,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val servingG: Double? = null,
)

@Serializable data class WaterDto(val date: String, val ml: Int)
@Serializable data class StepsDto(val date: String, val steps: Int)

@Serializable
data class DaySummaryDto(
    val date: String,
    val kcal: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val waterMl: Int,
    val targetKcal: Int? = null,
    val steps: Int? = null,
)

@Serializable data class SummaryResponse(val days: List<DaySummaryDto>)

@Serializable data class CopyRequest(val from: String, val to: String, val meal: String? = null)
@Serializable data class CopyResponse(val copied: Int)

@Serializable data class RecipeIngredientDto(val grams: Double, val food: FoodDto)

@Serializable
data class RecipeDto(
    val id: Int,
    val name: String,
    val servings: Int,
    val cookedG: Double? = null,
    val food: FoodDto,
    val ingredients: List<RecipeIngredientDto>,
)

@Serializable data class RecipeIngredientRequest(val foodId: Int, val grams: Double)

@Serializable
data class RecipeRequest(
    val name: String,
    val servings: Int,
    val cookedG: Double? = null,
    val ingredients: List<RecipeIngredientRequest>,
)

/** Times are ISO-8601 instants. */
@Serializable data class FastDto(val id: Int, val startedAt: String, val endedAt: String? = null, val goalHours: Int)
@Serializable data class FastingResponse(val current: FastDto? = null, val recent: List<FastDto> = emptyList())
@Serializable data class FastResponse(val fast: FastDto)
@Serializable data class StartFastRequest(val goalHours: Int, val startedAt: String? = null)
@Serializable data class UpdateFastRequest(val goalHours: Int? = null, val startedAt: String? = null, val end: Boolean? = null)

/** [factor]: their portion relative to mine. Without [entryIds] the whole meal goes. */
@Serializable
data class ShareRequest(
    val date: String,
    val meal: String,
    val toUserId: Int,
    val factor: Double = 1.0,
    val entryIds: List<Int>? = null,
)
@Serializable data class ShareResponse(val shared: Int)
@Serializable data class HouseholdResponse(val members: List<MemberDto>)

/** [grams] is null for a quick entry. */
@Serializable data class SavedMealItemDto(val name: String, val grams: Double? = null, val unit: String = "g", val kcal: Int)
@Serializable data class SavedMealDto(val id: Int, val name: String, val kcal: Int, val items: List<SavedMealItemDto>)
@Serializable data class SavedMealsResponse(val meals: List<SavedMealDto>)
@Serializable data class SaveMealRequest(val name: String, val date: String, val meal: String)
@Serializable data class LogMealRequest(val date: String, val meal: String)
@Serializable data class AddedResponse(val added: Int)

/** A named amount of a food; [mine] = added by this user, so it may be deleted. */
@Serializable data class FoodUnitDto(val id: Int, val name: String, val grams: Double, val mine: Boolean = false)
@Serializable data class NewUnitRequest(val name: String, val grams: Double)

/** One day's body measurements; null = not measured. */
@Serializable
data class MeasurementDto(
    val date: String,
    val waistCm: Double? = null,
    val hipCm: Double? = null,
    val chestCm: Double? = null,
    val thighCm: Double? = null,
    val armCm: Double? = null,
    val neckCm: Double? = null,
    val bodyFatPct: Double? = null,
)
@Serializable data class MeasurementsResponse(val measurements: List<MeasurementDto>)

@Serializable data class PhotoDto(val id: Int, val date: String)
@Serializable data class PhotosResponse(val photos: List<PhotoDto>)

@Serializable
data class WeekAverageDto(
    val kcal: Int? = null,
    val targetKcal: Int? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val waterMl: Int? = null,
    val steps: Int? = null,
)
@Serializable data class WeekPreviousDto(val kcal: Int? = null, val daysLogged: Int = 0, val daysOnTarget: Int = 0)
@Serializable data class WeekWeightDto(val average: Double? = null, val previousAverage: Double? = null, val change: Double? = null)
@Serializable data class WeekWaistDto(val cm: Double? = null, val change: Double? = null)
@Serializable data class WeekFastingDto(val count: Int = 0, val reached: Int = 0)

@Serializable
data class WeekResponse(
    val start: String,
    val end: String,
    val days: List<DaySummaryDto>,
    /** Days up to today; less than 7 while the week is running. */
    val daysCounted: Int,
    val daysLogged: Int,
    /** Between 70% and 105% of that day's target. */
    val daysOnTarget: Int,
    val average: WeekAverageDto,
    val previous: WeekPreviousDto,
    val weight: WeekWeightDto,
    val waist: WeekWaistDto,
    val fasting: WeekFastingDto,
)
