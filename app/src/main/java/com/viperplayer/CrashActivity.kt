package com.viperplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

class CrashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val message = intent?.getStringExtra(EXTRA_MESSAGE)
        val stackTrace = intent?.getStringExtra(EXTRA_STACK_TRACE)
        val threadName = intent?.getStringExtra(EXTRA_THREAD_NAME)

        setContent {
            CrashScreen(
                message = message ?: "Unknown error",
                stackTrace = stackTrace ?: "No stack trace available",
                threadName = threadName ?: "Unknown thread"
            )
        }
    }

    companion object {
        const val EXTRA_MESSAGE = "com.viperplayer.EXTRA_MESSAGE"
        const val EXTRA_STACK_TRACE = "com.viperplayer.EXTRA_STACK_TRACE"
        const val EXTRA_THREAD_NAME = "com.viperplayer.EXTRA_THREAD_NAME"
    }
}

@Composable
fun CrashScreen(
    message: String,
    stackTrace: String,
    threadName: String,
    modifier: Modifier = Modifier
) {
    MaterialTheme {
        Scaffold {
            Column(
                modifier = Modifier
                    .padding(it)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Crash Information")
                Text("Message: $message")
                Text("Stack Trace: $stackTrace")
                Text("Thread Name: $threadName")
            }
        }
    }
}

@Preview
@Composable
fun CrashScreenPreview() {
    CrashScreen(
        message = "An unexpected error occurred",
        stackTrace = "java.lang.NullPointerException: Null reference",
        threadName = "Main"
    )
}
