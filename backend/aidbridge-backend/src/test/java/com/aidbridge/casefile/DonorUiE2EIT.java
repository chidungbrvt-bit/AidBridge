package com.aidbridge.casefile;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Full Next -> same-origin proxy -> Spring Boot -> PostgreSQL; never a test controller or MockMvc. */
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_F07_REAL_E2E", matches = "true")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_PASSWORD", matches = ".+")
class DonorUiE2EIT extends AbstractCampaignHttpIT {
    @org.springframework.beans.factory.annotation.Autowired org.springframework.context.ApplicationContext context;
    @Test
    void donorRoutesUseRealBackendAndPersistTheirResults() throws Exception {
        assertThat(System.getenv("CI")).as("fixture may only run in disposable CI").isEqualTo("true");
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("aidbridge_test");
        assertThat(jdbc.queryForObject("SELECT current_schema()", String.class)).isEqualTo(SCHEMA);
        assertThat(context.getBeansOfType(com.aidbridge.blockchain.worker.BlockchainWorkerScheduler.class)).isEmpty();
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor donor = donor(true);
        var campaign = publishedCampaign(orgAdmin, List.of(
                Map.of("name", "Food", "unit", "PACKAGE", "quantity", 50),
                Map.of("name", "Hygiene", "unit", "KIT", "quantity", 30)));
        Actor confirmer = actor(ORG_A, "DELIVERY_CONFIRMER", false);
        jdbc.update("INSERT INTO membership_roles(membership_id,role) VALUES (?, 'DELIVERY_CONFIRMER')", volunteer.membership());
        var reviewCampaign = publishedCampaign(orgAdmin, List.of(
                Map.of("name", "Rice", "unit", "BAG", "quantity", 2),
                Map.of("name", "Water", "unit", "BOX", "quantity", 1)));
        Path frontend = Path.of("../../frontend").toAbsolutePath().normalize();
        Files.createDirectories(frontend.resolve(".cache"));
        Map<String, String> env = Map.ofEntries(
                Map.entry("NEXT_PUBLIC_API_MODE", "real"),
                Map.entry("API_INTERNAL_BASE_URL", "http://127.0.0.1:" + port),
                Map.entry("NEXT_TELEMETRY_DISABLED", "1"),
                Map.entry("F07_E2E_SCHEMA", SCHEMA),
                Map.entry("F07_E2E_DONOR_ID", donor.id().toString()),
                Map.entry("F07_E2E_DONOR_EMAIL", jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, donor.id())),
                Map.entry("F07_E2E_DONOR_PASSWORD", PASSWORD),
                Map.entry("F07_E2E_CAMPAIGN_ID", campaign.toString()),
                Map.entry("F07_E2E_CASE_ID", jdbc.queryForObject("SELECT aid_case_id FROM campaigns WHERE id = ?", java.util.UUID.class, campaign).toString()),
                Map.entry("F07_E2E_RECORDER_EMAIL", jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, orgAdmin.id())),
                Map.entry("F07_E2E_RECORDER_ID", orgAdmin.id().toString()),
                Map.entry("F07_E2E_VOLUNTEER_ID", volunteer.id().toString()),
                Map.entry("F07_E2E_VOLUNTEER_EMAIL", jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, volunteer.id())),
                Map.entry("F09_E2E_VERIFIER_EMAIL", jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, verifier.id())),
                Map.entry("F09_E2E_CONFIRMER_EMAIL", jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, confirmer.id())),
                Map.entry("F09_E2E_PLATFORM_EMAIL", jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, admin.id())),
                Map.entry("F09_E2E_CAMPAIGN_ID", reviewCampaign.toString()),
                Map.entry("F09_E2E_CASE_ID", jdbc.queryForObject("SELECT aid_case_id FROM campaigns WHERE id = ?", java.util.UUID.class, reviewCampaign).toString()));
        run(frontend, env, "node", "node_modules/next/dist/bin/next", "build", "--webpack");
        ProcessBuilder launch = new ProcessBuilder("node", "node_modules/next/dist/bin/next", "start", "--hostname", "127.0.0.1", "--port", "3000")
                .directory(frontend.toFile()).redirectErrorStream(true)
                .redirectOutput(frontend.resolve(".cache/f07-real-next.log").toFile());
        launch.environment().putAll(env);
        Process server = launch.start();
        try {
            HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(2)).build();
            boolean ready = false;
            int lastStatus = -1;
            for (int i = 0; i < 60 && server.isAlive(); i++) {
                try {
                    var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:3000/login"))
                            .timeout(Duration.ofSeconds(2)).GET().build(), HttpResponse.BodyHandlers.discarding());
                    lastStatus = response.statusCode();
                    if (response.statusCode() == 200) { ready = true; break; }
                } catch (java.io.IOException ignored) { }
                Thread.sleep(500);
            }
            if (!ready) System.out.println(Files.readString(frontend.resolve(".cache/f07-real-next.log")).replace(PASSWORD, "[REDACTED]"));
            assertThat(ready).as("Next production server must be reachable; last HTTP status %s", lastStatus).isTrue();
            run(frontend, env, "node", "node_modules/@playwright/test/cli.js", "test", "--project=donor-real", "--reporter=list,junit");
        } finally {
            server.destroy();
            if (!server.waitFor(10, TimeUnit.SECONDS)) server.destroyForcibly();
        }
    }

    private static void run(Path cwd, Map<String, String> env, String... command) throws Exception {
        // Native child stdout must not write directly into Maven Surefire's fork protocol channel.
        Path output = cwd.resolve(".cache/f07-real-child.log");
        ProcessBuilder builder = new ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(output.toFile()));
        builder.environment().putAll(env);
        Process child = builder.start();
        try {
            assertThat(child.waitFor(5, TimeUnit.MINUTES)).as("CI child process deadline").isTrue();
            System.out.println(Files.readString(output).replace(env.get("F07_E2E_DONOR_PASSWORD"), "[REDACTED]"));
            assertThat(child.exitValue()).as("CI child exit status").isZero();
        } finally { if (child.isAlive()) child.destroyForcibly(); }
    }
}
