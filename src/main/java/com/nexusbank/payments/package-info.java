@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {
        "corebanking",
        "corebanking :: corebanking-model",
        "corebanking :: corebanking-exceptions"
    }
)
package com.nexusbank.payments;
