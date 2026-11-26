package ru.awaymode.app;

import java.util.Collections;
import java.util.Set;

final class NetworkRuleDecision {
    final String key;
    final String reason;
    final Boolean connect;

    private NetworkRuleDecision(String key, String reason, Boolean connect) {
        this.key = key;
        this.reason = reason;
        this.connect = connect;
    }

    static NetworkRuleDecision evaluate(boolean wifiConnected, String currentSsid, String previousSsid,
                                        Set<String> trusted, Set<String> vpnRequired) {
        Set<String> white = trusted == null ? Collections.emptySet() : trusted;
        Set<String> black = vpnRequired == null ? Collections.emptySet() : vpnRequired;
        String previous = previousSsid == null ? "" : previousSsid;

        if (!wifiConnected) {
            String reason = previous.isEmpty()
                    ? "Wi-Fi отключён; по правилу мобильной сети включаем INCY"
                    : "Потеряли Wi-Fi сеть «" + previous + "»; на мобильной сети включаем INCY";
            return new NetworkRuleDecision("mobile|connect", reason, true);
        }
        if (currentSsid == null || currentSsid.isEmpty()) {
            throw new IllegalArgumentException("SSID is required while Wi-Fi is connected");
        }
        if (white.contains(currentSsid)) {
            return new NetworkRuleDecision("wifi|" + currentSsid + "|disconnect",
                    "Для сети «" + currentSsid + "» выбрано «Выключить VPN»", false);
        }
        if (black.contains(currentSsid)) {
            return new NetworkRuleDecision("wifi|" + currentSsid + "|connect",
                    "Для сети «" + currentSsid + "» выбрано «Включить VPN»", true);
        }
        if (!previous.isEmpty() && white.contains(previous)) {
            return new NetworkRuleDecision("wifi|" + currentSsid + "|outside-trusted|connect",
                    "Покинули доверенную сеть «" + previous + "»; включаем INCY", true);
        }
        return new NetworkRuleDecision("wifi|" + currentSsid + "|no-rule",
                "Сеть «" + currentSsid + "» без правила; VPN не меняем", null);
    }
}
