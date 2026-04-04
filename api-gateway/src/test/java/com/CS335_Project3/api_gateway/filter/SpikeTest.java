package com.CS335_Project3.api_gateway.filter;

import com.CS335_Project3.api_gateway.config.AbuseDetectionConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpikeTest {

    private Spike s;
    private AbuseDetectionConfig config;

    @BeforeEach
    void setUp() {
        config = new AbuseDetectionConfig();
        config.getSpike().setMaxRequestsPerWindow(5);
        config.getSpike().setWindowSeconds(10);
        s = new Spike(config);
    }

    @Test
    @DisplayName("Should not flag client below threshold")
    void belowThreshold_notFlagged() {
        for (int i = 0; i < 5; i++) {
            assertThat(s.recordAndCheck("client-A")).isFalse();
        }
    }

    @Test
    @DisplayName("Should flag client when threshold exceeded")
    void exceedsThreshold_flagged() {
        for (int i = 0; i < 5; i++) s.recordAndCheck("client-A");
        assertThat(s.recordAndCheck("client-A")).isTrue();
    }

    @Test
    @DisplayName("Different clients tracked independently")
    void differentClients_independent() {
        for (int i = 0; i < 5; i++) s.recordAndCheck("client-A");
        assertThat(s.recordAndCheck("client-B")).isFalse();
    }

    @Test
    @DisplayName("Counter resets after window expires")
    void windowExpiry_resetsCounter() throws InterruptedException {
        config.getSpike().setWindowSeconds(1);
        s = new Spike(config);
        for (int i = 0; i < 5; i++) s.recordAndCheck("client-A");
        Thread.sleep(1100);
        assertThat(s.recordAndCheck("client-A")).isFalse();
    }

    @Test
    @DisplayName("getRequestCount returns correct count")
    void getRequestCount_correct() {
        s.recordAndCheck("client-A");
        s.recordAndCheck("client-A");
        assertThat(s.getRequestCount("client-A")).isEqualTo(2);
    }

    @Test
    @DisplayName("reset() clears all state")
    void reset_clearsState() {
        for (int i = 0; i < 5; i++) s.recordAndCheck("client-A");
        s.reset();
        assertThat(s.recordAndCheck("client-A")).isFalse();
    }
}