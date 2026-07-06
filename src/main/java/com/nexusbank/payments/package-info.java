@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {
        "corebanking",
        "corebanking :: corebanking-model",
        "corebanking :: corebanking-exceptions",
        "fraud"
    }
)
package com.nexusbank.payments;
