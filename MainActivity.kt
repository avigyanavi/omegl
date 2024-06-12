package com.am24.omegl

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.rememberAsyncImagePainter
import com.am24.omegl.ui.theme.OmeglTheme
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity(), AdCallback {

    private var mInterstitialAd: InterstitialAd? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MobileAds.initialize(this) {}
        loadAd()
        checkExistingUser()
        requestPermissions()
        setContent {
            OmeglTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(this)
                }
            }
        }
    }

    private fun loadAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(this, "ca-app-pub-5094389629300846/6942625673", adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                mInterstitialAd = null
            }

            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                mInterstitialAd = interstitialAd
            }
        })
    }

    override fun showAd() {
        if (mInterstitialAd != null) {
            mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    mInterstitialAd = null
                    loadAd()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError?) {
                    mInterstitialAd = null
                    loadAd()
                }

                override fun onAdShowedFullScreenContent() {
                    mInterstitialAd = null
                }
            }
            mInterstitialAd?.show(this)
        }
    }

    private fun checkExistingUser() {
        val sharedPreferences = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val uid = sharedPreferences.getString("uid", null)
        if (uid != null) {
            Firebase.auth.signInAnonymously()
        }
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        requestPermissions(permissions, PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allPermissionsGranted = grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }
            if (!allPermissionsGranted) {
                // Handle the case where permissions are not granted
            }
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }
}

interface AdCallback {
    fun showAd()
}

@Composable
fun MainScreen(adCallback: AdCallback) {
    val navController = rememberNavController()
    val uid = Firebase.auth.currentUser?.uid

    LaunchedEffect(uid) {
        uid?.let {
            val notificationsRef = Firebase.database.reference.child("notifications").child(it)
            notificationsRef.addChildEventListener(object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    val notification = snapshot.getValue(Notification::class.java)
                    notification?.let { notif ->
                        if (notif.type == "chatEnded") {
                            navController.navigate("dashboard") {
                                popUpTo("chat/{chatId}") { inclusive = true }
                            }
                            adCallback.showAd()
                        }
                    }
                    snapshot.ref.removeValue()
                }

                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onChildRemoved(snapshot: DataSnapshot) {}
                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }

    NavHost(navController = navController, startDestination = "sign_in") {
        composable("sign_in") { SignInScreen(navController) }
        composable("dashboard") { DashboardScreen(navController) }
        composable("random_chat") { RandomChatScreen(navController) }
        composable(
            route = "chat/{chatId}?commonInterest={commonInterest}",
            arguments = listOf(
                navArgument("chatId") { type = NavType.StringType },
                navArgument("commonInterest") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId")
            val commonInterest = backStackEntry.arguments?.getString("commonInterest")
            ChatScreen(navController, chatId, commonInterest, adCallback)
        }
    }
}

data class Notification(
    val message: String = "",
    val type: String = ""
)

@Composable
fun SignInScreen(navController: NavHostController) {
    var loading by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (loading) {
            CircularProgressIndicator()
        } else {
            Button(onClick = {
                loading = true
                Firebase.auth.signInAnonymously()
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val user = Firebase.auth.currentUser
                            val sharedPreferences = navController.context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
                            sharedPreferences.edit().putString("uid", user?.uid).apply()
                            val db = Firebase.database.reference
                            val userDoc = mapOf(
                                "uid" to user?.uid,
                                "createdAt" to System.currentTimeMillis()
                            )
                            db.child("users").child(user?.uid ?: "").setValue(userDoc)
                                .addOnSuccessListener {
                                    navController.navigate("dashboard") {
                                        popUpTo("sign_in") { inclusive = true }
                                    }
                                }
                                .addOnFailureListener {
                                    loading = false
                                }
                        } else {
                            loading = false
                        }
                    }
            }) {
                Text(text = "Sign In Anonymously")
            }
        }
    }
}

@Composable
fun DashboardScreen(navController: NavHostController) {
    val context = navController.context
    val uid = Firebase.auth.currentUser?.uid ?: return
    val userRef = Firebase.database.reference.child("users").child(uid)
    var interest by remember { mutableStateOf("") }
    val interests = remember { mutableStateListOf<String>() }
    var interestBasedMatching by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    val welcomeMessage = "Welcome $uid"
    var waitingUserCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        userRef.child("interests").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                interests.clear()
                snapshot.children.forEach {
                    val interest = it.getValue(String::class.java)
                    if (interest != null) {
                        interests.add(interest)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })

        val waitingUsersRef = Firebase.database.reference.child("waitingUsers")
        waitingUsersRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                waitingUserCount = snapshot.childrenCount.toInt()
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(durationMillis = 1000))
        ) {
            Text(text = welcomeMessage, modifier = Modifier.padding(16.dp), color = Color.White)
        }

        Text(text = "Waiting users: $waitingUserCount", color = Color.White)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = interestBasedMatching,
                onCheckedChange = { checked ->
                    interestBasedMatching = checked
                    if (!checked) {
                        interests.clear()
                    }
                }
            )
            Text(text = "Interest-based Matching")
        }

        if (interestBasedMatching) {
            Column {
                interests.forEach { interest ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = interest,
                            modifier = Modifier.padding(4.dp)
                        )
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Remove interest",
                            tint = Color.Red,
                            modifier = Modifier.clickable {
                                interests.remove(interest)
                                userRef.child("interests").setValue(interests)
                            }
                        )
                    }
                }
                BasicTextField(
                    value = interest,
                    onValueChange = { interest = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .background(Color.DarkGray),
                    textStyle = LocalTextStyle.current.copy(color = Color.White)
                )
                Button(onClick = {
                    if (interest.isNotBlank()) {
                        loading = true
                        val newInterestRef = userRef.child("interests").push()
                        newInterestRef.setValue(interest)
                            .addOnCompleteListener {
                                interest = ""
                                loading = false
                            }
                    }
                }) {
                    Text(text = "Add Interest")
                }
            }
        }

        if (loading) {
            CircularProgressIndicator()
        }

        Button(onClick = {
            val newUser = mapOf(
                "uid" to uid,
                "timestamp" to System.currentTimeMillis(),
                "interestBased" to interestBasedMatching,
                "interests" to interests
            )
            val waitingUsersRef = Firebase.database.reference.child("waitingUsers")
            if (interestBasedMatching) {
                waitingUsersRef.child("interest").child(uid).setValue(newUser)
            } else {
                waitingUsersRef.child("random").child(uid).setValue(newUser)
            }
            navController.navigate("random_chat")
        }) {
            Text(text = "Random Chat")
        }

        Button(onClick = {
            deleteAccount(navController, uid, context)
        }) {
            Text(text = "Delete Account")
        }
    }
}

private fun deleteAccount(navController: NavHostController, uid: String, context: Context) {
    val db = Firebase.database.reference
    Firebase.auth.currentUser?.delete()?.addOnCompleteListener { task ->
        if (task.isSuccessful) {
            db.child("users").child(uid).removeValue()
            db.child("waitingUsers").child("random").child(uid).removeValue()
            db.child("waitingUsers").child("interest").child(uid).removeValue()
            db.child("chats").orderByChild("user1").equalTo(uid).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    snapshot.children.forEach {
                        it.ref.removeValue()
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
            db.child("chats").orderByChild("user2").equalTo(uid).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    snapshot.children.forEach {
                        it.ref.removeValue()
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })

            val sharedPreferences = context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
            sharedPreferences.edit().remove("uid").apply()
            navController.navigate("sign_in") {
                popUpTo("dashboard") { inclusive = true }
            }
        }
    }
}

@Composable
fun RandomChatScreen(navController: NavHostController) {
    val uid = Firebase.auth.currentUser?.uid ?: return
    val waitingUsersRef = Firebase.database.reference.child("waitingUsers")
    val userRef = Firebase.database.reference.child("users").child(uid)

    LaunchedEffect(Unit) {
        userRef.child("currentChat").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val chatId = snapshot.child("chatId").getValue(String::class.java)
                val commonInterest = snapshot.child("commonInterest").getValue(String::class.java)
                if (chatId != null) {
                    navController.navigate("chat/$chatId?commonInterest=$commonInterest") {
                        popUpTo("random_chat") { inclusive = true }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
            }
        })
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Finding a random chat partner...")

        BackHandler {
            waitingUsersRef.child("random").child(uid).removeValue()
            waitingUsersRef.child("interest").child(uid).removeValue()
            navController.popBackStack()
        }
    }
}

@Composable
fun ChatScreen(navController: NavHostController, chatId: String?, commonInterest: String?, adCallback: AdCallback) {
    if (chatId == null) return
    var message by remember { mutableStateOf(TextFieldValue("")) }
    val db = Firebase.database.reference
    val messagesRef = db.child("chats").child(chatId).child("messages")
    val messages = remember { mutableStateListOf<Map<String, Any>>() }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        messagesRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val newMessage = snapshot.value as? Map<String, Any>
                if (newMessage != null) {
                    messages.add(newMessage)
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        })

        val chatRef = db.child("chats").child(chatId)
        chatRef.child("ended").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val chatEnded = snapshot.getValue(Boolean::class.java) ?: false
                if (chatEnded) {
                    navController.navigate("dashboard") {
                        popUpTo("chat/$chatId") { inclusive = true }
                    }
                    adCallback.showAd()
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })

        val onDisconnectRef = chatRef.child("ended")
        onDisconnectRef.onDisconnect().setValue(true)
    }

    val context = LocalContext.current
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && imageUri != null) {
            uploadMedia(imageUri!!, context, messagesRef, "image", { loading = true }) { loading = false }
        }
    }

    val takeVideoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { uri ->
        uri?.let {
            uploadMedia(videoUri!!, context, messagesRef, "video", { loading = true }, { loading = false })
        }
    }

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        commonInterest?.let {
            if (it.isNotEmpty()) {
                Text(text = "You both like $it", color = Color.White, modifier = Modifier.padding(16.dp))
            }
        }

        Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
            messages.forEach { msg ->
                val alignment = if ((msg["from"] as String) == Firebase.auth.currentUser?.uid) Alignment.End else Alignment.Start
                val backgroundColor = Color.Transparent

                when (msg["type"]) {
                    "text" -> {
                        Text(
                            text = msg["content"] as String,
                            color = Color.White,
                            modifier = Modifier
                                .background(backgroundColor)
                                .padding(8.dp)
                                .align(alignment)
                        )
                    }
                    "image" -> {
                        val painter = rememberAsyncImagePainter(msg["content"] as String)
                        Image(
                            painter = painter,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(200.dp)
                                .background(backgroundColor)
                                .padding(8.dp)
                                .align(alignment)
                        )
                    }
                    "video" -> {
                        VideoPlayer(videoUrl = msg["content"] as String, modifier = Modifier.align(alignment))
                    }
                }
            }
        }

        if (loading) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        }

        BasicTextField(
            value = message,
            onValueChange = { message = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(Color.DarkGray),
            textStyle = LocalTextStyle.current.copy(color = Color.White)
        )
        Button(onClick = {
            val newMessage = mapOf("type" to "text", "content" to message.text, "from" to Firebase.auth.currentUser?.uid)
            messagesRef.push().setValue(newMessage)
            message = TextFieldValue("")
        }) {
            Text(text = "Send")
        }
        Row {
            Button(onClick = {
                imageUri = createImageFile(context)?.let {
                    FileProvider.getUriForFile(context, "${context.packageName}.provider", it)
                }
                imageUri?.let {
                    takePictureLauncher.launch(it)
                }
            }) {
                Text(text = "Take Picture")
            }
            Button(onClick = {
                videoUri = createVideoFile(context)?.let {
                    FileProvider.getUriForFile(context, "${context.packageName}.provider", it)
                }
                videoUri?.let {
                    takeVideoLauncher.launch(videoUri!!)
                }
            }) {
                Text(text = "Take Video")
            }
        }
        Button(onClick = {
            endChat(chatId, messagesRef, adCallback)
        }) {
            Text(text = "End Chat")
        }
    }

    BackHandler {
        endChat(chatId, messagesRef, adCallback)
    }
}

private fun endChat(chatId: String, messagesRef: DatabaseReference, adCallback: AdCallback) {
    val db = Firebase.database.reference
    val chatRef = db.child("chats").child(chatId)
    chatRef.child("ended").setValue(true).addOnCompleteListener {
        chatRef.removeValue().addOnCompleteListener {
            adCallback.showAd()
        }
    }
}

@Composable
fun VideoPlayer(videoUrl: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AndroidView(
        factory = {
            PlayerView(context).apply {
                player = ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(videoUrl))
                    prepare()
                    playWhenReady = true
                }
            }
        },
        modifier = modifier
            .size(200.dp)
            .padding(8.dp)
    )
}

private fun uploadMedia(
    uri: Uri,
    context: Context,
    messagesRef: DatabaseReference,
    type: String,
    onStart: () -> Unit,
    onComplete: () -> Unit
) {
    val storageRef = Firebase.storage.reference.child("media/${System.currentTimeMillis()}.${if (type == "image") "jpg" else "mp4"}")

    onStart()
    storageRef.putFile(uri).addOnSuccessListener {
        storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
            val message = mapOf("type" to type, "content" to downloadUri.toString(), "from" to Firebase.auth.currentUser?.uid)
            messagesRef.push().setValue(message)
            onComplete()
        }.addOnFailureListener {
            onComplete()
        }
    }.addOnFailureListener {
        onComplete()
    }
}

private fun createImageFile(context: Context): File? {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val storageDir = context.getExternalFilesDir(null)
    return File.createTempFile("JPEG_${timestamp}_", ".jpg", storageDir)
}

private fun createVideoFile(context: Context): File? {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val storageDir = context.getExternalFilesDir(null)
    return File.createTempFile("MP4_${timestamp}_", ".mp4", storageDir)
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    OmeglTheme {
        MainScreen(object : AdCallback {
            override fun showAd() {
                // No-op for preview
            }
        })
    }
}
