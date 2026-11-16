package com.github.gameclean.architecture;

import com.github.gameclean.core.port.SubcaseAlreadyPresented;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Makes the "parent swallows the marker" contract of the subcase pattern a build-time check: every call to
 * a subcase input port method, made from a class that is not itself a subcase, must sit inside a
 * {@code try} block that catches exactly {@link SubcaseAlreadyPresented}.
 *
 * <p>The bug it prevents: a subcase presents its outcome and throws the marker, the parent has no dedicated
 * catch, the marker falls into the parent's {@code catch (Exception e)} and the parent presents an error on
 * top of the outcome the player has already been shown. The compiler cannot help — the marker is unchecked,
 * and the catch-all would satisfy a checked one anyway.
 *
 * <p>How the rule reads the code:
 * <ul>
 *   <li>A <em>subcase port</em> is a type whose simple name ends in {@code SubcaseInputPort}; a
 *       <em>subcase</em> is a class which implements one. The suffix is therefore load-bearing.</li>
 *   <li>A subcase is exempt as a caller: it may let a nested subcase's marker propagate to its own
 *       parent.</li>
 *   <li>"Guarded" is lexical and per method: the call's own bytecode must lie inside the {@code try} block.
 *       A call moved into a lambda or into a private helper is reported even when the lambda or the helper
 *       is invoked from a guarded {@code try} — the rule cannot follow the call graph. Keep subcase calls
 *       directly in the interaction method.</li>
 * </ul>
 *
 * <p>Known limits, deliberately not solved here:
 * <ul>
 *   <li>The catch body is not inspected: a dedicated catch which presents again still passes. That stays
 *       covered by the parents' interaction tests.</li>
 *   <li>Every subcase port method is treated as possibly presenting, a returning-only one included.</li>
 *   <li>Nesting is not inspected: a call inside an inner {@code try} with only a catch-all, itself inside
 *       an outer {@code try} with the dedicated catch, passes although the inner catch-all gets the marker
 *       first.</li>
 * </ul>
 *
 * <p>Analyzes production classes only: the subcases' own unit tests call the ports directly and assert the
 * thrown marker, and the rule's test fixtures ({@link SubcaseMarkerGuardFixtures}) violate it on purpose.
 * {@link SubcaseMarkerGuardRuleTest} proves the rule bites.
 */
@AnalyzeClasses(packages = "com.github.gameclean", importOptions = ImportOption.DoNotIncludeTests.class)
class SubcaseMarkerGuardArchitectureTest {

    private static final String SUBCASE_PORT_SUFFIX = "SubcaseInputPort";

    private static final DescribedPredicate<JavaClass> ARE_NOT_SUBCASES =
            describe("are not subcases", type -> !isSubcaseType(type));

    private static final DescribedPredicate<JavaMethodCall> UNGUARDED_SUBCASE_CALL =
            describe("the target is a subcase input port and the call is not inside a try block catching "
                            + SubcaseAlreadyPresented.class.getSimpleName(),
                    call -> isSubcaseType(call.getTargetOwner()) && !isGuarded(call));

    @ArchTest
    static final ArchRule subcase_calls_must_be_guarded_by_the_marker_catch =
            noClasses()
                    .that(ARE_NOT_SUBCASES)
                    .should().callMethodWhere(UNGUARDED_SUBCASE_CALL)
                    .because("a subcase which has presented throws the marker, and without a dedicated catch "
                            + "the parent's catch-all presents an error on top");

    private static boolean isSubcaseType(JavaClass type) {
        return type.getSimpleName().endsWith(SUBCASE_PORT_SUFFIX)
                || type.getAllRawInterfaces().stream()
                .anyMatch(port -> port.getSimpleName().endsWith(SUBCASE_PORT_SUFFIX));
    }

    private static boolean isGuarded(JavaMethodCall call) {
        return call.getContainingTryBlocks().stream()
                .flatMap(tryBlock -> tryBlock.getCaughtThrowables().stream())
                .anyMatch(caught -> caught.isEquivalentTo(SubcaseAlreadyPresented.class));
    }
}
