package com.kellen;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * utils 登记的自动配置不能成为消费服务组件扫描的目标。
 *
 * <p>消费服务的主类都在 {@code com.kellen} 包，{@code @SpringBootApplication} 默认扫描整个 {@code com.kellen}，utils 的类也在其中。
 * Spring Boot 要求自动配置只由 imports 文件加载：{@code @SpringBootApplication} 的 {@link AutoConfigurationExcludeFilter}
 * 只挡住自动配置类本身，挡不住其中的嵌套 {@code @Configuration}。嵌套配置一旦被扫描，会早于主类上 {@code @EnableDubbo}
 * 等登记器处理，{@code @ConditionalOnBean} 之类的条件提前判定、结果与作为自动配置加载时不同。</p>
 */
class AutoConfigurationScanBoundaryTest {

    @Test
    void registeredAutoConfigurationsAndTheirNestedClassesAreNotComponentScanCandidates() {
        ClassLoader loader = getClass().getClassLoader();
        List<String> autoConfigurations = ImportCandidates.load(AutoConfiguration.class, loader).getCandidates().stream()
                .filter(name -> name.startsWith("com.kellen.")).toList();
        assertThat(autoConfigurations).isNotEmpty();
        AutoConfigurationExcludeFilter excludeFilter = new AutoConfigurationExcludeFilter();
        excludeFilter.setBeanClassLoader(loader);
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(true);
        scanner.addExcludeFilter(excludeFilter);
        Set<String> packages = autoConfigurations.stream()
                .map(name -> name.substring(0, name.lastIndexOf('.'))).collect(Collectors.toSet());
        List<String> scannedAutoConfigurationClasses = packages.stream()
                .flatMap(base -> scanner.findCandidateComponents(base).stream())
                .map(BeanDefinition::getBeanClassName)
                .filter(name -> autoConfigurations.stream().anyMatch(autoConfiguration ->
                        name.equals(autoConfiguration) || name.startsWith(autoConfiguration + "$")))
                .distinct().sorted().toList();
        assertThat(scannedAutoConfigurationClasses).isEmpty();
    }
}
