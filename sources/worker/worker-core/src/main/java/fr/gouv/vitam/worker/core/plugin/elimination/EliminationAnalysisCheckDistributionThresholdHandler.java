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
package fr.gouv.vitam.worker.core.plugin.elimination;

import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.metadata.client.MetaDataClientFactory;
import fr.gouv.vitam.processing.common.exception.ProcessingException;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;
import fr.gouv.vitam.worker.common.HandlerIO;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageServerException;

public class EliminationAnalysisCheckDistributionThresholdHandler extends EliminationCheckDistributionThresholdBase {

    private static final String ELIMINATION_ANALYSIS_CHECK_DISTRIBUTION_THRESHOLD =
        "ELIMINATION_ANALYSIS_CHECK_DISTRIBUTION_THRESHOLD";

    public EliminationAnalysisCheckDistributionThresholdHandler() {
        super(MetaDataClientFactory.getInstance());
    }

    public EliminationAnalysisCheckDistributionThresholdHandler(
        MetaDataClientFactory metaDataClientFactory) {
        super(metaDataClientFactory);
    }

    @Override
    public ItemStatus execute(WorkerParameters param, HandlerIO handler)
        throws ProcessingException, ContentAddressableStorageServerException {
        return checkThreshold(handler, VitamConfiguration.getEliminationAnalysisThreshold(),
            ELIMINATION_ANALYSIS_CHECK_DISTRIBUTION_THRESHOLD);
    }

    @Override
    public void checkMandatoryIOParameter(HandlerIO handler) throws ProcessingException {
        // NOP.
    }

    public static String getId() {
        return ELIMINATION_ANALYSIS_CHECK_DISTRIBUTION_THRESHOLD;
    }
}
