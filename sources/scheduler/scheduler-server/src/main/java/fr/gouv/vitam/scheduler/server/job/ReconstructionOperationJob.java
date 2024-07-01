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

package fr.gouv.vitam.scheduler.server.job;

import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.logbook.common.exception.LogbookClientServerException;
import fr.gouv.vitam.logbook.common.model.reconstruction.ReconstructionRequestItem;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClient;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClientFactory;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.util.List;
import java.util.stream.Collectors;

@DisallowConcurrentExecution
public class ReconstructionOperationJob implements Job {

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(ReconstructionOperationJob.class);

    private final LogbookOperationsClientFactory logbookOperationsClientFactory;

    public ReconstructionOperationJob() {
        this(LogbookOperationsClientFactory.getInstance());
    }

    ReconstructionOperationJob(LogbookOperationsClientFactory logbookOperationsClientFactory) {
        this.logbookOperationsClientFactory = logbookOperationsClientFactory;
    }

    public void execute(JobExecutionContext context) throws JobExecutionException {
        final Integer adminTenant = VitamConfiguration.getAdminTenant();
        try {
            VitamThreadUtils.getVitamSession().setTenantId(adminTenant);
            VitamThreadUtils.getVitamSession().setRequestId(GUIDFactory.newOperationLogbookGUID(adminTenant));
            List<ReconstructionRequestItem> reconstructionItems = VitamConfiguration.getTenants()
                .stream()
                .map(ReconstructionRequestItem::new)
                .collect(Collectors.toList());
            try (LogbookOperationsClient client = this.logbookOperationsClientFactory.getClient()) {
                LOGGER.info("Reconstruction operation in progress...");
                client.reconstructCollection(reconstructionItems);
                LOGGER.info("Reconstruction operation is finished");
            }
        } catch (LogbookClientServerException e) {
            throw new IllegalStateException(" Error when securing logbook operations  :  " + adminTenant, e);
        }
    }
}
