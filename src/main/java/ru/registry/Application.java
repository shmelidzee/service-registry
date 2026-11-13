package ru.registry;

import org.springframework.beans.BeansException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import ru.registry.config.RegistryContext;
import ru.registry.demo.Demo;
import ru.registry.service.ProviderRegistry;

import java.io.IOException;
import java.io.PrintStream;

public final class Application {

    static final int SUCCESS = 0;
    static final int AVAILABLE = 0;
    static final int UNAVAILABLE = 1;
    static final int USAGE_ERROR = 2;
    static final int FAILURE = 3;

    private Application() {
    }

    public static void main(String[] args) {
        ConsoleLogFormatter.install();
        System.exit(run(args, System.out, System.err));
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 0) {
            return demo(out, err);
        }
        if (args.length != 3) {
            err.println("Usage:");
            err.println("  service-registry                                        run demo with a local provider server");
            err.println("  service-registry <providers.json> <providerId> <phone>  check one phone number");
            return USAGE_ERROR;
        }
        return check(args[0], args[1], args[2], out, err);
    }

    private static int demo(PrintStream out, PrintStream err) {
        try {
            return Demo.run(out) ? SUCCESS : FAILURE;
        } catch (IOException | RuntimeException e) {
            err.println("Error: " + describe(e));
            return FAILURE;
        }
    }

    private static int check(String providersFile, String providerId, String phone, PrintStream out, PrintStream err) {
        try (AnnotationConfigApplicationContext context = RegistryContext.open(providersFile)) {
            ProviderRegistry registry = context.getBean(ProviderRegistry.class);
            out.println("Providers file:   " + providersFile);
            out.println("Loaded providers: " + registry.providerIds());
            out.println("Checking phone " + phone + " with provider " + providerId + "...");
            boolean available = registry.isPhoneAvailable(providerId, phone);
            out.println("Result: " + (available ? "AVAILABLE" : "UNAVAILABLE"));
            return available ? AVAILABLE : UNAVAILABLE;
        } catch (RuntimeException e) {
            err.println("Error: " + describe(e));
            return FAILURE;
        }
    }

    private static String describe(Exception e) {
        Throwable cause = e;
        while (cause instanceof BeansException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }
}
