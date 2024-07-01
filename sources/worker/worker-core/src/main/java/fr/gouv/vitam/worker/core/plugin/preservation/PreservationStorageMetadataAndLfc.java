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
package fr.gouv.vitam.worker.core.plugin.preservation;

import com.google.common.annotations.VisibleForTesting;
import fr.gouv.vitam.common.exception.VitamException;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.logbook.lifecycles.client.LogbookLifeCyclesClientFactory;
import fr.gouv.vitam.metadata.client.MetaDataClientFactory;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;
import fr.gouv.vitam.storage.engine.client.StorageClientFactory;
import fr.gouv.vitam.worker.common.HandlerIO;
import fr.gouv.vitam.worker.core.plugin.StoreMetaDataObjectGroupActionPlugin;
import fr.gouv.vitam.worker.core.plugin.preservation.model.WorkflowBatchResult;
import fr.gouv.vitam.worker.core.plugin.preservation.model.WorkflowBatchResults;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static fr.gouv.vitam.worker.core.utils.PluginHelper.buildItemStatus;

public class PreservationStorageMetadataAndLfc extends StoreMetaDataObjectGroupActionPlugin {

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(PreservationStorageMetadataAndLfc.class);

    private static final String PRESERVATION_STORAGE_METADATA_LFC = "PRESERVATION_STORAGE_METADATA_LFC";
    private static final int WORKFLOWBATCHRESULTS_IN_MEMORY = 0;

    public PreservationStorageMetadataAndLfc() {
        this(
            MetaDataClientFactory.getInstance(),
            LogbookLifeCyclesClientFactory.getInstance(),
            StorageClientFactory.getInstance()
        );
    }

    @VisibleForTesting
    PreservationStorageMetadataAndLfc(
        MetaDataClientFactory metaDataClientFactory,
        LogbookLifeCyclesClientFactory logbookLifeCyclesClientFactory,
        StorageClientFactory storageClientFactory
    ) {
        super(metaDataClientFactory, logbookLifeCyclesClientFactory, storageClientFactory);
    }

    @Override
    public List<ItemStatus> executeList(WorkerParameters params, HandlerIO handlerIO) {
        WorkflowBatchResults results = (WorkflowBatchResults) handlerIO.getInput(WORKFLOWBATCHRESULTS_IN_MEMORY);
        List<ItemStatus> itemStatuses = new ArrayList<>();
        List<WorkflowBatchResult> workflowBatchResults = results.getWorkflowBatchResults();
        for (WorkflowBatchResult result : workflowBatchResults) {
            List<String> objectGroupIdList = Collections.singletonList(result.getGotId());
            try {
                List<WorkflowBatchResult.OutputExtra> outputExtras = result
                    .getOutputExtras()
                    .stream()
                    .filter(
                        o ->
                            o.isOkAndGenerated() ||
                            o.isOkAndExtractedGot() ||
                            o.isOkAndIdentify() ||
                            o.isOkAndExtractedAu()
                    )
                    .collect(Collectors.toList());

                if (outputExtras.isEmpty()) {
                    itemStatuses.add(new ItemStatus(PRESERVATION_STORAGE_METADATA_LFC));
                    continue;
                }

                storeDocumentsWithLfc(params, handlerIO, objectGroupIdList);
                itemStatuses.addAll(this.getItemStatuses(objectGroupIdList, StatusCode.OK));
            } catch (VitamException e) {
                LOGGER.error("An error occurred during object group storage", e);
                itemStatuses.addAll(this.getItemStatuses(objectGroupIdList, StatusCode.FATAL));
            }
        }
        return itemStatuses;
    }

    private List<ItemStatus> getItemStatuses(List<String> objectGroupIds, StatusCode statusCode) {
        List<ItemStatus> itemStatuses = new ArrayList<>();
        for (int i = 0; i < objectGroupIds.size(); i++) {
            itemStatuses.add(buildItemStatus(PRESERVATION_STORAGE_METADATA_LFC, statusCode, null));
        }
        return itemStatuses;
    }
}
