package com.CS335_Project3.api_gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BlockedIpsTest {

    private BlockedIps blocklist;

    @BeforeEach
    void setUp() {
        blocklist = new BlockedIps();
    }

    @Test
    @DisplayName("Blocked IP should be blocked")
    void block_ipIsBlocked() {
        blocklist.block("10.0.0.1");
        assertThat(blocklist.isBlocked("10.0.0.1")).isTrue();
    }

    @Test
    @DisplayName("Unknown IP should not be blocked")
    void unknownIp_notBlocked() {
        assertThat(blocklist.isBlocked("10.0.0.99")).isFalse();
    }

    @Test
    @DisplayName("Unblocked IP should no longer be blocked")
    void unblock_removesIp() {
        blocklist.block("10.0.0.1");
        blocklist.unblock("10.0.0.1");
        assertThat(blocklist.isBlocked("10.0.0.1")).isFalse();
    }

    @Test
    @DisplayName("getBlockedIps returns all blocked IPs")
    void getBlockedIps_returnsAll() {
        blocklist.block("10.0.0.1");
        blocklist.block("10.0.0.2");
        assertThat(blocklist.getBlockedIps())
                .containsExactlyInAnyOrder("10.0.0.1", "10.0.0.2");
    }

    @Test
    @DisplayName("getBlockedIps returns immutable snapshot")
    void getBlockedIps_isImmutable() {
        blocklist.block("10.0.0.1");
        assertThat(blocklist.getBlockedIps()).isUnmodifiable();
    }

    @Test
    @DisplayName("reset() clears all blocked IPs")
    void reset_clearsAll() {
        blocklist.block("10.0.0.1");
        blocklist.reset();
        assertThat(blocklist.getBlockedIps()).isEmpty();
    }
}