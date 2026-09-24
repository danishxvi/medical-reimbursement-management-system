/**
 * Shared kernel: types every module may use (roles, the authenticated
 * principal, error handling, security configuration and small utilities).
 * It must never depend on a business module.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Shared kernel",
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.mrms.shared;
