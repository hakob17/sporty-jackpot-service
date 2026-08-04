---
description: Run the full delivery gate — tests, smoke test, review, docs — and report what is left
allowed-tools: Bash(mvn *), Bash(docker compose *), Bash(./scripts/smoke-test.sh*), Bash(curl http://localhost:8080/*), Read, Grep, Glob, Agent
---

Take the current state of the working tree through the delivery gate for this service, then report
honestly what passed and what did not. $ARGUMENTS

1. **Tests** — run `mvn test`. The Docker image builds with `-DskipTests`, so this is the only
   automated gate. Do not continue past a failure without saying so.

2. **Runtime** — start the service in whichever mode covers the change (the `verify-service` skill
   has the decision: `no-kafka` profile for anything that does not touch messaging, the Docker stack
   when it does) and run `./scripts/smoke-test.sh`. Then exercise anything new with curl
   specifically — the smoke test only knows the flows that existed when it was written.

3. **Review** — hand the change to the `jackpot-reviewer` agent and act on anything it finds that is
   a real defect. Report the rest.

4. **Docs** — check that `README.md` (API table, configuration tables, seeded jackpots) and
   `CLAUDE.md` (architecture map, conventions, gotchas) still describe the code. Stale docs here are
   a defect, since they are what the next session reads first.

Finish with a short verdict: what is verified, what is assumed, and what a reviewer should look at
first. If something is broken or unfinished, say that plainly instead of rounding it up to done.
