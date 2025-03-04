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
package fr.gouv.vitam.access.external;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;
import fr.gouv.vitam.access.external.client.AccessExternalClient;
import fr.gouv.vitam.access.external.client.AccessExternalClientFactory;
import fr.gouv.vitam.access.external.rest.AccessExternalMain;
import fr.gouv.vitam.access.internal.rest.AccessInternalMain;
import fr.gouv.vitam.common.DataLoader;
import fr.gouv.vitam.common.LocalDateUtil;
import fr.gouv.vitam.common.PropertiesUtils;
import fr.gouv.vitam.common.VitamRuleRunner;
import fr.gouv.vitam.common.VitamServerRunner;
import fr.gouv.vitam.common.client.VitamContext;
import fr.gouv.vitam.common.database.builder.query.QueryHelper;
import fr.gouv.vitam.common.database.builder.query.VitamFieldsHelper;
import fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken;
import fr.gouv.vitam.common.database.builder.request.exception.InvalidCreateOperationException;
import fr.gouv.vitam.common.database.builder.request.multiple.SelectMultiQuery;
import fr.gouv.vitam.common.elasticsearch.ElasticsearchRule;
import fr.gouv.vitam.common.error.VitamError;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.VitamClientException;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.model.JsonLineIterator;
import fr.gouv.vitam.common.model.RequestResponse;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.thread.RunWithCustomExecutor;
import fr.gouv.vitam.functional.administration.rest.AdminManagementMain;
import fr.gouv.vitam.ingest.external.rest.IngestExternalMain;
import fr.gouv.vitam.ingest.internal.upload.rest.IngestInternalMain;
import fr.gouv.vitam.logbook.common.server.database.collections.LogbookCollections;
import fr.gouv.vitam.logbook.common.server.database.collections.LogbookLifeCycleUnit;
import fr.gouv.vitam.logbook.rest.LogbookMain;
import fr.gouv.vitam.metadata.api.exception.MetaDataExecutionException;
import fr.gouv.vitam.metadata.core.database.collections.MetadataCollections;
import fr.gouv.vitam.metadata.core.database.collections.MetadataSnapshot;
import fr.gouv.vitam.metadata.core.database.collections.ObjectGroup;
import fr.gouv.vitam.metadata.core.database.collections.Unit;
import fr.gouv.vitam.metadata.rest.MetadataMain;
import fr.gouv.vitam.processing.management.rest.ProcessManagementMain;
import fr.gouv.vitam.worker.server.rest.WorkerMain;
import fr.gouv.vitam.workspace.rest.WorkspaceMain;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.FileNotFoundException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static fr.gouv.vitam.common.model.objectgroup.FileInfoModel.FILENAME;
import static fr.gouv.vitam.common.model.objectgroup.FileInfoModel.LAST_MODIFIED;
import static fr.gouv.vitam.common.model.objectgroup.ObjectGroupResponse.OPERATIONS;
import static fr.gouv.vitam.metadata.core.MetaDataImpl.SNAPSHOT_COLLECTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class AccessExternalIT extends VitamRuleRunner {

    private static final Integer tenantId = 0;
    private static final String APPLICATION_SESSION_ID = "ApplicationSessionId";
    private static final String ACCESS_CONTRACT = "aName3";

    private static final String UNITS_RESOURCE_FILE = "access/units.json";
    private static final String GOT_RESOURCE_FILE = "database/got.json";

    @ClassRule
    public static VitamServerRunner runner = new VitamServerRunner(
        AccessExternalIT.class,
        mongoRule.getMongoDatabase().getName(),
        ElasticsearchRule.getClusterName(),
        Sets.newHashSet(
            MetadataMain.class,
            WorkerMain.class,
            AdminManagementMain.class,
            LogbookMain.class,
            WorkspaceMain.class,
            ProcessManagementMain.class,
            AccessInternalMain.class,
            IngestInternalMain.class,
            AccessExternalMain.class,
            IngestExternalMain.class
        )
    );

    private static AccessExternalClient accessExternalClient;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        handleBeforeClass(Arrays.asList(0, 1), Collections.emptyMap());
        accessExternalClient = AccessExternalClientFactory.getInstance().getClient();

        new DataLoader("integration-ingest-internal").prepareData();
        insertUnits(UNITS_RESOURCE_FILE);
        insertGots(GOT_RESOURCE_FILE);
    }

    @After
    public void after() {
        runAfterMongo(Set.of(SNAPSHOT_COLLECTION));
    }

    @AfterClass
    public static void tearDownAfterClass() {
        runAfter();
        fr.gouv.vitam.common.external.client.VitamClientFactory.resetConnections();
        fr.gouv.vitam.common.client.VitamClientFactory.resetConnections();
    }

    @RunWithCustomExecutor
    @Test
    public void selectUnitsWithTrackTotalHitsInDSL() throws Exception {
        // given
        VitamContext vitamContext = new VitamContext(tenantId)
            .setApplicationSessionId(APPLICATION_SESSION_ID)
            .setAccessContract(ACCESS_CONTRACT);

        // WHEN
        RequestResponse<JsonNode> unitsWithPrecision = getMetadataWithTrackTotalHits(
            true,
            vitamContext,
            MetadataCollections.UNIT
        );
        RequestResponse<JsonNode> unitsWithoutPrecision = getMetadataWithTrackTotalHits(
            false,
            vitamContext,
            MetadataCollections.UNIT
        );

        // THEN
        assertFalse(unitsWithPrecision.isOk());
        assertThat(unitsWithPrecision.getStatus()).isEqualTo(Status.UNAUTHORIZED.getStatusCode());
        assertThat(((VitamError<JsonNode>) unitsWithPrecision).getDescription()).contains(
            "$track_total_hits is not authorized!"
        );

        List<JsonNode> resultsWithoutPrecision = ((RequestResponseOK<JsonNode>) unitsWithoutPrecision).getResults();
        assertNotNull(resultsWithoutPrecision);
        assertThat(resultsWithoutPrecision.size()).isGreaterThan(0);
    }

    @RunWithCustomExecutor
    @Test
    public void selectObjectGroupsByDSLWithBlackListedFields() throws Exception {
        // given
        VitamContext vitamContext = new VitamContext(tenantId)
            .setApplicationSessionId(APPLICATION_SESSION_ID)
            .setAccessContract(ACCESS_CONTRACT);

        final List<String> declaredBlackListedFieldsForGotInMetadatConf = List.of(FILENAME, LAST_MODIFIED, OPERATIONS);

        SelectMultiQuery query = new SelectMultiQuery();
        query.addQueries(QueryHelper.exists(VitamFieldsHelper.id()));

        RequestResponse<JsonNode> response = accessExternalClient.selectObjects(vitamContext, query.getFinalSelect());
        // THEN
        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        RequestResponseOK<JsonNode> jsonNode = (RequestResponseOK<JsonNode>) response;
        jsonNode
            .getResults()
            .forEach(result -> {
                declaredBlackListedFieldsForGotInMetadatConf.forEach(
                    field -> assertFalse(result.toString().contains(field))
                );
            });
    }

    @RunWithCustomExecutor
    @Test
    public void shouldStreamUnitsOK() throws Exception {
        // given
        VitamContext vitamContext = new VitamContext(tenantId)
            .setApplicationSessionId(APPLICATION_SESSION_ID)
            .setAccessContract(ACCESS_CONTRACT);

        SelectMultiQuery query = new SelectMultiQuery();
        query.addQueries(QueryHelper.exists(VitamFieldsHelper.id()));
        query.addProjection(JsonHandler.createObjectNode().put(VitamFieldsHelper.id(), 1));

        // WHEN
        final JsonLineIterator<JsonNode> iterator = accessExternalClient.streamUnits(
            vitamContext,
            query.getFinalSelect()
        );
        // THEN
        AtomicInteger size = new AtomicInteger();
        iterator.forEachRemaining(e -> size.getAndIncrement());
        assertEquals(15000, size.get());
    }

    @RunWithCustomExecutor
    @Test
    public void shouldStreamUnitsWithExceedExecutionLimitKO() throws Exception {
        // given
        mongoRule
            .getMongoCollection(SNAPSHOT_COLLECTION, MetadataSnapshot.class)
            .insertOne(
                new MetadataSnapshot(
                    "{ \"_id\" : \"aeaaaaaaaaeaaaabag5swal7ivc47uqaaaaq\", \"Name\" : \"Scroll\", \"_tenant\" : 0, \"Value\" : 3 }"
                )
            );
        mongoRule
            .getMongoCollection(SNAPSHOT_COLLECTION, MetadataSnapshot.class)
            .insertOne(
                new MetadataSnapshot(
                    "{ \"_id\" : \"aeaaaaaaaaeaaaabahd72al7ivfrywiaaaaq\", \"Name\" : \"LastScrollRequestDate\", \"_tenant\" : 0, \"Value\" : \"" +
                    LocalDateUtil.getFormattedDateTimeForMongo(LocalDate.now().atStartOfDay()) +
                    "\" }"
                )
            );
        VitamContext vitamContext = new VitamContext(tenantId)
            .setApplicationSessionId(APPLICATION_SESSION_ID)
            .setAccessContract(ACCESS_CONTRACT);

        SelectMultiQuery query = new SelectMultiQuery();
        query.addQueries(QueryHelper.exists(VitamFieldsHelper.id()));
        query.addProjection(JsonHandler.createObjectNode().put(VitamFieldsHelper.id(), 1));

        // THEN
        assertThatCode(() -> accessExternalClient.streamUnits(vitamContext, query.getFinalSelect())).isInstanceOf(
            VitamClientException.class
        );
    }

    @RunWithCustomExecutor
    @Test
    public void shouldStreamUnitsWithExceedExecutionLimitNextDayOK() throws Exception {
        // given
        mongoRule
            .getMongoCollection(SNAPSHOT_COLLECTION, MetadataSnapshot.class)
            .insertOne(
                new MetadataSnapshot(
                    "{ \"_id\" : \"aeaaaaaaaaeaaaabag5swal7ivc47uqaaaaq\", \"Name\" : \"Scroll\", \"_tenant\" : 0, \"Value\" : 3 }"
                )
            );
        mongoRule
            .getMongoCollection(SNAPSHOT_COLLECTION, MetadataSnapshot.class)
            .insertOne(
                new MetadataSnapshot(
                    "{ \"_id\" : \"aeaaaaaaaaeaaaabahd72al7ivfrywiaaaaq\", \"Name\" : \"LastScrollRequestDate\", \"_tenant\" : 0, \"Value\" : \"" +
                    LocalDateUtil.getFormattedDateTimeForMongo(LocalDate.now().minusDays(1).atStartOfDay()) +
                    "\" }"
                )
            );
        VitamContext vitamContext = new VitamContext(tenantId)
            .setApplicationSessionId(APPLICATION_SESSION_ID)
            .setAccessContract(ACCESS_CONTRACT);

        SelectMultiQuery query = new SelectMultiQuery();
        query.addQueries(QueryHelper.exists(VitamFieldsHelper.id()));
        query.addProjection(JsonHandler.createObjectNode().put(VitamFieldsHelper.id(), 1));

        // WHEN
        final JsonLineIterator<JsonNode> iterator = accessExternalClient.streamUnits(
            vitamContext,
            query.getFinalSelect()
        );
        // THEN
        AtomicInteger size = new AtomicInteger();
        iterator.forEachRemaining(e -> size.getAndIncrement());
        assertEquals(15000, size.get());
    }

    @RunWithCustomExecutor
    @Test
    public void shouldStreamUnitsWithThresholdKO() throws Exception {
        // given
        VitamContext vitamContext = new VitamContext(tenantId)
            .setApplicationSessionId(APPLICATION_SESSION_ID)
            .setAccessContract(ACCESS_CONTRACT);

        SelectMultiQuery query = new SelectMultiQuery();
        query.addQueries(QueryHelper.exists(VitamFieldsHelper.id()));
        query.addProjection(JsonHandler.createObjectNode().put(VitamFieldsHelper.id(), 1));
        query.setThreshold(12000L);

        // THEN
        assertThatCode(() -> accessExternalClient.streamUnits(vitamContext, query.getFinalSelect())).isInstanceOf(
            VitamClientException.class
        );
    }

    @RunWithCustomExecutor
    @Test
    public void selectObjectGroupsWithTrackTotalHitsInDSL() throws Exception {
        // given
        VitamContext vitamContext = new VitamContext(tenantId)
            .setApplicationSessionId(APPLICATION_SESSION_ID)
            .setAccessContract(ACCESS_CONTRACT);

        // WHEN
        assertThatThrownBy(() -> {
            getMetadataWithTrackTotalHits(true, vitamContext, MetadataCollections.OBJECTGROUP);
        })
            .isInstanceOf(VitamClientException.class)
            .hasMessageContaining("Error with the response, get status: '401' and reason 'Unauthorized'.");

        RequestResponse<JsonNode> gotsWithoutPrecision = getMetadataWithTrackTotalHits(
            false,
            vitamContext,
            MetadataCollections.OBJECTGROUP
        );

        // THEN
        List<JsonNode> resultsWithoutPrecision = ((RequestResponseOK<JsonNode>) gotsWithoutPrecision).getResults();
        assertNotNull(resultsWithoutPrecision);
        assertThat(resultsWithoutPrecision.size()).isGreaterThan(0);
    }

    private static void insertUnitAndLFC(final String unitFile, final String lfcFile)
        throws InvalidParseOperationException, FileNotFoundException, MetaDataExecutionException {
        insertUnits(unitFile);

        List<LogbookLifeCycleUnit> unitsLfc = JsonHandler.getFromFileAsTypeReference(
            PropertiesUtils.getResourceFile(lfcFile),
            new TypeReference<>() {}
        );

        LogbookCollections.LIFECYCLE_UNIT.<LogbookLifeCycleUnit>getVitamCollection()
            .getCollection()
            .insertMany(unitsLfc);
    }

    private static void insertUnits(String unitFile)
        throws InvalidParseOperationException, FileNotFoundException, MetaDataExecutionException {
        List<Unit> units = JsonHandler.getFromFileAsTypeReference(
            PropertiesUtils.getResourceFile(unitFile),
            new TypeReference<>() {}
        );
        MetadataCollections.UNIT.<Unit>getVitamCollection().getCollection().insertMany(units);
        MetadataCollections.UNIT.getEsClient().insertFullDocuments(MetadataCollections.UNIT, tenantId, units);
    }

    private static void insertGots(String gotFile)
        throws InvalidParseOperationException, FileNotFoundException, MetaDataExecutionException {
        List<ObjectGroup> gots = JsonHandler.getFromFileAsTypeReference(
            PropertiesUtils.getResourceFile(gotFile),
            new TypeReference<>() {}
        );
        MetadataCollections.OBJECTGROUP.<ObjectGroup>getVitamCollection().getCollection().insertMany(gots);
        MetadataCollections.OBJECTGROUP.getEsClient()
            .insertFullDocuments(MetadataCollections.OBJECTGROUP, tenantId, gots);
    }

    private RequestResponse<JsonNode> getMetadataWithTrackTotalHits(
        boolean shouldTrackTotalHits,
        VitamContext vitamContext,
        MetadataCollections collection
    ) throws VitamClientException, InvalidParseOperationException, InvalidCreateOperationException {
        SelectMultiQuery select = new SelectMultiQuery();
        select.addQueries(QueryHelper.exists(VitamFieldsHelper.id()));
        select.trackTotalHits(shouldTrackTotalHits);
        select.setProjection(
            JsonHandler.createObjectNode()
                .set(
                    BuilderToken.PROJECTION.FIELDS.name(),
                    JsonHandler.createObjectNode().put(VitamFieldsHelper.id(), 1)
                )
        );
        if (collection.equals(MetadataCollections.UNIT)) {
            return accessExternalClient.selectUnits(vitamContext, select.getFinalSelect());
        }
        return accessExternalClient.selectObjects(vitamContext, select.getFinalSelect());
    }
}
