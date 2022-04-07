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

package fr.gouv.vitam.worker.core.plugin.deleteGotVersions;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import fr.gouv.vitam.common.PropertiesUtils;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.thread.RunWithCustomExecutor;
import fr.gouv.vitam.common.thread.RunWithCustomExecutorRule;
import fr.gouv.vitam.common.thread.VitamThreadPoolExecutor;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;
import fr.gouv.vitam.storage.engine.client.StorageClient;
import fr.gouv.vitam.storage.engine.client.StorageClientFactory;
import fr.gouv.vitam.storage.engine.common.referential.model.StorageStrategy;
import fr.gouv.vitam.worker.common.HandlerIO;
import fr.gouv.vitam.worker.core.plugin.deleteGotVersions.handlers.DeleteGotVersionsStoragePlugin;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static fr.gouv.vitam.common.json.JsonHandler.getFromFile;
import static fr.gouv.vitam.common.json.JsonHandler.getFromJsonNode;
import static fr.gouv.vitam.common.model.StatusCode.OK;
import static fr.gouv.vitam.common.model.StatusCode.WARNING;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DeleteGotVersionsStoragePluginTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Rule
    public RunWithCustomExecutorRule runInThread =
        new RunWithCustomExecutorRule(VitamThreadPoolExecutor.getDefaultExecutor());

    @InjectMocks
    private DeleteGotVersionsStoragePlugin deleteGotVersionsStoragePlugin;

    @Mock
    private StorageClientFactory storageClientFactory;

    @Mock
    private StorageClient storageClient;

    @Mock
    private HandlerIO handlerIO;

    @Mock
    private WorkerParameters params;

    public static final String OK_AND_WARNING_RESULTS_LIST = "deleteGotVersions/1_OK_1_WARNING_list.json";
    public static final String OK_RESULTS_LIST = "deleteGotVersions/OK_list.json";
    public static final String STRATEGIES_FILE = "deleteGotVersions/strategies.json";

    @Before
    public void setUp() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(0);
        when(storageClientFactory.getClient()).thenReturn(storageClient);
        deleteGotVersionsStoragePlugin =
            new DeleteGotVersionsStoragePlugin(storageClientFactory);
    }

    @Test
    @RunWithCustomExecutor
    public void givenOkResultsThenDeleteGotVersionsStorageOk() throws Exception {
        when(params.getObjectNameList())
            .thenReturn(Collections.singletonList("aebaaaaaaaepjubnaasdualyqi65jkyaaaaq"));

        JsonNode results = getFromFile(PropertiesUtils.getResourceFile(OK_RESULTS_LIST));
        List<JsonNode> resultsNodes = getFromJsonNode(results, new TypeReference<>() {
        });
        when(params.getObjectMetadataList()).thenReturn(resultsNodes);
        when(storageClient.getStorageStrategies()).thenReturn(loadStorageStrategies());
        List<ItemStatus> itemStatusList = deleteGotVersionsStoragePlugin.executeList(params, handlerIO);
        assertEquals(1, itemStatusList.size());
        assertEquals(OK, itemStatusList.get(0).getGlobalStatus());
        verify(storageClient, times(2)).delete(any(), any(), any());
    }

    @Test
    @RunWithCustomExecutor
    public void givenOkAndWarningResultsThenDeleteGotVersionsStorageWarning() throws Exception {
        when(params.getObjectNameList())
            .thenReturn(Collections.singletonList("aebaaaaaaaepjubnaasdualyqi65jkyaaaaq"));

        JsonNode results = getFromFile(PropertiesUtils.getResourceFile(OK_AND_WARNING_RESULTS_LIST));
        List<JsonNode> resultsNodes = getFromJsonNode(results, new TypeReference<>() {
        });
        when(params.getObjectMetadataList()).thenReturn(resultsNodes);
        when(storageClient.getStorageStrategies()).thenReturn(loadStorageStrategies());
        List<ItemStatus> itemStatusList = deleteGotVersionsStoragePlugin.executeList(params, handlerIO);
        assertEquals(1, itemStatusList.size());
        assertEquals(WARNING, itemStatusList.get(0).getGlobalStatus());
        verify(storageClient, times(1)).delete(any(), any(), any());
    }

    private RequestResponseOK<StorageStrategy> loadStorageStrategies()
        throws FileNotFoundException, InvalidParseOperationException {
        StorageStrategy[] storageStrategiesArray = JsonHandler.getFromFileLowerCamelCase(
            PropertiesUtils.getResourceFile(STRATEGIES_FILE), StorageStrategy[].class);
        return new RequestResponseOK<StorageStrategy>().addAllResults(Arrays.asList(storageStrategiesArray));
    }
}
