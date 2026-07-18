package com.viperplayer.presentation.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viperplayer.R
import com.viperplayer.domain.account.AccountState

/**
 * Account screen: sign in / register against the ViPER backend, or show the signed-in account with a
 * sign-out action. Render-only — all auth logic lives in [AccountViewModel] and the repository.
 */
@Composable
fun AccountScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AccountContent(
        state = uiState,
        rootPadding = rootPadding,
        onNavigateBack = onNavigateBack,
        onSetMode = viewModel::setMode,
        onSignIn = viewModel::signIn,
        onRegister = viewModel::register,
        onSignOut = viewModel::signOut,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountContent(
    state: AccountUiState,
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    onSetMode: (AuthMode) -> Unit,
    onSignIn: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(top = rootPadding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                // Bottom inset (applied INSIDE the scroll region) so the last control clears the mini player.
                .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp + rootPadding.calculateBottomPadding()),
        ) {
            when {
                !state.isConfigured -> NotConfiguredCard()
                state.account.isSignedIn -> SignedInContent(state.account, onSignOut)
                else -> AuthForm(
                    state = state,
                    onSetMode = onSetMode,
                    onSignIn = onSignIn,
                    onRegister = onRegister,
                )
            }
        }
    }
}

@Composable
private fun NotConfiguredCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.account_not_configured),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SignedInContent(account: AccountState, onSignOut: () -> Unit) {
    val user = account.user ?: return
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Icon(
            imageVector = Icons.Default.AccountCircle,
            contentDescription = null,
            modifier = Modifier.height(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = user.displayName.ifBlank { user.email },
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = user.email,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.account_sign_out))
        }
    }
}

@Composable
private fun AuthForm(
    state: AccountUiState,
    onSetMode: (AuthMode) -> Unit,
    onSignIn: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }

    val isRegister = state.mode == AuthMode.Register
    val canSubmit = email.isNotBlank() && password.isNotBlank() && !state.isSubmitting

    Spacer(Modifier.height(16.dp))
    Text(
        text = stringResource(
            if (isRegister) R.string.account_register_heading else R.string.account_sign_in_heading
        ),
        style = MaterialTheme.typography.headlineSmall,
    )
    Spacer(Modifier.height(16.dp))

    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text(stringResource(R.string.account_email)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))

    if (isRegister) {
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text(stringResource(R.string.account_display_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
    }

    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text(stringResource(R.string.account_password)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )

    if (state.error != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = state.error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    Spacer(Modifier.height(16.dp))
    Button(
        onClick = {
            if (isRegister) onRegister(email, password, displayName) else onSignIn(email, password)
        },
        enabled = canSubmit,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (state.isSubmitting) {
            CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
        } else {
            Text(
                stringResource(
                    if (isRegister) R.string.account_create_account else R.string.account_sign_in
                )
            )
        }
    }

    Spacer(Modifier.height(8.dp))
    TextButton(
        onClick = { onSetMode(if (isRegister) AuthMode.SignIn else AuthMode.Register) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            stringResource(
                if (isRegister) R.string.account_have_account else R.string.account_need_account
            )
        )
    }
}
