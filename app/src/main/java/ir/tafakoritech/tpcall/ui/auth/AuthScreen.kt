package ir.tafakoritech.tpcall.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.tafakoritech.tpcall.R
import ir.tafakoritech.tpcall.call.CallController
import ir.tafakoritech.tpcall.ui.components.TpPasswordField
import ir.tafakoritech.tpcall.ui.components.TpPrimaryButton
import ir.tafakoritech.tpcall.ui.components.TpTextField
import ir.tafakoritech.tpcall.ui.components.TpTextButton
import ir.tafakoritech.tpcall.ui.theme.TpColors
import ir.tafakoritech.tpcall.ui.theme.TpDimens
import ir.tafakoritech.tpcall.util.ExtensionValidator

@Composable
fun AuthScreen(controller: CallController) {
    var showRegister by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TpColors.BackgroundPrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TpDimens.ScreenPadding)
            .padding(vertical = TpDimens.SpaceXxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppMark()
        Spacer(Modifier.height(TpDimens.SpaceXl))

        if (showRegister) {
            RegisterForm(controller, onSwitchToLogin = { showRegister = false })
        } else {
            LoginForm(controller, onSwitchToRegister = { showRegister = true })
        }
    }
}

@Composable
internal fun AppMark() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(TpColors.AccentDeep),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_call),
                contentDescription = null,
                tint = TpColors.AccentBright,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(TpDimens.SpaceMd))
        Text(
            text = "TP Call",
            style = MaterialTheme.typography.headlineMedium,
            color = TpColors.TextPrimary,
        )
        Spacer(Modifier.height(TpDimens.SpaceXxs))
        Text(
            text = "تلفن داخلی شرکت",
            style = MaterialTheme.typography.bodyMedium,
            color = TpColors.TextSecondary,
        )
    }
}

@Composable
private fun LoginForm(controller: CallController, onSwitchToRegister: () -> Unit) {
    var extension by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val busy by controller.authBusy.collectAsStateWithLifecycle()
    val error by controller.authError.collectAsStateWithLifecycle()

    LoginFormContent(
        extension = extension,
        password = password,
        passwordVisible = passwordVisible,
        busy = busy,
        error = error,
        onExtensionChange = { extension = ExtensionValidator.sanitize(it) },
        onPasswordChange = { password = it },
        onTogglePasswordVisibility = { passwordVisible = !passwordVisible },
        onSubmit = { controller.login(extension, password) },
        onSwitchToRegister = onSwitchToRegister,
    )
}

@Composable
internal fun LoginFormContent(
    extension: String,
    password: String,
    passwordVisible: Boolean,
    busy: Boolean,
    error: String?,
    onExtensionChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onSubmit: () -> Unit,
    onSwitchToRegister: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        TpTextField(
            value = extension,
            onValueChange = onExtensionChange,
            label = "شماره داخلی",
            keyboardType = KeyboardType.Number,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(TpDimens.SpaceMd))
        TpPasswordField(
            value = password,
            onValueChange = onPasswordChange,
            label = "رمز عبور",
            visible = passwordVisible,
            onToggleVisibility = onTogglePasswordVisibility,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(TpDimens.SpaceMd))
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = TpColors.Destructive,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(TpDimens.SpaceSm))
        }

        val enabled = !busy && ExtensionValidator.isValidExtension(extension) && password.isNotEmpty()
        TpPrimaryButton(
            text = "ورود",
            loading = busy,
            enabled = enabled,
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(TpDimens.SpaceSm))
        TpTextButton(
            text = "ثبت داخلی جدید",
            onClick = onSwitchToRegister,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RegisterForm(controller: CallController, onSwitchToLogin: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var extension by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val busy by controller.authBusy.collectAsStateWithLifecycle()
    val error by controller.authError.collectAsStateWithLifecycle()

    RegisterFormContent(
        name = name,
        extension = extension,
        password = password,
        passwordVisible = passwordVisible,
        busy = busy,
        error = error,
        onNameChange = { name = it },
        onExtensionChange = { extension = ExtensionValidator.sanitize(it) },
        onPasswordChange = { password = it },
        onTogglePasswordVisibility = { passwordVisible = !passwordVisible },
        onSubmit = { controller.register(name, extension, password) },
        onSwitchToLogin = onSwitchToLogin,
    )
}

@Composable
internal fun RegisterFormContent(
    name: String,
    extension: String,
    password: String,
    passwordVisible: Boolean,
    busy: Boolean,
    error: String?,
    onNameChange: (String) -> Unit,
    onExtensionChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onSubmit: () -> Unit,
    onSwitchToLogin: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "ثبت داخلی جدید",
            style = MaterialTheme.typography.titleLarge,
            color = TpColors.TextPrimary,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(TpDimens.SpaceMd))

        TpTextField(
            value = name,
            onValueChange = onNameChange,
            label = "نام",
            supportingText = if (name.isNotEmpty() && !ExtensionValidator.isNameValid(name)) "نام را وارد کنید" else null,
            isError = name.isNotEmpty() && !ExtensionValidator.isNameValid(name),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(TpDimens.SpaceSm))
        TpTextField(
            value = extension,
            onValueChange = onExtensionChange,
            label = "شماره داخلی",
            keyboardType = KeyboardType.Number,
            supportingText = when {
                extension.length in 1..2 -> "شماره داخلی باید سه رقم باشد"
                extension.isNotEmpty() && !ExtensionValidator.isValidExtension(extension) -> "شماره داخلی معتبر نیست"
                else -> null
            },
            isError = extension.isNotEmpty() && !ExtensionValidator.isValidExtension(extension),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(TpDimens.SpaceSm))
        TpPasswordField(
            value = password,
            onValueChange = onPasswordChange,
            label = "رمز عبور",
            visible = passwordVisible,
            onToggleVisibility = onTogglePasswordVisibility,
            supportingText = if (password.isNotEmpty() && !ExtensionValidator.isPasswordValid(password)) {
                "رمز عبور باید حداقل ۶ کاراکتر باشد"
            } else {
                null
            },
            isError = password.isNotEmpty() && !ExtensionValidator.isPasswordValid(password),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(TpDimens.SpaceMd))
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = TpColors.Destructive,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(TpDimens.SpaceSm))
        }

        val enabled = !busy &&
            ExtensionValidator.isNameValid(name) &&
            ExtensionValidator.isValidExtension(extension) &&
            ExtensionValidator.isPasswordValid(password)
        TpPrimaryButton(
            text = "ثبت و ورود",
            loading = busy,
            enabled = enabled,
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(TpDimens.SpaceSm))
        TpTextButton(
            text = "ورود به حساب موجود",
            onClick = onSwitchToLogin,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
