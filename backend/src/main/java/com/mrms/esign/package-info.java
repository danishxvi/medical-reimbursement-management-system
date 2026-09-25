/**
 * Signing: step up confirmation of legally significant actions, by password
 * or by Aadhaar eSign through a licensed eSign Service Provider (ESP).
 *
 * <p>The portal acts as the Application Service Provider (ASP). It never
 * sees or stores an Aadhaar number: the signer authenticates on the ESP's
 * page, and the portal receives only the signature and the signer's
 * certificate.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Signing (password and Aadhaar eSign)")
package com.mrms.esign;
