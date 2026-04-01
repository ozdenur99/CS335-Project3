# Rate Limiting Specification (Sean)

## 1. Algorithm: Sliding Window Log
We utilized a Sliding Window Log to prevent the "Boundary Burst" issue common in Fixed Window implementations.

## 2. Logic Implementation
- **Data Structure**: `Map<String, List<Long>>` stores a series of timestamps per Client ID.
- **Window Cleanup**: On every request, timestamps older than `currentTime - 10s` are evicted.
- **Decision Engine**: If `timestamps.size() >= threshold`, return HTTP 429.

## 3. Complexity Analysis
- **Time**: Average O(1) for map lookups; O(N) for eviction where N is the request limit.
- **Space**: O(C * T) where C is active clients and T is the per-client limit.