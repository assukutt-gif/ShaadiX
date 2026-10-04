Warning: truncated output (original token count: 18950)
Total output lines: 586

package com.assukutt.shaadix.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.assukutt.shaadix.AppContext
import com.assukutt.shaadix.MainActivity
import com.assukutt.shaadix.data.*
import com.assukutt.shaadix.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.NumberFormat
import java.util.Locale

private fun price(n:Int)="₹"+NumberFormat.getNumberInstance(Locale("en","IN")).format(n)
data class AdminListRow(val id:String,val title:String,val subtitle:String,val action:String="")
private fun imageParts(context:Context,uris:List<Uri>):List<MultipartBody.Part> = uris.mapIndexedNotNull{index,uri->runCatching{
    val type=context.contentResolver.getType(uri)?:return@mapIndexedNotNull null
    if(type !in setOf("image/jpeg","image/png","image/webp","image/avif"))return@mapIndexedNotNull null
    val bytes=context.contentResolver.openInputStream(uri)?.use{it.readBytes()}?:return@mapIndexedNotNull null
    if(bytes.size>8*1024*1024)return@mapIndexedNotNull null
    val body=bytes.toRequestBody(type.toMediaTypeOrNull())
    MultipartBody.Part.createFormData("images","shaadix-image-${index}.${type.substringAfter('/')}",body)
}.getOrNull()}

class ShaadiXViewModel(private val repository:ShaadiXBackendRepository=(AppContext.context.applicationContext as AppContext).container.backend):ViewModel(){
    var signedIn by mutableStateOf(false); var name by mutableStateOf("Ananya Menon"); var email by mutableStateOf("")
    var search by mutableStateOf(""); var category by mutableStateOf("All"); var selected by mutableStateOf(SampleData.services.first())
    var cityFilter by mutableStateOf("");var minPriceFilter by mutableStateOf("");var maxPriceFilter by mutableStateOf("");var ratingFilter by mutableStateOf(0.0);var dateFilter by mutableStateOf("")
    var sortOption by mutableStateOf("Recommended")
    var servicePage by mutableIntStateOf(1);var hasMoreServices by mutableStateOf(false)
    var bookingOpen by mutableStateOf(false); var message by mutableStateOf<String?>(null)
    var role by mutableStateOf("customer"); var phone by mutableStateOf(""); var guestMode by mutableStateOf(true); var authLoading by mutableStateOf(false)
    var otpSent by mutableStateOf(false)
    var passwordResetRequested by mutableStateOf(false)
    var currentBooking by mutableStateOf<Booking?>(null)
    var checkoutOrder by mutableStateOf<PaymentOrderResult?>(null)
    val services=mutableStateListOf<Service>().apply{addAll(SampleData.services)}
    private val _servicesState=MutableStateFlow<UiState<List<Service>>>(UiState.Success(SampleData.services))
    val servicesState:StateFlow<UiState<List<Service>>> = _servicesState
    val bookings=mutableStateListOf<Booking>().apply{addAll(SampleData.initialBookings)}
    val providerBookings=mutableStateListOf<Booking>()
    val favorites=mutableStateListOf<String>()
    val notificationItems=mutableStateListOf<NotificationApiModel>()
    var providerEarnings by mutableStateOf("₹0")
    var providerId by mutableStateOf("")
    var providerPendingCount by mutableIntStateOf(0)
    var providerCompletedCount by mutableIntStateOf(0)
    var adminStats by mutableStateOf(mapOf("users" to "—","providers" to "—","bookings" to "—","revenue" to "—"))
    val adminRows=mutableStateListOf<AdminListRow>()
    var adminLoading by mutableStateOf(false)
    init { refreshServices() }
    fun results():List<Service>{
        val found=services.filter{(category=="All"||categoryMatches(it.category,category))&&(search.isBlank()||listOf(it.title,it.provider,it.area,it.category).any{s->s.contains(search,true)})&&(cityFilter.isBlank()||it.area.contains(cityFilter,true))&&(minPriceFilter.toIntOrNull()?.let{n->it.price>=n}?:true)&&(maxPriceFilter.toIntOrNull()?.let{n->it.price<=n}?:true)&&(ratingFilter==0.0||it.rating>=ratingFilter)}
        return when(sortOption){"Price: low to high"->found.sortedBy{it.price};"Price: high to low"->found.sortedByDescending{it.price};"Highest rated"->found.sortedByDescending{it.rating};else->found}
    }
    fun countByCategory(value:String)=services.count{categoryMatches(it.category,value)}
    private fun categoryMatches(actual:String,selected:String):Boolean {
        val groups=listOf(listOf("Venues","Function Hall","Marriage Hall"),listOf("Decor","Decoration"),listOf("Makeup","Makeup Artists"),listOf("Music","DJ & Music"),listOf("Planners","Event Planner"))
        val group=groups.firstOrNull{values->values.any{it.equals(selected,true)||it.equals(actual,true)}}?:listOf(selected)
        return group.any{actual.equals(it,true)}
    }
    fun refreshServices(){servicePage=1
        viewModelScope.launch {
            _servicesState.value=UiState.Loading
            runCatching{repository.loadServices(search,category,cityFilter,minPriceFilter.toIntOrNull(),maxPriceFilter.toIntOrNull(),ratingFilter.takeIf{it>0},dateFilter.takeIf{it.matches(Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$"))},1)}.onSuccess{items->services.clear();services.addAll(items);hasMoreServices=items.size>=30;_servicesState.value=if(items.isEmpty())UiState.Success(emptyList())else UiState.Success(items)}
                .onFailure{_servicesState.value=UiState.Error("Showing sample listings. Connect the ShaadiX API to load live services.")}
        }
    }
    fun loadMoreServices(){if(!hasMoreServices)return;viewModelScope.launch{runCatching{repository.loadServices(search,category,cityFilter,minPriceFilter.toIntOrNull(),maxPriceFilter.toIntOrNull(),ratingFilter.takeIf{it>0},dateFilter.takeIf{it.matches(Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$"))},servicePage+1)}.onSuccess{items->servicePage++;services.addAll(items);hasMoreServices=items.size>=30;_servicesState.value=UiState.Success(services.toList())}}}
    fun favorite(s:Service){
        val key=s.providerId?:s.id;val remove=key in favorites
        if(remove)favorites.remove(key)else favorites.add(key)
        if(!guestMode&&!s.providerId.isNullOrBlank())viewModelScope.launch{runCatching{repository.toggleFavorite(s.providerId,remove)}.onFailure{message=it.localizedMessage?:"Could not update favourites."}}
    }
    fun isFavorite(s:Service)=s.id in favorites||(s.providerId!=null&&s.providerId in favorites)
    fun exploreAsGuest(){guestMode=true;role="customer";signedIn=true}
    fun signIn(e:String,p:String){
        if(!e.contains("@")||p.length<8){message="Enter a valid email and a password with at least 8 characters.";return}
        authLoading=true
        viewModelScope.launch{runCatching{repository.signIn(e,p)}.onSuccess{user->email=user.email;phone=user.phone;name=user.name;role=user.role;guestMode=false;signedIn=true;loadAccountData()}.onFailure{message="Sign in failed. Check your details or explore as a guest."}.also{authLoading=false}}
    }
    fun signUp(n:String,e:String,phone:String,p:String,accountRole:String){
        if(n.isBlank()||!e.contains("@")||phone.filter(Char::isDigit).length<10||p.length<10){message="Add your name, a valid email and phone, and a password with at least 10 characters.";return}
        authLoading=true
        viewModelScope.launch{runCatching{repository.signUp(n,e,phone,p,accountRole)}.onSuccess{email=e.trim();this@ShaadiXViewModel.phone=phone;name=n.trim();role=accountRole;otpSent=true;message="Account created. Check your email for the verification code."}.onFailure{message="Could not create your account. Check the ShaadiX API connection and your details."}.also{authLoading=false}}
    }
    fun verifyOtp(code:String){
        if(code.length!=6){message="Enter the six-digit code sent to your email.";return}
        authLoading=true
        viewModelScope.launch{runCatching{repository.verifyOtp(email,code)}.onSuccess{user->name=user.name;phone=user.phone;role=user.role;guestMode=false;otpSent=false;signedIn=true;loadAccountData()}.onFailure{message="That code was not accepted. Check it and try again."}.also{authLoading=false}}
    }
    fun requestPasswordReset(address:String){
        if(!address.contains("@")){message="Enter the email address on your account first.";return}
        email=address.trim();authLoading=true
        viewModelScope.launch{runCatching{repository.requestPasswordReset(email)}.onSuccess{passwordResetRequested=true;message="If an account matches, a reset code has been sent."}.onFailure{message="Could not request a reset code. Check your email and connection."}.also{authLoading=false}}
    }
    fun resetPassword(code:String,password:String){
        if(code.length!=6||password.length<10){message="Enter the six-digit code and a password with at least 10 characters.";return}
        authLoading=true
        viewModelScope.launch{runCatching{repository.resetPassword(email,code,password)}.onSuccess{passwordResetRequested=false;message="Password updated. Sign in with your new password."}.onFailure{message="The reset code was invalid or expired."}.also{authLoading=false}}
    }
    private fun loadAccountData(){
        viewModelScope.launch {
            runCatching{repository.loadFavorites()}.onSuccess{ids->favorites.clear();favorites.addAll(ids)}
            runCatching{repository.loadBookings()}.onSuccess{rows->
                val mapped=rows.mapNotNull{row->
                    val serviceId=runCatching{if(row.serviceId?.isJsonObject==true)row.serviceId.asJsonObject.get("_id")?.asString else row.serviceId?.asString}.getOrNull()
                    val service=services.firstOrNull{it.id==serviceId}?:selected
                    val status=runCatching{BookingStatus.valueOf(row.bookingStatus)}.getOrDefault(BookingStatus.PENDING)
                    Booking(row.id,service,row.eventDate,row.eventType,row.guestCount,row.totalAmount.toInt(),status,row.eventLocation,row.startTime,row.endTime)
                }
                bookings.clear();bookings.addAll(mapped)
            }
        }
    }
    fun book(event:String,date:String,guests:Int,method:String,start:String,end:String,location:String,requirements:String,onComplete:()->Unit){
        val cost=if(selected.category.equals("Catering",true))selected.price*guests else selected.price
        if(guestMode){
            val booking=Booking("SX${2000+bookings.size}",selected,date,event,guests,cost+600,BookingStatus.CONFIRMED,location,start,end)
            bookings.add(0,booking);currentBooking=booking;bookingOpen=false;message="Demo booking saved · no payment was collected";onComplete();return
        }
        val providerId=selected.providerId
        if(providerId.isNullOrBlank()||!selected.id.matches(Regex("^[a-fA-F0-9]{24}$"))){message="Live booking requires a service loaded from the ShaadiX backend.";return}
        viewModelScope.launch{
            val createdBooking=runCatching {
                val created=repository.createBooking(CreateBookingRequest(providerId,selected.id,event,date,start,end,guests,location,requirements))
                Booking(created.id,selected,created.eventDate,event,created.guestCount,created.totalAmount.toInt(),runCatching{BookingStatus.valueOf(created.bookingStatus)}.getOrDefault(BookingStatus.PENDING),created.eventLocation,created.startTime,created.endTime)
            }.getOrElse{message=it.localizedMessage?:"We couldn't send your booking. Please try again.";return@launch}
            bookings.add(0,createdBooking);currentBooking=createdBooking;bookingOpen=false
            val paymentMethod=when(method){"Card"->"CARD";"Net banking"->"NET_BANKING";"Pay later"->"PAY_LATER";else->method.uppercase()}
            runCatching{repository.createPaymentOrder(createdBooking.id,paymentMethod)}.onSuccess{order->
                if(method=="Pay later"){
                    message="Booking request sent · pay later selected";onComplete()
                }else if(!order.orderId.isNullOrBlank()&&!order.keyId.isNullOrBlank()){
                    checkoutOrder=order
                    onComplete()
                }else{
                    message="Booking request is saved. Online payment is unavailable; choose Pay later or try again."
                }
            }.onFailure{message="Booking request saved, but payment setup needs attention: ${it.localizedMessage?:"gateway unavailable"}"}
        }
    }
    fun verifyPayment(orderId:String,paymentId:String,signature:String){
        viewModelScope.launch{runCatching{repository.verifyPayment(orderId,paymentId,signature)}.onSuccess{
            currentBooking?.let{booking->val index=bookings.indexOfFirst{it.id==booking.id};if(index>=0){val paid=booking.copy(paymentStatus="PAID");bookings[index]=paid;currentBooking=paid}}
            message="Payment verified · booking request sent";checkoutOrder=null
        }.onFailure{message="Payment was received, but verification is pending. Contact support before trying to pay again."}}
    }
    fun cancel(booking:Booking){
        if(guestMode){val at=bookings.indexOfFirst{it.id==booking.id};if(at>=0)bookings[at]=booking.copy(status=BookingStatus.CANCELLED);return}
        viewModelScope.launch{runCatching{repository.cancelBooking(booking.id)}.onSuccess{val at=bookings.indexOfFirst{it.id==booking.id};if(at>=0)bookings[at]=booking.copy(status=BookingStatus.CANCELLED)}.onFailure{message=it.localizedMessage?:"Could not cancel booking."}}
    }
    fun refreshNotifications(){if(guestMode)return;viewModelScope.launch{runCatching{repository.loadNotifications()}.onSuccess{notificationItems.clear();notificationItems.addAll(it)}}}
    fun refreshProvider(){if(guestMode){providerBookings.clear();providerBookings.addAll(bookings);return};viewModelScope.launch{
        runCatching{repository.providerBookings()}.onSuccess{rows->providerBookings.clear();providerBookings.addAll(rows.map{row->val sid=runCatching{if(row.serviceId?.isJsonObject==true)row.serviceId.asJsonObject.get("_id")?.asString else row.serviceId?.asString}.getOrNull();val service=services.firstOrNull{it.id==sid}?:selected;Booking(row.id,service,row.eventDate,row.eventType,row.guestCount,row.totalAmount.toInt(),runCatching{BookingStatus.valueOf(row.bookingStatus)}.getOrDefault(BookingStatus.PENDING),row.eventLocation,row.startTime,row.endTime)})}
        runCatching{repository.providerDashboard()}.onSuccess{data->data.asJsonObject?.let{obj->providerEarnings="₹"+(obj.get("earnings")?.asInt?:0);providerPendingCount=obj.get("pendingBookings")?.asInt?:0;providerCompletedCount=obj.get("completedBookings")?.asInt?:0;providerId=obj.getAsJsonObject("provider")?.get("_id")?.asString.orEmpty()}}
    }}
    fun saveProviderProfile(business:String,category:String,description:String,location:String){
        if(guestMode){message="Create or sign in to a provider account to save a real profile.";return}
        if(business.length<2||category.length<2||description.length<10||location.length<4||phone.isBlank()){message="Complete the business details before saving.";return}
        val city=location.substringAfterLast(",").trim().ifBlank{location.trim()}
        val request=ProviderProfileRequest(business,category,description,phone,email,location,city)
        viewModelScope.launch{runCatching{repository.saveProviderProfile(providerId.ifBlank{null},request)}.onSuccess{message="Business profile submitted for approval.";refreshProvider()}.onFailure{message=it.localizedMessage?:"Could not save provider profile."}}
    }
    fun publishService(category:String,title:String,description:String,priceValue:String,location:String,images:List<MultipartBody.Part> = emptyList()){
        val amount=priceValue.toIntOrNull()
        if(title.length<3||category.length<2||description.length<10||amount==null||location.length<2){message="Complete each service detail first.";return}
        if(guestMode){message="Sample mode · sign in as a verified provider to publish listings.";return}
        val request=ServiceCreateRequest(category,title,description,amount,"flat",location,listOf("Professional event team","Flexible package"))
        viewModelScope.launch{runCatching{repository.createService(request,images)}.onSuccess{services.add(0,it);message="Service listing published."}.onFailure{message=it.localizedMessage?:"Provider verification is required before publishing."}}
    }
    fun updateService(service:Service,newPrice:String){
        val amount=newPrice.toIntOrNull()?:run{message="Enter a valid price.";return}
        if(guestMode){message="Service updates are preview-only in demo mode.";return}
        val request=ServiceCreateRequest(service.category,service.title,service.description,amount,"flat",service.area,service.includes)
        viewModelScope.launch{runCatching{repository.updateService(service.id,request)}.onSuccess{val index=services.indexOfFirst{it.id==service.id};if(index>=0)services[index]=it;message="Service updated."}.onFailure{message=it.localizedMessage?:"Could not update service."}}
    }
    fun deleteService(service:Service){
        if(guestMode){services.remove(service);return}
        viewModelScope.launch{runCatching{repository.deleteService(service.id)}.onSuccess{services.removeAll{it.id==service.id};message="Service removed."}.onFailure{message=it.localizedMessage?:"Could not remove service."}}
    }
    fun providerAction(booking:Booking,accept:Boolean){
        if(guestMode){val at=providerBookings.indexOfFirst{it.id==booking.id};if(at>=0)providerBookings[at]=booking.copy(status=if(accept)BookingStatus.CONFIRMED else BookingStatus.REJECTED);message=if(accept)"Booking accepted in demo." else "Booking declined in demo.";return}
        viewModelScope.launch{runCatching{repository.providerBookingAction(booking.id,accept)}.onSuccess{val at=providerBookings.indexOfFirst{it.id==booking.id};if(at>=0)providerBookings[at]=booking.copy(status=if(accept)BookingStatus.CONFIRMED else BookingStatus.REJECTED);message=if(accept)"Booking accepted." else "Booking declined."}.onFailure{message=it.localizedMessage?:"Could not update the booking."}}
    }
    fun saveAvailability(days:List<Int>,start:String,end:String,blockedDates:String){
        if(!start.matches(Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$"))||!end.matches(Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$"))||start>=end||days.isEmpty()){message="Choose at least one day and valid opening hours.";return}
        if(guestMode){message="Availability is previewed in demo mode.";return}
        val dates=blockedDates.split(",").map{it.trim()}.filter{it.matches(Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$"))}
        viewModelScope.launch{runCatching{repository.setAvailability(days,start,end,dates)}.onSuccess{message="Availability saved."}.onFailure{message=it.localizedMessage?:"Could not update availability."}}
    }
    fun refreshAdmin(){if(guestMode)return;viewModelScope.launch{runCatching{repository.adminDashboard()}.onSuccess{data->data.asJsonObject?.let{obj->adminStats=mapOf("users" to (obj.get("totalUsers")?.asString?:obj.get("totalUsers")?.asInt?.toString().orEmpty()),"providers" to (obj.get("totalProviders")?.asString?:obj.get("totalProviders")?.asInt?.toString().orEmpty()),"bookings" to (obj.get("totalBookings")?.asString?:obj.get("totalBookings")?.asInt?.toString().orEmpty()),"revenue" to "₹"+(obj.get("totalRevenue")?.asInt?:0))}}}}
    fun loadAdminSection(section:String){
        if(guestMode){message="Sign in with an administrator account to manage platform data.";return}
        adminLoading=true;viewModelScope.launch{runCatching{repository.adminList(section)}.onSuccess{items->adminRows.clear();adminRows.addAll(items.map{item->val obj=item.asJsonObject;val id=obj.get("_id")?.asString.orEmpty();val title=when(section){"providers"->obj.get("businessName")?.asString?:"Provider";"users"->obj.get("name")?.asString?:obj.get("email")?.asString?:"User";"bookings"->obj.get("eventType")?.asString?:"Booking";"reviews"->obj.get("comment")?.asString?:"Review";else->obj.get("name")?.asString?:"Category"};val subtitle=when(section){"providers"->(obj.get("city")?.asString.orEmpty()+if(obj.get("isVerified")?.asBoolean==true)" · Verified" else " · Awaiting approval");"users"->obj.get("email")?.asString.orEmpty()+" · "+obj.get("role")?.asString.orEmpty();"bookings"->(obj.get("eventDate")?.asString.orEmpty()+" · "+obj.get("bookingStatus")?.asString.orEmpty());"reviews"->"Flagged · "+obj.get("reportReason")?.asString.orEmpty();else->obj.get("description")?.asString.orEmpty()};val action=when(section){"providers"->if(obj.get("isVerified")?.asBoolean==true)"Deactivate" else "Approve";"users"->"Deactivate";"reviews"->"Hide review";else->""};AdminListRow(id,title,subtitle,action)})}.onFailure{message="Could not load admin records. Check your administrator access."}.also{adminLoading=false}}
    }
    fun adminAction(section:String,row:AdminListRow){viewModelScope.launch{runCatching{repository.adminAction(section,row.id,if(section=="providers"&&row.action=="Approve")"verify" else "deactivate")}.onSuccess{message="${row.title} updated.";loadAdminSection(section)}.onFailure{message=it.localizedMessage?:"Could not update this record."}}}
    fun createAdminCategory(name:String,description:String){if(name.trim().length<2){message="Category name must be at least 2 characters.";return};viewModelScope.launch{runCatching{repository.adminCreateCategory(name.trim(),description.trim())}.onSuccess{message="Category created.";loadAdminSection("categories")}.onFailure{message=it.localizedMessage?:"Could not create category."}}}
    fun submitReview(booking:Booking,rating:Int,comment:String){
        if(guestMode){message="Sign in to submit your review.";return}
        viewModelScope.launch{runCatching{repository.submitReview(booking.id,rating,comment)}.onSuccess{message="Thank you for sharing your review."}.onFailure{message=it.localizedMessage?:"Could not submit the review."}}
    }
    fun signOut(){viewModelScope.launch{repository.logout();signedIn=false;guestMode=true;role="customer";bookings.clear();bookings.addAll(SampleData.initialBookings);favorites.clear()}}
    }
}

@Composable fun ShaadiXApp(vm:ShaadiXViewModel=viewModel(),darkTheme:Boolean=false,onToggleTheme:(Boolean)->Unit={},onOpenCheckout:(PaymentOrderResult,String,String,String)->Unit={_,_,_,_->}){
    val appScope=rememberCoroutineScope()
    val context=LocalContext.current
    LaunchedEffect(context){(context as? MainActivity)?.setPaymentResultHandler{orderId,paymentId,signature->vm.verifyPayment(orderId,paymentId,signature)}}
    LaunchedEffect(vm.checkoutOrder){vm.checkoutOrder?.let{order->onOpenCheckout(order,vm.name,vm.email,vm.phone);vm.checkoutOrder=null}}
    var splashFinished by remember{mutableStateOf(false)}
    var onboardingComplete by remember{mutableStateOf<Boolean?>(null)}
    LaunchedEffect(Unit){
        onboardingComplete=runCatching{(AppContext.context.applicationContext as AppContext).container.preferences.onboardingComplete.first()}.getOrDefault(false)
        delay(950);splashFinished=true
    }
    if(!splashFinished||onboardingComplete==null){SplashScreen();return}
    if(onboardingComplete==false){OnboardingScreen{appScope.launch{kotlinx.coroutines.runCatching{(AppContext.context.applicationContext as AppContext).container.preferences.finishOnboarding()}};onboardingComplete=true};return}
    if(!vm.signedIn){Welcome(vm);return}
    val nav= rememberNavController();val entry by nav.currentBackStackEntryAsState();val route=entry?.destination?.route
    val snack= remember{SnackbarHostState()}
    LaunchedEffect(vm.message){vm.message?.let{snack.showSnackbar(it);vm.message=null}}
    Scaffold(containerColor=MaterialTheme.colorScheme.background,snackbarHost={SnackbarHost(snack)},bottomBar={
        if(route in listOf("home","explore","bookings","favorites","profile"))NavigationBar(containerColor=Color.White){
            listOf("home" to ("Home" to Icons.Default.Home),"explore" to ("Search" to Icons.Default.Search),"bookings" to ("Bookings" to Icons.Default.CalendarMonth),"favorites" to ("Favorites" to Icons.Default.FavoriteBorder),"profile" to ("Profile" to Icons.Default.Person)).forEach{(screen,item)->
                NavigationBarItem(route==screen,{nav.navigate(screen){popUpTo("home");launchSingleTop=true}},icon={Icon(item.second,item.first)},label={Text(item.first)})
            }
        }
    }){pad->NavHost(nav,"home",Modifier.padding(pad)){
        composable("home"){Home(vm,nav)};composable("explore"){Explore(vm,nav)};composable("bookings"){Bookings(vm,nav)};composable("profile"){Profile(vm,nav,darkTheme,onToggleTheme)}
        composable("favorites"){Favorites(vm,nav)};composable("categories"){Categories(vm,nav)};composable("notifications"){Notifications(vm,nav)};composable("provider"){Provider(vm,nav)};composable("admin"){Admin(vm,nav)};composable("reviews"){Reviews(vm,nav)}
        composable("confirmation"){BookingConfirmation(vm,nav)}
        composable("booking/{id}",arguments=listOf(navArgument("id"){type=NavType.StringType})){BookingDetails(vm,nav)}
        composable("admin/manage/{section}",arguments=listOf(navArgument("section"){type=NavType.StringType})){entry->AdminManagement(vm,nav,entry.arguments?.getString("section").orEmpty())}
        composable("detail/{id}",arguments=listOf(navArgument("id"){type=NavType.StringType})){Detail(vm,nav)}
    }}
    if(vm.bookingOpen)Booking(vm){nav.navigate("confirmation")}
}

@Composable private fun SplashScreen(){
    val scale by animateFloatAsState(1f,label="logo-scale")
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF321848),Royal,Violet))),contentAlignment=Alignment.Center){
        Column(horizontalAlignment=Alignment.CenterHorizontally){
            Box(Modifier.size((88*scale).dp).clip(RoundedCornerShape(28.dp)).background(Color.White.copy(alpha=.12f)),contentAlignment=Alignment.Center){Text("S",color=Gold,style=MaterialTheme.typography.displayLarge,fontWeight=FontWeight.Black)}
            Spacer(Modifier.height(12.dp));Text("ShaadiX",color=Color.White,style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold);Text("Plan. Book. Celebrate.",color=Color(0xFFF1DBAE))
        }
    }
}

@Composable private fun OnboardingScreen(onDone:()->Unit){
    var page by remember{mutableIntStateOf(0)}
    val titles=listOf("Find the Perfect Venue","Book Event Services","Celebrate Without Stress")
    val copy=listOf("Discover beautiful venues for your special events.","Photography, catering, decoration, makeup and more.","Manage your complete event booking in one place.")
    val icons=listOf(Icons.Default.FavoriteBorder,Icons.Default.AutoAwesome,Icons.Default.Celebration)
    Column(Modifier.fillMaxSize().background(Canvas).systemBarsPadding().padding(24.dp),verticalArrangement=Arrangement.SpaceBetween){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){TextButton(onClick=onDone){Text("Skip")}}
        Crossfade(page,label="onboarding-page"){index->Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally){
            Box(Modifier.fillMaxWidth().height(330.dp).clip(RoundedCornerShape(32.dp)).background(Brush.verticalGradient(listOf(Lilac,Color.White))),contentAlignment=Alignment.Center){Icon(icons[index],null,tint=Royal,modifier=Modifier.size(96.dp))}
            Spacer(Modifier.height(32.dp));Text(titles[index],style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,color=Ink);Spacer(Modifier.height(8.dp));Text(copy[index],color=Muted)
        }}
        Column(horizontalAlignment=Alignment.CenterHorizontally){Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){repeat(3){Box(Modifier.size(if(it==page)22.dp else 8.dp,8.dp).clip(CircleShape).background(if(it==page)Royal else Gold.copy(alpha=.35f)))}};Spacer(Modifier.height(22.dp));Button(onClick={if(page==2)onDone()else page++},Modifier.fillMaxWidth().height(54.dp),shape=RoundedCornerShape(16.dp)){Text(if(page==2)"Get Started" else "Next")}}
    }
}

@Composable private fun Welcome(vm:ShaadiXViewModel){
    var e by remember{mutableStateOf("")};var p by remember{mutableStateOf("")};var confirm by remember{mutableStateOf("")};var n by remember{mutableStateOf("")};var create by remember{mutableStateOf(false)};var phone by remember{mutableStateOf("")};var code by remember{mutableStateOf("")};var accountRole by remember{mutableStateOf("customer")}
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF321848),Royal,Violet)))){
        Column(Modifier.align(Alignment.Center).fillMaxWidth().heightIn(max=800.dp).verticalScroll(rememberScrollState()).padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Text("S",color=Gold,style=MaterialTheme.typography.displayMedium,fontWeight=FontWeight.Bold);Text("ShaadiX",color=Color.White,style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold);Text("Plan. Book. Celebrate.",color=Color(0xFFF1DBAE));Spacer(Modifier.height(20.dp))
            Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text(when{vm.otpSent->"Verify your email";vm.passwordResetRequested->"Reset your password";create->"Create your account";else->"Welcome back"},style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                if(vm.otpSent){Text("We sent a six-digit code to ${vm.email}",color=Muted);OutlinedTextField(code,{code=it.filter(Char::isDigit).take(6)},Modifier.fillMaxWidth(),label={Text("Email verification code")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true)
                    Button(onClick={vm.verifyOtp(code)},Modifier.fillMaxWidth(),enabled=!vm.authLoading&&code.length==6){Text(if(vm.authLoading)"Verifying…" else "Verify and continue")}
                }else if(vm.passwordResetRequested){Text("Enter the code from your email and choose a new password.",color=Muted);OutlinedTextField(code,{code=it.filter(Char::isDigit).take(6)},Modifier.fillMaxWidth(),label={Text("Reset code")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true);OutlinedTextField(confirm,{confirm=it},Modifier.fillMaxWidth(),label={Text("New password")},visualTransformation=PasswordVisualTransformation(),singleLine=true)
                    Button(onClick={vm.resetPassword(code,confirm)},Modifier.fillMaxWidth(),enabled=!vm.authLoading){Text("Save new password")}
                }else{
                    if(create){OutlinedTextField(n,{n=it},Modifier.fillMaxWidth(),label={Text("Full name")},singleLine=true)
                        OutlinedTextField(phone,{phone=it},Modifier.fillMaxWidth(),label={Text("Mobile number")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone),singleLine=true)
                        Text("I’m joining as",color=Muted);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("customer" to "Customer","provider" to "Service provider").forEach{(value,label)->FilterChip(accountRole==value,{accountRole=value},label={Text(label)})}}
                    }
                    OutlinedTextField(e,{e=it},Modifier.fillMaxWidth(),label={Text("Email address")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email),singleLine=true)
                    OutlinedTextField(p,{p=it},Modifier.fillMaxWidth(),label={Text("Password")},visualTransformation=PasswordVisualTransformation(),singleLine=true)
                    if(create)OutlinedTextField(confirm,{confirm=it},Modifier.fillMaxWidth(),label={Text("Confirm password")},visualTransformation=PasswordVisualTransformation(),singleLine=true)
                    Button(onClick={if(create){if(confirm!=p)vm.message="Your passwords do not match." else vm.signUp(n,e,phone,p,accountRole)}else vm.signIn(e,p)},Modifier.fillMaxWidth(),enabled=!vm.authLoading){Text(if(vm.authLoading)"Please wait…" else if(create)"Create account" else "Sign in")}
                    if(!create){TextButton(onClick={vm.requestPasswordReset(e)}){Text("Forgot password?")};OutlinedButton(onClick={vm.message="Google sign-in requires a configured Google OAuth client."},Modifier.fillMaxWidth()){Icon(Icons.Default.AccountCircle,null);Text("  Continue with Google")}}
                    TextButton(onClick={create=!create;vm.message=null;confirm=""}){Text(if(create)"Already registered? Sign in" else "New to ShaadiX? Create account")}
                }
                vm.message?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
                if(vm.otpSent||vm.passwordResetRequested)TextButton(onClick={vm.otpSent=false;vm.passwordResetRequested=false}){Text("Back to sign in")}
            }}
            TextButton(onClick=vm::exploreAsGuest){Text("Explore with sample listings",color=Color.White)}
        }
    }
}

@Composable private fun Header(title:String,sub:String?=null,end:@Composable (() -> Unit)?=null){
    Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){
        Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);sub?.let{Text(it,color=Muted,style=MaterialTheme.typography.bodySmall)}}
        end?.invoke()
    }
}
@Composable private fun Section(title:String,action:String?=null,onClick:()->Unit={}){
    Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically){Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);if(action!=null)TextButton(onClick){Text(action)}}
}
@Composable private fun Home(vm:ShaadiXViewModel,nav:NavHostController){
    LazyColumn(contentPadding=PaddingValues(bottom=20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        item{Header("Good morning, ${vm.name.substringBefore(" ")} ✨",end={IconButton(onClick={nav.navigate("notifications")}){Icon(Icons.Default.NotificationsNone,null,tint=Royal)}});Row(Modifier.padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.LocationOn,null,tint=Violet);Text("Kochi, Kerala");Spacer(Modifier.weight(1f));TextButton(onClick={vm.message="Location selector · Kochi"}){Text("CHANGE")}}}
        item{Box(Modifier.fillMaxWidth().padding(horizontal=20.dp).height(190.dp).clip(RoundedCornerShape(24.dp))){
            AsyncImage("https://images.unsplash.com/photo-151974149…950 tokens truncated…(horizontal=20.dp),color=Muted,style=MaterialTheme.typography.labelSmall)
        if(state is UiState.Loading&&vm.services.isEmpty())Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center){CircularProgressIndicator(color=Royal)}
        else {val list=vm.results();if(list.isEmpty())Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center){Blank("Nothing found yet","Try a different category or search.")}else LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(horizontal=18.dp,vertical=4.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){items(list,key={it.id}){Tile(it,vm,nav)};if(vm.hasMoreServices)item{TextButton(onClick=vm::loadMoreServices,Modifier.fillMaxWidth()){Text("Load more")}}}}
    }
    if(showFilters)AlertDialog(onDismissRequest={showFilters=false},title={Text("Refine your search")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        OutlinedTextField(vm.cityFilter,{vm.cityFilter=it},label={Text("Location")},singleLine=true)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(vm.minPriceFilter,{vm.minPriceFilter=it.filter(Char::isDigit)},Modifier.weight(1f),label={Text("Min ₹")},singleLine=true);OutlinedTextField(vm.maxPriceFilter,{vm.maxPriceFilter=it.filter(Char::isDigit)},Modifier.weight(1f),label={Text("Max ₹")},singleLine=true)}
        OutlinedTextField(vm.dateFilter,{vm.dateFilter=it},label={Text("Event date · YYYY-MM-DD")},singleLine=true)
        Text("Minimum rating",fontWeight=FontWeight.SemiBold);Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf(0.0,4.0,4.5,4.8).forEach{value->FilterChip(vm.ratingFilter==value,{vm.ratingFilter=value},label={Text(if(value==0.0)"Any" else "$value+")})}}
        Text("Sort by",fontWeight=FontWeight.SemiBold);Column{listOf("Recommended","Price: low to high","Price: high to low","Highest rated").forEach{option->FilterChip(vm.sortOption==option,{vm.sortOption=option},label={Text(option)})}}
    }},confirmButton={TextButton(onClick={showFilters=false;vm.refreshServices()}){Text("Apply filters")}},dismissButton={TextButton(onClick={showFilters=false}){Text("Cancel")}})
}

@Composable private fun Categories(vm:ShaadiXViewModel,nav:NavHostController){
    val columns=androidx.compose.foundation.lazy.grid.GridCells.Adaptive(145.dp)
    Column(Modifier.fillMaxSize()){Header("All categories","Choose a service for your celebration",end={IconButton(onClick={nav.popBackStack()}){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}})
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(columns=columns,contentPadding=PaddingValues(18.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            items(SampleData.serviceTypes){type->Card(Modifier.fillMaxWidth().clickable{vm.category=type;nav.navigate("explore")},shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.padding(16.dp)){Icon(Icons.Default.Category,null,tint=Royal);Spacer(Modifier.height(10.dp));Text(type,fontWeight=FontWeight.Bold);Text("${vm.countByCategory(type)} services",color=Muted,style=MaterialTheme.typography.bodySmall)}}}
        }
    }
}

@Composable private fun Tile(s:Service,vm:ShaadiXViewModel,nav:NavHostController,small:Boolean=false){
    Card((if(small)Modifier.width(270.dp)else Modifier.fillMaxWidth()).clickable{vm.selected=s;nav.navigate("detail/${s.id}")},shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=Color.White),elevation=CardDefaults.cardElevation(2.dp)){
        Column{Box(Modifier.fillMaxWidth().height(if(small)138.dp else 178.dp)){
            AsyncImage(s.image,s.title,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            IconButton(onClick={vm.favorite(s)},Modifier.align(Alignment.TopEnd).padding(8.dp).clip(CircleShape).background(Color.White)){Icon(if(vm.isFavorite(s))Icons.Default.Favorite else Icons.Default.FavoriteBorder,"Favorite",tint=if(vm.isFavorite(s))Color(0xFFC45169)else Royal)}
            Surface(Modifier.align(Alignment.BottomStart).padding(9.dp),shape=RoundedCornerShape(25.dp),color=Color.White){Text(s.category,Modifier.padding(horizontal=9.dp,vertical=5.dp),color=Royal,style=MaterialTheme.typography.labelSmall)}
        };Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(s.title,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
            Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.LocationOn,null,tint=Muted,modifier=Modifier.size(15.dp));Text(s.area,Modifier.weight(1f),color=Muted,style=MaterialTheme.typography.bodySmall);Icon(Icons.Default.Star,null,tint=Gold,modifier=Modifier.size(16.dp));Text("${s.rating} (${s.reviews})",style=MaterialTheme.typography.labelSmall)}
            Text(if(s.category=="Catering")"${price(s.price)} / guest" else "From ${price(s.price)}",color=Royal,fontWeight=FontWeight.Bold)
        }}
    }
}

@Composable private fun Detail(vm:ShaadiXViewModel,nav:NavHostController){
    val id=nav.currentBackStackEntry?.arguments?.getString("id");val s=vm.services.find{it.id==id}?:vm.selected
    Column(Modifier.fillMaxSize()){
        Box(Modifier.fillMaxWidth().height(245.dp)){BoxWithConstraints(Modifier.fillMaxSize()){LazyRow{items(s.gallery.ifEmpty{listOf(s.image)}){image->AsyncImage(image,s.title,Modifier.width(maxWidth).fillMaxHeight(),contentScale=ContentScale.Crop)}}};IconButton(onClick={nav.popBackStack()},Modifier.padding(10.dp).clip(CircleShape).background(Color.White)){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")};IconButton(onClick={vm.favorite(s)},Modifier.align(Alignment.TopEnd).padding(10.dp).clip(CircleShape).background(Color.White)){Icon(if(vm.isFavorite(s))Icons.Default.Favorite else Icons.Default.FavoriteBorder,"Favorite",tint=Royal)}}
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text(s.category.uppercase(),color=Violet,fontWeight=FontWeight.Bold);Text(s.title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            Text("⌖  ${s.area}         ★ ${s.rating} · ${s.reviews} reviews",color=Muted);HorizontalDivider()
            Text("A little about them",fontWeight=FontWeight.Bold);Text(s.description,color=Muted)
            Text("Included with your booking",fontWeight=FontWeight.Bold);s.includes.forEach{Text("✓  $it",color=Muted)}
            Text("Available dates",fontWeight=FontWeight.Bold);Text(if(s.availableDates.isEmpty())"Check your date with the provider when requesting a booking." else s.availableDates.take(8).joinToString(" · "),color=Muted)
            TextButton(onClick={vm.message="Contact ${s.provider} · provider chat demo"}){Icon(Icons.Default.Chat,null);Text(" Contact provider")}
        }
        Surface(shadowElevation=8.dp,color=Color.White){Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(14.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(price(s.price),color=Royal,fontWeight=FontWeight.Bold);Text("Final price before payment",color=Muted,style=MaterialTheme.typography.labelSmall)};Button(onClick={vm.selected=s;vm.bookingOpen=true}){Text("Book now")}}}
    }
}

@Composable private fun Booking(vm:ShaadiXViewModel,onComplete:()->Unit){
    var event by remember{mutableStateOf("Wedding")};var date by remember{mutableStateOf("2026-12-18")};var count by remember{mutableStateOf("150")};var method by remember{mutableStateOf("Pay later")}
    var start by remember{mutableStateOf("18:00")};var end by remember{mutableStateOf("21:00")};var location by remember{mutableStateOf("Kochi, Kerala")};var requirements by remember{mutableStateOf("")};var paymentStep by remember{mutableStateOf(false)}
    val s=vm.selected;val guests=count.toIntOrNull()?.coerceIn(1,5000)?:0;val cost=if(s.category.equals("Catering",true))s.price*guests else s.price
    ModalBottomSheet(onDismissRequest={vm.bookingOpen=false},containerColor=MaterialTheme.colorScheme.surface){Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
        Text(if(paymentStep)"Review and pay" else "Book your event",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(s.title,color=Royal)
        if(!paymentStep){
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf("Wedding","Engagement","Birthday","College Function","Reception","Corporate Event","Cultural Event").forEach{FilterChip(event==it,{event=it},label={Text(it)})}}
            OutlinedTextField(date,{date=it},Modifier.fillMaxWidth(),label={Text("Event date · YYYY-MM-DD")},leadingIcon={Icon(Icons.Default.CalendarMonth,null)},singleLine=true)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(start,{start=it},Modifier.weight(1f),label={Text("Start · HH:mm")},singleLine=true);OutlinedTextField(end,{end=it},Modifier.weight(1f),label={Text("End · HH:mm")},singleLine=true)}
            OutlinedTextField(count,{count=it.filter(Char::isDigit).take(5)},Modifier.fillMaxWidth(),label={Text("Number of guests")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true)
            OutlinedTextField(location,{location=it},Modifier.fillMaxWidth(),label={Text("Event location")},leadingIcon={Icon(Icons.Default.LocationOn,null)},singleLine=true)
            OutlinedTextField(requirements,{requirements=it},Modifier.fillMaxWidth(),label={Text("Special requirements (optional)")},minLines=2,maxLines=3)
            Button(onClick={if(!date.matches(Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$"))||guests<1||start.length!=5||end.length!=5||location.length<4)vm.message="Check the date, time, guest count and event location."else paymentStep=true},Modifier.fillMaxWidth()){Text("Continue to payment")}
        }else{
            Text("${event} · ${date} · ${start}–${end}",color=Muted);Text(location,color=Muted);Text("Payment option",fontWeight=FontWeight.SemiBold)
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)){listOf("UPI","Card","Net banking","Wallet","Pay later").forEach{option->FilterChip(method==option,{method=option},label={Text(option)},leadingIcon=if(method==option){{Icon(Icons.Default.Check,null)}}else null)}}
            PriceRow("Service price",price(cost));PriceRow("Platform fee",price(600));PriceRow("Total",price(cost+600),true)
            Text(if(vm.guestMode)"Demo checkout · no payment will be collected." else "Online payment is prepared by ShaadiX. Card details are never stored in the app.",color=Muted,style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={paymentStep=false}){Text("Edit event details")}
            Button(onClick={vm.book(event,date,guests,method,start,end,location,requirements,onComplete)},Modifier.fillMaxWidth(),enabled=count.isNotBlank()){Text(if(method=="Pay later")"Send booking request · ${price(cost+600)}" else "Pay now · ${price(cost+600)}")}
        }
        Spacer(Modifier.height(14.dp))
    }}
}
@Composable private fun PriceRow(label:String,value:String,strong:Boolean=false){Row(Modifier.fillMaxWidth()){Text(label,Modifier.weight(1f),color=if(strong)Ink else Muted,fontWeight=if(strong)FontWeight.Bold else FontWeight.Normal);Text(value,color=Royal,fontWeight=if(strong)FontWeight.Bold else FontWeight.Medium)}}

@Composable private fun Bookings(vm:ShaadiXViewModel,nav:NavHostController){
    var tab by remember{mutableStateOf("Upcoming")}
    Column(Modifier.fillMaxSize()){Header("My bookings","Every detail, all in one place");Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=18.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Upcoming","Completed","Cancelled").forEach{FilterChip(tab==it,{tab=it},label={Text(it)})}}
        val list=vm.bookings.filter{when(tab){"Upcoming"->it.status==BookingStatus.CONFIRMED||it.status==BookingStatus.PENDING;"Completed"->it.status==BookingStatus.COMPLETED;else->it.status==BookingStatus.CANCELLED||it.status==BookingStatus.REJECTED}}
        if(list.isEmpty())Blank("Your $tab plans will appear here","Discover a service to begin planning.")else LazyColumn(contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){items(list){b->Card(shape=RoundedCornerShape(19.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){
            Column{Row(Modifier.fillMaxWidth().clickable{nav.navigate("booking/${b.id}")}.padding(12.dp),verticalAlignment=Alignment.CenterVertically){AsyncImage(b.service.image,null,Modifier.size(68.dp).clip(RoundedCornerShape(13.dp)),contentScale=ContentScale.Crop);Column(Modifier.weight(1f).padding(start=10.dp)){Text(b.service.title,fontWeight=FontWeight.Bold);Text("${b.event} · ${b.date}",color=Muted);Text("${b.guests} guests · ${price(b.total)}",color=Royal)};BookingStatusBadge(b.status)}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){if(b.status==BookingStatus.COMPLETED)TextButton(onClick={nav.navigate("reviews")}){Icon(Icons.Default.Star,null);Text("Review")};if(b.status==BookingStatus.CONFIRMED||b.status==BookingStatus.PENDING)TextButton(onClick={vm.cancel(b)}){Text("Cancel",color=MaterialTheme.colorScheme.error)}}
        }}}}
    }
}

@Composable private fun BookingStatusBadge(status:BookingStatus){
    val color=when(status){BookingStatus.CONFIRMED->Color(0xFF24744D);BookingStatus.COMPLETED->Color(0xFF514078);BookingStatus.PENDING->Color(0xFF9A6A1E);else->Color(0xFF9A4141)}
    Surface(color=color.copy(alpha=.10f),shape=RoundedCornerShape(30.dp)){Text(status.name,Modifier.padding(horizontal=9.dp,vertical=5.dp),color=color,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)}
}

@Composable private fun BookingConfirmation(vm:ShaadiXViewModel,nav:NavHostController){
    val booking=vm.currentBooking
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
        Icon(Icons.Default.CheckCircle,null,tint=Color(0xFF38825C),modifier=Modifier.size(80.dp));Spacer(Modifier.height(14.dp));Text(if(booking?.status==BookingStatus.CONFIRMED)"Booking confirmed!" else "Request received!",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(if(booking?.status==BookingStatus.PENDING)"Your provider will confirm the date shortly." else "Your celebration plan is in one place.",color=Muted);Spacer(Modifier.height(20.dp))
        if(booking!=null)Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),shape=RoundedCornerShape(20.dp)){Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Booking ${booking.id}",color=Royal,fontWeight=FontWeight.Bold);Text(booking.service.title,fontWeight=FontWeight.SemiBold);PriceRow("Event",booking.event);PriceRow("Date",booking.date);PriceRow("Time","${booking.startTime}–${booking.endTime}");PriceRow("Location",booking.eventLocation);PriceRow("Total",price(booking.total),true);BookingStatusBadge(booking.status)}}
        Spacer(Modifier.height(20.dp));Button(onClick={if(booking!=null)nav.navigate("booking/${booking.id}")},Modifier.fillMaxWidth(),enabled=booking!=null){Text("View booking")};TextButton(onClick={nav.popBackStack("home",false)}){Text("Back to home")}
    }
}

@Composable private fun BookingDetails(vm:ShaadiXViewModel,nav:NavHostController){
    val id=nav.currentBackStackEntry?.arguments?.getString("id");val b=vm.bookings.firstOrNull{it.id==id}
    if(b==null){Blank("Booking not found","Return to your bookings to refresh the list.");return}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){Header("Booking details",b.id,end={IconButton(onClick={nav.popBackStack()}){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}})
        AsyncImage(b.service.image,b.service.title,Modifier.fillMaxWidth().height(220.dp),contentScale=ContentScale.Crop)
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(11.dp)){BookingStatusBadge(b.status);Text(b.service.title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("Provider · ${b.service.provider}",color=Muted);HorizontalDivider();PriceRow("Booking ID",b.id);PriceRow("Event",b.event);PriceRow("Date",b.date);PriceRow("Time","${b.startTime}–${b.endTime}");PriceRow("Guests",b.guests.toString());PriceRow("Location",b.eventLocation);PriceRow("Payment status",if(vm.guestMode)"Demo · not charged" else "Pending verification");PriceRow("Total",price(b.total),true)
            if(b.status==BookingStatus.PENDING||b.status==BookingStatus.CONFIRMED)OutlinedButton(onClick={vm.cancel(b)},Modifier.fillMaxWidth()){Text("Cancel booking")}
        }
    }
}

@Composable private fun Reviews(vm:ShaadiXViewModel,nav:NavHostController){
    var rating by remember{mutableIntStateOf(5)};var comment by remember{mutableStateOf("")};val eligible=vm.bookings.firstOrNull{it.status==BookingStatus.COMPLETED}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)){Header("Reviews & ratings","Tell others how it went",end={IconButton(onClick={nav.popBackStack()}){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}})
        if(eligible==null)Blank("No event to review yet","Reviews open after a booking is marked complete.")else{Text("Review ${eligible.service.title}",fontWeight=FontWeight.Bold);Row{(1..5).forEach{value->IconButton(onClick={rating=value}){Icon(Icons.Default.Star,null,tint=if(value<=rating)Gold else Muted)}}};OutlinedTextField(comment,{comment=it},Modifier.fillMaxWidth(),label={Text("Your experience")},minLines=4);Spacer(Modifier.height(12.dp));Button(onClick={if(comment.trim().length<5)vm.message="Write at least a few words about your experience."else vm.submitReview(eligible,rating,comment)},Modifier.fillMaxWidth()){Text("Submit review")}}
    }
}

@Composable private fun Blank(title:String,body:String){Column(Modifier.fillMaxSize().padding(30.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.EventAvailable,null,tint=Violet,modifier=Modifier.size(48.dp));Spacer(Modifier.height(12.dp));Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text(body,color=Muted)}}
@Composable private fun Link(title:String,detail:String,icon:androidx.compose.ui.graphics.vector.ImageVector,tint:Color=Royal,action:()->Unit){Row(Modifier.fillMaxWidth().clickable(onClick=action).padding(14.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=tint);Column(Modifier.weight(1f).padding(start=12.dp)){Text(title,fontWeight=FontWeight.SemiBold);Text(detail,color=Muted,style=MaterialTheme.typography.bodySmall)};Icon(Icons.Default.ChevronRight,null,tint=Muted)}}

@Composable private fun Profile(vm:ShaadiXViewModel,nav:NavHostController,darkTheme:Boolean,onToggleTheme:(Boolean)->Unit){
    LazyColumn(contentPadding=PaddingValues(bottom=25.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Header("Your profile","Make it feel like yours")}
        item{Card(Modifier.fillMaxWidth().padding(horizontal=20.dp),colors=CardDefaults.cardColors(containerColor=Royal),shape=RoundedCornerShape(22.dp)){Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){Text(vm.name.take(1),Modifier.clip(CircleShape).background(Violet).padding(15.dp),color=Gold,style=MaterialTheme.typography.headlineMedium);Column(Modifier.weight(1f).padding(start=12.dp)){Text(vm.name,color=Color.White,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge);Text(vm.email.ifBlank{"Guest planner · Kochi"},color=Color.White.copy(alpha=.75f))};Icon(Icons.Default.Edit,null,tint=Gold)}}}
        item{Column(Modifier.padding(horizontal=20.dp).clip(RoundedCornerShape(18.dp)).background(Color.White)){
            Link("My bookings","Your event plans",Icons.Default.CalendarMonth){nav.navigate("bookings")};Link("Favourites","${vm.favorites.size} saved services",Icons.Default.FavoriteBorder){nav.navigate("favorites")}
            Link("Reviews & ratings","Share your experience",Icons.Default.RateReview){nav.navigate("reviews")};Link("Notifications","Reminders, updates and offers",Icons.Default.NotificationsNone){nav.navigate("notifications")};Link("Payment history","View your payment activity",Icons.Default.CreditCard){vm.message="Payment history is available after backend account sign-in."}
        }}
        item{Section("Provider tools");Column(Modifier.padding(horizontal=20.dp).clip(RoundedCornerShape(18.dp)).background(Color.White)){
            if(vm.role=="provider"||vm.guestMode)Link("Provider dashboard","Business profile and booking requests",Icons.Default.Storefront){nav.navigate("provider")};if(vm.role=="admin")Link("Admin overview","Platform operations",Icons.Default.AdminPanelSettings){nav.navigate("admin")}
        }}
        item{Column(Modifier.padding(horizontal=20.dp).clip(RoundedCornerShape(18.dp)).background(Color.White)){
            Link("Help & support","We're happy to help",Icons.Default.HelpOutline){vm.message="Support: hello@shaadix.example"};Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.DarkMode,null,tint=Royal);Text("Dark theme",Modifier.weight(1f).padding(start=12.dp));Switch(darkTheme,onToggleTheme)}
            Link("About ShaadiX","Plan. Book. Celebrate.",Icons.Default.Info){vm.message="ShaadiX · Plan. Book. Celebrate."};Link("Sign out","Return to welcome",Icons.Default.Logout,MaterialTheme.colorScheme.error){vm.signOut()}
        }}
    }
}
@Composable private fun Favorites(vm:ShaadiXViewModel,nav:NavHostController){Column(Modifier.fillMaxSize()){Header("Your favourites","The shortlist for your big day");val list=vm.services.filter{vm.isFavorite(it)};if(list.isEmpty())Blank("Save a little inspiration","Tap a service heart to keep it close.")else LazyColumn(contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){items(list){Tile(it,vm,nav)}}}}

@Composable private fun Notifications(vm:ShaadiXViewModel,nav:NavHostController){
    LaunchedEffect(Unit){vm.refreshNotifications()}
    Column(Modifier.fillMaxSize()){Header("Notifications","A little nudge when it matters",end={IconButton(onClick={nav.popBackStack()}){Icon(Icons.Default.Close,"Close")}})
        if(!vm.guestMode&&vm.notificationItems.isEmpty())Blank("You're all caught up","New booking and payment updates will appear here.")
        else if(vm.guestMode)listOf("Booking confirmed|Courtyard Palace · 18 Dec 2026|Just now","A date to remember|Your celebration is 30 days away.|Yesterday","A little something for you|10% off select decor.|2 days ago","Provider message|Your venue sent a note.|3 days ago").forEach{val p=it.split("|");NotificationRow(p[0],p[1],p[2])}
        else vm.notificationItems.forEach{item->NotificationRow(item.title,item.message,item.createdAt.take(10).ifBlank{"Just now"})}
    }
}
@Composable private fun NotificationRow(title:String,message:String,time:String){Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Notifications,null,tint=Royal);Column(Modifier.weight(1f).padding(start=12.dp)){Text(title,fontWeight=FontWeight.Bold);Text(message,color=Muted);Text(time,color=Violet,style=MaterialTheme.typography.labelSmall)};HorizontalDivider()}}

@Composable private fun Provider(vm:ShaadiXViewModel,nav:NavHostController){
    LaunchedEffect(Unit){vm.refreshProvider()}
    val context=LocalContext.current
    val selectedImages=remember{mutableStateListOf<Uri>()}
    val imagePicker=rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(8)){uris->selectedImages.clear();selectedImages.addAll(uris)}
    var business by remember{mutableStateOf("")};var place by remember{mutableStateOf("")};var amount by remember{mutableStateOf("85000")};var providerCategory by remember{mutableStateOf("Function Hall")};var description by remember{mutableStateOf("")}
    var serviceTitle by remember{mutableStateOf("")};var serviceDescription by remember{mutableStateOf("")};var serviceAmount by remember{mutableStateOf("")};var serviceLocation by remember{mutableStateOf("")}
    var days by remember{mutableStateOf(setOf(1,2,3,4,5,6))};var opening by remember{mutableStateOf("09:00")};var closing by remember{mutableStateOf("21:00")};var blockedDates by remember{mutableStateOf("")}
    LazyColumn(contentPadding=PaddingValues(bottom=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Header("Provider studio","Grow your business",end={IconButton(onClick={nav.popBackStack()}){Icon(Icons.Default.Close,"Close")}})}
        item{Card(Modifier.fillMaxWidth().padding(horizontal=20.dp),colors=CardDefaults.cardColors(containerColor=Royal),shape=RoundedCornerShape(20.dp)){Row(Modifier.fillMaxWidth().padding(18.dp),horizontalArrangement=Arrangement.SpaceBetween){Text("Earnings\n${if(vm.guestMode)"₹1.82L" else vm.providerEarnings}",color=Color.White,fontWeight=FontWeight.Bold);Text("Pending\n${if(vm.guestMode)"08" else vm.providerPendingCount}",color=Color.White,fontWeight=FontWeight.Bold);Text("Completed\n${if(vm.guestMode)"24" else vm.providerCompletedCount}",color=Color.White,fontWeight=FontWeight.Bold)}}}
        item{Section("Business profile","Save"){vm.saveProviderProfile(business,providerCategory,description,place)};Column(Modifier.padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedTextField(business,{business=it},Modifier.fillMaxWidth(),label={Text("Business name")});OutlinedTextField(providerCategory,{providerCategory=it},Modifier.fillMaxWidth(),label={Text("Service category")});OutlinedTextField(description,{description=it},Modifier.fillMaxWidth(),label={Text("Business description")},minLines=2);OutlinedTextField(place,{place=it},Modifier.fillMaxWidth(),label={Text("Business address and city")})
            OutlinedTextField(amount,{amount=it.filter(Char::isDigit)},Modifier.fillMaxWidth(),label={Text("Starting price")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
            Text("Contact · ${vm.phone.ifBlank{"Add a phone number to your account"}}",color=Muted)
            Button(onClick={vm.saveProviderProfile(business,providerCategory,description,place)},Modifier.fillMaxWidth()){Text(if(vm.providerId.isBlank())"Submit business profile" else "Save business profile")}
        }}
        item{Section("Add a service listing");Column(Modifier.padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedTextField(serviceTitle,{serviceTitle=it},Modifier.fillMaxWidth(),label={Text("Service name")});OutlinedTextField(providerCategory,{providerCategory=it},Modifier.fillMaxWidth(),label={Text("Category")});OutlinedTextField(serviceDescription,{serviceDescription=it},Modifier.fillMaxWidth(),label={Text("Description")},minLines=2);OutlinedTextField(serviceAmount,{serviceAmount=it.filter(Char::isDigit)},Modifier.fillMaxWidth(),label={Text("Price · ₹")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number));OutlinedTextField(serviceLocation,{serviceLocation=it},Modifier.fillMaxWidth(),label={Text("Service location")})
            OutlinedButton(onClick={imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},Modifier.fillMaxWidth()){Icon(Icons.Default.AddPhotoAlternate,null);Text(if(selectedImages.isEmpty())" Add service images" else " ${selectedImages.size} image(s) selected")}
            if(selectedImages.isNotEmpty())LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){items(selectedImages){uri->AsyncImage(uri,null,Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)),contentScale=ContentScale.Crop)}}
            Button(onClick={vm.publishService(providerCategory,serviceTitle,serviceDescription,serviceAmount,serviceLocation,imageParts(context,selectedImages.toList()))},Modifier.fillMaxWidth()){Text("Publish service")}
        }}
        item{Section("Availability") ;Column(Modifier.padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("Open days",fontWeight=FontWeight.SemiBold);Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf("Sun","Mon","Tue","Wed","Thu","Fri","Sat").forEachIndexed{index,label->FilterChip(index in days,{days=if(index in days)days-index else days+index},label={Text(label)})}}
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(opening,{opening=it},Modifier.weight(1f),label={Text("Opens · HH:mm")},singleLine=true);OutlinedTextField(closing,{closing=it},Modifier.weight(1f),label={Text("Closes · HH:mm")},singleLine=true)}
            OutlinedTextField(blockedDates,{blockedDates=it},Modifier.fillMaxWidth(),label={Text("Unavailable dates · comma separated YYYY-MM-DD")},singleLine=true)
            OutlinedButton(onClick={vm.saveAvailability(days.toList(),opening,closing,blockedDates)},Modifier.fillMaxWidth()){Text("Save availability")}
        }}
        item{Section("Your service listings")}
        items(if(vm.guestMode)vm.services.take(3)else vm.services.filter{it.providerId==vm.providerId}){service->ServiceManagerCard(vm,service)}
        items((if(vm.guestMode)vm.bookings else vm.providerBookings).filter{it.status==BookingStatus.PENDING}.take(10)){b->Card(Modifier.fillMaxWidth().padding(horizontal=20.dp),colors=CardDefaults.cardColors(containerColor=Color.White),shape=RoundedCornerShape(18.dp)){Column(Modifier.padding(13.dp)){Text("${b.event} · ${b.date}",fontWeight=FontWeight.Bold);Text("${b.guests} guests · ${b.service.title}",color=Muted);Row{OutlinedButton(onClick={vm.providerAction(b,false)}){Text("Decline")};Spacer(Modifier.width(7.dp));Button(onClick={vm.providerAction(b,true)}){Text("Accept request")}}}}}
    }
}
@Composable private fun ServiceManagerCard(vm:ShaadiXViewModel,service:Service){
    var showEdit by remember{mutableStateOf(false)};var amount by remember(service.id){mutableStateOf(service.price.toString())}
    Card(Modifier.fillMaxWidth().padding(horizontal=20.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),shape=RoundedCornerShape(16.dp)){Row(Modifier.fillMaxWidth().padding(13.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(service.title,fontWeight=FontWeight.Bold);Text("${service.category} · ${price(service.price)}",color=Muted)};TextButton(onClick={showEdit=true}){Text("Edit")};IconButton(onClick={vm.deleteService(service)}){Icon(Icons.Default.Delete,"Delete service",tint=MaterialTheme.colorScheme.error)}}}
    if(showEdit)AlertDialog(onDismissRequest={showEdit=false},title={Text("Update service price")},text={OutlinedTextField(amount,{amount=it.filter(Char::isDigit)},label={Text("Price · ₹")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true)},confirmButton={TextButton(onClick={vm.updateService(service,amount);showEdit=false}){Text("Save")}},dismissButton={TextButton(onClick={showEdit=false}){Text("Cancel")}})
}
@Composable private fun Admin(vm:ShaadiXViewModel,nav:NavHostController){
    LaunchedEffect(Unit){vm.refreshAdmin()}
    LazyColumn(contentPadding=PaddingValues(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(13.dp)){
        item{Header("Platform overview","ShaadiX operations",end={IconButton(onClick={nav.popBackStack()}){Icon(Icons.Default.Close,"Close")}})}
        item{Column(Modifier.padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){AdminMetric("Users",if(vm.guestMode)"2,480" else vm.adminStats["users"].orEmpty(),Modifier.weight(1f));AdminMetric("Providers",if(vm.guestMode)"186" else vm.adminStats["providers"].orEmpty(),Modifier.weight(1f))};Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){AdminMetric("Bookings",if(vm.guestMode)"642" else vm.adminStats["bookings"].orEmpty(),Modifier.weight(1f));AdminMetric("Revenue",if(vm.guestMode)"₹18.4L" else vm.adminStats["revenue"].orEmpty(),Modifier.weight(1f))}}}
        item{Section("Needs your attention");Column(Modifier.padding(horizontal=20.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)){Link("Manage users","Search and deactivate accounts",Icons.Default.People){nav.navigate("admin/manage/users")};Link("Provider approvals","Review businesses waiting for approval",Icons.Default.VerifiedUser){nav.navigate("admin/manage/providers")};Link("Reported reviews","Review flagged customer feedback",Icons.Default.Flag){nav.navigate("admin/manage/reviews")};Link("Manage categories","Create and update service categories",Icons.Default.Category){nav.navigate("admin/manage/categories")};Link("Manage bookings","View platform activity",Icons.Default.EventNote){nav.navigate("admin/manage/bookings")}}}
        item{Text(if(vm.guestMode)"Sample platform metrics · sign in with an admin account for live data." else "Live metrics from the ShaadiX backend.",Modifier.padding(horizontal=20.dp),color=Muted)}
    }
}
@Composable private fun AdminMetric(label:String,value:String,modifier:Modifier=Modifier){Card(modifier,colors=CardDefaults.cardColors(containerColor=Color.White),shape=RoundedCornerShape(17.dp)){Column(Modifier.fillMaxWidth().padding(14.dp)){Text(label,color=Muted);Text(value,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,color=Royal)}}}

@Composable private fun AdminManagement(vm:ShaadiXViewModel,nav:NavHostController,section:String){
    var showCreate by remember{mutableStateOf(false)};var categoryName by remember{mutableStateOf("")};var categoryDescription by remember{mutableStateOf("")}
    LaunchedEffect(section){vm.loadAdminSection(section)}
    Column(Modifier.fillMaxSize()){
        Header("Manage ${section}","Administrator workspace",end={IconButton(onClick={nav.popBackStack()}){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}})
        if(section=="categories")Row(Modifier.fillMaxWidth().padding(horizontal=18.dp),horizontalArrangement=Arrangement.End){Button(onClick={showCreate=true}){Icon(Icons.Default.Add,null);Text(" Add category")}}
        if(vm.adminLoading)Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center){CircularProgressIndicator(color=Royal)}
        else if(vm.adminRows.isEmpty())Blank("No ${section} found","Records from your ShaadiX platform will appear here.")
        else LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){items(vm.adminRows,key={it.id}){row->Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),shape=RoundedCornerShape(17.dp)){Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(row.title,maxLines=2,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Bold);Text(row.subtitle,color=Muted,style=MaterialTheme.typography.bodySmall)};if(row.action.isNotBlank())TextButton(onClick={vm.adminAction(section,row)}){Text(row.action)}}}}}
    }
    if(showCreate)AlertDialog(onDismissRequest={showCreate=false},title={Text("New category")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(categoryName,{categoryName=it},label={Text("Name")},singleLine=true);OutlinedTextField(categoryDescription,{categoryDescription=it},label={Text("Description")},minLines=2)}},confirmButton={TextButton(onClick={vm.createAdminCategory(categoryName,categoryDescription);showCreate=false}){Text("Create")}},dismissButton={TextButton(onClick={showCreate=false}){Text("Cancel")}})
}

