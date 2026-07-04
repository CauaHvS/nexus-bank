@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {
        "identity :: identity-web",
        "identity :: identity-exceptions",
        "corebanking :: corebanking-exceptions",
        "payments :: payments-exceptions",
        "payments :: payments-ports"
    }
)
package com.nexusbank.infrastructure;
