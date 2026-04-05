package com.CS335_Project3.api_gateway.filter;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe IP blocklist.
 * IPs are added automatically when Spike or Failure exceeds threshold inside AbuseFilter.
 */
@Component
public class BlockedIps {

    private final Set<String> blockedIps = ConcurrentHashMap.newKeySet();

    public void block(String ip)          { blockedIps.add(ip); }
    public void unblock(String ip)        { blockedIps.remove(ip); }
    public boolean isBlocked(String ip)   { return blockedIps.contains(ip); }
    public Set<String> getBlockedIps()    { return Set.copyOf(blockedIps); }
    public void reset()                   { blockedIps.clear(); }
}