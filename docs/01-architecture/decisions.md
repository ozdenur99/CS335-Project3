Outline per Decision:
Status: Accepted / Proposed / Deprecated


# Architecture Decision Records (ADR)

## ADR-001: In-Memory State Management
- **Status**: Accepted
- **Context**: We needed a low-latency way to track requests without external dependencies.
- **Decision**: Implement thread-safe local caching using `ConcurrentHashMap`for IP/Key tracking.
- **Reasoning**:
    - **Pros**: Microsecond latency, zero setup for graders/mentors, thread-safe.
    - **Cons**: Data loss on restart, not scalable across multiple instances (Stateful).
- **Trade-offs**: Stateful design; data is lost on application restart and not shared across horizontal nodes.
- **Alternatives Considered**: Redis was considered but rejected to keep the environment 'Zero-Config' for MVP phase.

