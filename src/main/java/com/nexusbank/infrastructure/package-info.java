@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {
        "identity :: identity-web",
        "identity :: identity-exceptions",
        "corebanking :: corebanking-exceptions",
        "corebanking :: corebanking-outbox",
        "payments :: payments-exceptions",
        "payments :: payments-ports",
        "notifications :: notifications-exceptions"
    }
)
package com.nexusbank.infrastructure;
