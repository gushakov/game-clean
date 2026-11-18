package com.github.gameclean.architecture;

import com.github.gameclean.architecture.PresentationOutsideTransactionFixtures.PresentsAfterTheBlock;
import com.github.gameclean.architecture.PresentationOutsideTransactionFixtures.PresentsFromALambdaWithoutTransactionPort;
import com.github.gameclean.architecture.PresentationOutsideTransactionFixtures.PresentsInsideTheBlock;
import com.github.gameclean.architecture.PresentationOutsideTransactionFixtures.PresentsNothingPresenterOutputPort;
import com.github.gameclean.architecture.PresentationOutsideTransactionFixtures.PresentsThroughAHelperInsideTheBlock;
import com.github.gameclean.architecture.PresentationOutsideTransactionFixtures.PresentsThroughAMethodReference;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that the rule of {@link PresentationOutsideTransactionArchitectureTest} bites, and pins what it does at
 * its edges. Each fixture of {@link PresentationOutsideTransactionFixtures} is imported on its own (with the
 * nested classes its bytecode needs) and held against the rule.
 */
class PresentationOutsideTransactionRuleTest {

    @Nested
    class Bites {

        @Test
        void presentationInsideTheLambdaHandedToTheBlockViolates() {
            assertThat(violationsOf(PresentsInsideTheBlock.class)).hasSize(1);
        }

        @Test
        void presenterMethodReferenceHandedToTheBlockViolates() {
            assertThat(violationsOf(PresentsThroughAMethodReference.class, PresentsNothingPresenterOutputPort.class))
                    .hasSize(1);
        }
    }

    @Nested
    class Passes {

        @Test
        void presentationAfterTheBlockPasses() {
            assertThat(violationsOf(PresentsAfterTheBlock.class)).isEmpty();
        }

        @Test
        void classWithoutTheTransactionPortIsOutOfScope() {
            assertThat(violationsOf(PresentsFromALambdaWithoutTransactionPort.class)).isEmpty();
        }
    }

    /** Behaviour at the edges, recorded rather than wished for: the rule does not follow the call graph. */
    @Nested
    class Edges {

        @Test
        void presentingHelperCalledFromInsideTheBlockIsNotDetected() {
            assertThat(violationsOf(PresentsThroughAHelperInsideTheBlock.class)).isEmpty();
        }
    }

    private static List<String> violationsOf(Class<?>... fixtures) {
        ArchRule rule = PresentationOutsideTransactionArchitectureTest
                .no_presentation_from_a_callback_of_a_transaction_collaborator
                .allowEmptyShould(true);
        return rule.evaluate(new ClassFileImporter().importClasses(fixtures)).getFailureReport().getDetails();
    }
}
