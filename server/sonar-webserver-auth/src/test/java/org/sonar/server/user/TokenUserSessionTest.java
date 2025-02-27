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
package org.sonar.server.user;

import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.utils.System2;
import org.sonar.db.DbClient;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.permission.GlobalPermission;
import org.sonar.db.user.UserDto;
import org.sonar.db.user.UserTokenDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.api.web.UserRole.SCAN;
import static org.sonar.db.user.TokenType.GLOBAL_ANALYSIS_TOKEN;
import static org.sonar.db.user.TokenType.PROJECT_ANALYSIS_TOKEN;
import static org.sonar.db.user.TokenType.USER_TOKEN;

public class TokenUserSessionTest {

  @Rule
  public final DbTester db = DbTester.create(System2.INSTANCE);
  private final DbClient dbClient = db.getDbClient();

  @Test
  public void token_can_be_retrieved_from_the_session() {
    ComponentDto project1 = db.components().insertPrivateProject().getMainBranchComponent();

    UserDto user = db.users().insertUser();

    db.users().insertProjectPermissionOnUser(user, SCAN, project1);

    TokenUserSession userSession = mockTokenUserSession(user);

    assertThat(userSession.getUserToken()).isNotNull();
    assertThat(userSession.getUserToken().getName()).isEqualTo("User Token");
    assertThat(userSession.getUserToken().getUserUuid()).isEqualTo("userUid");
    assertThat(userSession.getUserToken().getType()).isEqualTo("USER_TOKEN");
  }

  @Test
  public void test_hasProjectsPermission_for_UserToken() {
    ComponentDto project1 = db.components().insertPrivateProject().getMainBranchComponent();
    ComponentDto project2 = db.components().insertPrivateProject().getMainBranchComponent();

    UserDto user = db.users().insertUser();

    db.users().insertProjectPermissionOnUser(user, SCAN, project1);

    TokenUserSession userSession = mockTokenUserSession(user);

    assertThat(userSession.hasProjectUuidPermission(SCAN, project1.branchUuid())).isTrue();
    assertThat(userSession.hasProjectUuidPermission(SCAN, project2.branchUuid())).isFalse();
  }

  @Test
  public void test_hasProjectsPermission_for_ProjecAnalysisToken() {
    ComponentDto project1 = db.components().insertPrivateProject().getMainBranchComponent();
    ComponentDto project2 = db.components().insertPrivateProject().getMainBranchComponent();

    UserDto user = db.users().insertUser();

    db.users().insertProjectPermissionOnUser(user, SCAN, project1);
    db.users().insertProjectPermissionOnUser(user, SCAN, project2);

    TokenUserSession userSession = mockProjectAnalysisTokenUserSession(user,project1);

    assertThat(userSession.hasProjectUuidPermission(SCAN, project1.branchUuid())).isTrue();
    assertThat(userSession.hasProjectUuidPermission(SCAN, project2.branchUuid())).isFalse();
  }

  @Test
  public void test_hasProjectsPermission_for_ProjectAnalysisToken_with_global_permission() {
    ComponentDto project1 = db.components().insertPrivateProject().getMainBranchComponent();
    ComponentDto project2 = db.components().insertPrivateProject().getMainBranchComponent();

    UserDto user = db.users().insertUser();

    db.users().insertGlobalPermissionOnUser(user, GlobalPermission.SCAN);

    TokenUserSession userSession = mockProjectAnalysisTokenUserSession(user,project1);

    assertThat(userSession.hasProjectUuidPermission(SCAN, project1.branchUuid())).isTrue();
    assertThat(userSession.hasProjectUuidPermission(SCAN, project2.branchUuid())).isFalse();
  }

  @Test
  public void test_hasGlobalPermission_for_UserToken() {
    UserDto user = db.users().insertUser();
    db.users().insertGlobalPermissionOnUser(user, GlobalPermission.SCAN);

    TokenUserSession userSession = mockTokenUserSession(user);

    assertThat(userSession.hasPermission(GlobalPermission.SCAN)).isTrue();
  }

  @Test
  public void test_hasGlobalPermission_for_ProjecAnalysisToken() {
    ComponentDto project1 = db.components().insertPrivateProject().getMainBranchComponent();
    ComponentDto project2 = db.components().insertPrivateProject().getMainBranchComponent();

    UserDto user = db.users().insertUser();

    db.users().insertProjectPermissionOnUser(user, SCAN, project1);
    db.users().insertProjectPermissionOnUser(user, SCAN, project2);

    db.users().insertGlobalPermissionOnUser(user, GlobalPermission.SCAN);

    TokenUserSession userSession = mockProjectAnalysisTokenUserSession(user,project1);

    assertThat(userSession.hasPermission(GlobalPermission.SCAN)).isFalse();
  }

  @Test
  public void test_hasGlobalPermission_for_GlobalAnalysisToken() {
    ComponentDto project1 = db.components().insertPrivateProject().getMainBranchComponent();

    UserDto user = db.users().insertUser();

    db.users().insertGlobalPermissionOnUser(user, GlobalPermission.SCAN);

    TokenUserSession userSession = mockGlobalAnalysisTokenUserSession(user);

    assertThat(userSession.hasProjectUuidPermission(SCAN, project1.branchUuid())).isFalse();
    assertThat(userSession.hasPermission(GlobalPermission.SCAN)).isTrue();
  }

  @Test
  public void test_hasProvisionProjectsGlobalPermission_for_GlobalAnalysisToken_returnsTrueIfUserIsGranted() {
    UserDto user = db.users().insertUser();

    db.users().insertGlobalPermissionOnUser(user, GlobalPermission.SCAN);
    db.users().insertGlobalPermissionOnUser(user, GlobalPermission.PROVISION_PROJECTS);

    TokenUserSession userSession = mockGlobalAnalysisTokenUserSession(user);

    assertThat(userSession.hasPermission(GlobalPermission.PROVISION_PROJECTS)).isTrue();
  }

  @Test
  public void test_hasProvisionProjectsGlobalPermission_for_GlobalAnalysisToken_returnsFalseIfUserIsNotGranted() {
    UserDto user = db.users().insertUser();

    db.users().insertGlobalPermissionOnUser(user, GlobalPermission.SCAN);

    TokenUserSession userSession = mockGlobalAnalysisTokenUserSession(user);

    assertThat(userSession.hasPermission(GlobalPermission.PROVISION_PROJECTS)).isFalse();
  }

  @Test
  public void test_hasAdministerGlobalPermission_for_GlobalAnalysisToken_returnsFalse() {
    UserDto user = db.users().insertUser();

    db.users().insertGlobalPermissionOnUser(user, GlobalPermission.ADMINISTER);

    TokenUserSession userSession = mockGlobalAnalysisTokenUserSession(user);

    assertThat(userSession.hasPermission(GlobalPermission.ADMINISTER)).isFalse();
  }

  private TokenUserSession mockTokenUserSession(UserDto userDto) {
    return new TokenUserSession(dbClient, userDto, mockUserTokenDto());
  }

  private TokenUserSession mockProjectAnalysisTokenUserSession(UserDto userDto, ComponentDto componentDto) {
    return new TokenUserSession(dbClient, userDto, mockProjectAnalysisTokenDto(componentDto));
  }

  private TokenUserSession mockGlobalAnalysisTokenUserSession(UserDto userDto) {
    return new TokenUserSession(dbClient, userDto, mockGlobalAnalysisTokenDto());
  }

  private static UserTokenDto mockUserTokenDto() {
    UserTokenDto userTokenDto = new UserTokenDto();
    userTokenDto.setType(USER_TOKEN.name());
    userTokenDto.setName("User Token");
    userTokenDto.setUserUuid("userUid");
    return userTokenDto;
  }

  private static UserTokenDto mockProjectAnalysisTokenDto(ComponentDto componentDto) {
    UserTokenDto userTokenDto = new UserTokenDto();
    userTokenDto.setType(PROJECT_ANALYSIS_TOKEN.name());
    userTokenDto.setName("Project Analysis Token");
    userTokenDto.setUserUuid("userUid");
    userTokenDto.setProjectKey(componentDto.getKey());
    userTokenDto.setProjectName(componentDto.name());
    userTokenDto.setProjectUuid(componentDto.branchUuid());
    return userTokenDto;
  }

  private static UserTokenDto mockGlobalAnalysisTokenDto() {
    UserTokenDto userTokenDto = new UserTokenDto();
    userTokenDto.setType(GLOBAL_ANALYSIS_TOKEN.name());
    userTokenDto.setName("Global Analysis Token");
    userTokenDto.setUserUuid("userUid");
    return userTokenDto;
  }

}
