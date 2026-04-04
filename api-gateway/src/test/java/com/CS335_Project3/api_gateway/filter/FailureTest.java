package com.CS335_Project3.api_gateway.filter;

import com.CS335_Project3.api_gateway.config.AbuseDetectionConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FailureTest {

    private Failure f;
    private AbuseDetectionConfig config;

    @BeforeEach
    void setUp() {
        config = new AbuseDetectionConfig();
        config.getFailure().setMaxFailuresPerWindow(3);
        config.getFailure().setWindowSeconds(60);
        f = new Failure(config);
    }

    @Test
    @DisplayName("Should not flag client below threshold")
    void belowThreshold_notFlagged() {
        f.recordAndCheck("client-A");
        f.recordAndCheck("client-A");
        assertThat(f.recordAndCheck("client-A")).isFalse();
    }

    @Test
    @DisplayName("Should flag client when threshold exceeded")
    void exceedsThreshold_flagged() {
        f.recordAndCheck("client-A");
        f.recordAndCheck("client-A");
        f.recordAndCheck("client-A");
        assertThat(f.recordAndCheck("client-A")).isTrue();
    }

    @Test
    @DisplayName("getFailureCount returns correct count")
    void getFailureCount_correct() {
        f.recordAndCheck("client-A");
        f.recordAndCheck("client-A");
        assertThat(f.getFailureCount("client-A")).isEqualTo(2);
    }

    @Test
    @DisplayName("getFailureCount returns 0 for unknown client")
    void getFailureCount_unknownClient() {
        assertThat(f.getFailureCount("unknown")).isEqualTo(0);
    }

    @Test
    @DisplayName("Different clients tracked independently")
    void differentClients_independent() {
        f.recordAndCheck("client-A");
        f.recordAndCheck("client-A");
        f.recordAndCheck("client-A");
        assertThat(f.recordAndCheck("client-B")).isFalse();
    }

    @Test
    @DisplayName("Counter resets after window expires")
    void windowExpiry_resetsCounter() throws InterruptedException {
        config.getFailure().setWindowSeconds(1);
        f = new Failure(config);
        f.recordAndCheck("client-A");
        f.recordAndCheck("client-A");
        f.recordAndCheck("client-A");
        Thread.sleep(1100);
        assertThat(f.recordAndCheck("client-A")).isFalse();
    }

    @Test
    @DisplayName("reset() clears all state")
    void reset_clearsState() {
        f.recordAndCheck("client-A");
        f.reset();
        assertThat(f.getFailureCount("client-A")).isEqualTo(0);
    }
}