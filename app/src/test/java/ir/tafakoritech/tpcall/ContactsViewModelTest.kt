package ir.tafakoritech.tpcall

import ir.tafakoritech.tpcall.call.CallController
import ir.tafakoritech.tpcall.data.contacts.CorporateContact
import ir.tafakoritech.tpcall.data.model.UserDto
import ir.tafakoritech.tpcall.ui.contacts.ContactsViewModel
import ir.tafakoritech.tpcall.util.PersianSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContactsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var lastVm: ContactsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private data class Harness(
        val vm: ContactsViewModel,
        val controller: CallController,
        val auth: FakeAuthGateway,
        val signaling: FakeSignalingGateway,
    )

    private fun TestScope.build(
        directory: List<UserDto> = listOf(
            UserDto(name = "محمد تفکری", extension = "101", online = true),
            UserDto(name = "حسابداری", extension = "203", online = true),
            UserDto(name = "مدیر فروش", extension = "305", online = false),
        ),
    ): Harness {
        val auth = FakeAuthGateway()
        auth.meResult = Result.success(UserDto(name = "محمد تفکری", extension = "101"))
        auth.directoryResult = Result.success(directory)
        val sessionStore = FakeSessionStorage()
        sessionStore.save("token", UserDto(name = "محمد تفکری", extension = "101"))
        val fakeSignaling = FakeSignalingGateway()
        val controller = CallController(
            scope = backgroundScope,
            sessionStore = sessionStore,
            authApi = auth,
            signalingFactory = { fakeSignaling },
            rtc = FakeRtcGateway(),
            audio = FakeCallAudio(),
            notifier = FakeCallNotifier(),
            telecom = FakeTelecomGateway(),
        )
        controller.initialize()
        runCurrent()
        val vm = ContactsViewModel(controller, auth, "token")
        lastVm = vm
        runCurrent()
        return Harness(vm, controller, auth, fakeSignaling)
    }

    @Test
    fun `contacts loaded from backend`() = runTest {
        val harness = build()
        assertEquals(3, harness.vm.uiState.value.contacts.size)
        assertFalse(harness.vm.uiState.value.loading)
        assertNull(harness.vm.uiState.value.error)
    }

    @Test
    fun `extension shown correctly on the contact`() = runTest {
        build()
        val contact = lastVm.filteredContacts().first { it.name == "حسابداری" }
        assertEquals("203", contact.extension)
        assertEquals("203", contact.id)
    }

    @Test
    fun `contacts sorted by extension ascending`() = runTest {
        build(
            directory = listOf(
                UserDto(name = "ز", extension = "305", online = true),
                UserDto(name = "الف", extension = "203", online = false),
                UserDto(name = "ب", extension = "101", online = true),
            ),
        )
        assertEquals(listOf("101", "203", "305"), lastVm.uiState.value.contacts.map { it.extension })
    }

    @Test
    fun `search by name`() = runTest {
        val harness = build()
        harness.vm.onQueryChange("حساب")
        runCurrent()
        assertEquals(listOf("حسابداری"), harness.vm.filteredContacts().map { it.name })
    }

    @Test
    fun `search by extension`() = runTest {
        val harness = build()
        harness.vm.onQueryChange("20")
        runCurrent()
        assertEquals(listOf("حسابداری"), harness.vm.filteredContacts().map { it.name })
    }

    @Test
    fun `search by exact extension digit`() = runTest {
        val harness = build()
        harness.vm.onQueryChange("305")
        runCurrent()
        assertEquals(listOf("305"), harness.vm.filteredContacts().map { it.extension })
    }

    @Test
    fun `empty query returns everything`() = runTest {
        val harness = build()
        harness.vm.onQueryChange("حساب")
        runCurrent()
        assertEquals(1, harness.vm.filteredContacts().size)
        harness.vm.onQueryChange("")
        runCurrent()
        assertEquals(3, harness.vm.filteredContacts().size)
    }

    @Test
    fun `persian yeh normalization matches arabic yeh input`() = runTest {
        build(directory = listOf(UserDto(name = "کیمیایی", extension = "201", online = true)))
        lastVm.onQueryChange("کیمیایی")
        runCurrent()
        assertEquals(1, lastVm.filteredContacts().size)
        // Arabic yeh (ي) should match Persian yeh (ی).
        lastVm.onQueryChange("كيميائي")
        runCurrent()
        assertEquals(1, lastVm.filteredContacts().size)
    }

    @Test
    fun `persian kaf normalization matches arabic kaf input`() = runTest {
        build(directory = listOf(UserDto(name = "کیمیایی", extension = "201", online = true)))
        lastVm.onQueryChange("كیمیایی")
        runCurrent()
        assertEquals(1, lastVm.filteredContacts().size)
    }

    @Test
    fun `search trims whitespace`() = runTest {
        val harness = build()
        harness.vm.onQueryChange("  حساب  ")
        runCurrent()
        assertEquals(listOf("حسابداری"), harness.vm.filteredContacts().map { it.name })
    }

    @Test
    fun `self call is marked`() = runTest {
        val harness = build()
        val self = harness.vm.filteredContacts().first { it.extension == "101" }
        assertTrue(harness.vm.isSelf(self))
        val other = harness.vm.filteredContacts().first { it.extension == "203" }
        assertFalse(harness.vm.isSelf(other))
    }

    @Test
    fun `call button routes through controller startCall`() = runTest {
        val harness = build()
        harness.controller.startSignaling()
        runCurrent()
        harness.signaling.setConnected()
        runCurrent()

        harness.vm.callContact("203")
        runCurrent()

        assertEquals("call", harness.signaling.sent.last().type)
        val payload = harness.signaling.sent.last().payload
        assertEquals("203", payload["to"]!!.toString().trim('"'))
    }

    @Test
    fun `error state surfaces friendly message`() = runTest {
        val harness = build(
            directory = listOf(UserDto(name = "محمد", extension = "101", online = true)),
        )
        harness.auth.directoryResult = Result.failure(RuntimeException("boom"))
        harness.vm.refresh()
        runCurrent()
        assertTrue(harness.vm.uiState.value.error == "دریافت مخاطبین ناموفق بود")
    }

    @Test
    fun `empty state when directory has no users`() = runTest {
        build(directory = emptyList())
        assertTrue(lastVm.uiState.value.contacts.isEmpty())
        assertEquals(0, lastVm.filteredContacts().size)
    }

    @Test
    fun `contacts with blank extension are skipped`() = runTest {
        build(
            directory = listOf(
                UserDto(name = "بدون داخلی", extension = "", online = true),
                UserDto(name = "عادی", extension = "410", online = false),
            ),
        )
        assertEquals(listOf("عادی"), lastVm.uiState.value.contacts.map { it.name })
    }
}

class PersianSearchTest {

    @Test
    fun `normalize trims and lowercases`() {
        assertEquals("حساب", PersianSearch.normalize("  حساب  "))
        assertEquals("abc", PersianSearch.normalize("ABC"))
    }

    @Test
    fun `normalize arabic yeh to persian`() {
        assertEquals("کیمیایی", PersianSearch.normalize("كيميائي"))
    }

    @Test
    fun `normalize arabic kaf to persian`() {
        assertEquals("کتاب", PersianSearch.normalize("كتاب"))
    }

    @Test
    fun `normalize persian digits to ascii`() {
        assertEquals("203", PersianSearch.normalize("۲۰۳"))
        assertEquals("203", PersianSearch.normalize("٢٠٣"))
    }

    @Test
    fun `matches by name`() {
        assertTrue(PersianSearch.matches("حسابداری", "203", "حساب"))
        assertTrue(PersianSearch.matches("حسابداری", "203", "حسابداري"))
        assertFalse(PersianSearch.matches("حسابداری", "203", "فروش"))
    }

    @Test
    fun `matches by extension`() {
        assertTrue(PersianSearch.matches("حسابداری", "203", "20"))
        assertTrue(PersianSearch.matches("حسابداری", "203", "۲۰"))
        assertTrue(PersianSearch.matches("حسابداری", "203", "203"))
        assertFalse(PersianSearch.matches("حسابداری", "203", "30"))
    }

    @Test
    fun `empty query matches everything`() {
        assertTrue(PersianSearch.matches("حسابداری", "203", ""))
        assertTrue(PersianSearch.matches("حسابداری", "203", "   "))
    }

    @Test
    fun `sort by extension ascending`() {
        val contacts = listOf(
            CorporateContact("305", "ز", "305", false),
            CorporateContact("101", "الف", "101", true),
            CorporateContact("203", "ب", "203", true),
        )
        assertEquals(listOf("101", "203", "305"), PersianSearch.sortByExtension(contacts).map { it.extension })
    }
}
