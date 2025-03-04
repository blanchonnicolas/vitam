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
package fr.gouv.vitam.metadata.core;

import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.database.server.elasticsearch.ElasticsearchIndexAlias;
import fr.gouv.vitam.common.database.server.elasticsearch.ElasticsearchNode;
import fr.gouv.vitam.common.database.server.mongodb.MongoDbAccess;
import fr.gouv.vitam.common.elasticsearch.ElasticsearchRule;
import fr.gouv.vitam.common.mongo.MongoRule;
import fr.gouv.vitam.common.server.application.configuration.MongoDbNode;
import fr.gouv.vitam.metadata.core.config.ElasticsearchMetadataIndexManager;
import fr.gouv.vitam.metadata.core.config.MetaDataConfiguration;
import fr.gouv.vitam.metadata.core.database.collections.MetadataCollections;
import fr.gouv.vitam.metadata.core.database.collections.MetadataCollectionsTestUtils;
import fr.gouv.vitam.metadata.core.database.collections.MongoDbAccessMetadataImpl;
import fr.gouv.vitam.metadata.core.database.collections.ObjectGroup;
import fr.gouv.vitam.metadata.core.database.collections.Unit;
import fr.gouv.vitam.metadata.core.mapping.MappingLoader;
import fr.gouv.vitam.metadata.core.utils.MappingLoaderTestUtils;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;

public class MongoDbAccessMetadataFactoryTest {

    private static final List<Integer> tenantList = Arrays.asList(0, 1);

    @ClassRule
    public static MongoRule mongoRule = new MongoRule(
        MongoDbAccess.getMongoClientSettingsBuilder(Unit.class, ObjectGroup.class)
    );

    @ClassRule
    public static ElasticsearchRule elasticsearchRule = new ElasticsearchRule();

    @Test
    public void testCreateMetadata() throws Exception {
        final List<MongoDbNode> mongoNodes = new ArrayList<>();
        mongoNodes.add(new MongoDbNode(MongoRule.MONGO_HOST, MongoRule.getDataBasePort()));

        List<ElasticsearchNode> esNodes = new ArrayList<>();
        esNodes.add(new ElasticsearchNode(ElasticsearchRule.getHost(), ElasticsearchRule.getPort()));

        MappingLoader mappingLoader = MappingLoaderTestUtils.getTestMappingLoader();
        ElasticsearchMetadataIndexManager indexManager = MetadataCollectionsTestUtils.createTestIndexManager(
            tenantList,
            Collections.emptyMap(),
            mappingLoader
        );

        MetaDataConfiguration config = new MetaDataConfiguration(
            mongoNodes,
            MongoRule.VITAM_DB,
            ElasticsearchRule.VITAM_CLUSTER,
            esNodes,
            mappingLoader
        );
        VitamConfiguration.setTenants(tenantList);
        MongoDbAccessMetadataImpl mongoDbAccess = MongoDbAccessMetadataFactory.create(
            config,
            mappingLoader,
            indexManager
        );

        assertNotNull(mongoDbAccess);
        assertThat(mongoDbAccess.getMongoDatabase().getName()).isEqualTo(MongoRule.VITAM_DB);

        // Ensure indexes initialized
        for (Integer tenant : tenantList) {
            ElasticsearchIndexAlias indexAlias = indexManager
                .getElasticsearchIndexAliasResolver(MetadataCollections.UNIT)
                .resolveIndexName(tenant);

            assertThat(mongoDbAccess.getEsClient().existsAlias(indexAlias)).isTrue();
            mongoDbAccess.getEsClient().deleteIndexByAliasForTesting(indexAlias);
            assertThat(mongoDbAccess.getEsClient().existsAlias(indexAlias)).isFalse();
        }

        mongoDbAccess.close();
    }
}
