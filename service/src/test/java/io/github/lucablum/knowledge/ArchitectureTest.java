package io.github.lucablum.knowledge;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.properties.CanBeAnnotated;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.jmolecules.archunit.JMoleculesArchitectureRules;
import org.jmolecules.architecture.hexagonal.Application;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.ddd.annotation.Service;
import org.jmolecules.ddd.annotation.ValueObject;

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
    static final ArchRule hexagonal = JMoleculesArchitectureRules.ensureHexagonal();

    @ArchTest
    static final ArchRule everyAdapterIsMarkedAsOne = classes()
            .that().resideInAPackage("..knowledge.adapter..")
            .and().resideOutsideOfPackage("..knowledge.adapter.config..")
            .and().arePublic()
            .and().areTopLevelClasses()
            .should().beAnnotatedWith(PrimaryAdapter.class)
            .orShould().beAnnotatedWith(SecondaryAdapter.class);

    @ArchTest
    static final ArchRule everyInterfaceOfTheApplicationIsAPort = classes()
            .that().resideInAPackage("..knowledge.application..")
            .and().areInterfaces()
            .should().beAnnotatedWith(PrimaryPort.class)
            .orShould().beAnnotatedWith(SecondaryPort.class);

    @ArchTest
    static final ArchRule portsAreInterfaces = classes()
            .that().areAnnotatedWith(PrimaryPort.class)
            .or().areAnnotatedWith(SecondaryPort.class)
            .should().beInterfaces();

    @ArchTest
    static final ArchRule everyConcreteClassOfTheApplicationImplementsAPrimaryPort = classes()
            .that().resideInAPackage("..knowledge.application..")
            .and().areNotInterfaces()
            .and().areNotAnnotatedWith(ValueObject.class)
            .and().areNotAssignableTo(Throwable.class)
            .should().beAnnotatedWith(Application.class)
            .andShould().implement(CanBeAnnotated.Predicates.annotatedWith(PrimaryPort.class));

    @ArchTest
    static final ArchRule everyPublicConceptOfTheDomainHasADddStereotype = classes()
            .that().resideInAPackage("..knowledge.domain..")
            .and().arePublic()
            .and().areTopLevelClasses()
            .and(DescribedPredicate.not(utilityClasses()))
            .should().beAnnotatedWith(ValueObject.class)
            .orShould().beAnnotatedWith(Service.class);

    @ArchTest
    static final ArchRule domainDependsOnNoUseCase = noClasses()
            .that().resideInAPackage("..knowledge.domain..")
            .should().dependOnClassesThat().resideInAPackage("..knowledge.application..");

    /** What a Note is underlies every other concept, so it may depend on none of them. */
    @ArchTest
    static final ArchRule noteDependsOnNoOtherDomainConcept = noClasses()
            .that().resideInAPackage("..knowledge.domain.note..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..knowledge.domain.chunk..", "..knowledge.domain.search..", "..knowledge.domain.catalog..",
                    "..knowledge.domain.link..");

    @ArchTest
    static final ArchRule domainConceptsAreFreeOfCycles = slices()
            .matching("..knowledge.domain.(*)..")
            .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule useCasesAreFreeOfCycles = slices()
            .matching("..knowledge.application.(*)..")
            .should().beFreeOfCycles();

    /** A class nobody can instantiate: statics only, such as {@code VaultRules}. */
    private static DescribedPredicate<JavaClass> utilityClasses() {
        return DescribedPredicate.describe("utility classes", javaClass -> javaClass.getConstructors().stream()
                .allMatch(constructor -> constructor.getModifiers().contains(JavaModifier.PRIVATE)));
    }
}
