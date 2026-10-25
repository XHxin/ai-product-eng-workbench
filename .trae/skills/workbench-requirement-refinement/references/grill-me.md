# Grill-Me Method

Use this to turn research evidence into a small number of high-value questions and to keep the dialogue recoverable across days.

## 1. Prepare

Before writing questions:

- Read the requirement, existing PRD versions, design docs, database schema, and relevant code.
- List facts, inferences, conflicts, and open gaps.
- Drop questions already answered by authoritative evidence.
- Group questions by business theme; keep unrelated themes in separate messages.

## 2. Question quality

Each question should contain:

- a stable ID such as `Q-001`
- the decision point
- known facts and source
- why the answer changes the requirement
- 2-3 realistic options when useful
- a recommendation only when evidence supports it
- impact on flow, data, permission, or acceptance
- whether it is blocking (`P0/P1`) or non-blocking (`P2/P3`)

Avoid yes/no questions when the real decision needs a rule. Avoid vague words such as "尽快", "正常", "适当", or "必要时".

## 3. Message template

Send one message per theme, with related questions together:

```text
主题：<业务主题>（第 N 轮）

背景：<1-2 句>
已知事实：
- [FACT] ...
- [OPEN] ...

请确认：
1. <问题>
   A. <选项>
   B. <选项>
   C. <其他/补充>
   推荐：<选项及理由>；影响：<影响>
2. <问题>
   ...

回复格式：1-A，2-B，3-补充说明。
如选项不符合业务，请直接补充规则。
```

Keep the whole message readable on a phone. Prefer 5-8 questions. Put P0/P1 questions first.

## 4. Rounds

- Round 1: scope, roles, main flow, state, data ownership, acceptance.
- Round 2: branch and exception flows, permissions, concurrency, retry, audit.
- Round 3: only remaining conflicts and edge cases.
- Stop when all blocking questions are answered and acceptance criteria are testable.

If an answer creates a new dependency, ask a focused follow-up in the next round. Do not re-send answered questions.

## 5. Reply parsing

Classify each reply:

- answered
- partially answered
- ambiguous
- conflicts with earlier decision
- new rule
- deferred

Only close a question when the decision is explicit. If the PM chooses "其他", record the exact wording as a new decision.

## 6. Closure checklist

For the refined requirement, verify:

- business goal and non-goals
- actors and permissions
- trigger and preconditions
- normal flow
- branch and exception flows
- state machine with legal/illegal transitions
- data creation, update, deletion, and audit
- concurrency, idempotency, retry
- external dependencies and async failures
- observable Given-When-Then acceptance criteria
- dependencies and conflicts with other requirements

Unresolved items must remain `[OPEN]` in the specification.
