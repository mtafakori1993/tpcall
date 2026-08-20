package ir.tafakoritech.tpcall.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.tafakoritech.tpcall.R
import ir.tafakoritech.tpcall.call.CallController
import ir.tafakoritech.tpcall.data.api.AuthGateway
import ir.tafakoritech.tpcall.data.contacts.CorporateContact
import ir.tafakoritech.tpcall.ui.components.TpAvatar
import ir.tafakoritech.tpcall.ui.components.TpPrimaryButton
import ir.tafakoritech.tpcall.ui.components.TpStatusPill
import ir.tafakoritech.tpcall.ui.components.TpTextButton
import ir.tafakoritech.tpcall.ui.components.TpTextField
import ir.tafakoritech.tpcall.ui.rememberMicrophonePermissionRequester
import ir.tafakoritech.tpcall.ui.theme.TpColors
import ir.tafakoritech.tpcall.ui.theme.TpDimens
import ir.tafakoritech.tpcall.ui.theme.TpRadius

@Composable
fun ContactsScreen(
    controller: CallController,
    authApi: AuthGateway,
    token: String,
) {
    val viewModel: ContactsViewModel = viewModel(
        key = "contacts_$token",
        factory = ContactsViewModel.factory(controller, authApi, token),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val micRequester = rememberMicrophonePermissionRequester()

    var selectedContact by remember { mutableStateOf<CorporateContact?>(null) }

    ContactsContent(
        state = state,
        contacts = viewModel.filteredContacts(),
        onQueryChange = viewModel::onQueryChange,
        onRefresh = viewModel::refresh,
        isSelf = viewModel::isSelf,
        onCall = { ext -> micRequester { viewModel.callContact(ext) } },
        onRowTap = { selectedContact = it },
    )

    selectedContact?.let { contact ->
        ContactDetailSheet(
            contact = contact,
            isSelf = viewModel.isSelf(contact),
            onCall = {
                selectedContact = null
                micRequester { viewModel.callContact(contact.extension) }
            },
            onDismiss = { selectedContact = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsContent(
    state: ContactsUiState,
    contacts: List<CorporateContact>,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    isSelf: (CorporateContact) -> Boolean,
    onCall: (String) -> Unit,
    onRowTap: (CorporateContact) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TpColors.BackgroundPrimary),
    ) {
        Text(
            text = "مخاطبین شرکت",
            style = MaterialTheme.typography.titleLarge,
            color = TpColors.TextPrimary,
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = TpDimens.ScreenPadding)
                .padding(top = TpDimens.SpaceXxs, bottom = TpDimens.SpaceSm),
        )

        TpTextField(
            value = state.query,
            onValueChange = onQueryChange,
            label = "جستجوی نام یا داخلی",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TpDimens.ScreenPadding),
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_backspace),
                            contentDescription = "پاک کردن جستجو",
                            tint = TpColors.TextSecondary,
                        )
                    }
                }
            },
        )
        Spacer(Modifier.height(TpDimens.SpaceSm))

        when {
            state.loading && state.contacts.isEmpty() -> ContactsLoading()
            else -> PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.contacts.isEmpty() && state.error != null -> ContactsError(onRetry = onRefresh)
                    state.contacts.isEmpty() -> ContactsEmpty()
                    contacts.isEmpty() -> NoSearchResults(query = state.query)
                    else -> ContactsList(
                        contacts = contacts,
                        isSelf = isSelf,
                        onCall = onCall,
                        onRowTap = onRowTap,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactsList(
    contacts: List<CorporateContact>,
    isSelf: (CorporateContact) -> Boolean,
    onCall: (String) -> Unit,
    onRowTap: (CorporateContact) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = TpDimens.ScreenPadding,
            vertical = TpDimens.SpaceXs,
        ),
        verticalArrangement = Arrangement.spacedBy(TpDimens.SpaceXs),
    ) {
        items(items = contacts, key = { it.id }) { contact ->
            ContactRow(
                contact = contact,
                isSelf = isSelf(contact),
                onCall = { onCall(contact.extension) },
                onRowTap = { onRowTap(contact) },
            )
        }
    }
}

@Composable
private fun ContactRow(
    contact: CorporateContact,
    isSelf: Boolean,
    onCall: () -> Unit,
    onRowTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TpRadius.Medium))
            .background(TpColors.SurfacePrimary)
            .clickable(onClick = onRowTap)
            .padding(horizontal = TpDimens.SpaceMd, vertical = TpDimens.SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TpAvatar(name = contact.name, size = 40.dp)
        Spacer(Modifier.width(TpDimens.SpaceSm))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TpColors.TextPrimary,
                    maxLines = 1,
                )
                if (isSelf) {
                    Spacer(Modifier.width(TpDimens.SpaceXs))
                    Text(
                        text = "شما",
                        style = MaterialTheme.typography.labelSmall,
                        color = TpColors.AccentBright,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = "داخلی ${contact.extension}",
                style = MaterialTheme.typography.bodyMedium,
                color = TpColors.TextSecondary,
            )
            Spacer(Modifier.height(2.dp))
            TpStatusPill(
                text = if (contact.online) "آنلاین" else "آفلاین",
                dotColor = if (contact.online) TpColors.Online else TpColors.Offline,
            )
        }

        IconButton(
            onClick = onCall,
            enabled = !isSelf,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_call),
                contentDescription = "تماس با ${contact.name}",
                tint = if (isSelf) TpColors.DisabledText else TpColors.AccentPrimary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactDetailSheet(
    contact: CorporateContact,
    isSelf: Boolean,
    onCall: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = TpColors.BackgroundSecondary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = TpDimens.SpaceXl)
                .padding(bottom = TpDimens.SpaceXl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TpAvatar(name = contact.name, size = 72.dp)
            Spacer(Modifier.height(TpDimens.SpaceMd))
            Text(
                text = contact.name,
                style = MaterialTheme.typography.titleLarge,
                color = TpColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(TpDimens.SpaceXxs))
            Text(
                text = "داخلی ${contact.extension}",
                style = MaterialTheme.typography.bodyLarge,
                color = TpColors.TextSecondary,
            )
            Spacer(Modifier.height(TpDimens.SpaceSm))
            TpStatusPill(
                text = if (contact.online) "آنلاین" else "آفلاین",
                dotColor = if (contact.online) TpColors.Online else TpColors.Offline,
            )
            Spacer(Modifier.height(TpDimens.SpaceLg))
            TpPrimaryButton(
                text = "تماس",
                icon = painterResource(R.drawable.ic_call),
                onClick = onCall,
                enabled = !isSelf,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ContactsLoading() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = TpDimens.ScreenPadding),
    ) {
        repeat(5) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = TpDimens.SpaceSm)
                    .height(64.dp)
                    .clip(RoundedCornerShape(TpRadius.Medium))
                    .background(TpColors.SurfacePrimary),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = TpDimens.SpaceMd)
                        .size(40.dp)
                        .clip(RoundedCornerShape(TpRadius.Small))
                        .background(TpColors.DisabledSurface),
                )
                Column {
                    Box(
                        modifier = Modifier
                            .size(width = 120.dp, height = 14.dp)
                            .clip(RoundedCornerShape(TpRadius.Small))
                            .background(TpColors.DisabledSurface),
                    )
                    Spacer(Modifier.height(TpDimens.SpaceXs))
                    Box(
                        modifier = Modifier
                            .size(width = 80.dp, height = 12.dp)
                            .clip(RoundedCornerShape(TpRadius.Small))
                            .background(TpColors.DisabledSurface),
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactsEmpty() {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Column(
                modifier = Modifier
                    .fillParentMaxSize()
                    .padding(horizontal = TpDimens.SpaceXl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_person),
                    contentDescription = null,
                    tint = TpColors.TextTertiary,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.height(TpDimens.SpaceMd))
                Text(
                    text = "مخاطبی ثبت نشده است",
                    style = MaterialTheme.typography.titleMedium,
                    color = TpColors.TextSecondary,
                )
                Spacer(Modifier.height(TpDimens.SpaceXs))
                Text(
                    text = "هنوز کارمندی در دایرکتوری داخلی ثبت نشده است.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TpColors.TextTertiary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun NoSearchResults(query: String) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Column(
                modifier = Modifier
                    .fillParentMaxSize()
                    .padding(horizontal = TpDimens.SpaceXl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "نتیجه‌ای برای «$query» یافت نشد",
                    style = MaterialTheme.typography.titleMedium,
                    color = TpColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ContactsError(onRetry: () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Column(
                modifier = Modifier
                    .fillParentMaxSize()
                    .padding(horizontal = TpDimens.SpaceXl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "دریافت مخاطبین ناموفق بود",
                    style = MaterialTheme.typography.titleMedium,
                    color = TpColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(TpDimens.SpaceSm))
                TpTextButton(text = "تلاش دوباره", onClick = onRetry)
            }
        }
    }
}
