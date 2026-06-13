package com.github.gameclean.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces the hexagonal dependency rule — the clean core never reaches outward, and the framework
 * never reaches in.
 *
 * <p>Four rules:
 * <ul>
 *   <li>{@code core} must not depend on {@code infrastructure} — the headline hexagonal
 *       boundary: ports define contracts inside, adapters implement them outside.</li>
 *   <li>{@code core.model} must not depend on {@code core.port} — the model (the innermost
 *       layer) depends on no other layer, not even ports. This is the rule that keeps
 *       always-valid construction free of any port/adapter concern (e.g. id-encoding lives
 *       in the generator, never in a model Value Object).</li>
 *   <li>The {@code @SpringBootApplication} class must reside in {@code infrastructure} — it roots
 *       component scanning at its own package, so placing it there confines the scan (and all
 *       autoconfiguration) to the infrastructure ring and the framework never scans {@code core}.
 *       This makes "Spring never scans the core" structural rather than a matter of where the file
 *       happens to sit.</li>
 *   <li>{@code core} must carry no Spring stereotype annotations — defense in depth on the same
 *       boundary: even if a {@code @Component}/{@code @Configuration} (or any stereotype, all
 *       meta-annotated with {@code @Component}) were authored in the core, this fails the build
 *       rather than silently inviting a bean into the clean layer.</li>
 * </ul>
 */
@AnalyzeClasses(packages = "com.github.gameclean")
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule core_must_not_depend_on_infrastructure =
            noClasses()
                    .that().resideInAPackage("..core..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule model_must_not_depend_on_ports =
            noClasses()
                    .that().resideInAPackage("..core.model..")
                    .should().dependOnClassesThat().resideInAPackage("..core.port..");

    @ArchTest
    static final ArchRule spring_boot_application_must_reside_in_infrastructure =
            classes()
                    .that().areAnnotatedWith(SpringBootApplication.class)
                    .should().resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule core_must_be_free_of_spring_stereotypes =
            noClasses()
                    .that().resideInAPackage("..core..")
                    .should().beAnnotatedWith(Component.class)
                    .orShould().beMetaAnnotatedWith(Component.class);
}
