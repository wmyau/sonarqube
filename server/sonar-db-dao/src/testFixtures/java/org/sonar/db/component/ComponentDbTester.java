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
package org.sonar.db.component;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.sonar.api.resources.Qualifiers;
import org.sonar.api.utils.System2;
import org.sonar.core.util.Uuids;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.portfolio.PortfolioDto;
import org.sonar.db.portfolio.PortfolioProjectDto;
import org.sonar.db.project.ProjectDto;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Arrays.asList;
import static org.sonar.db.component.BranchType.BRANCH;
import static org.sonar.db.portfolio.PortfolioDto.SelectionMode.NONE;

public class ComponentDbTester {
  private final DbTester db;
  private final DbClient dbClient;
  private final DbSession dbSession;
  private final boolean useDifferentUuids;

  public ComponentDbTester(DbTester db) {
    this(db, false);
  }

  public ComponentDbTester(DbTester db, boolean useDifferentProjectUuids) {
    this.db = db;
    this.dbClient = db.getDbClient();
    this.dbSession = db.getSession();
    this.useDifferentUuids = useDifferentProjectUuids;
  }

  public SnapshotDto insertProjectAndSnapshot(ComponentDto component) {
    insertComponentAndBranchAndProject(component, null, defaults(), defaults(), defaults());
    return insertSnapshot(component);
  }

  public SnapshotDto insertPortfolioAndSnapshot(ComponentDto component) {
    dbClient.componentDao().insert(dbSession, component, true);
    return insertSnapshot(component);
  }

  public ComponentDto insertComponent(ComponentDto component) {
    return insertComponentImpl(component, null, defaults());
  }

  public ProjectData insertPrivateProject() {
    return insertComponentAndBranchAndProject(ComponentTesting.newPrivateProjectDto(), true,
      defaults(), defaults(), defaults());
  }

  public BranchDto getBranchDto(ComponentDto branch) {
    return db.getDbClient().branchDao().selectByUuid(dbSession, branch.uuid())
      .orElseThrow(() -> new IllegalStateException("Project has invalid configuration"));
  }

  public ProjectDto getProjectDtoByMainBranch(ComponentDto mainBranch) {
    return db.getDbClient().projectDao().selectByBranchUuid(dbSession, mainBranch.uuid())
      .orElseThrow(() -> new IllegalStateException("Project has invalid configuration"));
  }

  public ComponentDto getComponentDto(ProjectDto project) {
    return db.getDbClient().componentDao().selectByUuid(dbSession, project.getUuid())
      .orElseThrow(() -> new IllegalStateException("Can't find project"));
  }

  public ComponentDto getComponentDto(BranchDto branch) {
    return db.getDbClient().componentDao().selectByUuid(dbSession, branch.getUuid())
      .orElseThrow(() -> new IllegalStateException("Can't find branch"));
  }

  public ProjectData insertPrivateProject(ComponentDto componentDto) {
    return insertComponentAndBranchAndProject(componentDto, true);
  }

  public ProjectData insertPublicProject() {
    return insertComponentAndBranchAndProject(ComponentTesting.newPublicProjectDto(), false);
  }

  public ProjectData insertPublicProject(String uuid) {
    if (useDifferentUuids) {
      return insertComponentAndBranchAndProject(ComponentTesting.newPublicProjectDto(), false, defaults(), defaults(), p -> p.setUuid(uuid));
    } else {
      return insertComponentAndBranchAndProject(ComponentTesting.newPublicProjectDto(uuid), false);
    }
  }

  public ProjectData insertPublicProject(String uuid, Consumer<ComponentDto> dtoPopulator) {
    if (useDifferentUuids) {
      return insertComponentAndBranchAndProject(ComponentTesting.newPublicProjectDto(), false, defaults(), dtoPopulator, p -> p.setUuid(uuid));
    } else {
      return insertComponentAndBranchAndProject(ComponentTesting.newPublicProjectDto(uuid), false, defaults(), dtoPopulator);
    }
  }

  public ProjectData insertPublicProject(ComponentDto componentDto) {
    return insertComponentAndBranchAndProject(componentDto, false);
  }

  public ProjectData insertPrivateProject(String uuid) {
    if (useDifferentUuids) {
      return insertComponentAndBranchAndProject(ComponentTesting.newPrivateProjectDto(), true, defaults(), defaults(), p -> p.setUuid(uuid));
    } else {
      return insertComponentAndBranchAndProject(ComponentTesting.newPrivateProjectDto(uuid), true);
    }
  }

  public final ProjectData insertPrivateProject(Consumer<ComponentDto> dtoPopulator) {
    return insertComponentAndBranchAndProject(ComponentTesting.newPrivateProjectDto(), true, defaults(), dtoPopulator);
  }

  public final ProjectData insertPrivateProject(Consumer<ComponentDto> componentDtoPopulator, Consumer<ProjectDto> projectDtoPopulator) {
    return insertComponentAndBranchAndProject(ComponentTesting.newPrivateProjectDto(),
      true, defaults(), componentDtoPopulator, projectDtoPopulator);
  }

  public final ProjectData insertPublicProject(Consumer<ComponentDto> dtoPopulator) {
    return insertComponentAndBranchAndProject(ComponentTesting.newPublicProjectDto(), false, defaults(), dtoPopulator);
  }

  public final ProjectData insertPublicProject(Consumer<ComponentDto> componentDtoPopulator, Consumer<ProjectDto> projectDtoPopulator) {
    return insertComponentAndBranchAndProject(ComponentTesting.newPublicProjectDto(), false, defaults(), componentDtoPopulator, projectDtoPopulator);
  }

  public ProjectData insertPrivateProject(Consumer<BranchDto> branchPopulator, Consumer<ComponentDto> componentDtoPopulator, Consumer<ProjectDto> projectDtoPopulator) {
    return insertPrivateProjectWithCustomBranch(branchPopulator, componentDtoPopulator, projectDtoPopulator);
  }

  public final ComponentDto insertFile(ProjectDto project) {
    ComponentDto projectComponent = getComponentDto(project);
    return insertComponent(ComponentTesting.newFileDto(projectComponent));
  }

  public final ComponentDto insertFile(BranchDto branch) {
    ComponentDto projectComponent = getComponentDto(branch);
    return insertComponent(ComponentTesting.newFileDto(projectComponent));
  }

  public final ProjectData insertPrivateProject(String uuid, Consumer<ComponentDto> dtoPopulator) {
    return insertComponentAndBranchAndProject(ComponentTesting.newPrivateProjectDto(uuid), true, defaults(), dtoPopulator);
  }

  public final ProjectData insertPrivateProjectWithCustomBranch(String branchKey) {
    return insertPrivateProjectWithCustomBranch(b -> b.setBranchType(BRANCH).setKey(branchKey), defaults());
  }

  public final ProjectData insertPrivateProjectWithCustomBranch(Consumer<BranchDto> branchPopulator, Consumer<ComponentDto> componentPopulator) {
    return insertComponentAndBranchAndProject(ComponentTesting.newPrivateProjectDto(), true, branchPopulator, componentPopulator);
  }

  public final ProjectData insertPublicProjectWithCustomBranch(Consumer<BranchDto> branchPopulator, Consumer<ComponentDto> componentPopulator) {
    return insertComponentAndBranchAndProject(ComponentTesting.newPublicProjectDto(), false, branchPopulator, componentPopulator);
  }

  public final ProjectData insertPrivateProjectWithCustomBranch(Consumer<BranchDto> branchPopulator, Consumer<ComponentDto> componentPopulator,
    Consumer<ProjectDto> projectPopulator) {
    return insertComponentAndBranchAndProject(ComponentTesting.newPrivateProjectDto(), true, branchPopulator, componentPopulator, projectPopulator);
  }

  public final ComponentDto insertPublicPortfolio() {
    return insertComponentAndPortfolio(ComponentTesting.newPortfolio().setPrivate(false), false, defaults(), defaults());
  }

  public final ComponentDto insertPublicPortfolio(String uuid, Consumer<ComponentDto> dtoPopulator) {
    return insertComponentAndPortfolio(ComponentTesting.newPortfolio(uuid).setPrivate(false), false, dtoPopulator, defaults());
  }

  public final ComponentDto insertPublicPortfolio(Consumer<ComponentDto> dtoPopulator) {
    return insertComponentAndPortfolio(ComponentTesting.newPortfolio().setPrivate(false), false, dtoPopulator, defaults());
  }

  public final ComponentDto insertPublicPortfolio(Consumer<ComponentDto> dtoPopulator, Consumer<PortfolioDto> portfolioPopulator) {
    return insertComponentAndPortfolio(ComponentTesting.newPortfolio().setPrivate(false), false, dtoPopulator, portfolioPopulator);
  }

  public final PortfolioDto insertPublicPortfolioDto() {
    return insertPublicPortfolioDto(defaults());
  }

  public final PortfolioDto insertPublicPortfolioDto(Consumer<ComponentDto> dtoPopulator) {
    ComponentDto component = insertComponentAndPortfolio(ComponentTesting.newPortfolio().setPrivate(false), false, dtoPopulator, defaults());
    return getPortfolioDto(component);
  }

  public final PortfolioDto insertPrivatePortfolioDto(String uuid) {
    ComponentDto component = insertComponentAndPortfolio(ComponentTesting.newPortfolio(uuid).setPrivate(true), true, defaults(), defaults());
    return getPortfolioDto(component);
  }

  public final PortfolioDto insertPrivatePortfolioDto(String uuid, Consumer<PortfolioDto> portfolioPopulator) {
    ComponentDto component = insertComponentAndPortfolio(ComponentTesting.newPortfolio(uuid).setPrivate(true), true, defaults(), portfolioPopulator);
    return getPortfolioDto(component);
  }

  public final PortfolioDto insertPrivatePortfolioDto() {
    ComponentDto component = insertComponentAndPortfolio(ComponentTesting.newPortfolio().setPrivate(true), true, defaults(), defaults());
    return getPortfolioDto(component);
  }

  public final PortfolioDto insertPrivatePortfolioDto(Consumer<ComponentDto> dtoPopulator) {
    ComponentDto component = insertComponentAndPortfolio(ComponentTesting.newPortfolio().setPrivate(true), true, dtoPopulator, defaults());
    return getPortfolioDto(component);
  }

  public final PortfolioDto insertPrivatePortfolioDto(Consumer<ComponentDto> dtoPopulator, Consumer<PortfolioDto> portfolioPopulator) {
    ComponentDto component = insertComponentAndPortfolio(ComponentTesting.newPortfolio().setPrivate(true), true, dtoPopulator, portfolioPopulator);
    return getPortfolioDto(component);
  }

  public final PortfolioDto insertPublicPortfolioDto(String uuid, Consumer<ComponentDto> dtoPopulator) {
    ComponentDto component = insertComponentAndPortfolio(ComponentTesting.newPortfolio(uuid).setPrivate(false), false, dtoPopulator, defaults());
    return getPortfolioDto(component);
  }

  public final PortfolioDto insertPublicPortfolioDto(String uuid, Consumer<ComponentDto> dtoPopulator, Consumer<PortfolioDto> portfolioPopulator) {
    ComponentDto component = insertComponentAndPortfolio(ComponentTesting.newPortfolio(uuid).setPrivate(false), false, dtoPopulator, portfolioPopulator);
    return getPortfolioDto(component);
  }

  public final PortfolioDto insertPublicPortfolioDto(Consumer<ComponentDto> dtoPopulator, Consumer<PortfolioDto> portfolioPopulator) {
    ComponentDto component = insertComponentAndPortfolio(ComponentTesting.newPortfolio().setPrivate(false), false, dtoPopulator, portfolioPopulator);
    return getPortfolioDto(component);
  }

  public PortfolioDto getPortfolioDto(ComponentDto portfolio) {
    return db.getDbClient().portfolioDao().selectByUuid(dbSession, portfolio.uuid())
      .orElseThrow(() -> new IllegalStateException("Portfolio has invalid configuration"));
  }

  public ComponentDto insertComponentAndPortfolio(ComponentDto componentDto, boolean isPrivate, Consumer<ComponentDto> componentPopulator,
    Consumer<PortfolioDto> portfolioPopulator) {
    insertComponentImpl(componentDto, isPrivate, componentPopulator);

    PortfolioDto portfolioDto = toPortfolioDto(componentDto, System2.INSTANCE.now());
    portfolioPopulator.accept(portfolioDto);
    dbClient.portfolioDao().insert(dbSession, portfolioDto);
    db.commit();
    return componentDto;
  }

  public final ComponentDto insertPrivatePortfolio() {
    return insertComponentAndPortfolio(ComponentTesting.newPortfolio().setPrivate(true), true, defaults(), defaults());
  }

  public final ComponentDto insertPrivatePortfolio(String uuid, Consumer<ComponentDto> dtoPopulator) {
    return insertComponentAndPortfolio(ComponentTesting.newPortfolio(uuid).setPrivate(true), true, dtoPopulator, defaults());
  }

  public final ComponentDto insertPrivatePortfolio(Consumer<ComponentDto> dtoPopulator) {
    return insertComponentAndPortfolio(ComponentTesting.newPortfolio().setPrivate(true), true, dtoPopulator, defaults());
  }

  public final ComponentDto insertPrivatePortfolio(Consumer<ComponentDto> dtoPopulator, Consumer<PortfolioDto> portfolioPopulator) {
    return insertComponentAndPortfolio(ComponentTesting.newPortfolio().setPrivate(true), true, dtoPopulator, portfolioPopulator);
  }

  public final ComponentDto insertSubportfolio(ComponentDto parentPortfolio) {
    ComponentDto subPortfolioComponent = ComponentTesting.newSubPortfolio(parentPortfolio);
    return insertComponentAndPortfolio(subPortfolioComponent, true, defaults(), sp -> sp.setParentUuid(sp.getRootUuid()));
  }

  public void addPortfolioReference(String portfolioUuid, String... referencerUuids) {
    for (String uuid : referencerUuids) {
      dbClient.portfolioDao().addReference(dbSession, portfolioUuid, uuid);
    }
    db.commit();
  }

  public void addPortfolioReference(ComponentDto portfolio, String... referencerUuids) {
    addPortfolioReference(portfolio.uuid(), referencerUuids);
  }

  public void addPortfolioReference(PortfolioDto portfolio, String... referencerUuids) {
    addPortfolioReference(portfolio.getUuid(), referencerUuids);
  }

  public void addPortfolioReference(PortfolioDto portfolio, PortfolioDto reference) {
    addPortfolioReference(portfolio.getUuid(), reference.getUuid());
  }

  public void addPortfolioProject(String portfolioUuid, String... projectUuids) {
    for (String uuid : projectUuids) {
      dbClient.portfolioDao().addProject(dbSession, portfolioUuid, uuid);
    }
    db.commit();
  }

  public void addPortfolioProject(ComponentDto portfolio, String... projectUuids) {
    addPortfolioProject(portfolio.uuid(), projectUuids);
  }

  public void addPortfolioProject(ComponentDto portfolio, ComponentDto... mainBranches) {
    List<BranchDto> branchDtos = dbClient.branchDao().selectByUuids(db.getSession(), Arrays.stream(mainBranches).map(ComponentDto::uuid).toList());
    addPortfolioProject(portfolio, branchDtos.stream().map(BranchDto::getProjectUuid).toArray(String[]::new));
  }

  public void addPortfolioProject(PortfolioDto portfolioDto, ProjectDto... projects) {
    for (ProjectDto project : projects) {
      dbClient.portfolioDao().addProject(dbSession, portfolioDto.getUuid(), project.getUuid());
    }
    db.commit();
  }

  public void addPortfolioProjectBranch(PortfolioDto portfolio, ProjectDto project, String branchUuid) {
    addPortfolioProjectBranch(portfolio, project.getUuid(), branchUuid);
  }

  public void addPortfolioProjectBranch(PortfolioDto portfolio, String projectUuid, String branchUuid) {
    addPortfolioProjectBranch(portfolio.getUuid(), projectUuid, branchUuid);
  }

  public void addPortfolioProjectBranch(String portfolioUuid, String projectUuid, String branchUuid) {
    PortfolioProjectDto portfolioProject = dbClient.portfolioDao().selectPortfolioProjectOrFail(dbSession, portfolioUuid, projectUuid);
    dbClient.portfolioDao().addBranch(db.getSession(), portfolioProject.getUuid(), branchUuid);
    db.commit();
  }

  public final ProjectData insertPublicApplication() {
    return insertPublicApplication(defaults());
  }

  public final ProjectData insertPublicApplication(Consumer<ComponentDto> dtoPopulator) {
    return insertComponentAndBranchAndProject(ComponentTesting.newApplication().setPrivate(false), false, defaults(), dtoPopulator);
  }

  public final ProjectData insertPrivateApplication(String uuid, Consumer<ComponentDto> dtoPopulator) {
    return insertPrivateApplication(dtoPopulator, p -> p.setUuid(uuid));
  }

  public final ProjectData insertPrivateApplication(String uuid) {
    return insertPrivateApplication(defaults(), p -> p.setUuid(uuid));
  }

  public final ProjectData insertPrivateApplication(Consumer<ComponentDto> dtoPopulator) {
    return insertPrivateApplication(dtoPopulator, defaults());
  }

  public final ProjectData insertPrivateApplication() {
    return insertPrivateApplication(defaults(), defaults());
  }

  public final ProjectData insertPrivateApplication(Consumer<ComponentDto> dtoPopulator, Consumer<ProjectDto> projectPopulator) {
    return insertComponentAndBranchAndProject(ComponentTesting.newApplication().setPrivate(true), true, defaults(), dtoPopulator, projectPopulator);
  }

  public final ComponentDto insertSubView(ComponentDto view) {
    return insertSubView(view, defaults());
  }

  public final ComponentDto insertSubView(ComponentDto view, Consumer<ComponentDto> dtoPopulator) {
    ComponentDto subViewComponent = ComponentTesting.newSubPortfolio(view);
    return insertComponentAndPortfolio(subViewComponent, view.isPrivate(), dtoPopulator, p -> p.setParentUuid(view.uuid()));
  }

  public void addPortfolioApplicationBranch(String portfolioUuid, String applicationUuid, String branchUuid) {
    dbClient.portfolioDao().addReferenceBranch(db.getSession(), portfolioUuid, applicationUuid, branchUuid);
    db.commit();
  }

  private ProjectData insertComponentAndBranchAndProject(ComponentDto component, @Nullable Boolean isPrivate, Consumer<BranchDto> branchPopulator,
    Consumer<ComponentDto> componentDtoPopulator, Consumer<ProjectDto> projectDtoPopulator) {
    insertComponentImpl(component, isPrivate, componentDtoPopulator);

    ProjectDto projectDto = toProjectDto(component, System2.INSTANCE.now(), useDifferentUuids);
    projectDtoPopulator.accept(projectDto);
    dbClient.projectDao().insert(dbSession, projectDto);

    BranchDto branchDto = ComponentTesting.newMainBranchDto(component, projectDto.getUuid());
    branchDto.setExcludeFromPurge(true);
    branchPopulator.accept(branchDto);
    branchDto.setIsMain(true);
    dbClient.branchDao().insert(dbSession, branchDto);

    db.commit();
    return new ProjectData(getProjectDtoByMainBranch(component), branchDto, component);
  }

  public void addApplicationProject(ProjectDto application, ProjectDto... projects) {
    for (ProjectDto project : projects) {
      dbClient.applicationProjectsDao().addProject(dbSession, application.getUuid(), project.getUuid());
    }
    db.commit();
  }

  public void addApplicationProject(ProjectData application, ProjectData... projects) {
    for (ProjectData project : projects) {
      dbClient.applicationProjectsDao().addProject(dbSession, application.getProjectDto().getUuid(), project.getProjectDto().getUuid());
    }
    db.commit();
  }

  public void addProjectBranchToApplicationBranch(ComponentDto applicationBranchComponent, ComponentDto... projectBranchesComponent) {
    BranchDto applicationBranch = getBranchDto(applicationBranchComponent);
    BranchDto[] componentDtos = Arrays.stream(projectBranchesComponent).map(this::getBranchDto).toArray(BranchDto[]::new);

    addProjectBranchToApplicationBranch(applicationBranch, componentDtos);
  }

  public void addProjectBranchToApplicationBranch(BranchDto applicationBranch, BranchDto... projectBranches) {
    for (BranchDto projectBranch : projectBranches) {
      dbClient.applicationProjectsDao().addProjectBranchToAppBranch(dbSession, applicationBranch, projectBranch);
    }
    db.commit();
  }

  private ProjectData insertComponentAndBranchAndProject(ComponentDto component, @Nullable Boolean isPrivate, Consumer<BranchDto> branchPopulator,
    Consumer<ComponentDto> componentDtoPopulator) {
    return insertComponentAndBranchAndProject(component, isPrivate, branchPopulator, componentDtoPopulator, defaults());
  }

  private ProjectData insertComponentAndBranchAndProject(ComponentDto component, @Nullable Boolean isPrivate, Consumer<BranchDto> branchPopulator) {
    return insertComponentAndBranchAndProject(component, isPrivate, branchPopulator, defaults());
  }

  private ProjectData insertComponentAndBranchAndProject(ComponentDto component, @Nullable Boolean isPrivate) {
    return insertComponentAndBranchAndProject(component, isPrivate, defaults());
  }

  private ComponentDto insertComponentImpl(ComponentDto component, @Nullable Boolean isPrivate, Consumer<ComponentDto> dtoPopulator) {
    dtoPopulator.accept(component);
    checkState(isPrivate == null || component.isPrivate() == isPrivate, "Illegal modification of private flag");
    dbClient.componentDao().insert(dbSession, component, true);
    db.commit();

    return component;
  }

  public void insertComponents(ComponentDto... components) {
    dbClient.componentDao().insert(dbSession, asList(components), true);
    db.commit();
  }

  public SnapshotDto insertSnapshot(SnapshotDto snapshotDto) {
    SnapshotDto snapshot = dbClient.snapshotDao().insert(dbSession, snapshotDto);
    db.commit();
    return snapshot;
  }

  public SnapshotDto insertSnapshot(ComponentDto componentDto) {
    return insertSnapshot(componentDto, defaults());
  }

  public SnapshotDto insertSnapshot(ComponentDto componentDto, Consumer<SnapshotDto> consumer) {
    SnapshotDto snapshotDto = SnapshotTesting.newAnalysis(componentDto);
    consumer.accept(snapshotDto);
    return insertSnapshot(snapshotDto);
  }

  public SnapshotDto insertSnapshot(BranchDto branchDto) {
    return insertSnapshot(branchDto, defaults());
  }

  public SnapshotDto insertSnapshot(ProjectDto project) {
    return insertSnapshot(project, defaults());
  }

  public SnapshotDto insertSnapshot(ProjectData project, Consumer<SnapshotDto> consumer) {
    return insertSnapshot(project.getProjectDto(), consumer);
  }

  public SnapshotDto insertSnapshot(ProjectDto project, Consumer<SnapshotDto> consumer) {
    SnapshotDto snapshotDto = SnapshotTesting.newAnalysis(project.getUuid());
    consumer.accept(snapshotDto);
    return insertSnapshot(snapshotDto);
  }

  public SnapshotDto insertSnapshot(BranchDto branchDto, Consumer<SnapshotDto> consumer) {
    SnapshotDto snapshotDto = SnapshotTesting.newAnalysis(branchDto);
    consumer.accept(snapshotDto);
    return insertSnapshot(snapshotDto);
  }

  public void insertSnapshots(SnapshotDto... snapshotDtos) {
    dbClient.snapshotDao().insert(dbSession, asList(snapshotDtos));
    db.commit();
  }

  @SafeVarargs
  public final ComponentDto insertProjectBranch(ComponentDto mainBranchComponent, Consumer<BranchDto>... dtoPopulators) {
    BranchDto mainBranch = dbClient.branchDao().selectByUuid(db.getSession(), mainBranchComponent.branchUuid()).orElseThrow(IllegalArgumentException::new);
    BranchDto branchDto = ComponentTesting.newBranchDto(mainBranch.getProjectUuid(), BRANCH);
    Arrays.stream(dtoPopulators).forEach(dtoPopulator -> dtoPopulator.accept(branchDto));
    return insertProjectBranch(mainBranchComponent, branchDto);
  }

  @SafeVarargs
  public final BranchDto insertProjectBranch(ProjectDto project, Consumer<BranchDto>... dtoPopulators) {
    BranchDto branchDto = ComponentTesting.newBranchDto(project.getUuid(), BRANCH);
    Arrays.stream(dtoPopulators).forEach(dtoPopulator -> dtoPopulator.accept(branchDto));
    insertProjectBranch(project, branchDto);
    return branchDto;
  }

  public final ComponentDto insertProjectBranch(ProjectDto project, BranchDto branchDto) {
    checkArgument(branchDto.getProjectUuid().equals(project.getUuid()));
    ComponentDto branch = ComponentTesting.newBranchComponent(project, branchDto);
    insertComponent(branch);
    dbClient.branchDao().insert(dbSession, branchDto);
    db.commit();
    return branch;
  }

  public final ComponentDto insertProjectBranch(ComponentDto project, BranchDto branchDto) {
    ComponentDto branch = ComponentTesting.newBranchComponent(project, branchDto);
    insertComponent(branch);
    dbClient.branchDao().insert(dbSession, branchDto);
    db.commit();
    return branch;
  }

  // TODO temporary constructor to quickly create project from previous project component.
  public static ProjectDto toProjectDto(ComponentDto componentDto, long createTime) {
    return toProjectDto(componentDto, createTime, false);
  }

  public static ProjectDto toProjectDto(ComponentDto componentDto, long createTime, boolean useDifferentProjectUuids) {
    return new ProjectDto()
      .setUuid(useDifferentProjectUuids ? Uuids.createFast() : componentDto.uuid())
      .setKey(componentDto.getKey())
      .setQualifier(componentDto.qualifier() != null ? componentDto.qualifier() : Qualifiers.PROJECT)
      .setCreatedAt(createTime)
      .setUpdatedAt(createTime)
      .setPrivate(componentDto.isPrivate())
      .setDescription(componentDto.description())
      .setName(componentDto.name());
  }

  public static PortfolioDto toPortfolioDto(ComponentDto componentDto, long createTime) {
    return new PortfolioDto()
      .setUuid(componentDto.uuid())
      .setKey(componentDto.getKey())
      .setRootUuid(componentDto.branchUuid())
      .setSelectionMode(NONE.name())
      .setCreatedAt(createTime)
      .setUpdatedAt(createTime)
      .setPrivate(componentDto.isPrivate())
      .setDescription(componentDto.description())
      .setName(componentDto.name());
  }

  public static <T> Consumer<T> defaults() {
    return t -> {
    };
  }
}
