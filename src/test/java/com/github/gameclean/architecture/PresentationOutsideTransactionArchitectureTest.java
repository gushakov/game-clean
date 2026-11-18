package com.github.gameclean.architecture;

import com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort;
import com.tngtech.archunit.core.domain.AccessTarget;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.stream.Stream;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Makes "presentation is the statement after the transaction block" a build-time check: a class that
 * collaborates with the {@link TransactionOperationsOutputPort} never calls a presenter output port from a
 * lambda body, and never hands a presenter port method on as a method reference.
 *
 * <p>The bug it prevents: a presentation moved back inside a block — registered for later, or reached from a
 * lock-loss handler — runs before the outcome is settled or after control has already been handed on, and
 * reopens the two-presentations hazard the port was reshaped to remove (design-notes §4, §5).
 *
 * <p>How the rule reads the code:
 * <ul>
 *   <li>A <em>presenter port</em> is a type whose simple name ends in {@code PresenterOutputPort}; the suffix
 *       is load-bearing, as {@code SubcaseInputPort} is for {@link SubcaseMarkerGuardArchitectureTest}.</li>
 *   <li>A lambda body compiles to a synthetic {@code lambda$…} method, but ArchUnit does not import it as a
 *       code unit of its own: it attributes the body's accesses to the enclosing method and flags them
 *       {@link JavaAccess#isDeclaredInLambda() declared in a lambda}. The rule keys on that flag; matching
 *       {@code lambda$} names would find nothing.</li>
 *   <li>A method reference ({@code presenter::presentX}) has no synthetic method: it is an access from the
 *       enclosing method itself, matched as a method reference.</li>
 * </ul>
 *
 * <p>Known limits, deliberately not solved here:
 * <ul>
 *   <li>The bytecode does not link a lambda to the call it is passed to, so the rule cannot tell a callback
 *       handed to the transaction port from any other lambda. It over-approximates instead: in a class holding
 *       the transaction port, <em>no</em> lambda presents. Today no use case presents from a lambda at all,
 *       and one that needed to would be presenting from deferred control anyway.</li>
 *   <li>The call graph is not followed: a private helper that presents, called from inside the block, passes.
 *       The use cases' interaction tests (an error inside the block presents only the error) cover that.</li>
 * </ul>
 *
 * <p>Analyzes production classes only; {@link PresentationOutsideTransactionRuleTest} proves the rule bites on
 * the fixtures of {@link PresentationOutsideTransactionFixtures}.
 */
@AnalyzeClasses(packages = "com.github.gameclean", importOptions = ImportOption.DoNotIncludeTests.class)
class PresentationOutsideTransactionArchitectureTest {

    private static final String PRESENTER_PORT_SUFFIX = "PresenterOutputPort";

    @ArchTest
    static final ArchRule no_presentation_from_a_callback_of_a_transaction_collaborator =
            classes()
                    .that(describe("hold the transaction port",
                            PresentationOutsideTransactionArchitectureTest::holdsTransactionPort))
                    .should(presentOnlyFromPlainStatements())
                    .because("the presentation is the statement after the transaction block, never part of a "
                            + "callback handed to it");

    private static boolean holdsTransactionPort(JavaClass type) {
        return type.getAllFields().stream()
                .anyMatch(field -> field.getRawType().isEquivalentTo(TransactionOperationsOutputPort.class));
    }

    private static ArchCondition<JavaClass> presentOnlyFromPlainStatements() {
        return new ArchCondition<>("not call a presenter port from a lambda nor reference a presenter port method") {
            @Override
            public void check(JavaClass type, ConditionEvents events) {
                type.getCodeUnits().forEach(codeUnit -> offendingAccesses(codeUnit).forEach(access ->
                        events.add(SimpleConditionEvent.violated(access, access.getDescription()))));
            }
        };
    }

    private static Stream<JavaAccess<?>> offendingAccesses(JavaCodeUnit codeUnit) {
        Stream<JavaAccess<?>> fromLambdaBody = codeUnit.getMethodCallsFromSelf().stream()
                .filter(call -> call.isDeclaredInLambda() && targetsPresenterPort(call.getTarget()))
                .map(call -> call);
        Stream<JavaAccess<?>> references = codeUnit.getMethodReferencesFromSelf().stream()
                .filter(reference -> targetsPresenterPort(reference.getTarget()))
                .map(reference -> reference);
        return Stream.concat(fromLambdaBody, references);
    }

    private static boolean targetsPresenterPort(AccessTarget target) {
        JavaClass owner = target.getOwner();
        return owner.getSimpleName().endsWith(PRESENTER_PORT_SUFFIX)
                || owner.getAllRawInterfaces().stream()
                .anyMatch(port -> port.getSimpleName().endsWith(PRESENTER_PORT_SUFFIX));
    }
}
