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
package fr.gouv.vitam.worker.core.plugin.dip;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.processing.common.exception.ProcessingException;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;
import fr.gouv.vitam.storage.engine.client.StorageClient;
import fr.gouv.vitam.storage.engine.client.StorageClientFactory;
import fr.gouv.vitam.storage.engine.client.exception.StorageAlreadyExistsClientException;
import fr.gouv.vitam.storage.engine.client.exception.StorageNotFoundClientException;
import fr.gouv.vitam.storage.engine.client.exception.StorageServerClientException;
import fr.gouv.vitam.storage.engine.common.model.DataCategory;
import fr.gouv.vitam.storage.engine.common.model.request.ObjectDescription;
import fr.gouv.vitam.worker.common.HandlerIO;
import fr.gouv.vitam.worker.core.handler.ActionHandler;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageException;
import fr.gouv.vitam.workspace.client.WorkspaceClient;
import fr.gouv.vitam.workspace.common.CompressInformation;

import static fr.gouv.vitam.common.model.IngestWorkflowConstants.SEDA_FILE;

/**
 * ZIP the ExportsPurge and move it from workspace to storage
 */
public class StoreExports extends ActionHandler {

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(StoreExports.class);

    private static final String STORE_DIP = "STORE_DIP";
    private static final String TRANSFER_DIP = "TRANSFER_DIP";
    static final String ARCHIVE_TRANSFER = "ARCHIVE_TRANSFER";
    static final String CONTENT = "Content";
    static final String DIP_CONTAINER = "DIP";
    public static final String TRANSFER_CONTAINER = "TRANSFER";
    private static final String JSONL_EXTENSION = ".jsonl";
    private static final String DIGEST = "SystemMessageDigest";
    private static final String DIGEST_TYPE = "SystemAlgorithm";
    private final StorageClientFactory storageClientFactory;

    public StoreExports() {
        this(StorageClientFactory.getInstance());
    }

    @VisibleForTesting
    StoreExports(StorageClientFactory storageClientFactory) {
        this.storageClientFactory = storageClientFactory;
    }


    @Override
    public ItemStatus execute(WorkerParameters params, HandlerIO handler)
        throws ProcessingException {

        String container;
        String statusAction;
        if (isTransferWorkflow(params)) {
            container = TRANSFER_CONTAINER;
            statusAction = TRANSFER_DIP;
        } else {
            container = DIP_CONTAINER;
            statusAction = STORE_DIP;
        }

        final ItemStatus itemStatus = new ItemStatus(statusAction);

        try {
            String tenantFolder = Integer.toString(VitamThreadUtils.getVitamSession().getTenantId());
            String zipFileName = params.getContainerName();

            if (isTransferWorkflow(params) &&
                handler.isExistingFileInWorkspace(params.getContainerName() + JSONL_EXTENSION)) {
                storeReportToOffers(params.getContainerName());
            }

            try (WorkspaceClient workspaceClient = handler.getWorkspaceClientFactory().getClient()) {
                if (workspaceClient.isExistingObject(handler.getContainerName(), SEDA_FILE)) {
                    zipWorkspace(workspaceClient, handler.getContainerName(), tenantFolder, zipFileName, container);
                    ObjectNode eventDetailDataNode = JsonHandler.createObjectNode();
                    eventDetailDataNode.put(DIGEST, computeObjectDigest(handler, container, tenantFolder, zipFileName));
                    eventDetailDataNode.put(DIGEST_TYPE, VitamConfiguration.getDefaultDigestType().getName());
                    itemStatus.setEvDetailData(JsonHandler.unprettyPrint(eventDetailDataNode));
                }
            }

            itemStatus.increment(StatusCode.OK);
        } catch (ContentAddressableStorageException | StorageAlreadyExistsClientException | StorageNotFoundClientException | StorageServerClientException e) {
            throw new ProcessingException(e);
        }
        return new ItemStatus(statusAction).setItemsStatus(statusAction, itemStatus);
    }

    private boolean isTransferWorkflow(WorkerParameters params) {
        return params.getWorkflowIdentifier().equals(ARCHIVE_TRANSFER);
    }

    private void storeReportToOffers(String container)
        throws StorageAlreadyExistsClientException, StorageNotFoundClientException, StorageServerClientException {
        try (StorageClient storageClient = storageClientFactory.getClient()) {
            ObjectDescription description = new ObjectDescription();
            description.setWorkspaceContainerGUID(container);
            description.setWorkspaceObjectURI(container + JSONL_EXTENSION);
            storageClient.storeFileFromWorkspace(VitamConfiguration.getDefaultStrategy(),
                DataCategory.REPORT, container + JSONL_EXTENSION, description);
        }
    }

    private void zipWorkspace(WorkspaceClient workspaceClient, String operationId, String outputDir, String outputFile, String container)
        throws ContentAddressableStorageException {
        LOGGER.debug("Try to compress into workspace...");

        // Create Content folder if not exists
        workspaceClient.createFolder(operationId, CONTENT);

        // Ensure target folder exists
        workspaceClient.createContainer(container);
        workspaceClient.createFolder(container, outputDir);

        // compress
        CompressInformation compressInformation = new CompressInformation();
        compressInformation.getFiles().add(SEDA_FILE);
        compressInformation.getFiles().add(CONTENT);
        compressInformation.setOutputFile(outputDir + "/" + outputFile);
        compressInformation.setOutputContainer(container);
        workspaceClient.compress(operationId, compressInformation);
    }

    private String computeObjectDigest(HandlerIO handler, String container, String tenantFolder, String fileName)
        throws ContentAddressableStorageException {
        LOGGER.debug("Compute object digest from workspace...");
        try (WorkspaceClient workspaceClient = handler.getWorkspaceClientFactory().getClient()) {
            return workspaceClient
                .computeObjectDigest(container, tenantFolder + "/" + fileName,
                    VitamConfiguration.getDefaultDigestType());
        }
    }

    @Override
    public void checkMandatoryIOParameter(HandlerIO handler) throws ProcessingException {
        // Nothing to check
    }
}
