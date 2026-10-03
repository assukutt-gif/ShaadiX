package com.assukutt.shaadix.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
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
import com.assukutt.shaadix.data.*
import com.assukutt.shaadix.ui.theme.*
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

private fun price(n:Int)="₹"+NumberFormat.getNumberInstance(Locale("en","IN")).format(n)

class ShaadiXViewModel:ViewModel(){
    var signedIn by mutableStateOf(false); var name by mutableStateOf("Ananya Menon"); var email by mutableStateOf("")
    var search by mutableStateOf(""); var category by mutableStateOf("All"); var selected by mutableStateOf(SampleData.services.first())
    var bookingOpen by mutableStateOf(false); var message by mutableStateOf<String?>(null)
    val bookings=mutableStateListOf<Booking>().apply{addAll(SampleData.initialBookings)}
    val favorites=mutableStateListOf<String>()
    fun results()=SampleData.services.filter{(category=="All"||it.category.equals(category,true))&&(search.isBlank()||listOf(it.title,it.area,it.category).any{s->s.contains(search,true)})}
    fun favorite(s:Service){if(s.id in favorites)favorites.remove(s.id)else favorites.add(s.id)}
    fun book(event:String,date:String,guests:Int,method:String){
        val cost=if(selected.category=="Catering")selected.price*guests else selected.price
        bookings.add(0,Booking("SX${2000+bookings.size}",selected,date,event,guests,cost+600,BookingStatus.CONFIRMED))
        bookingOpen=false;message="Booking confirmed · $method selected"
    }
    fun signIn(e:String,p:String){
        if(!e.contains("@")||p.length<6){message="Enter a valid email and password (6+ characters).";return}
        if(FirebaseApp.getApps(AppContext.context).isEmpty()){message="Firebase isn’t connected. Choose guest mode to try ShaadiX.";return}
        viewModelScope.launch{runCatching{FirebaseShaadiXRepository().signIn(e,p)}.onSuccess{email=e;name=e.substringBefore("@");signedIn=true}.onFailure{message=it.localizedMessage?:"Sign in failed."}}
    }
    fun signUp(n:String,e:String,p:String){
        if(n.isBlank()||!e.contains("@")||p.length<8){message="Add your name, a valid email, and a password with at least 8 characters.";return}
        if(FirebaseApp.getApps(AppContext.context).isEmpty()){message="Connect Firebase to create an account, or explore as guest.";return}
        viewModelScope.launch{runCatching{FirebaseShaadiXRepository().signUp(n,e,p)}.onSuccess{name=n.trim();email=e;signedIn=true}.onFailure{message=it.localizedMessage?:"Sign up failed."}}
    }
}

@Composable fun ShaadiXApp(vm:ShaadiXViewModel=viewModel()){
    if(!vm.signedIn){Welcome(vm);return}
    val nav= rememberNavController();val entry by nav.currentBackStackEntryAsState();val route=entry?.destination?.route
    val snack= remember{SnackbarHostState()}
    LaunchedEffect(vm.message){vm.message?.let{snack.showSnackbar(it);vm.message=null}}
    Scaffold(containerColor=Canvas,snackbarHost={SnackbarHost(snack)},bottomBar={
        if(route in listOf("home","explore","bookings","profile"))NavigationBar(containerColor=Color.White){
            listOf("home" to Icons.Default.Home,"explore" to Icons.Default.Search,"bookings" to Icons.Default.CalendarMonth,"profile" to Icons.Default.Person).forEach{(screen,icon)->
                NavigationBarItem(route==screen,{nav.navigate(screen){popUpTo("home");launchSingleTop=true}},icon={Icon(icon,screen)},label={Text(screen.replaceFirstChar{it.uppercase()})})
            }
        }
    }){pad->NavHost(nav,"home",Modifier.padding(pad)){
        composable("home"){Home(vm,nav)};composable("explore"){Explore(vm,nav)};composable("bookings"){Bookings(vm,nav)};composable("profile"){Profile(vm,nav)}
        composable("favorites"){Favorites(vm,nav)};composable("notifications"){Notifications(nav)};composable("provider"){Provider(vm,nav)};composable("admin"){Admin(nav)}
        composable("detail/{id}",arguments=listOf(navArgument("id"){type=NavType.StringType})){Detail(vm,nav)}
    }}
    if(vm.bookingOpen)Booking(vm)
}

@Composable private fun Welcome(vm:ShaadiXViewModel){
    var e by remember{mutableStateOf("")};var p by remember{mutableStateOf("")};var n by remember{mutableStateOf("")};var create by remember{mutableStateOf(false)};var phone by remember{mutableStateOf("")};var code by remember{mutableStateOf("")};var otp by remember{mutableStateOf(false)}
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF321848),Royal,Violet)))){
        Column(Modifier.align(Alignment.Center).padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Text("S",color=Gold,style=MaterialTheme.typography.displayMedium,fontWeight=FontWeight.Bold);Text("ShaadiX",color=Color.White,style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold);Text("Plan. Book. Celebrate.",color=Color(0xFFF1DBAE));Spacer(Modifier.height(20.dp))
            Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text(if(otp)"Verify your mobile" else if(create)"Create your account" else "Welcome back",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                if(otp){OutlinedTextField(phone,{phone=it},Modifier.fillMaxWidth(),label={Text("Mobile number")},prefix={Text("+91 ")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone),singleLine=true)
                    if(phone.filter(Char::isDigit).length>=10){OutlinedTextField(code,{code=it.take(6)},Modifier.fillMaxWidth(),label={Text("OTP")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true);Text("Demo code: 123456",color=Muted)}
                    Button(onClick={if(phone.filter(Char::isDigit).length<10)vm.message="Enter a 10-digit number." else if(code=="123456")vm.signedIn=true else if(code.isNotEmpty())vm.message="Use demo code 123456."},Modifier.fillMaxWidth()){Text(if(code.isBlank())"Send OTP" else "Verify & continue")}
                    TextButton(onClick={otp=false}){Text("Back")}
                }else{
                    if(create)OutlinedTextField(n,{n=it},Modifier.fillMaxWidth(),label={Text("Full name")},singleLine=true)
                    OutlinedTextField(e,{e=it},Modifier.fillMaxWidth(),label={Text("Email")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email),singleLine=true)
                    OutlinedTextField(p,{p=it},Modifier.fillMaxWidth(),label={Text("Password")},visualTransformation=PasswordVisualTransformation(),singleLine=true)
                    vm.message?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
                    Button(onClick={if(create)vm.signUp(n,e,p)else vm.signIn(e,p)},Modifier.fillMaxWidth()){Text(if(create)"Create account" else "Sign in")}
                    if(!create){
                        OutlinedButton(onClick={otp=true},Modifier.fillMaxWidth()){Icon(Icons.Default.PhoneAndroid,null);Text("  Continue with mobile OTP")}
                        OutlinedButton(onClick={vm.message="Google sign in connects after Firebase setup."},Modifier.fillMaxWidth()){Icon(Icons.Default.AccountCircle,null);Text("  Continue with Google")}
                    }
                    TextButton(onClick={create=!create;vm.message=null}){Text(if(create)"Already registered? Sign in" else "New to ShaadiX? Create account")}
                }
            }}
            TextButton(onClick={vm.signedIn=true}){Text("Explore as guest",color=Color.White)}
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
            AsyncImage("https://images.unsplash.com/photo-1519741497674-611481863552?w=1200",null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xEE351C4C),Color(0x665E2A64),Color.Transparent))))
            Column(Modifier.align(Alignment.CenterStart).padding(18.dp)){Text("YOUR DAY, BEAUTIFULLY DONE",color=Gold,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold);Text("Big moments\nstart right here.",color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Button(onClick={nav.navigate("explore")},colors=ButtonDefaults.buttonColors(containerColor=Color.White,contentColor=Royal)){Text("Find your people  →")}}
        }}
        item{Section("Celebrate your way","View all"){nav.navigate("explore")};LazyRow(contentPadding=PaddingValues(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){items(SampleData.categories){c->
            Column(Modifier.width(74.dp).clickable{vm.category="All";nav.navigate("explore")},horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(Lilac),Alignment.Center){Text(when(c){"Wedding"->"💍";"Birthday"->"🎂";"College Function"->"🎓";"Corporate"->"✨";else->"✦"},style=MaterialTheme.typography.titleLarge)};Text(c,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelSmall)}
        }}}
        item{Section("Made for your moments","See all"){nav.navigate("explore")};LazyRow(contentPadding=PaddingValues(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){items(SampleData.services.take(4)){Tile(it,vm,nav,true)}}}
        item{Section("Your next celebration");val b=vm.bookings.firstOrNull();if(b==null)Blank("No upcoming bookings yet","Find a service to begin planning.")else Card(Modifier.fillMaxWidth().padding(horizontal=20.dp).clickable{nav.navigate("bookings")},colors=CardDefaults.cardColors(containerColor=Color.White),shape=RoundedCornerShape(18.dp)){
            Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically){AsyncImage(b.service.image,null,Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)),contentScale=ContentScale.Crop);Column(Modifier.weight(1f).padding(start=12.dp)){Text(b.event,color=Muted);Text(b.service.title,fontWeight=FontWeight.Bold);Text("${b.date} · ${b.guests} guests",color=Muted)};Icon(Icons.Default.ArrowForward,null,tint=Royal)}
        }}
    }
}

@Composable private fun Explore(vm:ShaadiXViewModel,nav:NavHostController){
    Column(Modifier.fillMaxSize()){
        Header("Explore","Find the right people for your big day")
        OutlinedTextField(vm.search,{vm.search=it},Modifier.fillMaxWidth().padding(horizontal=18.dp),placeholder={Text("Search events, venues, services…")},leadingIcon={Icon(Icons.Default.Search,null,tint=Violet)},trailingIcon={Icon(Icons.Default.Tune,"Filter",tint=Royal)},shape=RoundedCornerShape(15.dp),singleLine=true)
        LazyRow(Modifier.padding(vertical=9.dp),contentPadding=PaddingValues(horizontal=18.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)){items(listOf("All")+SampleData.serviceTypes){c->FilterChip(vm.category.equals(c,true),{vm.category=c},label={Text(c)})}}
        Row(Modifier.padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically){Text("${vm.results().size} thoughtful picks",Modifier.weight(1f),color=Muted);TextButton(onClick={vm.message="Filters · Kochi · any date · 4.5+ rating"}){Icon(Icons.Default.Tune,null);Text(" Filters")}}
        val list=vm.results();if(list.isEmpty())Blank("Nothing found yet","Try a different category or search.")else LazyColumn(contentPadding=PaddingValues(horizontal=18.dp,vertical=4.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){items(list){Tile(it,vm,nav)}}
    }
}

@Composable private fun Tile(s:Service,vm:ShaadiXViewModel,nav:NavHostController,small:Boolean=false){
    Card(Modifier.width(if(small)270.dp else 440.dp).clickable{vm.selected=s;nav.navigate("detail/${s.id}")},shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=Color.White),elevation=CardDefaults.cardElevation(2.dp)){
        Column{Box(Modifier.fillMaxWidth().height(if(small)138.dp else 178.dp)){
            AsyncImage(s.image,s.title,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            IconButton(onClick={vm.favorite(s)},Modifier.align(Alignment.TopEnd).padding(8.dp).clip(CircleShape).background(Color.White)){Icon(if(s.id in vm.favorites)Icons.Default.Favorite else Icons.Default.FavoriteBorder,"Favorite",tint=if(s.id in vm.favorites)Color(0xFFC45169)else Royal)}
            Surface(Modifier.align(Alignment.BottomStart).padding(9.dp),shape=RoundedCornerShape(25.dp),color=Color.White){Text(s.category,Modifier.padding(horizontal=9.dp,vertical=5.dp),color=Royal,style=MaterialTheme.typography.labelSmall)}
        };Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(s.title,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
            Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.LocationOn,null,tint=Muted,modifier=Modifier.size(15.dp));Text(s.area,Modifier.weight(1f),color=Muted,style=MaterialTheme.typography.bodySmall);Icon(Icons.Default.Star,null,tint=Gold,modifier=Modifier.size(16.dp));Text("${s.rating} (${s.reviews})",style=MaterialTheme.typography.labelSmall)}
            Text(if(s.category=="Catering")"${price(s.price)} / guest" else "From ${price(s.price)}",color=Royal,fontWeight=FontWeight.Bold)
        }}
    }
}

@Composable private fun Detail(vm:ShaadiXViewModel,nav:NavHostController){
    val id=nav.currentBackStackEntry?.arguments?.getString("id");val s=SampleData.services.find{it.id==id}?:vm.selected
    Column(Modifier.fillMaxSize()){
        Box(Modifier.fillMaxWidth().height(245.dp)){AsyncImage(s.image,s.title,Modifier.fillMaxSize(),contentScale=ContentScale.Crop);IconButton(onClick={nav.popBackStack()},Modifier.padding(10.dp).clip(CircleShape).background(Color.White)){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")};IconButton(onClick={vm.favorite(s)},Modifier.align(Alignment.TopEnd).padding(10.dp).clip(CircleShape).background(Color.White)){Icon(Icons.Default.FavoriteBorder,"Favorite",tint=Royal)}}
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text(s.category.uppercase(),color=Violet,fontWeight=FontWeight.Bold);Text(s.title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            Text("⌖  ${s.area}         ★ ${s.rating} · ${s.reviews} reviews",color=Muted);HorizontalDivider()
            Text("A little about them",fontWeight=FontWeight.Bold);Text(s.description,color=Muted)
            Text("Included with your booking",fontWeight=FontWeight.Bold);s.includes.forEach{Text("✓  $it",color=Muted)}
            TextButton(onClick={vm.message="Contact ${s.provider} · provider chat demo"}){Icon(Icons.Default.Chat,null);Text(" Contact provider")}
        }
        Surface(shadowElevation=8.dp,color=Color.White){Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(14.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(price(s.price),color=Royal,fontWeight=FontWeight.Bold);Text("Final price before payment",color=Muted,style=MaterialTheme.typography.labelSmall)};Button(onClick={vm.selected=s;vm.bookingOpen=true}){Text("Book now")}}}
    }
}

@Composable private fun Booking(vm:ShaadiXViewModel){
    var event by remember{mutableStateOf("Wedding")};var date by remember{mutableStateOf("18 Dec 2026")};var count by remember{mutableStateOf("150")};var method by remember{mutableStateOf("UPI")}
    val s=vm.selected;val guests=count.toIntOrNull()?.coerceIn(1,5000)?:150;val cost=if(s.category=="Catering")s.price*guests else s.price
    ModalBottomSheet(onDismissRequest={vm.bookingOpen=false},containerColor=Color.White){Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
        Text("Plan your booking",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(s.title,color=Royal)
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf("Wedding","Engagement","Birthday","College","Corporate").forEach{FilterChip(event==it,{event=it},label={Text(it)})}}
        OutlinedTextField(date,{date=it},Modifier.fillMaxWidth(),label={Text("Event date")},leadingIcon={Icon(Icons.Default.CalendarMonth,null)},singleLine=true)
        OutlinedTextField(count,{count=it.filter(Char::isDigit).take(4)},Modifier.fillMaxWidth(),label={Text("Number of guests")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true)
        Text("Payment method",fontWeight=FontWeight.SemiBold);Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf("UPI","Card","Net banking","Wallet","Pay later").forEach{FilterChip(method==it,{method=it},label={Text(it)})}}
        PriceRow("Service price",price(cost));PriceRow("Platform fee",price(600));PriceRow("Total",price(cost+600),true)
        Text("Demo checkout · no payment will be collected.",color=Muted,style=MaterialTheme.typography.bodySmall)
        Button(onClick={if(date.length<6)vm.message="Please add the event date."else vm.book(event,date,guests,method)},Modifier.fillMaxWidth(),enabled=count.isNotBlank()){Text("Confirm booking · ${price(cost+600)}")};Spacer(Modifier.height(14.dp))
    }}
}
@Composable private fun PriceRow(label:String,value:String,strong:Boolean=false){Row(Modifier.fillMaxWidth()){Text(label,Modifier.weight(1f),color=if(strong)Ink else Muted,fontWeight=if(strong)FontWeight.Bold else FontWeight.Normal);Text(value,color=Royal,fontWeight=if(strong)FontWeight.Bold else FontWeight.Medium)}}

@Composable private fun Bookings(vm:ShaadiXViewModel,nav:NavHostController){
    var tab by remember{mutableStateOf("Upcoming")}
    Column(Modifier.fillMaxSize()){Header("My bookings","Every detail, all in one place");Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=18.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Upcoming","Completed","Cancelled").forEach{FilterChip(tab==it,{tab=it},label={Text(it)})}}
        val list=vm.bookings.filter{when(tab){"Upcoming"->it.status==BookingStatus.CONFIRMED||it.status==BookingStatus.PENDING;"Completed"->it.status==BookingStatus.COMPLETED;else->it.status==BookingStatus.CANCELLED}}
        if(list.isEmpty())Blank("Your $tab plans will appear here","Discover a service to begin planning.")else LazyColumn(contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){items(list){b->Card(shape=RoundedCornerShape(19.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){
            Column{Row(Modifier.fillMaxWidth().clickable{vm.selected=b.service;nav.navigate("detail/${b.service.id}")}.padding(12.dp),verticalAlignment=Alignment.CenterVertically){AsyncImage(b.service.image,null,Modifier.size(68.dp).clip(RoundedCornerShape(13.dp)),contentScale=ContentScale.Crop);Column(Modifier.weight(1f).padding(start=10.dp)){Text(b.service.title,fontWeight=FontWeight.Bold);Text("${b.event} · ${b.date}",color=Muted);Text("${b.guests} guests · ${price(b.total)}",color=Royal)};Text(b.status.name,color=Color(0xFF2E7750),style=MaterialTheme.typography.labelSmall)}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){TextButton(onClick={vm.message="Thanks for your review!"}){Icon(Icons.Default.Star,null);Text("Review")};if(b.status==BookingStatus.CONFIRMED)TextButton(onClick={val i=vm.bookings.indexOf(b);if(i>=0)vm.bookings[i]=b.copy(status=BookingStatus.CANCELLED)}){Text("Cancel",color=MaterialTheme.colorScheme.error)}}
        }}}}
    }
}

@Composable private fun Blank(title:String,body:String){Column(Modifier.fillMaxSize().padding(30.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.EventAvailable,null,tint=Violet,modifier=Modifier.size(48.dp));Spacer(Modifier.height(12.dp));Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text(body,color=Muted)}}
@Composable private fun Link(title:String,detail:String,icon:androidx.compose.ui.graphics.vector.ImageVector,tint:Color=Royal,action:()->Unit){Row(Modifier.fillMaxWidth().clickable(onClick=action).padding(14.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=tint);Column(Modifier.weight(1f).padding(start=12.dp)){Text(title,fontWeight=FontWeight.SemiBold);Text(detail,color=Muted,style=MaterialTheme.typography.bodySmall)};Icon(Icons.Default.ChevronRight,null,tint=Muted)}}

@Composable private fun Profile(vm:ShaadiXViewModel,nav:NavHostController){
    LazyColumn(contentPadding=PaddingValues(bottom=25.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Header("Your profile","Make it feel like yours")}
        item{Card(Modifier.fillMaxWidth().padding(horizontal=20.dp),colors=CardDefaults.cardColors(containerColor=Royal),shape=RoundedCornerShape(22.dp)){Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){Text(vm.name.take(1),Modifier.clip(CircleShape).background(Violet).padding(15.dp),color=Gold,style=MaterialTheme.typography.headlineMedium);Column(Modifier.weight(1f).padding(start=12.dp)){Text(vm.name,color=Color.White,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge);Text(vm.email.ifBlank{"Guest planner · Kochi"},color=Color.White.copy(alpha=.75f))};Icon(Icons.Default.Edit,null,tint=Gold)}}}
        item{Column(Modifier.padding(horizontal=20.dp).clip(RoundedCornerShape(18.dp)).background(Color.White)){
            Link("My bookings","Your event plans",Icons.Default.CalendarMonth){nav.navigate("bookings")};Link("Favourites","${vm.favorites.size} saved services",Icons.Default.FavoriteBorder){nav.navigate("favorites")}
            Link("Reviews & ratings","Share your experience",Icons.Default.RateReview){vm.message="Reviews ready to connect."};Link("Notifications","Reminders, updates and offers",Icons.Default.NotificationsNone){nav.navigate("notifications")}
        }}
        item{Section("Provider tools");Column(Modifier.padding(horizontal=20.dp).clip(RoundedCornerShape(18.dp)).background(Color.White)){
            Link("Provider dashboard","Business profile and booking requests",Icons.Default.Storefront){nav.navigate("provider")};Link("Admin overview","Platform operations",Icons.Default.AdminPanelSettings){nav.navigate("admin")}
        }}
        item{Column(Modifier.padding(horizontal=20.dp).clip(RoundedCornerShape(18.dp)).background(Color.White)){
            Link("Help & support","We're happy to help",Icons.Default.HelpOutline){vm.message="Support: hello@shaadix.example"};Link("Settings","Account and preferences",Icons.Default.Settings){vm.message="Settings panel"}
            Link("Sign out","Return to welcome",Icons.Default.Logout,MaterialTheme.colorScheme.error){vm.signedIn=false}
        }}
    }
}
@Composable private fun Favorites(vm:ShaadiXViewModel,nav:NavHostController){Column(Modifier.fillMaxSize()){Header("Your favourites","The shortlist for your big day");val list=SampleData.services.filter{it.id in vm.favorites};if(list.isEmpty())Blank("Save a little inspiration","Tap a service heart to keep it close.")else LazyColumn(contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){items(list){Tile(it,vm,nav)}}}}

@Composable private fun Notifications(nav:NavHostController){Column(Modifier.fillMaxSize()){Header("Notifications","A little nudge when it matters",end={IconButton(onClick={nav.popBackStack()}){Icon(Icons.Default.Close,"Close")}})
    listOf("Booking confirmed|Courtyard Palace · 18 Dec 2026|Just now","A date to remember|Your celebration is 30 days away.|Yesterday","A little something for you|10% off select decor.|2 days ago","Provider message|Your venue sent a note.|3 days ago").forEach{val p=it.split("|");Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Notifications,null,tint=Royal);Column(Modifier.weight(1f).padding(start=12.dp)){Text(p[0],fontWeight=FontWeight.Bold);Text(p[1],color=Muted);Text(p[2],color=Violet,style=MaterialTheme.typography.labelSmall)}};HorizontalDivider()}
}}

@Composable private fun Provider(vm:ShaadiXViewModel,nav:NavHostController){
    var business by remember{mutableStateOf("The Courtyard Palace")};var place by remember{mutableStateOf("Kakkanad, Kochi")};var amount by remember{mutableStateOf("85000")}
    LazyColumn(contentPadding=PaddingValues(bottom=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Header("Provider studio","Grow your business",end={IconButton(onClick={nav.popBackStack()}){Icon(Icons.Default.Close,"Close")}})}
        item{Card(Modifier.fillMaxWidth().padding(horizontal=20.dp),colors=CardDefaults.cardColors(containerColor=Royal),shape=RoundedCornerShape(20.dp)){Row(Modifier.fillMaxWidth().padding(18.dp),horizontalArrangement=Arrangement.SpaceBetween){Text("Earnings\n₹1.82L",color=Color.White,fontWeight=FontWeight.Bold);Text("Requests\n08",color=Color.White,fontWeight=FontWeight.Bold);Text("Rating\n4.9 ★",color=Color.White,fontWeight=FontWeight.Bold)}}}
        item{Section("Business profile","Save"){vm.message="Profile saved in demo."};Column(Modifier.padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedTextField(business,{business=it},Modifier.fillMaxWidth(),label={Text("Business name")});OutlinedTextField(place,{place=it},Modifier.fillMaxWidth(),label={Text("Location")})
            OutlinedTextField(amount,{amount=it.filter(Char::isDigit)},Modifier.fillMaxWidth(),label={Text("Starting price")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
            OutlinedButton(onClick={vm.message="Service photos connect to Firebase Storage."},Modifier.fillMaxWidth()){Icon(Icons.Default.AddPhotoAlternate,null);Text(" Add service images")}
            Button(onClick={vm.message="Business profile updated."},Modifier.fillMaxWidth()){Text("Save profile")}
        }}
        item{Section("Booking requests","Availability"){vm.message="Availability calendar opened."}}
        items(vm.bookings.take(3)){b->Card(Modifier.fillMaxWidth().padding(horizontal=20.dp),colors=CardDefaults.cardColors(containerColor=Color.White),shape=RoundedCornerShape(18.dp)){Column(Modifier.padding(13.dp)){Text("${b.event} · ${b.date}",fontWeight=FontWeight.Bold);Text("${b.guests} guests · ${b.service.title}",color=Muted);Row{OutlinedButton(onClick={vm.message="Request declined."}){Text("Decline")};Spacer(Modifier.width(7.dp));Button(onClick={vm.message="Request accepted."}){Text("Accept request")}}}}}
    }
}
@Composable private fun Admin(nav:NavHostController){
    LazyColumn(contentPadding=PaddingValues(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(13.dp)){
        item{Header("Platform overview","ShaadiX operations",end={IconButton(onClick={nav.popBackStack()}){Icon(Icons.Default.Close,"Close")}})}
        item{Column(Modifier.padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){AdminMetric("Users","2,480",Modifier.weight(1f));AdminMetric("Providers","186",Modifier.weight(1f))};Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){AdminMetric("Bookings","642",Modifier.weight(1f));AdminMetric("Revenue","₹18.4L",Modifier.weight(1f))}}}
        item{Section("Needs your attention");Column(Modifier.padding(horizontal=20.dp).clip(RoundedCornerShape(18.dp)).background(Color.White)){Link("Provider approvals","12 businesses waiting",Icons.Default.VerifiedUser){};Link("Reported reviews","3 reports to review",Icons.Default.Flag){};Link("Manage categories","7 event types · 12 services",Icons.Default.Category){};Link("Manage bookings","View platform activity",Icons.Default.EventNote){}}}
        item{Text("Demo dashboard · configure Firebase roles before using real admin data.",Modifier.padding(horizontal=20.dp),color=Muted)}
    }
}
@Composable private fun AdminMetric(label:String,value:String,modifier:Modifier=Modifier){Card(modifier,colors=CardDefaults.cardColors(containerColor=Color.White),shape=RoundedCornerShape(17.dp)){Column(Modifier.fillMaxWidth().padding(14.dp)){Text(label,color=Muted);Text(value,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,color=Royal)}}}
