package com.github.gameclean.architecture;

import com.github.gameclean.architecture.SubcaseMarkerGuardFixtures.BareParent;
import com.github.gameclean.architecture.SubcaseMarkerGuardFixtures.CatchAllOnlyParent;
import com.github.gameclean.architecture.SubcaseMarkerGuardFixtures.CatchAllOnlyParentOfNestingSubcase;
import com.github.gameclean.architecture.SubcaseMarkerGuardFixtures.GuardedParent;
import com.github.gameclean.architecture.SubcaseMarkerGuardFixtures.HelperInsideGuardedTryParent;
import com.github.gameclean.architecture.SubcaseMarkerGuardFixtures.InnerCatchAllInsideGuardedTryParent;
import com.github.gameclean.architecture.SubcaseMarkerGuardFixtures.LambdaInsideGuardedTryParent;
import com.github.gameclean.architecture.SubcaseMarkerGuardFixtures.NestingSubcase;
import com.github.gameclean.architecture.SubcaseMarkerGuardFixtures.NestingSubcaseInputPort;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that the rule of {@link SubcaseMarkerGuardArchitectureTest} bites, and pins what it does at its
 * edges. Each fixture of {@link SubcaseMarkerGuardFixtures} is imported on its own and held against the rule.
 */
class SubcaseMarkerGuardRuleTest {

    @Nested
    class Bites {

        @Test
        void parentWithOnlyTheCatchAllViolates() {
            assertThat(violationsOf(CatchAllOnlyParent.class)).hasSize(1);
        }

        @Test
        void parentWithNoTryBlockViolates() {
            assertThat(violationsOf(BareParent.class)).hasSize(1);
        }

        @Test
        void parentOfASubcaseFoundByItsPortSuffixViolates() {
            assertThat(violationsOf(CatchAllOnlyParentOfNestingSubcase.class, NestingSubcaseInputPort.class))
                    .hasSize(1);
        }
    }

    @Nested
    class Passes {

        @Test
        void parentWithTheDedicatedCatchPasses() {
            assertThat(violationsOf(GuardedParent.class)).isEmpty();
        }

        @Test
        void subcaseCallingANestedSubcaseUnguardedPasses() {
            assertThat(violationsOf(NestingSubcase.class, NestingSubcaseInputPort.class)).isEmpty();
        }
    }

    /** Behaviour at the edges, recorded rather than wished for: the guard is lexical and per method. */
    @Nested
    class Edges {

        @Test
        void callInsideALambdaWithinAGuardedTryIsReported() {
            assertThat(violationsOf(LambdaInsideGuardedTryParent.class)).hasSize(1);
        }

        @Test
        void callInsideAPrivateHelperWithinAGuardedTryIsReported() {
            assertThat(violationsOf(HelperInsideGuardedTryParent.class)).hasSize(1);
        }

        @Test
        void innerCatchAllInsideAnOuterDedicatedCatchIsNotDetected() {
            assertThat(violationsOf(InnerCatchAllInsideGuardedTryParent.class)).isEmpty();
        }
    }

    private static List<String> violationsOf(Class<?>... fixtures) {
        // A fixture which is itself a subcase leaves the rule nothing to check, which is the point of it.
        ArchRule rule = SubcaseMarkerGuardArchitectureTest.subcase_calls_must_be_guarded_by_the_marker_catch
                .allowEmptyShould(true);
        return rule.evaluate(new ClassFileImporter().importClasses(fixtures)).getFailureReport().getDetails();
    }
}
