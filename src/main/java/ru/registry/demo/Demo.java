package ru.registry.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import ru.registry.config.RegistryContext;
import ru.registry.provider.Provider;
import ru.registry.service.ProviderRegistry;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class Demo {

    private static final String TABLE_INDENT = "   ";
    private static final int[] COLUMN_WIDTHS = {4, 6, 10, 15, 13};
    private static final int STEP_TITLE_WIDTH = 37;

    private final PrintStream out;
    private int passed;
    private int total;

    private Demo(PrintStream out) {
        this.out = out;
    }

    public static boolean run(PrintStream out) throws IOException {
        return new Demo(out).run();
    }

    private boolean run() throws IOException {
        out.println("Service registry demo");
        out.println("=====================");
        out.println();
        Map<String, String> apiKeys = Map.of("alpha", "alpha-key", "beta", "beta-key", "gamma", "gamma-key");
        try (DemoProviderServer server = DemoProviderServer.start(apiKeys)) {
            step(1, "Demo provider server started", server.baseUrl(""));
            Path file = Files.createTempFile("providers-", ".json");
            try {
                List<Provider> providers = List.of(
                        new Provider("alpha", server.baseUrl("alpha"), "alpha-key"),
                        new Provider("beta", server.baseUrl("beta"), "beta-key"),
                        new Provider("broken", server.baseUrl("beta"), "wrong-key"));
                new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(file.toFile(), providers);
                step(2, "Providers file written", file.toString());
                try (AnnotationConfigApplicationContext context = RegistryContext.open(file.toString())) {
                    ProviderRegistry registry = context.getBean(ProviderRegistry.class);
                    step(3, "Providers loaded from file", registry.providerIds().toString());
                    checkInitialProviders(registry);
                    registry.replaceProviders(List.of(new Provider("gamma", server.baseUrl("gamma"), "gamma-key")));
                    step(5, "Provider set replaced with a list", registry.providerIds().toString());
                    checkReplacedProviders(registry);
                }
            } finally {
                Files.deleteIfExists(file);
            }
        }
        out.println();
        boolean success = passed == total;
        out.println("Result: " + passed + " of " + total + " checks passed. "
                + (success ? "The service works as expected." : "Some checks FAILED."));
        return success;
    }

    private void checkInitialProviders(ProviderRegistry registry) {
        out.println();
        step(4, "Checking phone numbers", "");
        header();
        check(registry, "alpha", "79990000001", Outcome.AVAILABLE);
        check(registry, "alpha", "+79990000001", Outcome.AVAILABLE);
        check(registry, "alpha", "79990000002", Outcome.UNAVAILABLE);
        check(registry, "beta", "79990000001", Outcome.AVAILABLE);
        check(registry, "beta", "79990000002", Outcome.UNAVAILABLE);
        check(registry, "beta", "79990000500", Outcome.ERROR);
        check(registry, "broken", "79990000001", Outcome.ERROR);
        check(registry, "gamma", "79990000001", Outcome.ERROR);
        check(registry, "alpha", "12-34", Outcome.ERROR);
        out.println();
    }

    private void checkReplacedProviders(ProviderRegistry registry) {
        out.println();
        header();
        check(registry, "gamma", "79990000001", Outcome.AVAILABLE);
        check(registry, "gamma", "79990000002", Outcome.UNAVAILABLE);
        check(registry, "alpha", "79990000001", Outcome.ERROR);
    }

    private void check(ProviderRegistry registry, String provider, String phone, Outcome expected) {
        Outcome actual;
        String details;
        try {
            actual = registry.isPhoneAvailable(provider, phone) ? Outcome.AVAILABLE : Outcome.UNAVAILABLE;
            details = actual.name();
        } catch (RuntimeException e) {
            actual = Outcome.ERROR;
            details = "ERROR " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        total++;
        boolean ok = actual == expected;
        if (ok) {
            passed++;
        }
        row(String.valueOf(total), ok ? "OK" : "FAIL", provider, phone, expected.name(), details);
    }

    private void header() {
        row("#", "", "PROVIDER", "PHONE", "EXPECTED", "ACTUAL");
    }

    private void row(String... columns) {
        StringBuilder line = new StringBuilder(TABLE_INDENT);
        for (int i = 0; i < columns.length; i++) {
            boolean last = i == columns.length - 1;
            line.append(last ? columns[i] : padRight(columns[i], COLUMN_WIDTHS[i]));
        }
        out.println(line);
    }

    private void step(int number, String title, String value) {
        String line = number + ". " + (value.isEmpty() ? title : padRight(title, STEP_TITLE_WIDTH) + value);
        out.println(line);
    }

    private static String padRight(String value, int width) {
        return value + " ".repeat(Math.max(1, width - value.length()));
    }

    private enum Outcome {
        AVAILABLE,
        UNAVAILABLE,
        ERROR
    }
}
