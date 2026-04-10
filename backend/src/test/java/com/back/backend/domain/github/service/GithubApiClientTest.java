package com.back.backend.domain.github.service;

import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GithubApiClientTest {

    private static final String TOKEN = "test-token";

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private GithubApiClient client;

    @BeforeEach
    void setUp() {
        client = new GithubApiClient(wireMock.baseUrl(), "client-id", "client-secret");
    }

    @Test
    void getPublicRepos_followsPaginationLinks() {
        wireMock.stubFor(get(urlPathEqualTo("/users/octocat/repos"))
                .withQueryParam("per_page", equalTo("100"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Link",
                                "<" + wireMock.baseUrl() + "/users/octocat/repos?page=2&per_page=100>; rel=\"next\"")
                        .withBody("""
                                [
                                  {
                                    "id": 1,
                                    "name": "repo-one",
                                    "full_name": "octocat/repo-one",
                                    "html_url": "https://github.com/octocat/repo-one",
                                    "private": false,
                                    "default_branch": "main",
                                    "owner": { "login": "octocat" },
                                    "pushed_at": "2026-01-01T00:00:00Z",
                                    "language": "Java"
                                  }
                                ]
                                """)));
        wireMock.stubFor(get(urlPathEqualTo("/users/octocat/repos"))
                .withQueryParam("page", equalTo("2"))
                .withQueryParam("per_page", equalTo("100"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  {
                                    "id": 2,
                                    "name": "repo-two",
                                    "full_name": "octocat/repo-two",
                                    "html_url": "https://github.com/octocat/repo-two",
                                    "private": false,
                                    "default_branch": "develop",
                                    "owner": { "login": "octocat" },
                                    "pushed_at": "2026-01-02T00:00:00Z",
                                    "language": "Kotlin"
                                  }
                                ]
                                """)));

        List<GithubApiClient.GithubRepoInfo> repos = client.getPublicRepos("octocat");

        assertThat(repos).hasSize(2);
        assertThat(repos).extracting(GithubApiClient.GithubRepoInfo::name)
                .containsExactly("repo-one", "repo-two");
        assertThat(repos.get(0).pushedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void getAuthenticatedUserRepos_filtersPrivateReposFromResponseBody() {
        wireMock.stubFor(get(urlPathEqualTo("/user/repos"))
                .withQueryParam("visibility", equalTo("public"))
                .withQueryParam("per_page", equalTo("100"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  {
                                    "id": 1,
                                    "name": "public-repo",
                                    "full_name": "octocat/public-repo",
                                    "html_url": "https://github.com/octocat/public-repo",
                                    "private": false,
                                    "default_branch": "main",
                                    "owner": { "login": "octocat" }
                                  },
                                  {
                                    "id": 2,
                                    "name": "private-repo",
                                    "full_name": "octocat/private-repo",
                                    "html_url": "https://github.com/octocat/private-repo",
                                    "private": true,
                                    "default_branch": "main",
                                    "owner": { "login": "octocat" }
                                  }
                                ]
                                """)));

        List<GithubApiClient.GithubRepoInfo> repos = client.getAuthenticatedUserRepos(TOKEN);

        assertThat(repos).singleElement()
                .extracting(GithubApiClient.GithubRepoInfo::name)
                .isEqualTo("public-repo");
    }

    @Test
    void getCommits_mapsBare403ToScopeInsufficient() {
        wireMock.stubFor(get(urlPathEqualTo("/repos/octocat/portfolio/commits"))
                .withQueryParam("author", equalTo("octocat"))
                .withQueryParam("per_page", equalTo("100"))
                .willReturn(aResponse().withStatus(403)));

        assertThatThrownBy(() -> client.getCommits("octocat", "portfolio", "octocat", TOKEN))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> {
                    ServiceException error = (ServiceException) ex;
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.GITHUB_SCOPE_INSUFFICIENT);
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    @Test
    void getPublicRepos_maps429RetryAfterToRateLimitExceeded() {
        wireMock.stubFor(get(urlPathEqualTo("/users/octocat/repos"))
                .withQueryParam("per_page", equalTo("100"))
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Retry-After", "42")));

        assertThatThrownBy(() -> client.getPublicRepos("octocat"))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> {
                    ServiceException error = (ServiceException) ex;
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.GITHUB_RATE_LIMIT_EXCEEDED);
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(error.getMessage()).contains("42");
                });
    }

    @Test
    void validatePublicRepo_mapsNotFoundToForbidden() {
        wireMock.stubFor(get(urlPathEqualTo("/repos/octocat/missing"))
                .willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> client.validatePublicRepo("octocat", "missing", null))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> {
                    ServiceException error = (ServiceException) ex;
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.GITHUB_REPOSITORY_FORBIDDEN);
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    @Test
    void validatePublicRepo_rejectsPrivateRepoEvenWhenResponseIs200() {
        wireMock.stubFor(get(urlPathEqualTo("/repos/octocat/private-repo"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": 10,
                                  "full_name": "octocat/private-repo",
                                  "html_url": "https://github.com/octocat/private-repo",
                                  "private": true,
                                  "size": 512,
                                  "default_branch": "main",
                                  "owner": { "login": "octocat" },
                                  "language": { "name": "Java" }
                                }
                                """)));

        assertThatThrownBy(() -> client.validatePublicRepo("octocat", "private-repo", TOKEN))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> {
                    ServiceException error = (ServiceException) ex;
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.GITHUB_REPOSITORY_FORBIDDEN);
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    @Test
    void countUserCommits_returnsZeroForEmptyArray() {
        wireMock.stubFor(get(urlPathEqualTo("/repos/octocat/portfolio/commits"))
                .withQueryParam("author", equalTo("octocat"))
                .withQueryParam("per_page", equalTo("1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        int count = client.countUserCommits("octocat", "portfolio", "octocat", TOKEN);

        assertThat(count).isZero();
    }

    @Test
    void countUserCommits_returnsZeroWhenBodyIsNull() {
        wireMock.stubFor(get(urlPathEqualTo("/repos/octocat/portfolio/commits"))
                .withQueryParam("author", equalTo("octocat"))
                .withQueryParam("per_page", equalTo("1"))
                .willReturn(aResponse().withStatus(204)));

        int count = client.countUserCommits("octocat", "portfolio", "octocat", TOKEN);

        assertThat(count).isZero();
    }

    @Test
    void getContributedRepos_filtersPrivateReposAndMapsGraphQlResponse() {
        wireMock.stubFor(post(urlPathEqualTo("/graphql"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "data": {
                                    "viewer": {
                                      "contributionsCollection": {
                                        "commitContributionsByRepository": [
                                          {
                                            "repository": {
                                              "nameWithOwner": "octocat/public-repo",
                                              "url": "https://github.com/octocat/public-repo",
                                              "isPrivate": false,
                                              "databaseId": 100,
                                              "diskUsage": 2048,
                                              "primaryLanguage": { "name": "Java" }
                                            },
                                            "contributions": { "totalCount": 7 }
                                          },
                                          {
                                            "repository": {
                                              "nameWithOwner": "octocat/private-repo",
                                              "url": "https://github.com/octocat/private-repo",
                                              "isPrivate": true,
                                              "databaseId": 101,
                                              "diskUsage": 1024,
                                              "primaryLanguage": { "name": "Kotlin" }
                                            },
                                            "contributions": { "totalCount": 3 }
                                          }
                                        ]
                                      }
                                    }
                                  }
                                }
                                """)));

        List<GithubApiClient.GithubContributedRepo> repos = client.getContributedRepos(TOKEN, 0);

        assertThat(repos).singleElement()
                .satisfies(repo -> {
                    assertThat(repo.githubRepoId()).isEqualTo(100L);
                    assertThat(repo.nameWithOwner()).isEqualTo("octocat/public-repo");
                    assertThat(repo.contributionCount()).isEqualTo(7);
                });
    }

    @Test
    void getContributedRepos_mapsAdapterFailureToExternalServiceUnavailable() {
        wireMock.stubFor(post(urlPathEqualTo("/graphql"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.getContributedRepos(TOKEN, 0))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> {
                    ServiceException error = (ServiceException) ex;
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE);
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });
    }

    @Test
    void getRateLimit_returnsFallbackWhenApiFails() {
        wireMock.stubFor(get(urlPathEqualTo("/rate_limit"))
                .willReturn(aResponse().withStatus(500)));

        GithubApiClient.RateLimitInfo rateLimit = client.getRateLimit(TOKEN);

        assertThat(rateLimit.graphqlRemaining()).isZero();
        assertThat(rateLimit.restRemaining()).isZero();
        assertThat(rateLimit.resetAt()).isAfter(Instant.now().plusSeconds(3500));
    }
}
