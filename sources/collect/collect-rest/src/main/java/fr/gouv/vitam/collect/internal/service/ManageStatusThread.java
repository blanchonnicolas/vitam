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


package fr.gouv.vitam.collect.internal.service;

import fr.gouv.vitam.collect.internal.exception.CollectException;
import fr.gouv.vitam.collect.internal.server.CollectConfiguration;
import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.thread.ExecutorUtils;
import fr.gouv.vitam.common.thread.VitamThreadUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;


public class ManageStatusThread implements Runnable {

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(ManageStatusThread.class);
    private final TransactionService transactionService;
    private final Map<Integer, Integer> statusTransactionDelayInMinutes;
    CollectConfiguration collectConfiguration;
    private final static String ERROR_THREAD_EXECUTING = "Error when executing threads:";


    public ManageStatusThread(
        TransactionService transactionService, CollectConfiguration collectConfiguration) {
        this.transactionService = transactionService;
        this.statusTransactionDelayInMinutes = collectConfiguration.getStatusTransactionDelayInMinutes();
        this.collectConfiguration = collectConfiguration;
    }

    @Override
    public void run() {
        try {
            process();
        } catch (CollectException e) {
            LOGGER.error("Error when executing threads: {}", e);
        }


    }

    private void process() throws CollectException {

        Thread.currentThread().setName(ManageStatusThread.class.getName());
        VitamThreadUtils.getVitamSession()
            .setRequestId(GUIDFactory.newRequestIdGUID(VitamConfiguration.getAdminTenant()));
        int threadPoolSize = Math.min(this.collectConfiguration.getTransactionStatusThreadPoolSize(),
            statusTransactionDelayInMinutes.entrySet().size());
        ExecutorService executorService = ExecutorUtils.createScalableBatchExecutorService(threadPoolSize);
        try {
            List<CompletableFuture<Void>> completableFuturesList = new ArrayList<>();
            for (var entry : statusTransactionDelayInMinutes.entrySet()) {
                CompletableFuture<Void> traceabilityCompletableFuture =
                    CompletableFuture.runAsync(() -> {
                        Thread.currentThread().setName(ManageStatusThread.class.getName() + "-" + entry.getKey());
                        VitamThreadUtils.getVitamSession().setTenantId(entry.getKey());
                        try {
                            this.transactionService.manageTransactionsStatus();
                        } catch (CollectException e) {
                            LOGGER.error("Error when managing status transaction: {}", e);
                        }
                    }, executorService);

                completableFuturesList.add(traceabilityCompletableFuture);
            }
            CompletableFuture<Void> combinedFuture =
                CompletableFuture
                    .allOf(completableFuturesList.toArray(new CompletableFuture[0]));
            combinedFuture.get();
        } catch (InterruptedException e) {
            LOGGER.error(ERROR_THREAD_EXECUTING + " {}", e);
            Thread.currentThread().interrupt();
            throw new CollectException(ERROR_THREAD_EXECUTING + " {}" + e);
        } catch (ExecutionException e) {
            LOGGER.error(ERROR_THREAD_EXECUTING + " {}", e);
            throw new CollectException(ERROR_THREAD_EXECUTING + " {}" + e);
        } finally {
            executorService.shutdown();
        }
    }


}
