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
package fr.gouv.vitam.collect.internal.core.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Iterators;
import fr.gouv.culture.archivesdefrance.seda.v2.LevelType;
import fr.gouv.vitam.collect.common.exception.CollectInternalException;
import fr.gouv.vitam.collect.common.exception.CollectInternalInvalidRequestException;
import fr.gouv.vitam.collect.common.exception.CsvParseInternalException;
import fr.gouv.vitam.collect.internal.core.common.ProjectModel;
import fr.gouv.vitam.collect.internal.core.common.TransactionModel;
import fr.gouv.vitam.collect.internal.core.helpers.CsvHelper;
import fr.gouv.vitam.collect.internal.core.helpers.MetadataHelper;
import fr.gouv.vitam.collect.internal.core.helpers.TempWorkspace;
import fr.gouv.vitam.collect.internal.core.repository.MetadataRepository;
import fr.gouv.vitam.collect.internal.core.repository.ProjectRepository;
import fr.gouv.vitam.common.CommonMediaType;
import fr.gouv.vitam.common.database.builder.query.VitamFieldsHelper;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.format.identification.model.FormatIdentifierResponse;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.MetadataType;
import fr.gouv.vitam.common.model.VitamConstants;
import fr.gouv.vitam.common.model.objectgroup.ObjectGroupResponse;
import fr.gouv.vitam.common.model.unit.ArchiveUnitModel;
import fr.gouv.vitam.common.storage.compress.ArchiveEntryInputStream;
import fr.gouv.vitam.common.storage.compress.VitamArchiveStreamFactory;
import fr.gouv.vitam.common.stream.StreamUtils;
import fr.gouv.vitam.worker.core.distribution.JsonLineGenericIterator;
import fr.gouv.vitam.worker.core.distribution.JsonLineModel;
import fr.gouv.vitam.worker.core.distribution.JsonLineWriter;
import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.input.CountingInputStream;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.common.Strings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.AbstractMap.SimpleEntry;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static fr.gouv.vitam.collect.internal.core.helpers.MetadataHelper.STATIC_ATTACHMENT;
import static fr.gouv.vitam.collect.internal.core.helpers.MetadataHelper.findUnitParent;
import static fr.gouv.vitam.common.mapping.mapper.VitamObjectMapper.buildSerializationObjectMapper;
import static fr.gouv.vitam.common.model.IngestWorkflowConstants.CONTENT_FOLDER;

public class FluxService {

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(FluxService.class);

    private static final int BULK_SIZE = 1000;
    private static final String TRANSFORMED_METADATA_JSONL_FILE = "transformed_metadata.jsonl";
    private static final String TITLE = "Title";
    static final String METADATA_CSV_FILE = "metadata.csv";

    private final CollectService collectService;
    private final MetadataService metadataService;
    private final ProjectRepository projectRepository;
    private final MetadataRepository metadataRepository;

    public FluxService(
        CollectService collectService,
        MetadataService metadataService,
        ProjectRepository projectRepository,
        MetadataRepository metadataRepository
    ) {
        this.collectService = collectService;
        this.metadataService = metadataService;
        this.projectRepository = projectRepository;
        this.metadataRepository = metadataRepository;
    }

    public void processStream(InputStream inputStreamObject, TransactionModel transactionModel)
        throws CollectInternalException {
        Optional<ProjectModel> projectById = projectRepository.findProjectById(transactionModel.getProjectId());
        if (projectById.isEmpty()) {
            throw new CollectInternalException("Project not found");
        }
        ProjectModel projectModel = projectById.get();

        try (
            final TempWorkspace tempWorkspace = new TempWorkspace();
            final InputStream inputStreamClosable = StreamUtils.getRemainingReadOnCloseInputStream(inputStreamObject);
            final ArchiveInputStream archiveInputStream = new VitamArchiveStreamFactory()
                .createArchiveInputStream(CommonMediaType.ZIP_TYPE, inputStreamClosable)
        ) {
            ArchiveEntry entry;
            boolean isEmpty = true;
            Map<String, String> unitIds = metadataService.prepareAttachmentUnits(
                projectModel,
                transactionModel.getId()
            );
            File metadataCsvFile = null;
            // create entryInputStream to resolve the stream closed problem
            final ArchiveEntryInputStream entryInputStream = new ArchiveEntryInputStream(archiveInputStream);
            int maxLevel = -1;
            while ((entry = archiveInputStream.getNextEntry()) != null) {
                if (archiveInputStream.canReadEntryData(entry)) {
                    checkNonEmptyBinary(entry);

                    String path = FilenameUtils.normalize(entry.getName());
                    if (!FilenameUtils.equals(entry.getName(), path)) {
                        throw new IllegalStateException("path " + path + " is not canonical");
                    }
                    if (Strings.isNullOrEmpty(path)) {
                        continue;
                    }
                    path = FilenameUtils.normalizeNoEndSeparator(path);
                    if (!entry.isDirectory() && path.equals(METADATA_CSV_FILE)) {
                        if (metadataCsvFile != null) {
                            throw new CollectInternalInvalidRequestException(
                                "Cannot process zip upload for " +
                                projectById +
                                "/" +
                                transactionModel.getId() +
                                ". Multiple metadata update files"
                            );
                        }

                        metadataCsvFile = tempWorkspace.writeToFile(path, entryInputStream);
                    } else {
                        maxLevel = createMetadata(
                            tempWorkspace,
                            transactionModel.getId(),
                            path,
                            entryInputStream,
                            entry.isDirectory(),
                            maxLevel,
                            unitIds,
                            projectModel.getUnitUp() != null
                        );
                    }
                    isEmpty = false;
                }
                entryInputStream.setClosed(false);
            }

            if (isEmpty) {
                throw new CollectInternalException("File is empty");
            }

            File tranformedMetadataFile = null;
            if (metadataCsvFile != null) {
                tranformedMetadataFile = tempWorkspace.getFile(TRANSFORMED_METADATA_JSONL_FILE);
                try (InputStream is = new FileInputStream(metadataCsvFile)) {
                    CsvHelper.convertCsvToMetadataFile(is, tranformedMetadataFile);
                }
            }

            Map<String, String> unitUps = (tranformedMetadataFile != null)
                ? findUnitUps(tranformedMetadataFile, projectModel, unitIds)
                : new HashMap<>();

            bulkWriteUnits(tempWorkspace, maxLevel, unitUps);

            bulkWriteObjectGroups(tempWorkspace);

            if (tranformedMetadataFile != null) {
                try (InputStream is = new FileInputStream(tranformedMetadataFile)) {
                    metadataService.updateUnitsWithMetadataFile(transactionModel.getId(), is);
                }
            }
        } catch (IOException | ArchiveException e) {
            LOGGER.error("An error occurs when try to upload the ZIP: {}", e);
            throw new CollectInternalException("An error occurs when try to upload the ZIP: {}");
        } catch (InvalidParseOperationException | CsvParseInternalException e) {
            throw new CollectInternalException(e.getMessage(), e);
        }
    }

    private void checkNonEmptyBinary(ArchiveEntry entry) throws CollectInternalInvalidRequestException {
        if (!entry.isDirectory() && entry.getSize() == 0L) {
            throw new CollectInternalInvalidRequestException("Cannot upload empty file '" + entry.getName() + "'");
        }
    }

    private Map<String, String> findUnitUps(
        File tranformedMetadataFile,
        ProjectModel projectModel,
        Map<String, String> unitIds
    ) throws FileNotFoundException {
        if (projectModel.getUnitUps() != null) {
            try (
                JsonLineGenericIterator<JsonLineModel> iterator = new JsonLineGenericIterator<>(
                    new FileInputStream(tranformedMetadataFile),
                    new TypeReference<>() {}
                )
            ) {
                return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false)
                    .filter(e -> StringUtils.countMatches(e.getId(), File.separator) == 0)
                    .map(e -> {
                        String id = e.getId();
                        ObjectNode unit = (ObjectNode) e.getParams();
                        unit.put(VitamFieldsHelper.id(), id);
                        return unit;
                    })
                    .map(e -> findUnitParent(e, projectModel.getUnitUps(), unitIds))
                    .filter(e -> Objects.nonNull(e.getValue()))
                    .collect(Collectors.toMap(Entry<String, String>::getKey, Entry<String, String>::getValue));
            }
        } else {
            return new HashMap<>();
        }
    }

    private int createMetadata(
        TempWorkspace tempWorkspace,
        String transactionId,
        String path,
        InputStream entryInputStream,
        boolean isDirectory,
        int maxLevel,
        Map<String, String> unitIds,
        boolean isAttachmentAuExist
    ) throws IOException, CollectInternalException, InvalidParseOperationException {
        LevelType descriptionLevel = isDirectory ? LevelType.RECORD_GRP : LevelType.ITEM;
        String parentPath = FilenameUtils.getPathNoEndSeparator(path);

        String parentUnit;
        if (Strings.isNullOrEmpty(parentPath)) {
            if (isAttachmentAuExist) {
                parentUnit = unitIds.get(STATIC_ATTACHMENT);
            } else {
                parentUnit = null;
            }
        } else {
            parentUnit = unitIds.get(parentPath);
            if (parentUnit == null) {
                LOGGER.debug("Creating implicit parent folder '{}'", parentPath);
                createMetadata(
                    tempWorkspace,
                    transactionId,
                    parentPath,
                    null,
                    true,
                    maxLevel,
                    unitIds,
                    isAttachmentAuExist
                );
            }

            parentUnit = unitIds.get(parentPath);
        }
        String fileName = FilenameUtils.getName(path);

        ArchiveUnitModel unit = MetadataHelper.createUnit(transactionId, descriptionLevel, fileName, parentUnit);

        unitIds.put(path, unit.getId());
        if (!isDirectory) {
            String extension = FilenameUtils.getExtension(fileName).toLowerCase();
            String objectId = GUIDFactory.newGUID().getId();
            String newFilename = (Strings.isNullOrEmpty(extension)) ? objectId : objectId + "." + extension;

            File binaryFile = tempWorkspace.writeToFile(newFilename, entryInputStream);
            try {
                FormatIdentifierResponse formatIdentifierResponse = collectService.detectFileFormat(binaryFile);
                Entry<String, Long> binaryInformations = writeObjectToWorkspace(transactionId, binaryFile, newFilename);
                ObjectGroupResponse objectGroup = MetadataHelper.createObjectGroup(
                    transactionId,
                    fileName,
                    objectId,
                    newFilename,
                    formatIdentifierResponse,
                    binaryInformations.getKey(),
                    binaryInformations.getValue()
                );
                writeObjectGroupToTemporaryFile(tempWorkspace, objectGroup);
                unit.setOg(objectGroup.getId());
            } finally {
                Files.deleteIfExists(binaryFile.toPath());
            }
        }

        maxLevel = writeUnitToTemporaryFile(
            tempWorkspace,
            StringUtils.countMatches(path, File.separator),
            maxLevel,
            unit
        );
        return maxLevel;
    }

    private void bulkWriteUnits(TempWorkspace tempWorkspace, int maxLevel, Map<String, String> unitUps)
        throws IOException {
        for (int level = 0; level <= maxLevel; level++) {
            File unitFile = tempWorkspace.getFile(
                MetadataType.UNIT.getName() + "_" + level + VitamConstants.JSONL_EXTENSION
            );
            Iterator<ObjectNode> unitIterator = new JsonLineGenericIterator<>(
                new FileInputStream(unitFile),
                new TypeReference<>() {}
            );

            if (level == 0 && !unitUps.isEmpty()) {
                unitIterator = IteratorUtils.transformedIterator(unitIterator, e -> updateParent(e, unitUps));
            }

            Iterators.partition(unitIterator, BULK_SIZE).forEachRemaining(units -> {
                try {
                    metadataRepository.saveArchiveUnits(units);
                } catch (CollectInternalException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private ObjectNode updateParent(ObjectNode unit, Map<String, String> unitUps) {
        String title = unit.get(TITLE).asText();
        String up = unitUps.get(title);
        if (up != null) {
            unit.set(VitamFieldsHelper.unitups(), JsonHandler.createArrayNode().add(up));
        }
        return unit;
    }

    private void bulkWriteObjectGroups(TempWorkspace tempWorkspace) throws IOException {
        File ogFile = tempWorkspace.getFile(MetadataType.OBJECTGROUP.getName() + VitamConstants.JSONL_EXTENSION);
        JsonLineGenericIterator<ObjectNode> ogIterator = new JsonLineGenericIterator<>(
            new FileInputStream(ogFile),
            new TypeReference<>() {}
        );
        Iterators.partition(ogIterator, BULK_SIZE).forEachRemaining(objectGroups -> {
            try {
                metadataRepository.saveObjectGroups(objectGroups);
            } catch (CollectInternalException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void writeObjectGroupToTemporaryFile(TempWorkspace tempWorkspace, Object objectGroup) throws IOException {
        File file = tempWorkspace.getFile(MetadataType.OBJECTGROUP.getName() + VitamConstants.JSONL_EXTENSION);
        try (JsonLineWriter writer = new JsonLineWriter(new FileOutputStream(file, true), file.length() == 0)) {
            JsonNode objectGroupToSave = buildSerializationObjectMapper().convertValue(objectGroup, JsonNode.class);
            writer.addEntry(objectGroupToSave);
        }
    }

    private int writeUnitToTemporaryFile(TempWorkspace tempWorkspace, int level, int maxLevel, Object unit)
        throws IOException {
        File file = tempWorkspace.getFile(MetadataType.UNIT.getName() + "_" + level + VitamConstants.JSONL_EXTENSION);
        try (JsonLineWriter writer = new JsonLineWriter(new FileOutputStream(file, true), file.length() == 0)) {
            JsonNode unitToSave = buildSerializationObjectMapper().convertValue(unit, JsonNode.class);
            writer.addEntry(unitToSave);
        }
        return Math.max(maxLevel, level);
    }

    private Entry<String, Long> writeObjectToWorkspace(String transactionId, File fileToWrite, String fileName)
        throws IOException, CollectInternalException {
        try (CountingInputStream countingInputStream = new CountingInputStream(new FileInputStream(fileToWrite))) {
            String digest = collectService.pushStreamToWorkspace(
                transactionId,
                countingInputStream,
                CONTENT_FOLDER.concat(File.separator).concat(fileName)
            );

            return new SimpleEntry<>(digest, countingInputStream.getByteCount());
        }
    }
}
