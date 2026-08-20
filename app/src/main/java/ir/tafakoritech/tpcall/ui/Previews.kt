package ir.tafakoritech.tpcall.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import ir.tafakoritech.tpcall.call.CallState
import ir.tafakoritech.tpcall.data.contacts.CorporateContact
import ir.tafakoritech.tpcall.ui.auth.LoginFormContent
import ir.tafakoritech.tpcall.ui.auth.RegisterFormContent
import ir.tafakoritech.tpcall.ui.call.ConnectingCallContent
import ir.tafakoritech.tpcall.ui.call.InCallContent
import ir.tafakoritech.tpcall.ui.call.IncomingCallContent
import ir.tafakoritech.tpcall.ui.call.OutgoingCallContent
import ir.tafakoritech.tpcall.ui.contacts.ContactsContent
import ir.tafakoritech.tpcall.ui.contacts.ContactsUiState
import ir.tafakoritech.tpcall.ui.theme.TPCallTheme

private const val TP_BACKGROUND = 0xFF021C25

@Preview(name = "Login", showBackground = true, backgroundColor = TP_BACKGROUND, widthDp = 360, heightDp = 720)
@Composable
fun LoginPreview() {
    TPCallTheme {
        LoginFormContent(
            extension = "101",
            password = "12345678",
            passwordVisible = false,
            busy = false,
            error = null,
            onExtensionChange = {},
            onPasswordChange = {},
            onTogglePasswordVisibility = {},
            onSubmit = {},
            onSwitchToRegister = {},
        )
    }
}

@Preview(name = "Register", showBackground = true, backgroundColor = TP_BACKGROUND, widthDp = 360, heightDp = 720)
@Composable
fun RegisterPreview() {
    TPCallTheme {
        RegisterFormContent(
            name = "محمد تفکری",
            extension = "203",
            password = "12345678",
            passwordVisible = false,
            busy = false,
            error = null,
            onNameChange = {},
            onExtensionChange = {},
            onPasswordChange = {},
            onTogglePasswordVisibility = {},
            onSubmit = {},
            onSwitchToLogin = {},
        )
    }
}

@Preview(name = "Incoming call", showBackground = true, backgroundColor = TP_BACKGROUND, widthDp = 360, heightDp = 720)
@Composable
fun IncomingCallPreview() {
    TPCallTheme {
        IncomingCallContent(
            state = CallState.IncomingRinging(
                callId = "preview",
                extension = "101",
                name = "محمد تفکری",
            ),
            onAccept = {},
            onReject = {},
        )
    }
}

@Preview(name = "Outgoing call", showBackground = true, backgroundColor = TP_BACKGROUND, widthDp = 360, heightDp = 720)
@Composable
fun OutgoingCallPreview() {
    TPCallTheme {
        OutgoingCallContent(
            state = CallState.OutgoingRinging(
                callId = "preview",
                extension = "203",
                name = "حسابداری",
            ),
            onHangup = {},
        )
    }
}

@Preview(name = "Connecting", showBackground = true, backgroundColor = TP_BACKGROUND, widthDp = 360, heightDp = 720)
@Composable
fun ConnectingCallPreview() {
    TPCallTheme {
        ConnectingCallContent(
            state = CallState.Connecting(
                callId = "preview",
                peerExtension = "203",
                peerName = "حسابداری",
            ),
            onHangup = {},
        )
    }
}

@Preview(name = "Active call", showBackground = true, backgroundColor = TP_BACKGROUND, widthDp = 360, heightDp = 720)
@Composable
fun InCallPreview() {
    TPCallTheme {
        InCallContent(
            state = CallState.InCall(
                callId = "preview",
                peerExtension = "203",
                peerName = "حسابداری",
                startedAt = System.currentTimeMillis() - 38_000,
            ),
            muted = false,
            speakerOn = false,
            onMute = {},
            onSpeaker = {},
            onHangup = {},
            elapsedSeconds = { 38 },
        )
    }
}

@Preview(name = "Corporate contacts", showBackground = true, backgroundColor = TP_BACKGROUND, widthDp = 360, heightDp = 720)
@Composable
fun CorporateContactsPreview() {
    TPCallTheme {
        ContactsContent(
            state = ContactsUiState(),
            contacts = listOf(
                CorporateContact(id = "101", name = "محمد تفکری", extension = "101", online = true),
                CorporateContact(id = "203", name = "حسابداری", extension = "203", online = true),
                CorporateContact(id = "305", name = "مدیر فروش", extension = "305", online = false),
            ),
            onQueryChange = {},
            onRefresh = {},
            isSelf = { c -> c.extension == "101" },
            onCall = {},
            onRowTap = {},
        )
    }
}
