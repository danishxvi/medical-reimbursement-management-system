/**
 * Document module: validated, encrypted storage of uploaded prescriptions,
 * bills and supporting papers. Other modules decide who may read a
 * document (for example, the PAO for a claim in its queue) and then call
 * {@link com.mrms.document.DocumentStore#read}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Documents")
package com.mrms.document;
