package com.it.greendeer;

import org.junit.platform.engine.ConfigurationParameters;
import org.junit.platform.engine.CancellationToken;
import org.junit.platform.engine.DiscoveryFilter;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.EngineDiscoveryRequest;
import org.junit.platform.engine.EngineExecutionListener;
import org.junit.platform.engine.ExecutionRequest;
import org.junit.platform.engine.OutputDirectoryCreator;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestEngine;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.reporting.OutputDirectoryProvider;
import org.junit.platform.engine.support.store.Namespace;
import org.junit.platform.engine.support.store.NamespacedHierarchicalStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 极简 JUnit5 运行器：本机离线缺少 junit-platform-launcher，无法走 mvn surefire，
 * 直接通过 junit-platform-engine SPI 执行 @Test 类。
 * 用法：java -cp <test-classes;main-classes;依赖> com.it.greendeer.OfflineTestRunner <类名...>
 */
public class OfflineTestRunner {

    public static void main(String[] args) throws Exception {
        TestEngine engine = null;
        for (TestEngine e : ServiceLoader.load(TestEngine.class)) {
            if (e.getId().contains("jupiter")) {
                engine = e;
                break;
            }
        }
        if (engine == null) {
            throw new IllegalStateException("no TestEngine SPI found (junit-jupiter-engine missing?)");
        }

        AtomicInteger passed = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();

        EngineExecutionListener listener = new EngineExecutionListener() {
            @Override
            public void executionSkipped(TestDescriptor testDescriptor, String reason) {
                skipped.incrementAndGet();
            }

            @Override
            public void executionFinished(TestDescriptor testDescriptor, TestExecutionResult result) {
                if (testDescriptor.isTest()) {
                    if (result.getStatus() == TestExecutionResult.Status.SUCCESSFUL) {
                        passed.incrementAndGet();
                        System.out.println("  PASS  " + testDescriptor.getDisplayName());
                    } else if (result.getStatus() == TestExecutionResult.Status.FAILED) {
                        failed.incrementAndGet();
                        System.out.println("  FAIL  " + testDescriptor.getDisplayName());
                        result.getThrowable().ifPresent(t -> t.printStackTrace(System.out));
                    }
                }
            }
        };

        ConfigurationParameters emptyParams = new ConfigurationParameters() {
            @Override
            public Optional<String> get(String key) {
                return Optional.empty();
            }

            @Override
            public Optional<Boolean> getBoolean(String key) {
                return Optional.empty();
            }

            @Override
            public java.util.Set<String> keySet() {
                return Collections.emptySet();
            }
        };

        for (String className : args) {
            List<? extends DiscoverySelector> selectors = List.of(DiscoverySelectors.selectClass(className));
            OutputDirectoryProvider outputProvider = tempOutputProvider();
            EngineDiscoveryRequest request = new EngineDiscoveryRequest() {
                @Override
                @SuppressWarnings("unchecked")
                public <T extends DiscoverySelector> List<T> getSelectorsByType(Class<T> selectorType) {
                    return (List<T>) selectors.stream().filter(selectorType::isInstance).toList();
                }

                @Override
                public <T extends DiscoveryFilter<?>> List<T> getFiltersByType(Class<T> filterType) {
                    return List.of();
                }

                @Override
                public ConfigurationParameters getConfigurationParameters() {
                    return emptyParams;
                }

                @Override
                public OutputDirectoryCreator getOutputDirectoryCreator() {
                    return outputProvider;
                }
            };
            TestDescriptor descriptor = engine.discover(request, UniqueId.forEngine(engine.getId()));
            System.out.println("== test class: " + className + " ==");
            // Jupiter 需要"请求级 store 有父级"（模拟 launcher 提供的 root→request 层级）
            NamespacedHierarchicalStore<Namespace> rootStore = new NamespacedHierarchicalStore<>(null);
            NamespacedHierarchicalStore<Namespace> store = new NamespacedHierarchicalStore<>(rootStore);
            engine.execute(ExecutionRequest.create(descriptor, listener, emptyParams,
                    outputProvider, store, CancellationToken.create()));
        }

        System.out.printf("%nsummary: passed=%d failed=%d skipped=%d%n", passed.get(), failed.get(), skipped.get());
        if (failed.get() > 0) {
            System.exit(1);
        }
    }

    /** engine 6 要求提供输出目录（替代 launcher 的服务）。 */
    private static OutputDirectoryProvider tempOutputProvider() {
        return new OutputDirectoryProvider() {
            private final Path root = createRoot();
            private final AtomicLong seq = new AtomicLong();

            private Path createRoot() {
                try {
                    return Files.createTempDirectory("gd-junit-output");
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }

            @Override
            public Path getRootDirectory() {
                return root;
            }

            @Override
            public Path createOutputDirectory(TestDescriptor testDescriptor) throws IOException {
                return Files.createDirectory(root.resolve("out-" + seq.incrementAndGet()));
            }
        };
    }
}
