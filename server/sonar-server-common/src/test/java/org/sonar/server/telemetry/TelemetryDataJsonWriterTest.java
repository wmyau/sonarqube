/*
 * SonarQube
 * Copyright (C) 2009-2023 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.telemetry;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.codec.digest.DigestUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.text.JsonWriter;
import org.sonar.core.platform.EditionProvider;
import org.sonar.core.telemetry.TelemetryExtension;
import org.sonar.core.util.stream.MoreCollectors;
import org.sonar.db.user.UserTelemetryDto;

import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonar.db.newcodeperiod.NewCodePeriodType.NUMBER_OF_DAYS;
import static org.sonar.db.newcodeperiod.NewCodePeriodType.PREVIOUS_VERSION;
import static org.sonar.test.JsonAssert.assertJson;

@RunWith(DataProviderRunner.class)
public class TelemetryDataJsonWriterTest {

  private final Random random = new Random();

  private final TelemetryExtension extension = mock(TelemetryExtension.class);

  private final System2 system2 = mock(System2.class);

  private final TelemetryDataJsonWriter underTest = new TelemetryDataJsonWriter(List.of(extension), system2);

  private static final int NCD_ID = 12345;

  private static final TelemetryData.NewCodeDefinition NCD_INSTANCE =
    new TelemetryData.NewCodeDefinition(PREVIOUS_VERSION.name(), "", "instance");
  private static final TelemetryData.NewCodeDefinition NCD_PROJECT =
    new TelemetryData.NewCodeDefinition(NUMBER_OF_DAYS.name(), "30", "project");

  @Test
  public void write_server_id_version_and_sequence() {
    TelemetryData data = telemetryBuilder().build();

    String json = writeTelemetryData(data);
    assertJson(json).isSimilarTo("""
      {
        "id": "%s",
        "version": "%s",
        "messageSequenceNumber": %s
      }
      """.formatted(data.getServerId(), data.getVersion(), data.getMessageSequenceNumber()));
  }

  @Test
  public void does_not_write_edition_if_null() {
    TelemetryData data = telemetryBuilder().build();

    String json = writeTelemetryData(data);

    assertThat(json).doesNotContain("edition");
  }

  @Test
  @UseDataProvider("allEditions")
  public void writes_edition_if_non_null(EditionProvider.Edition edition) {
    TelemetryData data = telemetryBuilder()
      .setEdition(edition)
      .build();

    String json = writeTelemetryData(data);
    assertJson(json).isSimilarTo("""
      {
        "edition": "%s"
      }
      """.formatted(edition.name().toLowerCase(Locale.ENGLISH)));
  }

  @Test
  public void writes_default_qg() {
    TelemetryData data = telemetryBuilder()
      .setDefaultQualityGate("default-qg")
      .build();

    String json = writeTelemetryData(data);
    assertJson(json).isSimilarTo("""
      {
        "defaultQualityGate": "%s"
      }
      """.formatted(data.getDefaultQualityGate()));
  }

  @Test
  public void writes_database() {
    String name = randomAlphabetic(12);
    String version = randomAlphabetic(10);
    TelemetryData data = telemetryBuilder()
      .setDatabase(new TelemetryData.Database(name, version))
      .build();

    String json = writeTelemetryData(data);
    assertJson(json).isSimilarTo("""
      {
        "database": {
          "name": "%s",
          "version": "%s"
        }
      }
      """.formatted(name, version));
  }

  @Test
  public void writes_no_plugins() {
    TelemetryData data = telemetryBuilder()
      .setPlugins(Collections.emptyMap())
      .build();

    String json = writeTelemetryData(data);

    assertJson(json).isSimilarTo("""
      { 
        "plugins": []
      }
      """);
  }

  @Test
  public void writes_all_plugins() {
    Map<String, String> plugins = IntStream.range(0, 1 + random.nextInt(10))
      .boxed()
      .collect(MoreCollectors.uniqueIndex(i -> "P" + i, i -> "V" + i));
    TelemetryData data = telemetryBuilder()
      .setPlugins(plugins)
      .build();

    String json = writeTelemetryData(data);
    assertJson(json).isSimilarTo("""
      {
        "plugins": [%s]
      }
      """.formatted(plugins.entrySet().stream().map(e -> "{\"name\":\"" + e.getKey() + "\",\"version\":\"" + e.getValue() + "\"}").collect(joining(","))));
  }

  @Test
  public void does_not_write_installation_date_if_null() {
    TelemetryData data = telemetryBuilder()
      .setInstallationDate(null)
      .build();

    String json = writeTelemetryData(data);

    assertThat(json).doesNotContain("installationDate");
  }

  @Test
  public void write_installation_date_in_utc_format() {
    TelemetryData data = telemetryBuilder()
      .setInstallationDate(1_000L)
      .build();

    String json = writeTelemetryData(data);

    assertJson(json).isSimilarTo("""
      {
        "installationDate":"1970-01-01T00:00:01+0000"
      }
      """);
  }

  @Test
  public void does_not_write_installation_version_if_null() {
    TelemetryData data = telemetryBuilder()
      .setInstallationVersion(null)
      .build();

    String json = writeTelemetryData(data);

    assertThat(json).doesNotContain("installationVersion");
  }

  @Test
  public void write_installation_version() {
    String installationVersion = randomAlphabetic(5);
    TelemetryData data = telemetryBuilder()
      .setInstallationVersion(installationVersion)
      .build();

    String json = writeTelemetryData(data);
    assertJson(json).isSimilarTo("""
      {
        "installationVersion": "%s"
      }
      """.formatted(installationVersion));
  }

  @Test
  @UseDataProvider("getFeatureFlagEnabledStates")
  public void write_docker_flag(boolean isInDocker) {
    TelemetryData data = telemetryBuilder()
      .setInDocker(isInDocker)
      .build();

    String json = writeTelemetryData(data);
    assertJson(json).isSimilarTo("""
      {
        "docker": %s
      }
      """.formatted(isInDocker));
  }

  @DataProvider
  public static Object[][] getManagedInstanceData() {
    return new Object[][] {
      {true, "scim"},
      {true, "github"},
      {true, "gitlab"},
      {false, null},
    };
  }

  @Test
  @UseDataProvider("getManagedInstanceData")
  public void writeTelemetryData_encodesCorrectlyManagedInstanceInformation(boolean isManaged, String provider) {
    TelemetryData data = telemetryBuilder()
      .setManagedInstanceInformation(new TelemetryData.ManagedInstanceInformation(isManaged, provider))
      .build();

    String json = writeTelemetryData(data);

    if (isManaged) {
      assertJson(json).isSimilarTo("""
        {
        "managedInstanceInformation": {
          "isManaged": true,
            "provider": "%s"
          }
        }
        """.formatted(provider));
    } else {
      assertJson(json).isSimilarTo("""
        {
        "managedInstanceInformation": {
          "isManaged": false
          }
        }
        """);
    }
  }

  @Test
  public void writes_has_unanalyzed_languages() {
    TelemetryData data = telemetryBuilder()
      .setHasUnanalyzedC(true)
      .setHasUnanalyzedCpp(false)
      .build();

    String json = writeTelemetryData(data);

    assertJson(json).isSimilarTo("""
      {
        "hasUnanalyzedC": true,
        "hasUnanalyzedCpp": false,
      }
      """);
  }

  @Test
  public void writes_security_custom_config() {
    TelemetryData data = telemetryBuilder()
      .setCustomSecurityConfigs(Set.of("php", "java"))
      .build();

    String json = writeTelemetryData(data);

    assertJson(json).isSimilarTo("""
      {
        "customSecurityConfig": ["php", "java"]
      }
      """);
  }

  @Test
  public void writes_local_timestamp() {
    when(system2.now()).thenReturn(1000L);

    TelemetryData data = telemetryBuilder().build();
    String json = writeTelemetryData(data);

    assertJson(json).isSimilarTo("""
      {
        "localTimestamp": "1970-01-01T00:00:01+0000"
      }
      """);
  }

  @Test
  public void writes_all_users_with_anonymous_md5_uuids() {
    TelemetryData data = telemetryBuilder()
      .setUsers(attachUsers())
      .build();

    String json = writeTelemetryData(data);

    assertJson(json).isSimilarTo("""
      {
        "users": [
          {
            "userUuid": "%s",
            "status": "active",
            "identityProvider": "gitlab",
            "lastActivity": "1970-01-01T00:00:00+0000",
            "lastSonarlintActivity": "1970-01-01T00:00:00+0000",
            "managed": true
          },
          {
            "userUuid": "%s",
            "status": "inactive",
            "identityProvider": "gitlab",
            "lastActivity": "1970-01-01T00:00:00+0000",
            "lastSonarlintActivity": "1970-01-01T00:00:00+0000",
            "managed": false
          },
          {
            "userUuid": "%s",
            "status": "active",
            "identityProvider": "gitlab",
            "lastActivity": "1970-01-01T00:00:00+0000",
            "lastSonarlintActivity": "1970-01-01T00:00:00+0000",
            "managed": true
          }
        ]
      }
      """
      .formatted(DigestUtils.sha3_224Hex("uuid-0"), DigestUtils.sha3_224Hex("uuid-1"), DigestUtils.sha3_224Hex("uuid-2")));
  }

  @Test
  public void writes_all_projects() {
    TelemetryData data = telemetryBuilder()
      .setProjects(attachProjects())
      .build();

    String json = writeTelemetryData(data);

    assertJson(json).isSimilarTo("""
      {
        "projects": [
          {
            "projectUuid": "uuid-0",
            "lastAnalysis": "1970-01-01T00:00:00+0000",
            "language": "lang-0",
            "loc": 2
          },
          {
            "projectUuid": "uuid-1",
            "lastAnalysis": "1970-01-01T00:00:00+0000",
            "language": "lang-1",
            "loc": 4
          },
          {
            "projectUuid": "uuid-2",
            "lastAnalysis": "1970-01-01T00:00:00+0000",
            "language": "lang-2",
            "loc": 6
          }
        ]
      }
      """);
  }

  @Test
  public void writeTelemetryData_whenAnalyzedLanguages_shouldwriteAllProjectsStats() {
    TelemetryData data = telemetryBuilder()
      .setProjectStatistics(attachProjectStatsWithMetrics())
      .build();

    String json = writeTelemetryData(data);

    assertJson(json).isSimilarTo("""
      {
        "projects-general-stats": [
          {
            "projectUuid": "uuid-0",
            "branchCount": 2,
            "pullRequestCount": 2,
            "qualityGate": "qg-0",
            "scm": "scm-0",
            "ci": "ci-0",
            "devopsPlatform": "devops-0",
            "bugs": 2,
            "vulnerabilities": 3,
            "securityHotspots": 4,
            "technicalDebt": 60,
            "developmentCost": 30,
            "ncdId": 12345
          },
          {
            "projectUuid": "uuid-1",
            "branchCount": 4,
            "pullRequestCount": 4,
            "qualityGate": "qg-1",
            "scm": "scm-1",
            "ci": "ci-1",
            "devopsPlatform": "devops-1",
            "bugs": 4,
            "vulnerabilities": 6,
            "securityHotspots": 8,
            "technicalDebt": 120,
            "developmentCost": 60,
            "ncdId": 12345
          },
          {
            "projectUuid": "uuid-2",
            "branchCount": 6,
            "pullRequestCount": 6,
            "qualityGate": "qg-2",
            "scm": "scm-2",
            "ci": "ci-2",
            "devopsPlatform": "devops-2",
            "bugs": 6,
            "vulnerabilities": 9,
            "securityHotspots": 12,
            "technicalDebt": 180,
            "developmentCost": 90,
            "ncdId": 12345
          }
        ]
      }
      """
    );
  }

  @Test
  public void writes_all_projects_stats_with_unanalyzed_languages() {
    TelemetryData data = telemetryBuilder()
      .setProjectStatistics(attachProjectStats())
      .build();

    String json = writeTelemetryData(data);
    assertThat(json).doesNotContain("hasUnanalyzedC", "hasUnanalyzedCpp");
  }

  @Test
  public void writes_all_projects_stats_without_missing_metrics() {
    TelemetryData data = telemetryBuilder()
      .setProjectStatistics(attachProjectStats())
      .build();
    String json = writeTelemetryData(data);
    assertThat(json).doesNotContain("bugs", "vulnerabilities", "securityHotspots", "technicalDebt", "developmentCost");
  }

  @Test
  public void writes_all_quality_gates() {
    TelemetryData data = telemetryBuilder()
      .setQualityGates(attachQualityGates())
      .build();

    String json = writeTelemetryData(data);
    assertJson(json).isSimilarTo("""
      {
        "quality-gates": [
          {
            "uuid": "uuid-0",
            "caycStatus": "non-compliant"
          },
          {
            "uuid": "uuid-1",
            "caycStatus": "compliant"
          },
          {
            "uuid": "uuid-2",
            "caycStatus": "over-compliant"
          }
        ]
      }
      """
    );
  }

  @Test
  public void writes_all_branches() {
    TelemetryData data = telemetryBuilder()
      .setBranches(attachBranches())
      .build();

    String json = writeTelemetryData(data);
    assertJson(json).isSimilarTo("""
      {
        "branches": [
          {
            "projectUuid": "projectUuid1",
            "branchUuid": "branchUuid1",
            "ncdId": 12345,
            "greenQualityGateCount": 1,
            "analysisCount": 2,
            "excludeFromPurge": true
          },
          {
            "projectUuid": "projectUuid2",
            "branchUuid": "branchUuid2",
            "ncdId": 12345,
            "greenQualityGateCount": 0,
            "analysisCount": 2,
            "excludeFromPurge": true
          }
        ]
      }
      """);
  }

  @Test
  public void writes_new_code_definitions() {
    TelemetryData data = telemetryBuilder()
      .setNewCodeDefinitions(attachNewCodeDefinitions())
      .build();

    String json = writeTelemetryData(data);
    assertJson(json).isSimilarTo("""
      {
        "new-code-definitions": [
          {
            "ncdId": %s,
            "type": "%s",
            "value": "%s",
            "scope": "%s"
          },
          {
            "ncdId": %s,
            "type": "%s",
            "value": "%s",
            "scope": "%s"
          },
        ]

      }
      """.formatted(NCD_INSTANCE.hashCode(), NCD_INSTANCE.type(), NCD_INSTANCE.value(), NCD_INSTANCE.scope(), NCD_PROJECT.hashCode(),
      NCD_PROJECT.type(), NCD_PROJECT.value(), NCD_PROJECT.scope()));
  }

  @Test
  public void writes_instance_new_code_definition() {
    TelemetryData data = telemetryBuilder().build();

    String json = writeTelemetryData(data);
    assertThat(json).contains("ncdId");

  }

  private static TelemetryData.Builder telemetryBuilder() {
    return TelemetryData.builder()
      .setServerId("foo")
      .setVersion("bar")
      .setMessageSequenceNumber(1L)
      .setPlugins(Collections.emptyMap())
      .setManagedInstanceInformation(new TelemetryData.ManagedInstanceInformation(false, null))
      .setCloudUsage(new TelemetryData.CloudUsage(false))
      .setDatabase(new TelemetryData.Database("H2", "11"))
      .setNcdId(NCD_ID);
  }

  @NotNull
  private static List<UserTelemetryDto> attachUsers() {
    return IntStream.range(0, 3)
      .mapToObj(
        i -> new UserTelemetryDto().setUuid("uuid-" + i).setActive(i % 2 == 0).setLastConnectionDate(1L)
          .setLastSonarlintConnectionDate(2L).setExternalIdentityProvider("gitlab").setScimUuid(i % 2 == 0 ? "scim-uuid-" + i : null))
      .collect(Collectors.toList());
  }

  private static List<TelemetryData.Project> attachProjects() {
    return IntStream.range(0, 3).mapToObj(i -> new TelemetryData.Project("uuid-" + i, 1L, "lang-" + i, (i + 1L) * 2L)).collect(Collectors.toList());
  }

  private static List<TelemetryData.ProjectStatistics> attachProjectStatsWithMetrics() {
    return IntStream.range(0, 3).mapToObj(i -> getProjectStatisticsWithMetricBuilder(i).build()).toList();
  }

  private static List<TelemetryData.ProjectStatistics> attachProjectStats() {
    return IntStream.range(0, 3).mapToObj(i -> getProjectStatisticsBuilder(i).build()).toList();
  }

  private static TelemetryData.ProjectStatistics.Builder getProjectStatisticsBuilder(int i) {
    return new TelemetryData.ProjectStatistics.Builder()
      .setProjectUuid("uuid-" + i)
      .setBranchCount((i + 1L) * 2L)
      .setPRCount((i + 1L) * 2L)
      .setQG("qg-" + i).setCi("ci-" + i)
      .setScm("scm-" + i)
      .setDevops("devops-" + i)
      .setNcdId(NCD_ID);
  }

  private static TelemetryData.ProjectStatistics.Builder getProjectStatisticsWithMetricBuilder(int i) {
    return getProjectStatisticsBuilder(i)
      .setBugs((i + 1L) * 2)
      .setVulnerabilities((i + 1L) * 3)
      .setSecurityHotspots((i + 1L) * 4)
      .setDevelopmentCost((i + 1L) * 30d)
      .setTechnicalDebt((i + 1L) * 60d);
  }

  private List<TelemetryData.QualityGate> attachQualityGates() {
    return List.of(new TelemetryData.QualityGate("uuid-0", "non-compliant"),
      new TelemetryData.QualityGate("uuid-1", "compliant"),
      new TelemetryData.QualityGate("uuid-2", "over-compliant"));
  }

  private List<TelemetryData.Branch> attachBranches() {
    return List.of(new TelemetryData.Branch("projectUuid1", "branchUuid1", NCD_ID, 1, 2, true),
      new TelemetryData.Branch("projectUuid2", "branchUuid2", NCD_ID, 0, 2, true));
  }

  private List<TelemetryData.NewCodeDefinition> attachNewCodeDefinitions() {
    return List.of(NCD_INSTANCE, NCD_PROJECT);
  }

  @DataProvider
  public static Object[][] allEditions() {
    return Arrays.stream(EditionProvider.Edition.values())
      .map(t -> new Object[] {t})
      .toArray(Object[][]::new);
  }

  private String writeTelemetryData(TelemetryData data) {
    StringWriter jsonString = new StringWriter();
    try (JsonWriter json = JsonWriter.of(jsonString)) {
      underTest.writeTelemetryData(json, data);
    }
    return jsonString.toString();
  }

  @DataProvider
  public static Set<Boolean> getFeatureFlagEnabledStates() {
    return Set.of(true, false);
  }
}
