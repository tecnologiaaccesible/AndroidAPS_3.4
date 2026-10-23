package app.aaps.pump.ypsopump.ble

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YpsoGattDiscoveryGateTest {
    @Test fun duplicateSuccessfulDiscoveryIsIgnored() {
        val gate = YpsoGattDiscoveryGate()
        assertTrue(gate.claimSuccessfulDiscovery())
        assertFalse(gate.claimSuccessfulDiscovery())
        assertFalse(gate.claimSuccessfulDiscovery())
    }

    @Test fun onlyAnExplicitLinkResetAllowsNewDiscovery() {
        val gate = YpsoGattDiscoveryGate()
        assertTrue(gate.claimSuccessfulDiscovery())
        assertFalse(gate.claimSuccessfulDiscovery())
        gate.resetForNewLink()
        assertTrue(gate.claimSuccessfulDiscovery())
        assertFalse(gate.claimSuccessfulDiscovery())
    }
}
