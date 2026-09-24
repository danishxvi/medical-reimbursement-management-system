package com.mrms;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Guards the modular monolith: fails the build if a module reaches into
 * another module's internal package or if module dependencies form a cycle.
 */
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(MrmsApplication.class);

    @Test
    void moduleBoundariesAreRespected() {
        modules.verify();
    }

    @Test
    void writeModuleDocumentation() {
        // Generates PlantUML component diagrams under target/spring-modulith-docs
        new Documenter(modules).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
    }
}
