package com.CS335_Project3.api_gateway.filter;

import com.CS335_Project3.api_gateway.config.AbuseDetectionConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BlockedIpsTest {

    private BlockedIps blocklist;
    private AbuseDetectionConfig config;

    @BeforeEach
    void setUp() {
        config = new AbuseDetectionConfig();
        config.setBlockDurationSeconds(300); // 5 minutes default
        blocklist = new BlockedIps(config);
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
    @DisplayName("Manually unblocked IP should no longer be blocked")
    void unblock_removesIp() {
        blocklist.block("10.0.0.1");
        blocklist.unblock("10.0.0.1");
        assertThat(blocklist.isBlocked("10.0.0.1")).isFalse();
    }

    @Test
    @DisplayName("getBlockedIps returns all currently blocked IPs")
    void getBlockedIps_returnsAll() {
        blocklist.block("10.0.0.1");
        blocklist.block("10.0.0.2");
        assertThat(blocklist.getBlockedIps())
                .containsExactlyInAnyOrder("10.0.0.1", "10.0.0.2");
    }

    @Test
    @DisplayName("Block expires automatically after cooldown period")
    void block_expiresAfterCooldown() throws InterruptedException {
        config.setBlockDurationSeconds(1); // 1 second for fast test
        blocklist = new BlockedIps(config);

        blocklist.block("10.0.0.1");
        assertThat(blocklist.isBlocked("10.0.0.1")).isTrue();

        Thread.sleep(1100); // wait for cooldown to expire

        // Should be automatically unblocked now
        assertThat(blocklist.isBlocked("10.0.0.1")).isFalse();
    }

    @Test
    @DisplayName("Expired blocks do not appear in getBlockedIps")
    void expiredBlocks_notInSnapshot() throws InterruptedException {
        config.setBlockDurationSeconds(1);
        blocklist = new BlockedIps(config);

        blocklist.block("10.0.0.1");
        Thread.sleep(1100);

        assertThat(blocklist.getBlockedIps()).isEmpty();
    }

    @Test
    @DisplayName("reset() clears all blocked IPs")
    void reset_clearsAll() {
        blocklist.block("10.0.0.1");
        blocklist.reset();
        assertThat(blocklist.getBlockedIps()).isEmpty();
    }
}