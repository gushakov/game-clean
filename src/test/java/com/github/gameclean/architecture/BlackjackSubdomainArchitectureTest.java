package com.github.gameclean.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Simulates the Maven module boundary we deliberately did not build around the cards generic subdomain:
 * {@code core.model.blackjack} may depend only on itself, {@code core.model.dice} (the entropy capability a
 * standalone library would take an equivalent of), the {@code core.model} root (the always-valid construction
 * gate), the JDK, and Lombok. In particular the package can never name a {@code PlayerId} or {@code NpcId} —
 * a blackjack round is <em>anonymous</em> by construction; who plays it lives outside the "library" (see the
 * package's {@code package-info}).
 *
 * <p>Separate from {@link HexagonalArchitectureTest} because this is a <em>positive</em> rule ("may only
 * depend on"), which must analyze <b>production classes only</b> — the package's unit tests legitimately
 * depend on JUnit and AssertJ — and the test-exclusion import option is declared per {@code @AnalyzeClasses}.
 * The hexagonal rules over there are negative ("must not depend on") and stay stricter by including tests.
 */
@AnalyzeClasses(packages = "com.github.gameclean", importOptions = ImportOption.DoNotIncludeTests.class)
class BlackjackSubdomainArchitectureTest {

    @ArchTest
    static final ArchRule blackjack_generic_subdomain_must_stay_library_pure =
            classes()
                    .that().resideInAPackage("..core.model.blackjack..")
                    .should().onlyDependOnClassesThat().resideInAnyPackage(
                            "com.github.gameclean.core.model.blackjack",
                            "com.github.gameclean.core.model.dice",
                            "com.github.gameclean.core.model",
                            "java..",
                            "lombok..");
}
