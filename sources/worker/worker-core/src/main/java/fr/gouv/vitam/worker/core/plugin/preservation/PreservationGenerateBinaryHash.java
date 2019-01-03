/*
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
 */
package fr.gouv.vitam.worker.core.plugin.preservation;

import fr.gouv.vitam.common.digest.Digest;
import fr.gouv.vitam.common.digest.DigestType;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.processing.common.exception.ProcessingException;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;
import fr.gouv.vitam.worker.common.HandlerIO;
import fr.gouv.vitam.worker.core.handler.ActionHandler;
import fr.gouv.vitam.worker.core.plugin.preservation.model.WorkflowBatchResult;
import fr.gouv.vitam.worker.core.plugin.preservation.model.WorkflowBatchResult.OutputExtra;
import fr.gouv.vitam.worker.core.plugin.preservation.model.WorkflowBatchResults;
import fr.gouv.vitam.worker.core.utils.PluginHelper.EventDetails;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static fr.gouv.vitam.common.digest.DigestType.SHA512;
import static fr.gouv.vitam.common.model.StatusCode.KO;
import static fr.gouv.vitam.common.model.StatusCode.OK;
import static fr.gouv.vitam.common.model.StatusCode.WARNING;
import static fr.gouv.vitam.worker.core.plugin.preservation.PreservationActionPlugin.OUTPUT_FILES;
import static fr.gouv.vitam.worker.core.utils.PluginHelper.buildItemStatus;

public class PreservationGenerateBinaryHash extends ActionHandler {
    private final  VitamLogger logger = VitamLoggerFactory.getInstance(PreservationGenerateBinaryHash.class);
    static DigestType digestPreservationGeneration = SHA512;
    private static final String ITEM_ID = "PRESERVATION_BINARY_HASH";

    @Override
    public List<ItemStatus> executeList(WorkerParameters workerParameters, HandlerIO handler)
        throws ProcessingException {
        logger.info("Starting PRESERVATION_BINARY_HASH.");

        handler.setCurrentObjectId(WorkflowBatchResults.NAME);
        WorkflowBatchResults results = (WorkflowBatchResults) handler.getInput(0);

        List<ItemStatus> itemStatuses = new ArrayList<>();
        List<WorkflowBatchResult> workflowBatchResults = new ArrayList<>();

        for (WorkflowBatchResult workflowBatchResult : results.getWorkflowBatchResults()) {
            List<OutputExtra> outputExtras = workflowBatchResult.getOutputExtras()
                .stream()
                .filter(OutputExtra::isOkAndGenerated)
                .map(a -> getOutputExtra(results, a))
                .collect(Collectors.toList());

            if (outputExtras.isEmpty()) {
                workflowBatchResults.add(workflowBatchResult);
                ItemStatus itemStatus = new ItemStatus(ITEM_ID);
                itemStatus.disableLfc();
                itemStatuses.add(itemStatus);
                continue;
            }

            itemStatuses.add(getItemStatus(outputExtras));

            Stream<OutputExtra> otherActions = workflowBatchResult.getOutputExtras()
                .stream()
                .filter(o -> !o.isOkAndGenerated());

            List<OutputExtra> previousAndNewExtras =
                Stream.concat(otherActions, outputExtras.stream().filter(outputExtra -> !outputExtra.isInError()))
                    .collect(Collectors.toList());
            workflowBatchResults.add(WorkflowBatchResult.of(workflowBatchResult, previousAndNewExtras));
        }

        handler.addOutputResult(0, new WorkflowBatchResults(results.getBatchDirectory(), workflowBatchResults));

        return itemStatuses;
    }

    private ItemStatus getItemStatus(List<OutputExtra> outputExtras) {
        String error = outputExtras.stream()
            .filter(o -> o.getError().isPresent())
            .map(o -> o.getError().get())
            .collect(Collectors.joining(","));
        if (outputExtras.stream().allMatch(OutputExtra::isInError)) {
            return buildItemStatus(ITEM_ID, KO, EventDetails.of(error)).disableLfc();
        }
        String hashs = outputExtras.stream()
            .filter(o -> o.getBinaryHash().isPresent())
            .map(OutputExtra::getBinaryHash)
            .map(Optional::get)
            .collect(Collectors.joining(", "));
        if (outputExtras.stream().noneMatch(OutputExtra::isInError)) {
            return buildItemStatus(ITEM_ID, OK, EventDetails.of(hashs));
        }
        return buildItemStatus(ITEM_ID, WARNING, EventDetails.of(error, hashs));
    }

    private OutputExtra getOutputExtra(WorkflowBatchResults results, OutputExtra a) {
        Digest digest = new Digest(digestPreservationGeneration);
        try {
            Path outputPath = results.getBatchDirectory().resolve(OUTPUT_FILES).resolve(a.getOutput().getOutputName());
            InputStream binaryFile = Files.newInputStream(outputPath);
            digest.update(binaryFile);
            String binaryHash = digest.digestHex();

            return OutputExtra.withBinaryHashAndSize(a, binaryHash, Files.size(outputPath));
        } catch (IOException e) {
            logger.error(e);
            return OutputExtra.inError(e.getMessage());
        }
    }
}
