package io.github.lucablum.knowledge;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** The domain and the use cases are plain Java; frameworks appear only in adapters. */
@AnalyzeClasses(packages = "io.github.lucablum.knowledge", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule coreDependsOnNoFrameworkOrAdapter = noClasses()
            .that().resideInAnyPackage("..knowledge.domain..", "..knowledge.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "io.quarkus..",
                    "io.quarkiverse..",
                    "io.smallrye..",
                    "io.vertx..",
                    "io.agroal..",
                    "jakarta..",
                    "org.eclipse.microprofile..",
                    "dev.langchain4j..",
                    "org.postgresql..",
                    "java.sql..",
                    "javax.sql..",
                    "org.flywaydb..",
                    "com.fasterxml.jackson..",
                    "..knowledge.adapter..");

    @ArchTest
    static final ArchRule domainDependsOnNoUseCase = noClasses()
            .that().resideInAPackage("..knowledge.domain..")
            .should().dependOnClassesThat().resideInAPackage("..knowledge.application..");

    @ArchTest
    static final ArchRule domainModelDependsOnNoSplitting = noClasses()
            .that().resideInAPackage("..knowledge.domain")
            .should().dependOnClassesThat().resideInAPackage("..knowledge.domain.splitting..");
}
