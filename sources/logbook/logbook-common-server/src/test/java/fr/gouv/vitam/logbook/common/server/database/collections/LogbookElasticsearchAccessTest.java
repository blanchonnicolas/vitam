/*
 * Copyright French Prime minister Office/SGMAP/DINSIC/Vitam Program (2015-2022)
 *
 * contact.vitam@culture.gouv.fr
 *
 * This software is a computer program whose purpose is to implement a digital archiving back-office system managing
 * high volumetry securely and efficiently.
 *
 * This software is governed by the CeCILL 2.1 license under French law and abiding by the rules of distribution of free
 * software. You can use, modify and/ or redistribute the software under the terms of the CeCILL 2.1 license as
 * circulated by CEA, CNRS and INRIA at the following URL "https://cecill.info".
 *
 * As a counterpart to the access to the source code and rights to copy, modify and redistribute granted by the license,
 * users are provided only with a limited warranty and the software's author, the holder of the economic rights, and the
 * successive licensors have only limited liability.
 *
 * In this respect, the user's attention is drawn to the risks associated with loading, using, modifying and/or
 * developing or reproducing the software by the user in light of its specific status of free software, that may mean
 * that it is complicated to manipulate, and that also therefore means that it is reserved for developers and
 * experienced professionals having in-depth computer knowledge. Users are therefore encouraged to load and test the
 * software's suitability as regards their requirements in conditions enabling the security of their systems and/or data
 * to be ensured and, more generally, to use and operate it in the same conditions as regards security.
 *
 * The fact that you are presently reading this means that you have had knowledge of the CeCILL 2.1 license and that you
 * accept its terms.
 */
package fr.gouv.vitam.logbook.common.server.database.collections;

import com.google.common.collect.Lists;
import fr.gouv.vitam.common.LocalDateUtil;
import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.database.server.elasticsearch.ElasticsearchNode;
import fr.gouv.vitam.common.database.server.mongodb.BsonHelper;
import fr.gouv.vitam.common.database.server.mongodb.MongoDbAccess;
import fr.gouv.vitam.common.database.server.mongodb.VitamDocument;
import fr.gouv.vitam.common.elasticsearch.ElasticsearchRule;
import fr.gouv.vitam.common.guid.GUID;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.mongo.MongoRule;
import fr.gouv.vitam.common.thread.RunWithCustomExecutor;
import fr.gouv.vitam.common.thread.RunWithCustomExecutorRule;
import fr.gouv.vitam.common.thread.VitamThreadPoolExecutor;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.logbook.common.parameters.LogbookOperationParameters;
import fr.gouv.vitam.logbook.common.parameters.LogbookParameterHelper;
import fr.gouv.vitam.logbook.common.parameters.LogbookParameterName;
import fr.gouv.vitam.logbook.common.parameters.LogbookTypeProcess;
import fr.gouv.vitam.logbook.common.server.config.ElasticsearchLogbookIndexManager;
import fr.gouv.vitam.logbook.common.server.exception.LogbookException;
import net.javacrumbs.jsonunit.JsonAssert;
import org.bson.Document;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LogbookElasticsearchAccessTest {

    private static final String PREFIX = GUIDFactory.newGUID().getId();

    @ClassRule
    public static MongoRule mongoRule = new MongoRule(MongoDbAccess.getMongoClientSettingsBuilder());

    @ClassRule
    public static ElasticsearchRule elasticsearchRule = new ElasticsearchRule();

    @Rule
    public RunWithCustomExecutorRule runInThread = new RunWithCustomExecutorRule(
        VitamThreadPoolExecutor.getDefaultExecutor()
    );

    @ClassRule
    public static TemporaryFolder tempFolder = new TemporaryFolder();

    private static LogbookElasticsearchAccess esClient;

    private static final int tenantId = 0;
    private static final ElasticsearchLogbookIndexManager indexManager =
        LogbookCollectionsTestUtils.createTestIndexManager(Collections.singletonList(tenantId), Collections.emptyMap());

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        List<ElasticsearchNode> esNodes = Lists.newArrayList(
            new ElasticsearchNode(ElasticsearchRule.getHost(), ElasticsearchRule.getPort())
        );

        LogbookCollectionsTestUtils.beforeTestClass(
            mongoRule.getMongoDatabase(),
            PREFIX,
            new LogbookElasticsearchAccess(ElasticsearchRule.VITAM_CLUSTER, esNodes, indexManager)
        );
        esClient = new LogbookElasticsearchAccess(ElasticsearchRule.VITAM_CLUSTER, esNodes, indexManager);
        VitamConfiguration.setAdminTenant(tenantId);
    }

    @AfterClass
    public static void tearDownAfterClass() {
        LogbookCollectionsTestUtils.afterTestClass(indexManager, true);
        esClient.close();
    }

    @Before
    public void setUp() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(tenantId);
    }

    @Test
    @RunWithCustomExecutor
    public void testElasticsearchAccessOperation() throws Exception {
        // data
        GUID eventIdentifier = GUIDFactory.newEventGUID(tenantId);
        String eventType = "IMPORT_FORMAT";
        GUID eventIdentifierProcess = GUIDFactory.newEventGUID(tenantId);
        LogbookTypeProcess eventTypeProcess = LogbookTypeProcess.MASTERDATA;
        StatusCode outcome = StatusCode.STARTED;
        String outcomeDetailMessage = "IMPORT_FORMAT." + StatusCode.STARTED.name();
        GUID eventIdentifierRequest = GUIDFactory.newEventGUID(tenantId);

        // add indexEntry
        final LogbookOperationParameters parametersForCreation = LogbookParameterHelper.newLogbookOperationParameters(
            eventIdentifier,
            eventType,
            eventIdentifierProcess,
            eventTypeProcess,
            outcome,
            outcomeDetailMessage,
            eventIdentifierRequest
        );
        for (final LogbookParameterName name : LogbookParameterName.values()) {
            if (LogbookParameterName.eventDateTime.equals(name)) {
                parametersForCreation.putParameterValue(name, LocalDateUtil.nowFormatted());
            } else if (LogbookParameterName.parentEventIdentifier.equals(name)) {
                parametersForCreation.putParameterValue(name, null);
            } else {
                parametersForCreation.putParameterValue(name, GUIDFactory.newEventGUID(tenantId).getId());
            }
        }

        LogbookOperation operationForCreation = new LogbookOperation(parametersForCreation, false);
        String id = operationForCreation.getId();
        operationForCreation.remove(VitamDocument.ID);
        operationForCreation.put(VitamDocument.TENANT_ID, tenantId);
        final String esJson = BsonHelper.stringify(operationForCreation);
        assertTrue(esJson.contains(LogbookMongoDbName.parentEventIdentifier.getDbname()));
        JsonAssert.assertJsonEquals(operationForCreation, esJson);
        esClient.indexEntry(LogbookCollections.OPERATION, tenantId, id, operationForCreation);
        esClient.refreshIndex(LogbookCollections.OPERATION, tenantId);
        // check entry
        QueryBuilder query = QueryBuilders.matchAllQuery();
        SearchResponse elasticSearchResponse = esClient.search(
            LogbookCollections.OPERATION,
            tenantId,
            query,
            null,
            null,
            0,
            10
        );

        assertEquals(1, elasticSearchResponse.getHits().getTotalHits().value);
        assertNotNull(elasticSearchResponse.getHits().getAt(0));

        // update entry
        Map<String, Object> created = elasticSearchResponse.getHits().getAt(0).getSourceAsMap();
        for (int i = 0; i < 3; i++) {
            outcome = StatusCode.OK;
            outcomeDetailMessage = "IMPORT_FORMAT." + StatusCode.OK.name();
            final LogbookOperationParameters parametersForUpdate = LogbookParameterHelper.newLogbookOperationParameters(
                eventIdentifier,
                eventType,
                eventIdentifierProcess,
                eventTypeProcess,
                outcome,
                outcomeDetailMessage,
                eventIdentifierRequest
            );
            // add update as event
            ArrayList<Document> events = (ArrayList<Document>) created.get(LogbookDocument.EVENTS);
            if (events == null) {
                events = new ArrayList<>();
            }
            events.add(new LogbookOperation(parametersForUpdate, true));
            created.put(LogbookDocument.EVENTS, events);
            String idUpdate = id;
            String esJsonUpdate = JsonHandler.unprettyPrint(created);
            LogbookOperation document = JsonHandler.getFromString(esJsonUpdate, LogbookOperation.class);

            esClient.updateFullDocument(LogbookCollections.OPERATION, tenantId, idUpdate, document);
        }
        // check entry
        SearchResponse elasticSearchResponse2 = esClient.search(
            LogbookCollections.OPERATION,
            tenantId,
            query,
            null,
            null,
            0,
            10
        );
        assertEquals(1, elasticSearchResponse2.getHits().getTotalHits().value);
        assertNotNull(elasticSearchResponse2.getHits().getAt(0));
        SearchHit hit = elasticSearchResponse2.getHits().iterator().next();
        assertNotNull(hit);

        // check search
        SearchResponse elasticSearchResponse3 = esClient.search(
            LogbookCollections.OPERATION,
            tenantId,
            query,
            null,
            null,
            0,
            01
        );
        assertEquals(1, elasticSearchResponse3.getHits().getTotalHits().value);

        // refresh index
        esClient.refreshIndex(LogbookCollections.OPERATION, tenantId);

        // delete index
        esClient.deleteIndexByAliasForTesting(LogbookCollections.OPERATION, tenantId);

        // check post delete
        try {
            esClient.search(LogbookCollections.OPERATION, tenantId, query, null, null, 0, 10);
            fail("Should have failed : IndexNotFoundException");
        } catch (LogbookException e) {}
    }
}
