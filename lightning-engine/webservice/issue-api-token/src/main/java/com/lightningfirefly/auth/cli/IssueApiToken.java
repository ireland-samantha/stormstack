package com.lightningfirefly.auth.cli;

import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Command-line tool to issue non-expiring API tokens.
 *
 * <p>Usage:
 * <pre>
 * java -jar issue-api-token.jar --roles=admin,command_manager
 * java -jar issue-api-token.jar --roles=view_only --user=service-account
 * java -jar issue-api-token.jar --secret=my-secret-key --roles=admin
 * </pre>
 *
 * <p>Role names are dynamic and can be any string. Common roles include:
 * admin, command_manager, view_only.
 */
public class IssueApiToken {

    private static final String DEFAULT_USER = "api-token";
    private static final String DEFAULT_SECRET_ENV = "JWT_SECRET";

    public static void main(String[] args) {
        IssueApiToken cli = new IssueApiToken();
        int exitCode = cli.run(args, System.out, System.err);
        System.exit(exitCode);
    }

    public int run(String[] args, PrintStream out, PrintStream err) {
        try {
            Arguments arguments = parseArguments(args);

            if (arguments.help) {
                printHelp(out);
                return 0;
            }

            if (arguments.roles.isEmpty()) {
                err.println("Error: At least one role is required.");
                err.println("Use --help for usage information.");
                return 1;
            }

            // Get secret from argument or environment variable
            String secret = arguments.secret;
            if (secret == null || secret.isBlank()) {
                secret = System.getenv(DEFAULT_SECRET_ENV);
            }
            if (secret == null || secret.isBlank()) {
                // Generate a random secret if none provided
                secret = UUID.randomUUID().toString();
                err.println("Warning: No secret provided. Generated random secret.");
                err.println("Set " + DEFAULT_SECRET_ENV + " environment variable for consistent tokens.");
            }

            // Issue the token
            ApiTokenIssuer issuer = new ApiTokenIssuer(secret);
            String token = issuer.issueToken(arguments.user, arguments.roles);

            // Output the token
            out.println(token);

            return 0;
        } catch (IllegalArgumentException e) {
            err.println("Error: " + e.getMessage());
            err.println("Use --help for usage information.");
            return 1;
        } catch (Exception e) {
            err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private Arguments parseArguments(String[] args) {
        Arguments arguments = new Arguments();

        for (String arg : args) {
            if (arg.equals("--help") || arg.equals("-h")) {
                arguments.help = true;
            } else if (arg.startsWith("--roles=")) {
                String rolesStr = arg.substring("--roles=".length());
                arguments.roles = parseRoles(rolesStr);
            } else if (arg.startsWith("--user=")) {
                arguments.user = arg.substring("--user=".length());
            } else if (arg.startsWith("--secret=")) {
                arguments.secret = arg.substring("--secret=".length());
            } else {
                throw new IllegalArgumentException("Unknown argument: " + arg);
            }
        }

        if (arguments.user == null || arguments.user.isBlank()) {
            arguments.user = DEFAULT_USER;
        }

        return arguments;
    }

    private Set<String> parseRoles(String rolesStr) {
        Set<String> roles = new HashSet<>();
        for (String roleStr : rolesStr.split(",")) {
            String trimmed = roleStr.trim();
            if (!trimmed.isEmpty()) {
                roles.add(trimmed);
            }
        }
        return roles;
    }

    private void printHelp(PrintStream out) {
        out.println("API Token Issuer - Issue non-expiring JWT API tokens");
        out.println();
        out.println("Usage:");
        out.println("  java -jar issue-api-token.jar --roles=<roles> [options]");
        out.println();
        out.println("Options:");
        out.println("  --roles=<roles>   Comma-separated list of role names (required)");
        out.println("                    Common roles: admin, command_manager, view_only");
        out.println("                    Custom role names are also supported");
        out.println("  --user=<name>     User/service account name (default: api-token)");
        out.println("  --secret=<key>    JWT signing secret (default: from JWT_SECRET env var)");
        out.println("  --help, -h        Show this help message");
        out.println();
        out.println("Environment variables:");
        out.println("  JWT_SECRET        JWT signing secret (used if --secret not provided)");
        out.println();
        out.println("Examples:");
        out.println("  java -jar issue-api-token.jar --roles=admin");
        out.println("  java -jar issue-api-token.jar --roles=command_manager,view_only --user=game-client");
        out.println("  java -jar issue-api-token.jar --roles=custom_role,another_role");
        out.println("  JWT_SECRET=my-secret java -jar issue-api-token.jar --roles=view_only");
        out.println();
        out.println("Docker:");
        out.println("  docker run --rm -e JWT_SECRET=my-secret issue-api-token --roles=admin");
    }

    private static class Arguments {
        boolean help = false;
        Set<String> roles = new HashSet<>();
        String user = null;
        String secret = null;
    }
}
