@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {
        "identity :: identity-web",
        "identity :: identity-exceptions",
        "corebanking :: corebanking-exceptions"
    }
)
package com.nexusbank.infrastructure;
