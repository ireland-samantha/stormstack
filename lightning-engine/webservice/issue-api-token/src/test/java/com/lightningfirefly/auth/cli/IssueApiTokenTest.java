package com.lightningfirefly.auth.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

class IssueApiTokenTest {

    @Test
    void run_withHelpFlag_printsHelpAndReturnsZero() {
        IssueApiToken cli = new IssueApiToken();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = cli.run(new String[]{"--help"}, new PrintStream(out), new PrintStream(err));

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("API Token Issuer");
        assertThat(output).contains("--roles=<roles>");
        assertThat(output).contains("--user=<name>");
        assertThat(output).contains("--secret=<key>");
    }

    @Test
    void run_withShortHelpFlag_printsHelpAndReturnsZero() {
        IssueApiToken cli = new IssueApiToken();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = cli.run(new String[]{"-h"}, new PrintStream(out), new PrintStream(err));

        assertThat(exitCode).isEqualTo(0);
        assertThat(out.toString()).contains("API Token Issuer");
    }

    @Test
    void run_withNoRoles_returnsErrorCode() {
        IssueApiToken cli = new IssueApiToken();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = cli.run(new String[]{}, new PrintStream(out), new PrintStream(err));

        assertThat(exitCode).isEqualTo(1);
        assertThat(err.toString()).contains("At least one role is required");
    }

    @Test
    void run_withCustomRole_issuesToken() {
        IssueApiToken cli = new IssueApiToken();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = cli.run(
                new String[]{"--roles=custom_role", "--secret=test-secret"},
                new PrintStream(out),
                new PrintStream(err)
        );

        assertThat(exitCode).isEqualTo(0);
        String token = out.toString().trim();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void run_withUnknownArgument_returnsErrorCode() {
        IssueApiToken cli = new IssueApiToken();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = cli.run(new String[]{"--unknown=value"}, new PrintStream(out), new PrintStream(err));

        assertThat(exitCode).isEqualTo(1);
        assertThat(err.toString()).contains("Unknown argument: --unknown=value");
    }

    @Test
    void run_withValidAdminRole_issuesToken() {
        IssueApiToken cli = new IssueApiToken();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = cli.run(
                new String[]{"--roles=admin", "--secret=test-secret"},
                new PrintStream(out),
                new PrintStream(err)
        );

        assertThat(exitCode).isEqualTo(0);
        String token = out.toString().trim();
        // JWT has 3 parts separated by dots
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void run_withMultipleRoles_issuesToken() {
        IssueApiToken cli = new IssueApiToken();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = cli.run(
                new String[]{"--roles=admin,command_manager,view_only", "--secret=test-secret"},
                new PrintStream(out),
                new PrintStream(err)
        );

        assertThat(exitCode).isEqualTo(0);
        String token = out.toString().trim();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void run_withCustomUser_issuesToken() {
        IssueApiToken cli = new IssueApiToken();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = cli.run(
                new String[]{"--roles=view_only", "--user=my-service", "--secret=test-secret"},
                new PrintStream(out),
                new PrintStream(err)
        );

        assertThat(exitCode).isEqualTo(0);
        String token = out.toString().trim();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void run_withNoSecret_warnsAndGeneratesRandomSecret() {
        IssueApiToken cli = new IssueApiToken();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = cli.run(
                new String[]{"--roles=admin"},
                new PrintStream(out),
                new PrintStream(err)
        );

        assertThat(exitCode).isEqualTo(0);
        assertThat(err.toString()).contains("Warning: No secret provided");
        String token = out.toString().trim();
        assertThat(token.split("\\.")).hasSize(3);
    }
}
