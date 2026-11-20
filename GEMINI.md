# Project Rules & Guidelines

## Build Execution & Background Tasks
- **Never poll Gradle builds:** When launching long-running Gradle commands (`assembleRelease`, `compileDebugKotlin`, etc.), execute them asynchronously or as background tasks.
- **Zero active polling:** Do NOT schedule recurring timers, status checks, or polling loops (`manage_task status`) to inspect running builds.
- **Rely on reactive notifications:** After launching a long-running command, simply inform the user with a brief message and end the turn. Wait for the reactive system completion notification before resuming work.
