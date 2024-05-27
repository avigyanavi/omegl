package com.am24.omegl

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.rememberAsyncImagePainter
import com.am24.omegl.ui.theme.OmeglTheme
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions()
        setContent {
            OmeglTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
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

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "sign_in") {
        composable("sign_in") { SignInScreen(navController) }
        composable("dashboard") { DashboardScreen(navController) }
        composable("random_chat") { RandomChatScreen(navController) }
        composable("chat/{chatId}") { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: return@composable
            ChatScreen(navController, chatId)
        }
    }
}

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
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(onClick = { navController.navigate("random_chat") }) {
            Text(text = "Random Chat")
        }
        Button(onClick = {
            Firebase.auth.signOut()
            val sharedPreferences = navController.context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
            sharedPreferences.edit().remove("uid").apply()
            navController.navigate("sign_in") {
                popUpTo("dashboard") { inclusive = true }
            }
        }) {
            Text(text = "Logout")
        }
    }
}

@Composable
fun RandomChatScreen(navController: NavHostController) {
    val context = navController.context
    val uid = Firebase.auth.currentUser?.uid ?: return
    val waitingUsersRef = Firebase.database.reference.child("waitingUsers/random")
    val userRef = Firebase.database.reference.child("users").child(uid)

    LaunchedEffect(Unit) {
        val newUser = mapOf("uid" to uid, "timestamp" to System.currentTimeMillis())
        waitingUsersRef.child(uid).setValue(newUser)
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Finding a random chat partner...")

        LaunchedEffect(Unit) {
            userRef.child("currentChat").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val chatId = snapshot.getValue(String::class.java)
                    if (chatId != null) {
                        navController.navigate("chat/$chatId") {
                            popUpTo("random_chat") { inclusive = true }
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                }
            })
        }

        BackHandler {
            waitingUsersRef.child(uid).removeValue()
            navController.popBackStack()
        }
    }
}

@Composable
fun ChatScreen(navController: NavHostController, chatId: String) {
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
        Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
            messages.forEach { msg ->
                when (msg["type"]) {
                    "text" -> {
                        Text(
                            text = msg["content"] as String,
                            color = Color.White,
                            modifier = Modifier
                                .background(if ((msg["from"] as String) == Firebase.auth.currentUser?.uid) Color.Green else Color.Gray)
                                .padding(8.dp)
                                .align(if ((msg["from"] as String) == Firebase.auth.currentUser?.uid) Alignment.End else Alignment.Start)
                        )
                    }
                    "image", "video" -> {
                        val painter = rememberAsyncImagePainter(msg["content"] as String)
                        Image(
                            painter = painter,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(200.dp)
                                .background(if ((msg["from"] as String) == Firebase.auth.currentUser?.uid) Color.Green else Color.Gray)
                                .padding(8.dp)
                                .align(if ((msg["from"] as String) == Firebase.auth.currentUser?.uid) Alignment.End else Alignment.Start)
                        )
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
            endChat(chatId, navController)
        }) {
            Text(text = "End Chat")
        }
    }

    BackHandler {
        endChat(chatId, navController)
    }
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

private fun endChat(chatId: String, navController: NavHostController) {
    val db = Firebase.database.reference
    val chatRef = db.child("chats").child(chatId)

    chatRef.removeValue().addOnSuccessListener {
        navController.navigate("dashboard") {
            popUpTo("chat/$chatId") { inclusive = true }
        }
    }.addOnFailureListener {
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    OmeglTheme {
        MainScreen()
    }
}
