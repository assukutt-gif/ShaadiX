package com.assukutt.shaadix.data

import com.assukutt.shaadix.BuildConfig
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MultipartBody
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.google.gson.Gson
import com.google.gson.JsonParser
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Multipart
import retrofit2.http.Part
import retrofit2.http.PartMap
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class ApiEnvelope<T>(val success: Boolean = false, val message: String = "", val data: T? = null)
data class ApiUser(val id: String = "", val name: String = "", val email: String = "", val phone: String = "", val role: String = "customer")
data class AuthPayload(val accessToken: String = "", val refreshToken: String = "", val user: ApiUser = ApiUser())
data class LoginRequest(val identifier: String, val password: String)
data class RegisterRequest(val name: String, val email: String, val phone: String, val password: String, val role: String)
data class OtpRequest(val email: String, val code: String, val purpose: String = "verify")
data class ForgotPasswordRequest(val email: String)
data class ResetPasswordRequest(val resetToken: String, val password: String)
data class ResetTokenResult(val resetToken: String = "")
data class CreateBookingRequest(
    val providerId: String, val serviceId: String, val eventType: String, val eventDate: String,
    val startTime: String, val endTime: String, val guestCount: Int,
    val eventLocation: String, val specialRequirements: String = ""
)
data class BookingRequestResult(
    @SerializedName("_id") val id: String = "", val eventType: String = "", val eventDate: String = "",
    val startTime: String = "18:00", val endTime: String = "21:00", val guestCount: Int = 0,
    val totalAmount: Double = 0.0, val bookingStatus: String = "PENDING", val eventLocation: String = "",
    val serviceId: JsonElement? = null, val paymentStatus:String="PENDING"
)
data class PaymentOrderRequest(val bookingId: String, val paymentMethod: String)
data class PaymentVerifyRequest(@SerializedName("razorpay_order_id") val orderId:String,@SerializedName("razorpay_payment_id") val paymentId:String,@SerializedName("razorpay_signature") val signature:String)
data class ReviewRequest(val bookingId: String, val rating: Int, val comment: String)
data class ProviderProfileRequest(val businessName:String,val category:String,val description:String,val phone:String,val email:String,val address:String,val city:String)
data class ServiceCreateRequest(val category:String,val title:String,val description:String,val price:Int,val pricingUnit:String,val location:String,val features:List<String>)
data class AvailabilitySlotRequest(val dayOfWeek:Int,val startTime:String,val endTime:String,val isAvailable:Boolean=true)
data class ProviderAvailabilityRequest(val availability:List<AvailabilitySlotRequest>,val blockedDates:List<String>)
data class PaymentOrderResult(val paymentId: String = "", val orderId: String? = null, val amount: Long = 0, val currency: String = "INR", val keyId: String? = null)
data class ServiceApiModel(
    @SerializedName("_id") val id: String = "", val title: String = "", val category: String = "",
    val description: String = "", val providerId: JsonElement? = null, val price: Double = 0.0,
    val pricingUnit: String = "flat", val location: String = "", val images: List<String> = emptyList(),
    val features: List<String> = emptyList(), val availability:List<String> = emptyList()
) {
    fun toDomain(): Service {
        val providerJson = providerId?.takeIf { it.isJsonObject }?.asJsonObject
        val providerIdentifier = if (providerId?.isJsonObject == true) providerId.asJsonObject.get("_id")?.asString else providerId?.takeIf { it.isJsonPrimitive }?.asString
        val providerName = providerJson?.get("businessName")?.asString ?: "ShaadiX Provider"
        val city = providerJson?.get("city")?.asString ?: location.ifBlank { "India" }
        val rating = providerJson?.get("rating")?.asDouble ?: 4.8
        val reviews = providerJson?.get("totalReviews")?.asInt ?: 0
        val demoImage=SampleData.services.firstOrNull{it.category.equals(category,true)||it.category.contains(category,true)}?.image?:SampleData.services.first().image
        return Service(id.ifBlank { "demo-$title" }, title, category, providerName, city, price.toInt(), rating, reviews,
            images.firstOrNull().orEmpty().ifBlank{demoImage}, description, features.ifEmpty { listOf("Professional event team", "Flexible package") }, providerIdentifier,images.ifEmpty{listOf(demoImage)},availability)
    }
}
data class FavoriteApiModel(@SerializedName("_id") val id: String = "", val providerId: JsonElement? = null) {
    fun providerIdentifier(): String? = if (providerId?.isJsonObject == true) providerId.asJsonObject.get("_id")?.asString else providerId?.takeIf { it.isJsonPrimitive }?.asString
}
data class NotificationApiModel(@SerializedName("_id") val id: String = "", val title: String = "", val message: String = "", val type: String = "SYSTEM", val isRead: Boolean = false, val createdAt: String = "")

interface ShaadiXApi {
    @POST("auth/login") suspend fun login(@Body request: LoginRequest): retrofit2.Response<ApiEnvelope<AuthPayload>>
    @POST("auth/register") suspend fun register(@Body request: RegisterRequest): retrofit2.Response<ApiEnvelope<JsonElement>>
    @POST("auth/verify-otp") suspend fun verifyOtp(@Body request: OtpRequest): retrofit2.Response<ApiEnvelope<AuthPayload>>
    @POST("auth/verify-otp") suspend fun verifyResetOtp(@Body request: OtpRequest): retrofit2.Response<ApiEnvelope<ResetTokenResult>>
    @POST("auth/forgot-password") suspend fun forgotPassword(@Body request: ForgotPasswordRequest): retrofit2.Response<ApiEnvelope<JsonElement>>
    @POST("auth/reset-password") suspend fun resetPassword(@Body request: ResetPasswordRequest): retrofit2.Response<ApiEnvelope<JsonElement>>
    @GET("services/search") suspend fun searchServices(@Query("keyword") keyword: String?, @Query("category") category: String?, @Query("city") city: String?, @Query("minPrice") minPrice: Int?, @Query("maxPrice") maxPrice: Int?, @Query("rating") rating: Double?, @Query("eventDate") eventDate: String?, @Query("page") page: Int = 1, @Query("limit") limit: Int = 30): retrofit2.Response<ApiEnvelope<List<ServiceApiModel>>>
    @GET("services/{id}") suspend fun service(@Path("id") id: String): retrofit2.Response<ApiEnvelope<ServiceApiModel>>
    @POST("bookings") suspend fun createBooking(@Body request: CreateBookingRequest): retrofit2.Response<ApiEnvelope<BookingRequestResult>>
    @GET("bookings") suspend fun bookings(@Query("page") page: Int = 1, @Query("limit") limit: Int = 30): retrofit2.Response<ApiEnvelope<List<BookingRequestResult>>>
    @PUT("bookings/{id}/cancel") suspend fun cancelBooking(@Path("id") id: String, @Body body: Map<String, String>): retrofit2.Response<ApiEnvelope<JsonElement>>
    @GET("favorites") suspend fun favorites(): retrofit2.Response<ApiEnvelope<List<FavoriteApiModel>>>
    @POST("favorites/{providerId}") suspend fun addFavorite(@Path("providerId") providerId: String): retrofit2.Response<ApiEnvelope<JsonElement>>
    @DELETE("favorites/{providerId}") suspend fun removeFavorite(@Path("providerId") providerId: String): retrofit2.Response<ApiEnvelope<JsonElement>>
    @GET("notifications") suspend fun notifications(@Query("page") page: Int = 1): retrofit2.Response<ApiEnvelope<JsonElement>>
    @POST("payments/create-order") suspend fun createPaymentOrder(@Body request: PaymentOrderRequest): retrofit2.Response<ApiEnvelope<PaymentOrderResult>>
    @POST("payments/verify") suspend fun verifyPayment(@Body request:PaymentVerifyRequest):retrofit2.Response<ApiEnvelope<JsonElement>>
    @POST("reviews") suspend fun createReview(@Body request: ReviewRequest): retrofit2.Response<ApiEnvelope<JsonElement>>
    @GET("provider/bookings") suspend fun providerBookings(@Query("page") page: Int = 1): retrofit2.Response<ApiEnvelope<List<BookingRequestResult>>>
    @PUT("provider/availability") suspend fun setAvailability(@Body request:ProviderAvailabilityRequest):retrofit2.Response<ApiEnvelope<JsonElement>>
    @PUT("bookings/{id}/confirm") suspend fun confirmBooking(@Path("id") id: String): retrofit2.Response<ApiEnvelope<JsonElement>>
    @PUT("bookings/{id}/reject") suspend fun rejectBooking(@Path("id") id: String, @Body body: Map<String, String>): retrofit2.Response<ApiEnvelope<JsonElement>>
    @GET("provider/dashboard") suspend fun providerDashboard(): retrofit2.Response<ApiEnvelope<JsonElement>>
    @POST("providers") suspend fun createProvider(@Body request: ProviderProfileRequest): retrofit2.Response<ApiEnvelope<JsonElement>>
    @PUT("providers/{id}") suspend fun updateProvider(@Path("id") id:String,@Body request:ProviderProfileRequest):retrofit2.Response<ApiEnvelope<JsonElement>>
    @POST("services") suspend fun createService(@Body request:ServiceCreateRequest):retrofit2.Response<ApiEnvelope<ServiceApiModel>>
    @Multipart @POST("services") suspend fun createServiceWithImages(@PartMap fields:Map<String,@JvmSuppressWildcards okhttp3.RequestBody>,@Part images:List<MultipartBody.Part>):retrofit2.Response<ApiEnvelope<ServiceApiModel>>
    @PUT("services/{id}") suspend fun updateService(@Path("id") id:String,@Body request:ServiceCreateRequest):retrofit2.Response<ApiEnvelope<ServiceApiModel>>
    @DELETE("services/{id}") suspend fun deleteService(@Path("id") id:String):retrofit2.Response<ApiEnvelope<JsonElement>>
    @GET("admin/dashboard") suspend fun adminDashboard(): retrofit2.Response<ApiEnvelope<JsonElement>>
    @GET("admin/users") suspend fun adminUsers(@Query("page") page:Int=1):retrofit2.Response<ApiEnvelope<List<JsonElement>>>
    @GET("admin/providers") suspend fun adminProviders(@Query("page") page:Int=1):retrofit2.Response<ApiEnvelope<List<JsonElement>>>
    @GET("admin/bookings") suspend fun adminBookings(@Query("page") page:Int=1):retrofit2.Response<ApiEnvelope<List<JsonElement>>>
    @GET("admin/reviews") suspend fun adminReviews(@Query("page") page:Int=1,@Query("reported") reported:Boolean?=null):retrofit2.Response<ApiEnvelope<List<JsonElement>>>
    @GET("categories") suspend fun categories():retrofit2.Response<ApiEnvelope<List<JsonElement>>>
    @PUT("admin/providers/{id}/verify") suspend fun verifyProvider(@Path("id") id:String):retrofit2.Response<ApiEnvelope<JsonElement>>
    @DELETE("admin/users/{id}") suspend fun deactivateUser(@Path("id") id:String):retrofit2.Response<ApiEnvelope<JsonElement>>
    @DELETE("admin/providers/{id}") suspend fun deactivateProvider(@Path("id") id:String):retrofit2.Response<ApiEnvelope<JsonElement>>
    @PUT("admin/reviews/{id}/moderate") suspend fun moderateReview(@Path("id") id:String,@Body body:Map<String,Boolean>):retrofit2.Response<ApiEnvelope<JsonElement>>
    @POST("admin/categories") suspend fun createCategory(@Body body:Map<String,String>):retrofit2.Response<ApiEnvelope<JsonElement>>
}

class ShaadiXApiFactory(tokenStore: SecureTokenStore) {
    private val authenticator = SessionAuthenticator(tokenStore)
    private val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
        val token = runBlocking { tokenStore.accessToken() }
        val request = chain.request().newBuilder().apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer " + token) }.build()
        chain.proceed(request)
    }).authenticator(authenticator).connectTimeout(6, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    val api: ShaadiXApi = Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(client)
        .addConverterFactory(GsonConverterFactory.create()).build().create(ShaadiXApi::class.java)
}

private class SessionAuthenticator(private val tokens: SecureTokenStore) : Authenticator {
    override fun authenticate(route: okhttp3.Route?, response: Response): Request? = runBlocking {
        if (responseCount(response) >= 2) return@runBlocking null
        val refresh = tokens.refreshToken() ?: return@runBlocking null
        val body = Gson().toJson(mapOf("refreshToken" to refresh)).toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(BuildConfig.API_BASE_URL + "auth/refresh").post(body).build()
        val refreshResponse = OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build().newCall(request).execute()
        refreshResponse.use { result ->
            if (!result.isSuccessful) { tokens.clear(); return@runBlocking null }
            val json = result.body?.string()?.let(JsonParser::parseString)?.asJsonObject ?: return@runBlocking null
            val data = json.getAsJsonObject("data") ?: return@runBlocking null
            val access = data.get("accessToken")?.asString.orEmpty()
            val nextRefresh = data.get("refreshToken")?.asString.orEmpty()
            if (access.isBlank() || nextRefresh.isBlank()) return@runBlocking null
            tokens.save(access, nextRefresh)
            response.request.newBuilder().header("Authorization", "Bearer " + access).build()
        }
    }
    private fun responseCount(response: Response): Int { var count=1;var prior=response.priorResponse;while(prior!=null){count++;prior=prior.priorResponse};return count }
}

class ShaadiXBackendRepository(private val api: ShaadiXApi, private val tokens: SecureTokenStore) {
    suspend fun signIn(identifier: String, password: String): ApiUser {
        val response = api.login(LoginRequest(identifier.trim(), password))
        val payload = response.requireData()
        tokens.save(payload.accessToken, payload.refreshToken)
        return payload.user
    }
    suspend fun signUp(name: String, email: String, phone: String, password: String, role: String) {
        api.register(RegisterRequest(name.trim(), email.trim(), phone.filter { it.isDigit() || it == '+' }, password, role)).requireData()
    }
    suspend fun verifyOtp(email: String, code: String): ApiUser {
        val payload = api.verifyOtp(OtpRequest(email.trim(), code)).requireData()
        tokens.save(payload.accessToken, payload.refreshToken)
        return payload.user
    }
    suspend fun requestPasswordReset(email: String) { api.forgotPassword(ForgotPasswordRequest(email.trim())).requireData() }
    suspend fun resetPassword(email: String, code: String, password: String) {
        val token=api.verifyResetOtp(OtpRequest(email.trim(),code,"reset")).requireData().resetToken
        api.resetPassword(ResetPasswordRequest(token,password)).requireData()
    }
    suspend fun loadServices(keyword: String = "", category: String = "All", city: String = "", minPrice: Int? = null, maxPrice: Int? = null, rating: Double? = null, eventDate: String? = null, page: Int = 1): List<Service> {
        val backendCategory = when (category) { "Venues" -> "Function Hall"; "Decor" -> "Decoration"; "Makeup" -> "Makeup Artists"; "Music" -> "DJ & Music"; "Planners" -> "Event Planner"; else -> category.takeUnless { it == "All" } }
        return api.searchServices(keyword.ifBlank { null }, backendCategory, city.ifBlank { null }, minPrice, maxPrice, rating, eventDate, page).requireData().map { it.toDomain() }
    }
    suspend fun createBooking(request: CreateBookingRequest): BookingRequestResult = api.createBooking(request).requireData()
    suspend fun loadBookings(): List<BookingRequestResult> = api.bookings().requireData()
    suspend fun loadNotifications(): List<NotificationApiModel> {
        val data=api.notifications().requireData()
        val items=if(data.isJsonObject)data.asJsonObject.getAsJsonArray("items") else null
        return items?.map{Gson().fromJson(it,NotificationApiModel::class.java)}?:emptyList()
    }
    suspend fun cancelBooking(id: String) { api.cancelBooking(id, emptyMap()).requireData() }
    suspend fun loadFavorites(): List<String> = api.favorites().requireData().mapNotNull { it.providerIdentifier() }
    suspend fun toggleFavorite(providerId: String, remove: Boolean) { if (remove) api.removeFavorite(providerId).requireData() else api.addFavorite(providerId).requireData() }
    suspend fun createPaymentOrder(bookingId: String, method: String): PaymentOrderResult = api.createPaymentOrder(PaymentOrderRequest(bookingId, method)).requireData()
    suspend fun verifyPayment(orderId:String,paymentId:String,signature:String){api.verifyPayment(PaymentVerifyRequest(orderId,paymentId,signature)).requireData()}
    suspend fun submitReview(bookingId: String, rating: Int, comment: String) { api.createReview(ReviewRequest(bookingId,rating,comment)).requireData() }
    suspend fun providerBookings(): List<BookingRequestResult> = api.providerBookings().requireData()
    suspend fun providerBookingAction(id: String, accept: Boolean) { if(accept)api.confirmBooking(id).requireData() else api.rejectBooking(id,emptyMap()).requireData() }
    suspend fun setAvailability(days:List<Int>,start:String,end:String,blockedDates:List<String>){api.setAvailability(ProviderAvailabilityRequest(days.map{AvailabilitySlotRequest(it,start,end)},blockedDates)).requireData()}
    suspend fun providerDashboard(): JsonElement = api.providerDashboard().requireData()
    suspend fun adminDashboard(): JsonElement = api.adminDashboard().requireData()
    suspend fun adminList(section:String):List<JsonElement> = when(section){"users"->api.adminUsers().requireData();"providers"->api.adminProviders().requireData();"bookings"->api.adminBookings().requireData();"reviews"->api.adminReviews(reported=true).requireData();else->api.categories().requireData()}
    suspend fun adminAction(section:String,id:String,action:String){when(section){"providers"->if(action=="verify")api.verifyProvider(id).requireData()else api.deactivateProvider(id).requireData();"users"->api.deactivateUser(id).requireData();"reviews"->api.moderateReview(id,mapOf("isVisible" to false)).requireData()}}
    suspend fun adminCreateCategory(name:String,description:String){api.createCategory(mapOf("name" to name,"description" to description)).requireData()}
    suspend fun saveProviderProfile(id:String?,request:ProviderProfileRequest){if(id.isNullOrBlank())api.createProvider(request).requireData()else api.updateProvider(id,request).requireData()}
    suspend fun createService(request:ServiceCreateRequest):Service=api.createService(request).requireData().toDomain()
    suspend fun createService(request:ServiceCreateRequest,images:List<MultipartBody.Part>):Service=if(images.isEmpty())createService(request)else{
        val fields=mapOf("category" to request.category,"title" to request.title,"description" to request.description,"price" to request.price.toString(),"pricingUnit" to request.pricingUnit,"location" to request.location).mapValues{it.value.toRequestBody("text/plain".toMediaType())}
        api.createServiceWithImages(fields,images).requireData().toDomain()
    }
    suspend fun updateService(id:String,request:ServiceCreateRequest):Service=api.updateService(id,request).requireData().toDomain()
    suspend fun deleteService(id:String){api.deleteService(id).requireData()}
    suspend fun logout() { tokens.clear() }
}

private fun <T> retrofit2.Response<ApiEnvelope<T>>.requireData(): T {
    if (!isSuccessful) throw IllegalStateException(errorBody()?.string()?.take(240) ?: "Request failed (${code()}).")
    val envelope = body() ?: throw IllegalStateException("The server returned an empty response.")
    if (!envelope.success || envelope.data == null) throw IllegalStateException(envelope.message.ifBlank { "The server could not complete the request." })
    return envelope.data
}

