/*******************************************************************************
 * Copyright French Prime minister Office/SGMAP/DINSIC/Vitam Program (2015-2019)
 *
 * contact.vitam@culture.gouv.fr
 *
 * This software is a computer program whose purpose is to implement a digital archiving back-office system managing
 * high volumetry securely and efficiently.
 *
 * This software is governed by the CeCILL 2.1 license under French law and abiding by the rules of distribution of free
 * software. You can use, modify and/ or redistribute the software under the terms of the CeCILL 2.1 license as
 * circulated by CEA, CNRS and INRIA at the following URL "http://www.cecill.info".
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
 *******************************************************************************/
package fr.gouv.vitam.worker.core.plugin.lfc_traceability;

import com.google.common.annotations.VisibleForTesting;
import fr.gouv.vitam.common.alert.AlertService;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.logbook.common.server.database.collections.LogbookLifeCycleObjectGroup;
import fr.gouv.vitam.processing.common.exception.ProcessingException;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;
import fr.gouv.vitam.storage.engine.client.StorageClientFactory;
import fr.gouv.vitam.worker.common.HandlerIO;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageServerException;

public class BuildObjectGroupTraceabilityActionPlugin extends BuildTraceabilityActionPlugin {

    private static final VitamLogger LOGGER =
        VitamLoggerFactory.getInstance(PrepareUnitLfcTraceabilityActionPlugin.class);

    private static final String STP_OG_LFC_TRACEABILITY = "STP_OG_LFC_TRACEABILITY";
    private static final String ACTION_HANDLER_ID = "BUILD_OG_LFC_TRACEABILITY";

    public BuildObjectGroupTraceabilityActionPlugin() {
        super();
    }

    @VisibleForTesting
    BuildObjectGroupTraceabilityActionPlugin(
        StorageClientFactory storageClientFactory,
        int batchSize, AlertService alertService) {
        super(storageClientFactory, batchSize, alertService);
    }

    @Override
    public ItemStatus execute(WorkerParameters params, HandlerIO handler)
        throws ProcessingException, ContentAddressableStorageServerException {

        LOGGER.info("Building object group traceability data");
        StatusCode statusCode = buildTraceabilityData(handler, LogbookLifeCycleObjectGroup.class.getName());
        LOGGER.info("Building object group traceability data finished with status " + statusCode);

        final ItemStatus itemStatus = new ItemStatus(ACTION_HANDLER_ID);
        itemStatus.increment(statusCode);
        return new ItemStatus(ACTION_HANDLER_ID).setItemsStatus(ACTION_HANDLER_ID, itemStatus);
    }

    @Override
    protected String stepName() {
        return STP_OG_LFC_TRACEABILITY;
    }

    @Override
    protected String actionName() {
        return ACTION_HANDLER_ID;
    }

    public static String getId() {
        return ACTION_HANDLER_ID;
    }
}
