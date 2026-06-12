package com.github.gameclean.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces the hexagonal dependency rule — the clean core never reaches outward.
 *
 * <p>Two rules, both phrased as forbidden dependencies:
 * <ul>
 *   <li>{@code core} must not depend on {@code infrastructure} — the headline hexagonal
 *       boundary: ports define contracts inside, adapters implement them outside.</li>
 *   <li>{@code core.model} must not depend on {@code core.port} — the model (the innermost
 *       layer) depends on no other layer, not even ports. This is the rule that keeps
 *       always-valid construction free of any port/adapter concern (e.g. id-encoding lives
 *       in the generator, never in a model Value Object).</li>
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
}
