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
import fr.gouv.vitam.collect.internal.model.TransactionModel;
import fr.gouv.vitam.collect.internal.server.CollectConfiguration;
import fr.gouv.vitam.common.LocalDateUtil;
import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.thread.ExecutorUtils;
import fr.gouv.vitam.common.thread.VitamThreadUtils;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class PurgeTransactionThread implements Runnable {

    private static final String TRANSACTION_NOT_FOUND = "No transaction found";

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(PurgeTransactionThread.class);

    private final TransactionService transactionService;

    private final CollectService collectService;

    private final Map<Integer, Integer> purgeTransactionDelayInMinutes;

    private final int purgeTransactionThreadPoolSize;

    public PurgeTransactionThread(CollectConfiguration collectConfiguration, TransactionService transactionService,
        CollectService collectService) {
        this.collectService = collectService;
        this.transactionService = transactionService;
        purgeTransactionDelayInMinutes = collectConfiguration.getPurgeTransactionDelayInMinutes();
        this.purgeTransactionThreadPoolSize = collectConfiguration.getPurgeTransactionThreadPoolSize();
    }

    @Override
    public void run() {
        try {
            process();
        } catch (CollectException e) {
            LOGGER.error("Error when processing purge transaction: {}", e);
        }
    }

    public void deleteTransaction(Integer tenantId, Integer delay) throws CollectException, ParseException {
        LOGGER.debug("start delete transaction content" + tenantId + " " +Thread.currentThread().getId());
        List<TransactionModel> transactionModelList = transactionService.getListTransactionToDeleteByTenant(tenantId);
        if (transactionModelList.isEmpty()) {
            LOGGER.debug(TRANSACTION_NOT_FOUND);
        }
        for (TransactionModel transactionModel : transactionModelList) {
            if (isToDelete(transactionModel.getManifestContext().getLastUpdate(), delay)) {
                collectService.deleteTransactionContent(transactionModel.getId());
            }
        }

    }

    private boolean isToDelete(String transactionDate, Integer delay) throws ParseException {
        Date dateLastUpdate = LocalDateUtil.getDate(transactionDate);
        Date dateNow = new Date(System.currentTimeMillis());
        long differenceInMinutes = ((dateNow.getTime() - dateLastUpdate.getTime()) / (1000 * 60));
        return differenceInMinutes >= delay;
    }


    public void process() throws CollectException {
        Thread.currentThread().setName(PurgeTransactionThread.class.getName());
        VitamThreadUtils.getVitamSession()
            .setRequestId(GUIDFactory.newRequestIdGUID(VitamConfiguration.getAdminTenant()));
        ExecutorService executorService = ExecutorUtils.createScalableBatchExecutorService(this.purgeTransactionThreadPoolSize);
        try {
            List<CompletableFuture<Void>> completableFuturesList = new ArrayList<>();
            for (var entry : purgeTransactionDelayInMinutes.entrySet()) {
                CompletableFuture<Void> traceabilityCompletableFuture =
                    CompletableFuture.runAsync(() -> {
                        VitamThreadUtils.getVitamSession().setTenantId(entry.getKey());
                        try {
                            deleteTransaction(entry.getKey(), entry.getValue());
                        } catch (ParseException | CollectException e) {
                            LOGGER.error("Error when deleting transaction: {}", e);
                        }
                    }, executorService);

                completableFuturesList.add(traceabilityCompletableFuture);
            }
            CompletableFuture<Void> combinedFuture =
                CompletableFuture
                    .allOf(completableFuturesList.toArray(new CompletableFuture[0]));
            combinedFuture.get();
        } catch (InterruptedException  e) {
            LOGGER.error("Error when executing threads: {}", e);
            Thread.currentThread().interrupt();
            throw new CollectException("Error when executing threads: " + e);
        } catch (ExecutionException e) {
            LOGGER.error("Error when executing threads: {}", e);
            throw new CollectException("Error when executing threads: " + e);
        }
    }
}
