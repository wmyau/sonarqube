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
package org.sonar.server.component.index;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.sonar.core.util.stream.MoreCollectors;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.entity.EntityDto;
import org.sonar.db.es.EsQueueDto;
import org.sonar.server.es.BaseDoc;
import org.sonar.server.es.BulkIndexer;
import org.sonar.server.es.BulkIndexer.Size;
import org.sonar.server.es.EsClient;
import org.sonar.server.es.IndexType;
import org.sonar.server.es.IndexingResult;
import org.sonar.server.es.OneToManyResilientIndexingListener;
import org.sonar.server.es.ProjectIndexer;
import org.sonar.server.permission.index.AuthorizationDoc;
import org.sonar.server.permission.index.AuthorizationScope;
import org.sonar.server.permission.index.NeedAuthorizationIndexer;

import static java.util.Collections.emptyList;
import static org.sonar.server.component.index.ComponentIndexDefinition.TYPE_COMPONENT;

public class ComponentIndexer implements ProjectIndexer, NeedAuthorizationIndexer {

  private static final AuthorizationScope AUTHORIZATION_SCOPE = new AuthorizationScope(TYPE_COMPONENT, project -> true);
  private static final ImmutableSet<IndexType> INDEX_TYPES = ImmutableSet.of(TYPE_COMPONENT);

  private final DbClient dbClient;
  private final EsClient esClient;

  public ComponentIndexer(DbClient dbClient, EsClient esClient) {
    this.dbClient = dbClient;
    this.esClient = esClient;
  }

  @Override
  public Set<IndexType> getIndexTypes() {
    return INDEX_TYPES;
  }

  @Override
  public void indexOnStartup(Set<IndexType> uninitializedIndexTypes) {
    doIndexByProjectUuid(Size.LARGE);
  }

  public void indexAll() {
    doIndexByProjectUuid(Size.REGULAR);
  }

  @Override
  public void indexOnAnalysis(String branchUuid) {
    indexOnAnalysis(branchUuid, Set.of());
  }

  @Override
  public void indexOnAnalysis(String entityUuid, Set<String> unchangedComponentUuids) {
    doIndexByProjectUuid(entityUuid);
  }

  @Override
  public AuthorizationScope getAuthorizationScope() {
    return AUTHORIZATION_SCOPE;
  }

  @Override
  public Collection<EsQueueDto> prepareForRecovery(DbSession dbSession, Collection<String> projectUuids, Cause cause) {
    switch (cause) {
      case MEASURE_CHANGE, PROJECT_TAGS_UPDATE, PERMISSION_CHANGE:
        // measures, tags and permissions are not part of type components/component
        return emptyList();
      case PROJECT_CREATION, PROJECT_DELETION, PROJECT_KEY_UPDATE:
        List<EsQueueDto> items = projectUuids.stream()
          .map(projectUuid -> EsQueueDto.create(TYPE_COMPONENT.format(), projectUuid, null, projectUuid))
          .collect(MoreCollectors.toArrayList(projectUuids.size()));
        return dbClient.esQueueDao().insert(dbSession, items);
      default:
        // defensive case
        throw new IllegalStateException("Unsupported cause: " + cause);
    }
  }

  @Override
  public IndexingResult index(DbSession dbSession, Collection<EsQueueDto> items) {
    if (items.isEmpty()) {
      return new IndexingResult();
    }

    OneToManyResilientIndexingListener listener = new OneToManyResilientIndexingListener(dbClient, dbSession, items);
    BulkIndexer bulkIndexer = new BulkIndexer(esClient, TYPE_COMPONENT, Size.REGULAR, listener);
    bulkIndexer.start();
    Set<String> entityUuids = items.stream().map(EsQueueDto::getDocId).collect(MoreCollectors.toHashSet(items.size()));
    Set<String> remaining = new HashSet<>(entityUuids);

    for (String entityUuid : entityUuids) {
      dbClient.entityDao().scrollForIndexing(dbSession, entityUuid, context -> {
        EntityDto dto = context.getResultObject();
        remaining.remove(dto.getUuid());
        bulkIndexer.add(toDocument(dto).toIndexRequest());
      });
    }

    // the remaining uuids reference projects that don't exist in db. They must
    // be deleted from index.
    remaining.forEach(projectUuid -> addProjectDeletionToBulkIndexer(bulkIndexer, projectUuid));

    return bulkIndexer.stop();
  }

  /**
   * @param entityUuid the uuid of the project to analyze, or {@code null} if all content should be indexed.<br/>
   *                   <b>Warning:</b> only use {@code null} during startup.
   */
  private void doIndexByProjectUuid(String entityUuid) {
    BulkIndexer bulk = new BulkIndexer(esClient, TYPE_COMPONENT, Size.REGULAR);
    bulk.start();

    try (DbSession dbSession = dbClient.openSession(false)) {
      Optional<EntityDto> entityDto = dbClient.entityDao().selectByUuid(dbSession, entityUuid);

      if (entityDto.isEmpty()) {
        return;
      }
      EntityDto entity = entityDto.get();

      bulk.add(toDocument(entity).toIndexRequest());

      if (entity.getQualifier().equals("VW")) {
        dbClient.portfolioDao().selectTree(dbSession, entityUuid).forEach(sub ->
          bulk.add(toDocument(sub).toIndexRequest()));
      }
    }

    bulk.stop();
  }

  private void doIndexByProjectUuid(Size bulkSize) {
    BulkIndexer bulk = new BulkIndexer(esClient, TYPE_COMPONENT, bulkSize);
    bulk.start();
    try (DbSession dbSession = dbClient.openSession(false)) {
      dbClient.entityDao().scrollForIndexing(dbSession, null, context -> {
        EntityDto dto = context.getResultObject();
        bulk.add(toDocument(dto).toIndexRequest());
      });
    }

    bulk.stop();
  }

  private static void addProjectDeletionToBulkIndexer(BulkIndexer bulkIndexer, String projectUuid) {
    SearchRequest searchRequest = EsClient.prepareSearch(TYPE_COMPONENT.getMainType())
      .source(new SearchSourceBuilder().query(QueryBuilders.termQuery(ComponentIndexDefinition.FIELD_UUID, projectUuid)))
      .routing(AuthorizationDoc.idOf(projectUuid));
    bulkIndexer.addDeletion(searchRequest);
  }

  public void delete(String projectUuid, Collection<String> disabledComponentUuids) {
    BulkIndexer bulk = new BulkIndexer(esClient, TYPE_COMPONENT, Size.REGULAR);
    bulk.start();
    disabledComponentUuids.forEach(uuid -> bulk.addDeletion(TYPE_COMPONENT, uuid, AuthorizationDoc.idOf(projectUuid)));
    bulk.stop();
  }

  @VisibleForTesting
  void index(EntityDto... docs) {
    BulkIndexer bulk = new BulkIndexer(esClient, TYPE_COMPONENT, Size.REGULAR);
    bulk.start();
    Arrays.stream(docs)
      .map(ComponentIndexer::toDocument)
      .map(BaseDoc::toIndexRequest)
      .forEach(bulk::add);
    bulk.stop();
  }

  public static ComponentDoc toDocument(EntityDto component) {
    return new ComponentDoc()
      .setId(component.getUuid())
      .setAuthUuid(component.getAuthUuid())
      .setName(component.getName())
      .setKey(component.getKey())
      .setQualifier(component.getQualifier());
  }
}
