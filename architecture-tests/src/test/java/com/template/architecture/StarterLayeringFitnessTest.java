package com.template.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Fitness functions that enforce the template's module-layering invariants.
 *
 * These rules are the enforcing (hard) layer of the project's constraints. The guiding (soft)
 * layer lives in CLAUDE.md and docs/constraints/layering.md, which explain WHY each rule
 * exists. When an agent (or a human) violates an invariant, this test fails the build with a
 * concrete dependency path, so the mistake is caught even if the written convention was ignored.
 *
 * Dependency direction is one-way:   commons  <-  starters  <-  services / infra
 * Starters themselves form two tiers: foundation (persistence, kafka)  <-  feature (everything else)
 */
class StarterLayeringFitnessTest {

    private static final String[] COMMONS = {
            "com.template.core..",
            "com.template.messaging..",
            "com.template.web..",
            "com.template.test.."
    };

    /** Foundation starters: feature starters are allowed to build on these (plus commons). */
    private static final String[] FOUNDATION_STARTERS = {
            "com.template.persistence..",
            "com.template.kafka.."
    };

    /** Every starter package, across both the legacy and the current naming scheme. */
    private static final String[] ALL_STARTERS = {
            "com.template.starter..",
            "com.template.persistence..",
            "com.template.kafka..",
            "com.template.observer.."
    };

    private static final String[] SERVICES = {
            "com.template.microservices.."
    };

    private static final String[] INFRA = {
            "com.template.gateway..",
            "com.template.config..",
            "com.template.service.discovery.."
    };

    private static JavaClasses imported;

    @BeforeAll
    static void importProductionClasses() {
        imported = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.template");
    }

    @Test
    @DisplayName("commons stays pure: it must not depend on starters, services or infra")
    void commonsStaysPure() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage(COMMONS)
                .should().dependOnClassesThat().resideInAnyPackage(concat(ALL_STARTERS, SERVICES, INFRA));
        rule.check(imported);
    }

    @Test
    @DisplayName("starters must not depend on business services or infra modules")
    void startersDoNotDependOnServicesOrInfra() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage(ALL_STARTERS)
                .should().dependOnClassesThat().resideInAnyPackage(concat(SERVICES, INFRA));
        rule.check(imported);
    }

    @Test
    @DisplayName("feature starters must not depend on each other (only commons + foundation starters)")
    void featureStartersDoNotDependOnEachOther() {
        ArchRule rule = slices().assignedFrom(STARTER_SLICES)
                .should().notDependOnEachOther()
                // Dependencies whose TARGET is a foundation starter are allowed and ignored here.
                .ignoreDependency(alwaysTrue(), resideInAnyPackage(FOUNDATION_STARTERS));
        rule.check(imported);
    }

    @Test
    @DisplayName("starters must be free of dependency cycles")
    void startersAreFreeOfCycles() {
        ArchRule rule = slices().assignedFrom(STARTER_SLICES).should().beFreeOfCycles();
        rule.check(imported);
    }

    /**
     * Assigns each class to a slice named after its starter, covering both the current
     * ("com.template.starter.<name>") and the legacy ("com.template.<name>") package schemes.
     * Classes that belong to no starter are ignored.
     */
    private static final SliceAssignment STARTER_SLICES = new SliceAssignment() {
        @Override
        public SliceIdentifier getIdentifierOf(JavaClass javaClass) {
            String pkg = javaClass.getPackageName();
            if (pkg.startsWith("com.template.starter.")) {
                String rest = pkg.substring("com.template.starter.".length());
                int dot = rest.indexOf('.');
                return SliceIdentifier.of("starter:" + (dot < 0 ? rest : rest.substring(0, dot)));
            }
            if (startsWithPackage(pkg, "com.template.persistence")) {
                return SliceIdentifier.of("starter:persistence");
            }
            if (startsWithPackage(pkg, "com.template.kafka")) {
                return SliceIdentifier.of("starter:kafka");
            }
            if (startsWithPackage(pkg, "com.template.observer")) {
                return SliceIdentifier.of("starter:observability");
            }
            return SliceIdentifier.ignore();
        }

        @Override
        public String getDescription() {
            return "starter modules";
        }
    };

    private static boolean startsWithPackage(String pkg, String root) {
        return pkg.equals(root) || pkg.startsWith(root + ".");
    }

    private static String[] concat(String[]... groups) {
        return Arrays.stream(groups).flatMap(Arrays::stream).toArray(String[]::new);
    }
}
