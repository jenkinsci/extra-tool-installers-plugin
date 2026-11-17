package io.jenkins.plugins.extratoolinstallers.installers;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import hudson.FilePath;
import hudson.tools.ToolInstallation;
import io.jenkins.plugins.generic_tool.GenericToolInstallation;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class to validate {@link AuthenticatedZipExtractionInstaller}.
 */
@WithJenkins
class AuthenticatedZipExtractionInstallerTest {

    // matches the file in __files
    private static final String DUMMY_ZIP = "dummy.zip";
    private static final String TEST_TXT = "test.txt";
    private static final String TEST_PATH = "/test/" + DUMMY_ZIP;

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(options().dynamicPort())
            .build();

    @Test
    void shouldDownload(JenkinsRule r) throws Exception {
        // endpoint without authentication
        wireMock.stubFor(get(urlEqualTo(TEST_PATH))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.SC_OK)
                        .withBodyFile(DUMMY_ZIP) // matches the file in __files
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/zip")
                        .withHeader(HttpHeaders.LAST_MODIFIED, DateUtils.formatStandardDate(Instant.now()))));

        // define tool and installer
        ToolInstallation installation = new GenericToolInstallation(DUMMY_ZIP, r.jenkins.getRootDir().getAbsolutePath(), List.of());

        AuthenticatedZipExtractionInstaller installer = new AuthenticatedZipExtractionInstaller("unauthenticated");
        installer.setUrl(wireMock.baseUrl() + TEST_PATH);
        FilePath location = installer.performInstallation(installation, r.jenkins, r.createTaskListener());

        // validate
        assertEquals(r.jenkins.getRootPath(), location);
        assertTrue(location.child(TEST_TXT).exists());
        assertEquals(TEST_TXT, location.child(TEST_TXT).readToString());
    }

    @Test
    void shouldKeepExistingInstallationWhenNotModified(JenkinsRule r) throws Exception {
        String lastModified = DateUtils.formatStandardDate(Instant.parse("2024-01-01T00:00:00Z"));
        wireMock.stubFor(get(urlEqualTo(TEST_PATH))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.SC_OK)
                        .withBodyFile(DUMMY_ZIP)
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/zip")
                        .withHeader(HttpHeaders.LAST_MODIFIED, lastModified)));

        ToolInstallation installation = new GenericToolInstallation(DUMMY_ZIP, r.jenkins.getRootDir().getAbsolutePath(), List.of());
        AuthenticatedZipExtractionInstaller installer = new AuthenticatedZipExtractionInstaller("unauthenticated");
        installer.setUrl(wireMock.baseUrl() + TEST_PATH);
        FilePath location = installer.performInstallation(installation, r.jenkins, r.createTaskListener());
        assertTrue(location.child(TEST_TXT).exists());
        assertEquals(TEST_TXT, location.child(TEST_TXT).readToString());

        long timestampBefore = location.child(".timestamp").lastModified();
        wireMock.stubFor(get(urlEqualTo(TEST_PATH))
                .withHeader(HttpHeaders.IF_MODIFIED_SINCE, equalTo(lastModified))
                .willReturn(aResponse().withStatus(HttpStatus.SC_NOT_MODIFIED)));

        FilePath secondLocation = installer.performInstallation(installation, r.jenkins, r.createTaskListener());
        assertEquals(location, secondLocation);
        assertTrue(secondLocation.child(TEST_TXT).exists());
        assertEquals(TEST_TXT, secondLocation.child(TEST_TXT).readToString());
        assertEquals(timestampBefore, secondLocation.child(".timestamp").lastModified());
        wireMock.verify(1, getRequestedFor(urlEqualTo(TEST_PATH))
                .withHeader(HttpHeaders.IF_MODIFIED_SINCE, equalTo(lastModified)));
    }

    @Test
    void shouldDownloadWithBasicAuth(JenkinsRule r) throws Exception {
        String username = RandomStringUtils.secure().nextAlphabetic(10);
        String password = RandomStringUtils.secure().nextAlphabetic(10);

        // endpoint with authentication
        wireMock.stubFor(get(urlEqualTo(TEST_PATH))
                .withBasicAuth(username, password)
                .willReturn(aResponse()
                        .withStatus(HttpStatus.SC_OK)
                        .withBodyFile(DUMMY_ZIP) // matches the file in __files
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/zip")
                        .withHeader(HttpHeaders.LAST_MODIFIED, DateUtils.formatStandardDate(Instant.now()))));

        // add credential to the store
        UsernamePasswordCredentialsImpl credential = new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL,
                null,
                null,
                username,
                password
        );
        SystemCredentialsProvider.getInstance().getCredentials().add(credential);

        // define tool and installer
        ToolInstallation installation = new GenericToolInstallation(DUMMY_ZIP, r.jenkins.getRootDir().getAbsolutePath(), List.of());

        AuthenticatedZipExtractionInstaller installer = new AuthenticatedZipExtractionInstaller("authenticated");
        installer.setUrl(wireMock.baseUrl() + TEST_PATH);
        installer.setCredentialsId(credential.getId());
        FilePath location = installer.performInstallation(installation, r.jenkins, r.createTaskListener());

        // validate
        assertEquals(r.jenkins.getRootPath(), location);
        assertTrue(location.child(TEST_TXT).exists());
        assertEquals(TEST_TXT, location.child(TEST_TXT).readToString());
    }

    @Test
    void shouldFailWithBadCredentials(JenkinsRule r) throws Exception {
        String username = RandomStringUtils.secure().nextAlphabetic(10);
        String password = RandomStringUtils.secure().nextAlphabetic(10);

        // endpoint with authentication
        wireMock.stubFor(get(urlEqualTo(TEST_PATH))
                .withBasicAuth(username, password)
                .willReturn(aResponse()
                        .withStatus(HttpStatus.SC_OK)
                        .withBodyFile(DUMMY_ZIP) // matches the file in __files
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/zip")
                        .withHeader(HttpHeaders.LAST_MODIFIED, DateUtils.formatStandardDate(Instant.now()))));

        // add non-matching credential to the store
        UsernamePasswordCredentialsImpl credential = new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL,
                null,
                null,
                RandomStringUtils.secure().nextAlphabetic(10),
                RandomStringUtils.secure().nextAlphabetic(10)
        );
        SystemCredentialsProvider.getInstance().getCredentials().add(credential);

        // define tool and installer
        ToolInstallation installation = new GenericToolInstallation(DUMMY_ZIP, r.jenkins.getRootDir().getAbsolutePath(), List.of());

        AuthenticatedZipExtractionInstaller installer = new AuthenticatedZipExtractionInstaller("authenticated");
        installer.setUrl(wireMock.baseUrl() + TEST_PATH);
        installer.setCredentialsId(credential.getId());

        // validate
        AuthenticatedDownloadCallable.HttpGetException ex = assertThrows(
                AuthenticatedDownloadCallable.HttpGetException.class,
                () -> installer.performInstallation(installation, r.jenkins, r.createTaskListener()));
        assertEquals(
                "Authenticated HTTP GET of " + wireMock.baseUrl() + TEST_PATH + " as " + credential.getUsername() + " failed, 404",
                ex.getMessage());
    }
}
