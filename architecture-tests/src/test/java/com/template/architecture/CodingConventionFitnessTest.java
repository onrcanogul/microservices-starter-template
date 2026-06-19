package com.template.architecture;

import com.template.messaging.event.base.Event;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

/**
 * Fitness functions for the coding conventions in docs/conventions.md.
 *
 * These enforce the conventions that are mechanically checkable from bytecode. The guiding layer
 * (the full convention list with rationale) is docs/conventions.md; this is the enforcing layer for
 * the subset below. Rules not yet covered here (e.g. "controllers return ApiResponse<T>") remain a
 * tracked follow-up.
 */
class CodingConventionFitnessTest {

    private static JavaClasses imported;

    @BeforeAll
    static void importProductionClasses() {
        imported = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.template");
    }

    @Test
    @DisplayName("constructor injection only: no field is annotated with @Autowired")
    void noFieldInjection() {
        ArchRule rule = noFields().should().beAnnotatedWith(Autowired.class)
                .because("use constructor injection (Lombok @RequiredArgsConstructor), never field @Autowired");
        rule.check(imported);
    }

    @Test
    @DisplayName("logging only via SLF4J: no access to System.out / System.err")
    void noStandardStreams() {
        NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS
                .because("log via @Slf4j, never System.out/System.err")
                .check(imported);
    }

    @Test
    @DisplayName("logging only via SLF4J: no use of java.util.logging")
    void noJavaUtilLogging() {
        NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING
                .because("log via @Slf4j (SLF4J), never java.util.logging")
                .check(imported);
    }

    @Test
    @DisplayName("domain events implement the Event contract")
    void eventsImplementEventContract() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Event")
                .and().areNotInterfaces()
                .should().implement(Event.class)
                .because("events must implement Event so they can be wrapped in EventWrapper<T>");
        rule.check(imported);
    }
}
