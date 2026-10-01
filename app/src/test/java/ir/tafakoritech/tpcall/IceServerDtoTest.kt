package ir.tafakoritech.tpcall

import ir.tafakoritech.tpcall.data.api.ApiClient
import ir.tafakoritech.tpcall.data.model.IceConfigDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IceServerDtoTest {
    @Test
    fun parsesStunAndTurnWithCredentials() {
        val body = """
            {"iceServers":[
              {"urls":["stun:stun.l.google.com:19302"]},
              {"urls":["turn:tpcallapi.tafakorpardazan.ir:3478?transport=udp"],
               "username":"1700000000:101","credential":"abc="}
            ]}
        """.trimIndent()
        val cfg = ApiClient.json.decodeFromString(IceConfigDto.serializer(), body)

        assertEquals(2, cfg.iceServers.size)
        assertNull(cfg.iceServers[0].username)
        assertNull(cfg.iceServers[0].credential)
        assertEquals("1700000000:101", cfg.iceServers[1].username)
        assertEquals("abc=", cfg.iceServers[1].credential)
    }
}
