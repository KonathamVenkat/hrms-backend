package com.hrms.employee.config;

import com.hrms.employee.EmployeeApplication;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.repository.Repository;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The application lists its packages by hand in {@code @ComponentScan}, {@code @EnableJpaRepositories}
 * and {@code @EntityScan}. A new package that is not added there compiles fine and then fails (or is
 * silently skipped) at runtime. This test fails instead when a bean, entity or repository under
 * {@code com.hrms} sits outside the listed packages.
 */
class ComponentScanCoverageTest {

    private static final String ROOT = "com.hrms";

    @Test
    void everyBeanIsInAScannedPackage() {
        var provider = new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(new AnnotationTypeFilter(Component.class));   // also covers @Service, @RestController, @Configuration...
        List<String> missing = outside(provider, packages(EmployeeApplication.class.getAnnotation(ComponentScan.class).basePackages()));
        assertTrue(missing.isEmpty(), "Not covered by @ComponentScan: " + missing);
    }

    @Test
    void everyEntityIsInAnEntityScanPackage() {
        var provider = new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        List<String> missing = outside(provider, packages(EmployeeApplication.class.getAnnotation(EntityScan.class).basePackages()));
        assertTrue(missing.isEmpty(), "Not covered by @EntityScan: " + missing);
    }

    @Test
    void everyRepositoryIsInAJpaRepositoriesPackage() {
        var provider = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
                return definition.getMetadata().isInterface();
            }
        };
        provider.addIncludeFilter(new AssignableTypeFilter(Repository.class));
        List<String> missing = outside(provider, packages(EmployeeApplication.class.getAnnotation(EnableJpaRepositories.class).basePackages()));
        assertTrue(missing.isEmpty(), "Not covered by @EnableJpaRepositories: " + missing);
    }

    private static List<String> packages(String[] declared) {
        return Arrays.asList(declared);
    }

    private static List<String> outside(ClassPathScanningCandidateComponentProvider provider, List<String> covered) {
        List<String> missing = new ArrayList<>();
        for (BeanDefinition bd : provider.findCandidateComponents(ROOT)) {
            String name = bd.getBeanClassName();
            boolean ok = covered.stream().anyMatch(p -> name.equals(p) || name.startsWith(p + "."));
            if (!ok) missing.add(name);
        }
        return missing;
    }
}
