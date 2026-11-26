package ru.awaymode.app;

import org.junit.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NetworkRuleDecisionTest {
    private static final String WIFI = "Logistik";

    @Test public void trustedWifiDisconnectsAndMobileReconnects() {
        NetworkRuleDecision trusted = NetworkRuleDecision.evaluate(true, WIFI, "", setOf(WIFI), Collections.emptySet());
        NetworkRuleDecision mobile = NetworkRuleDecision.evaluate(false, null, WIFI, setOf(WIFI), Collections.emptySet());

        assertEquals(Boolean.FALSE, trusted.connect);
        assertEquals(Boolean.TRUE, mobile.connect);
        assertNotEquals(trusted.key, mobile.key);
    }

    @Test public void vpnRequiredWifiAndMobileBothConnectButAreDifferentStates() {
        NetworkRuleDecision wifi = NetworkRuleDecision.evaluate(true, WIFI, "", Collections.emptySet(), setOf(WIFI));
        NetworkRuleDecision mobile = NetworkRuleDecision.evaluate(false, null, WIFI, Collections.emptySet(), setOf(WIFI));

        assertEquals(Boolean.TRUE, wifi.connect);
        assertEquals(Boolean.TRUE, mobile.connect);
        assertNotEquals(wifi.key, mobile.key);
    }

    @Test public void leavingTrustedWifiForUnruledWifiConnects() {
        NetworkRuleDecision decision = NetworkRuleDecision.evaluate(true, "Cafe", WIFI, setOf(WIFI), Collections.emptySet());

        assertEquals(Boolean.TRUE, decision.connect);
        assertTrue(decision.reason.contains("Покинули доверенную сеть"));
    }

    @Test public void unruledWifiDoesNotChangeVpnUnlessLeavingTrustedWifi() {
        NetworkRuleDecision decision = NetworkRuleDecision.evaluate(true, "Cafe", "Office", Collections.emptySet(), Collections.emptySet());

        assertNull(decision.connect);
        assertTrue(decision.reason.contains("VPN не меняем"));
    }

    @Test public void changingRuleOnSameWifiChangesDecision() {
        NetworkRuleDecision enable = NetworkRuleDecision.evaluate(true, WIFI, WIFI, Collections.emptySet(), setOf(WIFI));
        NetworkRuleDecision disable = NetworkRuleDecision.evaluate(true, WIFI, WIFI, setOf(WIFI), Collections.emptySet());

        assertEquals(Boolean.TRUE, enable.connect);
        assertEquals(Boolean.FALSE, disable.connect);
        assertNotEquals(enable.key, disable.key);
    }

    @Test public void repeatedSameNetworkAndRuleHaveSameDecisionKey() {
        NetworkRuleDecision first = NetworkRuleDecision.evaluate(true, WIFI, "", Collections.emptySet(), setOf(WIFI));
        NetworkRuleDecision next = NetworkRuleDecision.evaluate(true, WIFI, WIFI, Collections.emptySet(), setOf(WIFI));

        assertEquals(first.key, next.key);
    }

    private static Set<String> setOf(String value) {
        return Collections.singleton(value);
    }
}
