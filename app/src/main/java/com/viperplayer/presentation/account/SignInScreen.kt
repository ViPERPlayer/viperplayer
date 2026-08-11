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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viperplayer.R
import com.viperplayer.presentation.common.components.FilledPill
import com.viperplayer.presentation.theme.Spacing

/**
 * Sign-in screen (route `SignIn`). Reuses [AccountViewModel] for the login call, state, and error.
 * When the session becomes signed-in the [onAuthenticated] callback pops back to the caller.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onAuthenticated: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // On a successful sign-in the account flips to signed-in; leave the auth screen.
    LaunchedEffect(state.account.isSignedIn) {
        if (state.account.isSignedIn) onAuthenticated()
    }

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val canSubmit = email.isNotBlank() && password.isNotBlank() && !state.isSubmitting

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
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
                .padding(start = Spacing.xxl, end = Spacing.xxl, bottom = Spacing.sm + rootPadding.calculateBottomPadding()),
        ) {
            AuthBrandLockup()

            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.auth_sign_in_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.auth_sign_in_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(22.dp))
            AuthTextField(
                value = email,
                onValueChange = { email = it },
                label = stringResource(R.string.auth_email),
                leadingIcon = Icons.Rounded.Mail,
                keyboardType = KeyboardType.Email,
            )
            Spacer(Modifier.height(Spacing.md))
            AuthTextField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.auth_password),
                leadingIcon = Icons.Rounded.Lock,
                keyboardType = KeyboardType.Password,
                visualTransformation = passwordTransformation(passwordVisible),
                trailing = { PasswordVisibilityToggle(passwordVisible) { passwordVisible = !passwordVisible } },
            )

            // No "Forgot password?" link: there is no account-recovery route in the app and no reset
            // endpoint in the backend, so the control would be a dead no-op. A real recovery flow needs a
            // backend password-reset endpoint plus email delivery, which is out of pure-code scope here.

            if (state.error != null) {
                Text(
                    text = state.error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = Spacing.xs, top = Spacing.xs),
                )
                Spacer(Modifier.height(Spacing.xs))
            }

            Spacer(Modifier.height(10.dp))
            FilledPill(
                text = stringResource(R.string.auth_sign_in_cta),
                onClick = { viewModel.signIn(email, password) },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            )
            if (state.isSubmitting) {
                Spacer(Modifier.height(Spacing.md))
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally).height(Spacing.xxl),
                    strokeWidth = 2.dp,
                )
            }

            Spacer(Modifier.height(6.dp))
            AccountSwitchLink(
                prompt = stringResource(R.string.auth_switch_to_register_prompt),
                action = stringResource(R.string.auth_switch_to_register_action),
                onClick = onNavigateToRegister,
            )
        }
    }
}
