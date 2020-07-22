/*
 * Copyright French Prime minister Office/SGMAP/DINSIC/Vitam Program (2015-2020)
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
package fr.gouv.vitam.functional.administration.agencies.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import fr.gouv.vitam.common.LocalDateUtil;
import fr.gouv.vitam.common.PropertiesUtils;
import fr.gouv.vitam.common.client.OntologyLoader;
import fr.gouv.vitam.common.database.builder.query.QueryHelper;
import fr.gouv.vitam.common.database.builder.query.VitamFieldsHelper;
import fr.gouv.vitam.common.database.builder.query.action.SetAction;
import fr.gouv.vitam.common.database.builder.request.exception.InvalidCreateOperationException;
import fr.gouv.vitam.common.database.builder.request.multiple.SelectMultiQuery;
import fr.gouv.vitam.common.database.builder.request.single.Delete;
import fr.gouv.vitam.common.database.builder.request.single.Select;
import fr.gouv.vitam.common.database.builder.request.single.Update;
import fr.gouv.vitam.common.database.parser.request.adapter.VarNameAdapter;
import fr.gouv.vitam.common.database.parser.request.single.UpdateParserSingle;
import fr.gouv.vitam.common.database.server.DbRequestResult;
import fr.gouv.vitam.common.error.VitamCode;
import fr.gouv.vitam.common.error.VitamError;
import fr.gouv.vitam.common.exception.BadRequestException;
import fr.gouv.vitam.common.exception.DatabaseException;
import fr.gouv.vitam.common.exception.DocumentAlreadyExistsException;
import fr.gouv.vitam.common.exception.InvalidGuidOperationException;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.SchemaValidationException;
import fr.gouv.vitam.common.exception.VitamException;
import fr.gouv.vitam.common.guid.GUID;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.guid.GUIDReader;
import fr.gouv.vitam.common.i18n.VitamErrorMessages;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.RequestResponse;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.model.VitamAutoCloseable;
import fr.gouv.vitam.common.model.administration.AccessContractModel;
import fr.gouv.vitam.common.model.administration.AgenciesModel;
import fr.gouv.vitam.common.parameter.ParameterHelper;
import fr.gouv.vitam.common.stream.StreamUtils;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.functional.administration.ContractsFinder;
import fr.gouv.vitam.functional.administration.common.AccessContract;
import fr.gouv.vitam.functional.administration.common.AccessionRegisterDetail;
import fr.gouv.vitam.functional.administration.common.Agencies;
import fr.gouv.vitam.functional.administration.common.AgenciesParser;
import fr.gouv.vitam.functional.administration.common.ErrorReportAgencies;
import fr.gouv.vitam.functional.administration.common.FileAgenciesErrorCode;
import fr.gouv.vitam.functional.administration.common.FunctionalBackupService;
import fr.gouv.vitam.functional.administration.common.ReportConstants;
import fr.gouv.vitam.functional.administration.common.VitamErrorUtils;
import fr.gouv.vitam.functional.administration.common.counter.SequenceType;
import fr.gouv.vitam.functional.administration.common.counter.VitamCounterService;
import fr.gouv.vitam.functional.administration.common.exception.AgencyImportDeletionException;
import fr.gouv.vitam.functional.administration.common.exception.ReferentialException;
import fr.gouv.vitam.functional.administration.common.server.FunctionalAdminCollections;
import fr.gouv.vitam.functional.administration.common.server.MongoDbAccessAdminImpl;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClient;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClientFactory;
import fr.gouv.vitam.metadata.api.exception.MetaDataClientServerException;
import fr.gouv.vitam.metadata.api.exception.MetaDataDocumentSizeException;
import fr.gouv.vitam.metadata.api.exception.MetaDataExecutionException;
import fr.gouv.vitam.metadata.client.MetaDataClient;
import fr.gouv.vitam.metadata.client.MetaDataClientFactory;
import fr.gouv.vitam.storage.engine.common.model.DataCategory;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;

import javax.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static fr.gouv.vitam.common.database.builder.query.QueryHelper.eq;
import static fr.gouv.vitam.common.database.builder.query.QueryHelper.in;
import static fr.gouv.vitam.functional.administration.common.Agencies.DESCRIPTION;
import static fr.gouv.vitam.functional.administration.common.Agencies.IDENTIFIER;
import static fr.gouv.vitam.functional.administration.common.Agencies.NAME;
import static fr.gouv.vitam.functional.administration.common.server.FunctionalAdminCollections.AGENCIES;

/**
 * AgenciesService class allowing multiple operation on AgenciesService collection
 */
public class AgenciesService implements VitamAutoCloseable {

    /**
     * IMPORT_AGENCIES
     */
    static final String AGENCIES_IMPORT_EVENT = "IMPORT_AGENCIES";
    /**
     * AGENCIES_REPORT
     */
    private static final String AGENCIES_REPORT_EVENT = "AGENCIES_REPORT";
    /**
     * BACKUP_AGENCIES
     */
    private static final String AGENCIES_BACKUP_EVENT = "BACKUP_AGENCIES";
    /**
     * IMPORT_AGENCIES_BACKUP_CSV
     */
    private static final String IMPORT_AGENCIES_BACKUP_CSV = "IMPORT_AGENCIES_BACKUP_CSV";
    public static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(AgenciesService.class);
    private static final String AGENCIES_IMPORT_DELETION_ERROR = "DELETION";
    private static final String AGENCIES_IMPORT_AU_USAGE = AGENCIES_IMPORT_EVENT + ".USED_AU";
    private static final String AGENCIES_IMPORT_CONTRACT_USAGE = AGENCIES_IMPORT_EVENT + ".USED_CONTRACT";
    private static final String CSV = "csv";

    private static final String INVALID_CSV_FILE = "Invalid CSV File";

    private static final String MESSAGE_ERROR = "Import agency error > ";
    private static final String UND_ID = "_id";
    private static final String UND_TENANT = "_tenant";

    private final MongoDbAccessAdminImpl mongoAccess;
    private final LogbookOperationsClient logBookclient;
    private final VitamCounterService vitamCounterService;
    private final LogbookOperationsClientFactory logbookOperationsClientFactory;
    private final FunctionalBackupService backupService;
    private final OntologyLoader ontologyLoader;
    private AgenciesManager manager;
    private Map<Integer, List<ErrorReportAgencies>> errorsMap;
    private Set<AgenciesModel> usedAgenciesByContracts;
    private Set<AgenciesModel> usedAgenciesByAU;
    private Set<AgenciesModel> unusedAgenciesToDelete;
    private Set<AgenciesModel> agenciesToInsert;
    private Set<AgenciesModel> agenciesToUpdate;
    private Set<AgenciesModel> agenciesToDelete;
    private Set<AgenciesModel> agenciesToImport = new HashSet<>();
    private Set<AgenciesModel> agenciesInDb;
    private GUID eip;
    private ContractsFinder finder;

    public AgenciesService(MongoDbAccessAdminImpl mongoAccess,
        VitamCounterService vitamCounterService, FunctionalBackupService backupService, OntologyLoader ontologyLoader)
        throws InvalidGuidOperationException {
        this.mongoAccess = mongoAccess;
        this.vitamCounterService = vitamCounterService;
        this.backupService = backupService;
        this.logbookOperationsClientFactory = LogbookOperationsClientFactory.getInstance();
        this.logBookclient = LogbookOperationsClientFactory.getInstance().getClient();
        this.errorsMap = new HashMap<>();
        this.usedAgenciesByContracts = new HashSet<>();
        this.usedAgenciesByAU = new HashSet<>();
        this.unusedAgenciesToDelete = new HashSet<>();
        this.agenciesToInsert = new HashSet<>();
        this.agenciesToUpdate = new HashSet<>();
        this.agenciesToDelete = new HashSet<>();
        this.agenciesToImport = new HashSet<>();
        this.agenciesInDb = new HashSet<>();
        this.finder = new ContractsFinder(mongoAccess, vitamCounterService);
        this.eip = GUIDReader.getGUID(VitamThreadUtils.getVitamSession().getRequestId());
        this.manager = new AgenciesManager(logBookclient, eip);
        this.ontologyLoader = ontologyLoader;
    }

    @VisibleForTesting
    public AgenciesService(
        MongoDbAccessAdminImpl mongoAccess,
        VitamCounterService vitamCounterService,
        FunctionalBackupService backupService,
        LogbookOperationsClientFactory logbookOperationsClientFactory,
        AgenciesManager manager,
        Set<AgenciesModel> agenciesInDb,
        Set<AgenciesModel> agenciesToDelete,
        Set<AgenciesModel> agenciesToInsert,
        Set<AgenciesModel> agenciesToUpdate,
        Set<AgenciesModel> usedAgenciesByAU,
        Set<AgenciesModel> usedAgenciesByContracts,
        Set<AgenciesModel> unusedAgenciesToDelete,
        OntologyLoader ontologyLoader) {
        this.mongoAccess = mongoAccess;
        this.vitamCounterService = vitamCounterService;
        this.backupService = backupService;
        this.logbookOperationsClientFactory = logbookOperationsClientFactory;
        this.logBookclient = this.logbookOperationsClientFactory.getClient();
        this.errorsMap = new HashMap<>();
        this.agenciesInDb = agenciesInDb;
        this.agenciesToDelete = agenciesToDelete;
        this.agenciesToInsert = agenciesToInsert;
        this.agenciesToUpdate = agenciesToUpdate;
        this.usedAgenciesByAU = usedAgenciesByAU;
        this.usedAgenciesByContracts = usedAgenciesByContracts;
        this.finder = new ContractsFinder(mongoAccess, vitamCounterService);
        this.manager = manager;
        this.unusedAgenciesToDelete = unusedAgenciesToDelete;
        this.ontologyLoader = ontologyLoader;
    }

    /**
     * Find documents with a query
     *
     * @param select the query as a json
     * @return list of response as a RequestResponseOK object
     * @throws ReferentialException thrown if an error is encountered
     */
    public RequestResponseOK findDocuments(JsonNode select)
        throws ReferentialException {
        return findAgencies(select).getRequestResponseOK(select, Agencies.class);
    }

    private List<Agencies> findAllAgencies() throws ReferentialException {
        final Select select = new Select();
        RequestResponseOK<Agencies> response = findDocuments(select.getFinalSelect());
        return response.getResults();
    }


    /**
     * Construct query DSL for find all Agencies (referential)
     *
     * @throws VitamException thrown if query could not be executed
     */
    public void findAllAgenciesUsedByUnits() throws VitamException {
        // no need to do the check, just log status ok
        if (agenciesToUpdate.isEmpty()) {
            manager.logEventSuccess(AGENCIES_IMPORT_AU_USAGE);
            return;
        }

        for (AgenciesModel agency : agenciesToUpdate) {
            final SelectMultiQuery selectMultiple = new SelectMultiQuery();
            try (MetaDataClient metaDataClient = MetaDataClientFactory.getInstance().getClient()) {

                // FIXME Add limit when Dbrequest is Fix and when distinct is implement in DbRequest:
                ObjectNode objectNode = JsonHandler.createObjectNode();
                objectNode.put(VitamFieldsHelper.id(), 1);
                ArrayNode arrayNode = JsonHandler.createArrayNode();
                selectMultiple
                    .setQuery(eq(VitamFieldsHelper.management() + ".OriginatingAgency", agency.getIdentifier()));
                selectMultiple.addRoots(arrayNode);
                selectMultiple.addProjection(JsonHandler.createObjectNode().set("$fields", objectNode));

                final JsonNode unitsResultNode = metaDataClient.selectUnits(selectMultiple.getFinalSelect());

                if (unitsResultNode != null && unitsResultNode.get("$results").size() > 0) {
                    usedAgenciesByAU.add(agency);
                }
            } catch (InvalidCreateOperationException | InvalidParseOperationException | MetaDataExecutionException |
                MetaDataDocumentSizeException | MetaDataClientServerException e) {
                LOGGER.error("Query construction not valid ", e);
            }
        }

        // no update is done on used agencies, just log success
        if (usedAgenciesByAU.isEmpty()) {
            manager.logEventSuccess(AGENCIES_IMPORT_AU_USAGE);
            return;
        }

        // log warning
        final ArrayNode usedAgenciesAUNode = JsonHandler.createArrayNode();
        usedAgenciesByAU.forEach(agency -> usedAgenciesAUNode.add(agency.getIdentifier()));

        final ObjectNode data = JsonHandler.createObjectNode();
        data.set(ReportConstants.ADDITIONAL_INFORMATION, usedAgenciesAUNode);

        manager.setEvDetData(data);

        manager.logEventWarning(AGENCIES_IMPORT_AU_USAGE);
    }

    /**
     * Find all agencies used by access contracts
     *
     * @throws InvalidCreateOperationException thrown if the query could not be created
     * @throws VitamException thrown if an error is encountered
     */
    public void findAllAgenciesUsedByAccessContracts() throws InvalidCreateOperationException, VitamException {
        // no need to do the check, just log status ok
        if (agenciesToUpdate.isEmpty()) {
            manager.logEventSuccess(AGENCIES_IMPORT_CONTRACT_USAGE);
            return;
        }

        for (AgenciesModel agency : agenciesToUpdate) {

            final Select select = new Select();
            select.setQuery(in(AccessContract.ORIGINATINGAGENCIES, agency.getIdentifier()));
            final JsonNode queryDsl = select.getFinalSelect();

            RequestResponseOK<AccessContractModel> result = finder.findAccessContrats(queryDsl);

            if (result != null && !result.getResults().isEmpty()) {
                usedAgenciesByContracts.add(agency);

            }
        }

        // no update is done on used agencies, just log success
        if (usedAgenciesByContracts.isEmpty()) {
            manager.logEventSuccess(AGENCIES_IMPORT_CONTRACT_USAGE);
            return;
        }

        // log warning
        final ArrayNode usedAgenciesContractNode = JsonHandler.createArrayNode();
        usedAgenciesByContracts.forEach(agency -> usedAgenciesContractNode.add(agency.getIdentifier()));

        final ObjectNode data = JsonHandler.createObjectNode();
        data.set(ReportConstants.ADDITIONAL_INFORMATION, usedAgenciesContractNode);

        manager.setEvDetData(data);

        manager.logEventWarning(AGENCIES_IMPORT_CONTRACT_USAGE);
    }

    private File convertInputStreamToFile(InputStream agenciesStream) throws IOException {
        try {
            String uniqueFileId = GUIDFactory.newGUID().getId();
            File csvFile = PropertiesUtils.fileFromTmpFolder(uniqueFileId + "." + AgenciesService.CSV);
            Files.copy(agenciesStream, csvFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return csvFile;
        } finally {
            StreamUtils.closeSilently(agenciesStream);
        }
    }

    /**
     * Check file integrity
     *
     * @param stream the stream to be checked
     * @throws ReferentialException thrown if the file is not correct
     * @throws IOException thrown if the file could be read
     */
    public void checkFile(InputStream stream)
        throws ReferentialException,
        IOException {
        int tenantId = VitamThreadUtils.getVitamSession().getTenantId();
        int lineNumber = 1;
        File csvFileReader = convertInputStreamToFile(stream);
        InputStream csvFileInputStream = null;

        try (FileReader reader = new FileReader(csvFileReader)) {
            final CSVParser parser =
                new CSVParser(reader, CSVFormat.DEFAULT.withHeader().withTrim().withIgnoreEmptyLines(false));
            final HashSet<String> idsset = new HashSet<>();
            try {
                for (final CSVRecord record : parser) {
                    List<ErrorReportAgencies> errors = new ArrayList<>();
                    lineNumber++;
                    if (checkRecords(record)) {
                        final String identifier = record.get(AgenciesModel.TAG_IDENTIFIER);
                        final String name = record.get(AgenciesModel.TAG_NAME);
                        final String description = record.get(AgenciesModel.TAG_DESCRIPTION);

                        final AgenciesModel agenciesModel = new AgenciesModel(identifier, name, description, tenantId);

                        checkParametersNotEmpty(identifier, name, description, errors, lineNumber);

                        if (idsset.contains(identifier)) {
                            errors
                                .add(new ErrorReportAgencies(FileAgenciesErrorCode.STP_IMPORT_AGENCIES_ID_DUPLICATION,
                                    lineNumber, agenciesModel));
                        }

                        idsset.add(identifier);

                        if (!errors.isEmpty()) {
                            errorsMap.put(lineNumber, errors);
                        }
                    }
                }
            } catch (IllegalArgumentException e) {
                String message = e.getMessage();
                if (message.contains("Name not found")) {
                    message = ReportConstants.FILE_INVALID + "Name";
                }
                if (message.contains("Identifier not found")) {
                    message = ReportConstants.FILE_INVALID + "Identifier";
                }
                if (message.contains("Description not found")) {
                    message = ReportConstants.FILE_INVALID + "Description";
                }
                List<ErrorReportAgencies> errors = new ArrayList<>();
                errors
                    .add(new ErrorReportAgencies(FileAgenciesErrorCode.STP_IMPORT_AGENCIES_NOT_CSV_FORMAT,
                        lineNumber, message));
                errorsMap.put(lineNumber, errors);

                throw new ReferentialException(message);
            } catch (Exception e) {
                throw new ReferentialException(e);
            }

            csvFileInputStream = new FileInputStream(csvFileReader);
            agenciesToImport = new HashSet<>(AgenciesParser.readFromCsv(csvFileInputStream));

            if (errorsMap.size() > 0) {
                throw new ReferentialException(INVALID_CSV_FILE);
            }
        } finally {
            IOUtils.closeQuietly(csvFileInputStream);
            if (csvFileReader != null && !csvFileReader.delete()) {
                LOGGER.warn("Failed to delete file");
            }
        }
    }

    /**
     * Check agencies in database
     */
    public void checkAgenciesInDb() throws ReferentialException {

        List<Agencies> tempAgencies = findAllAgencies();
        tempAgencies.forEach(a -> agenciesInDb.add(a.wrap()));

    }

    private void createInsertUpdateDeleteList() {

        agenciesToInsert.addAll(agenciesToImport);

        agenciesToDelete.addAll(agenciesInDb);

        for (AgenciesModel agencyToImport : agenciesToImport) {
            for (AgenciesModel agencyInDb : agenciesInDb) {

                boolean descriptionChanged =
                    !Objects.equals(agencyInDb.getDescription(), agencyToImport.getDescription());
                boolean nameChanged = !Objects.equals(agencyInDb.getName(), agencyToImport.getName());

                if (agencyInDb.getIdentifier().equals(agencyToImport.getIdentifier())
                    && (nameChanged || descriptionChanged)) {

                    agenciesToUpdate.add(agencyToImport);
                }

                if (agencyToImport.getIdentifier().equals(agencyInDb.getIdentifier())) {
                    agenciesToInsert.remove(agencyToImport);
                }

                if (agencyInDb.getIdentifier().equals(agencyToImport.getIdentifier())) {
                    agenciesToDelete.remove(agencyInDb);
                }
            }
        }
    }

    private void checkParametersNotEmpty(String identifier, String name, String description,
        List<ErrorReportAgencies> errors, int line) {
        List<String> missingParam = new ArrayList<>();
        if (StringUtils.isEmpty(identifier)) {
            missingParam.add(AgenciesModel.TAG_IDENTIFIER);
        }
        if (StringUtils.isEmpty(name)) {
            missingParam.add(AgenciesModel.TAG_NAME);
        }
        if (!missingParam.isEmpty()) {
            errors.add(new ErrorReportAgencies(FileAgenciesErrorCode.STP_IMPORT_AGENCIES_MISSING_INFORMATIONS, line,
                String.join("", missingParam)));
        }
    }

    private boolean checkRecords(CSVRecord record) {
        return record.get(IDENTIFIER) != null && record.get(NAME) != null && record.get(DESCRIPTION) != null;
    }


    /**
     * Import an input stream into agencies collection
     *
     * @param stream the stream to be imported
     * @param filename the file name
     * @return a response as a RequestResponse <AgenciesModel> object
     * @throws VitamException thrown if logbook could not be initialized
     * @throws IOException thrown in case or error with stream
     * @throws InvalidCreateOperationException thrown if the error report could not be stored
     */
    public RequestResponse<AgenciesModel> importAgencies(InputStream stream, String filename)
        throws VitamException, IOException {

        manager.logStarted(AGENCIES_IMPORT_EVENT);
        InputStream reportStream;
        File file = null;
        InputStream csvFileInputStream = null;
        try {

            file = convertInputStreamToFile(stream);

            try (FileInputStream inputStream = new FileInputStream(file)) {
                checkFile(inputStream);
            }

            checkAgenciesInDb();

            createInsertUpdateDeleteList();

            checkAgenciesDeletion();

            findAllAgenciesUsedByAccessContracts();

            findAllAgenciesUsedByUnits();

            commitAgencies();

            reportStream = generateReportOK();
            // store report
            backupService.saveFile(reportStream, eip, AGENCIES_REPORT_EVENT, DataCategory.REPORT,
                eip + ".json");

            // store source File
            csvFileInputStream = new FileInputStream(file);
            backupService.saveFile(csvFileInputStream, eip, IMPORT_AGENCIES_BACKUP_CSV, DataCategory.REPORT,
                eip + ".csv");
            // store collection
            backupService.saveCollectionAndSequence(eip, AGENCIES_BACKUP_EVENT,
                FunctionalAdminCollections.AGENCIES, eip.toString());

            manager.logFinish(filename);
        } catch (final AgencyImportDeletionException e) {

            LOGGER.error(MESSAGE_ERROR, e);
            InputStream errorStream = generateErrorReport();

            backupService.saveFile(errorStream, eip, AGENCIES_REPORT_EVENT, DataCategory.REPORT,
                eip + ".json");
            errorStream.close();

            ObjectNode errorMessage = JsonHandler.createObjectNode();
            String listAgencies = agenciesToDelete.stream().map(AgenciesModel::getIdentifier)
                .collect(Collectors.joining(","));
            errorMessage.put("Agencies ", listAgencies);

            return generateVitamBadRequestError(errorMessage.toString());

        } catch (SchemaValidationException | BadRequestException e) {
            LOGGER.error(MESSAGE_ERROR, e);

            InputStream errorStream = generateErrorReport();
            backupService.saveFile(errorStream, eip, AGENCIES_REPORT_EVENT, DataCategory.REPORT,
                eip + ".json");
            errorStream.close();

            return getVitamError(VitamCode.AGENCIES_VALIDATION_ERROR.getItem(), e.getMessage()
            ).setHttpCode(Response.Status.BAD_REQUEST.getStatusCode());
        } catch (final Exception e) {
            LOGGER.error(MESSAGE_ERROR, e);
            InputStream errorStream = generateErrorReport();
            backupService.saveFile(errorStream, eip, AGENCIES_REPORT_EVENT, DataCategory.REPORT,
                eip + ".json");
            errorStream.close();
            return generateVitamError(MESSAGE_ERROR + e.getMessage());
        } finally {
            IOUtils.closeQuietly(csvFileInputStream);
            if (file != null && !file.delete()) {
                LOGGER.warn("Failed to delete file");
            }
        }

        return new RequestResponseOK<AgenciesModel>().setHttpCode(Response.Status.CREATED.getStatusCode());

    }

    private void checkAgenciesDeletion() throws VitamException, InvalidCreateOperationException {

        if (agenciesToDelete.isEmpty()) {
            manager.logEventSuccess(AGENCIES_IMPORT_CONTRACT_USAGE);
            return;
        }

        for (AgenciesModel agency : agenciesToDelete) {

            final Select select = new Select();
            select.setQuery(in(AccessContract.ORIGINATINGAGENCIES, agency.getIdentifier()));
            final JsonNode queryDsl = select.getFinalSelect();

            RequestResponseOK<AccessContractModel> result = finder.findAccessContrats(queryDsl);

            if (result != null && result.getResults().isEmpty()) {
                unusedAgenciesToDelete.add(agency);
            }
        }

        for (AgenciesModel agency : agenciesToDelete) {
            Select select = new Select();
            select.setQuery(
                QueryHelper.and()
                    .add(QueryHelper.eq(AccessionRegisterDetail.ORIGINATING_AGENCY, agency.getIdentifier())));
            final JsonNode queryDsl = select.getFinalSelect();
            DbRequestResult result =
                mongoAccess.findDocuments(queryDsl, FunctionalAdminCollections.ACCESSION_REGISTER_SUMMARY);
            RequestResponseOK<AccessionRegisterDetail> response =
                result.getRequestResponseOK(queryDsl, AccessionRegisterDetail.class);

            if (response != null && !response.getResults().isEmpty()) {
                throw new AgencyImportDeletionException("used Agencies want to be deleted");
            }
        }

        // not used anywhere , then will be deleted
        unusedAgenciesToDelete = new HashSet<>(unusedAgenciesToDelete);
        if (!unusedAgenciesToDelete.isEmpty()) {
            return;
        }

        manager.logError(AGENCIES_IMPORT_AU_USAGE, "used Agencies want to be deleted");
        throw new AgencyImportDeletionException("used Agencies want to be deleted");
    }

    private VitamError getVitamError(String vitamCode, String error) {
        return VitamErrorUtils.getVitamError(vitamCode, error, "Agencies", StatusCode.KO);
    }

    private VitamError generateVitamBadRequestError(String err) throws VitamException {
        manager.logError(err, AgenciesService.AGENCIES_IMPORT_DELETION_ERROR);
        return new VitamError(VitamCode.AGENCIES_VALIDATION_ERROR.getItem())
            .setHttpCode(Response.Status.BAD_REQUEST.getStatusCode())
            .setCode(VitamCode.AGENCIES_VALIDATION_ERROR.getItem())
            .setDescription(err)
            .setHttpCode(Response.Status.BAD_REQUEST.getStatusCode());
    }

    private VitamError generateVitamError(String err) throws VitamException {
        manager.logError(err, null);
        return new VitamError(VitamCode.AGENCIES_VALIDATION_ERROR.getItem())
            .setHttpCode(Response.Status.BAD_REQUEST.getStatusCode())
            .setCode(VitamCode.GLOBAL_INTERNAL_SERVER_ERROR.getItem())
            .setDescription(err)
            .setHttpCode(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
    }

    private void insertDocuments(Set<AgenciesModel> agenciesToInsert, Integer sequence)
        throws InvalidParseOperationException, ReferentialException, SchemaValidationException,
        DocumentAlreadyExistsException {

        ArrayNode agenciesNodeToPersist = JsonHandler.createArrayNode();

        for (final AgenciesModel agency : agenciesToInsert) {
            agency.setId(GUIDFactory.newGUID().getId());
            agency.setTenant(ParameterHelper.getTenantParameter());
            ObjectNode agencyNode = (ObjectNode) JsonHandler.toJsonNode(agency);
            JsonNode jsonNode = agencyNode.remove(VitamFieldsHelper.id());
            if (jsonNode != null) {
                agencyNode.set(UND_ID, jsonNode);
            }
            JsonNode hashTenant = agencyNode.remove(VitamFieldsHelper.tenant());
            if (hashTenant != null) {
                agencyNode.set(UND_TENANT, hashTenant);
            }
            agenciesNodeToPersist.add(agencyNode);
        }

        if (!agenciesToInsert.isEmpty()) {
            mongoAccess.insertDocuments(agenciesNodeToPersist, AGENCIES, sequence).close();
        }

    }

    private void commitAgencies()
        throws InvalidParseOperationException, ReferentialException, InvalidCreateOperationException,
        SchemaValidationException, BadRequestException, DocumentAlreadyExistsException {

        Integer sequence = vitamCounterService
            .getNextSequence(ParameterHelper.getTenantParameter(), SequenceType.AGENCIES_SEQUENCE);

        for (AgenciesModel agency : agenciesToUpdate) {
            updateAgency(agency, sequence);
        }

        unusedAgenciesToDelete.forEach(this::deleteAgency);

        if (!agenciesToInsert.isEmpty()) {
            insertDocuments(agenciesToInsert, sequence);
        }
    }

    private void updateAgency(AgenciesModel fileAgenciesModel, Integer sequence)
        throws InvalidCreateOperationException,
        ReferentialException,
        InvalidParseOperationException, SchemaValidationException, BadRequestException {

        final UpdateParserSingle updateParser = new UpdateParserSingle(new VarNameAdapter());
        final Update updateFileAgencies = new Update();
        List<SetAction> actions = new ArrayList<>();
        SetAction setAgencyValue = new SetAction(AgenciesModel.TAG_NAME, fileAgenciesModel.getName());
        SetAction setAgencyDescription =
            new SetAction(AgenciesModel.TAG_DESCRIPTION, fileAgenciesModel.getDescription());

        actions.add(setAgencyValue);
        actions.add(setAgencyDescription);
        updateFileAgencies.setQuery(eq(AgenciesModel.TAG_IDENTIFIER, fileAgenciesModel.getIdentifier()));
        updateFileAgencies.addActions(actions.toArray(new SetAction[0]));

        updateParser.parse(updateFileAgencies.getFinalUpdate());

        mongoAccess.updateData(updateParser.getRequest().getFinalUpdate(), AGENCIES, sequence);
    }

    private void deleteAgency(AgenciesModel fileAgenciesModel) {
        final Delete delete = new Delete();
        DbRequestResult result;

        try {
            delete.setQuery(eq(AgenciesModel.TAG_IDENTIFIER, fileAgenciesModel.getIdentifier()));
            result = mongoAccess.deleteCollectionForTesting(FunctionalAdminCollections.AGENCIES, delete);
            result.close();
        } catch (InvalidCreateOperationException | DatabaseException e) {
            LOGGER.error(e);
        }
    }

    /**
     * Find agencies with a specific query
     *
     * @param queryDsl the query to be executed
     * @return a DbRequestResult containing agencies
     * @throws ReferentialException thrown if the query could not be executed
     */
    public DbRequestResult findAgencies(JsonNode queryDsl)
        throws ReferentialException {
        return mongoAccess.findDocuments(queryDsl, AGENCIES);
    }

    private InputStream generateReportOK() {
        AgenciesReport reportFinal = generateReport();
        return new ByteArrayInputStream(JsonHandler.unprettyPrint(reportFinal).getBytes(StandardCharsets.UTF_8));
    }

    private AgenciesReport generateReport() {

        final ArrayList<String> insertAgencies = new ArrayList<>();
        final ArrayList<String> updateAgencies = new ArrayList<>();
        final ArrayList<String> usedAgenciesContract = new ArrayList<>();
        final ArrayList<String> usedAgenciesAU = new ArrayList<>();
        final ArrayList<String> agenciesTodelete = new ArrayList<>();
        final ArrayList<String> allAgencies = new ArrayList<>();
        AgenciesReport report = new AgenciesReport();
        HashMap<String, String> guidmasterNode = new HashMap<>();

        guidmasterNode.put(ReportConstants.EV_TYPE, AGENCIES_IMPORT_EVENT);
        guidmasterNode.put(ReportConstants.EV_DATE_TIME, LocalDateUtil.getFormattedDateForMongo(LocalDateUtil.now()));
        if (eip == null) {
            eip = GUIDFactory.newOperationLogbookGUID(ParameterHelper.getTenantParameter());
        }
        guidmasterNode.put(ReportConstants.EV_ID, eip.toString());

        agenciesToInsert.forEach(agency -> insertAgencies.add(agency.getIdentifier()));

        agenciesToUpdate.forEach(agency -> updateAgencies.add(agency.getIdentifier()));

        usedAgenciesByContracts.forEach(agency -> usedAgenciesContract.add(agency.getIdentifier()));

        usedAgenciesByAU.forEach(agency -> usedAgenciesAU.add(agency.getIdentifier()));

        agenciesToDelete.forEach(agency -> agenciesTodelete.add(agency.getIdentifier()));

        agenciesToImport.forEach(agency -> allAgencies.add(agency.getIdentifier()));

        report.setJdo(guidmasterNode);
        report.setAgenciesToImport(allAgencies);
        report.setInsertAgencies(insertAgencies);
        report.setUpdatedAgencies(updateAgencies);
        report.setUsedAgenciesByContracts(usedAgenciesContract);
        report.setUsedAgenciesByAu(usedAgenciesAU);
        report.setUsedAgenciesToDelete(agenciesTodelete);

        return report;
    }


    /**
     * Generate an error report
     *
     * @return an input stream containing the report
     */
    public InputStream generateErrorReport() {

        final AgenciesReport reportFinal = generateReport();

        final ArrayNode messagesArrayNode = JsonHandler.createArrayNode();
        final HashMap<String, Object> errors = new HashMap<>();

        for (Integer line : errorsMap.keySet()) {
            List<ErrorReportAgencies> errorsReports = errorsMap.get(line);
            for (ErrorReportAgencies error : errorsReports) {
                final ObjectNode errorNode = JsonHandler.createObjectNode();
                errorNode.put(ReportConstants.CODE, error.getCode().name() + ".KO");
                errorNode.put(ReportConstants.MESSAGE, VitamErrorMessages.getFromKey(error.getCode().name()));
                switch (error.getCode()) {
                    case STP_IMPORT_AGENCIES_MISSING_INFORMATIONS:
                        errorNode.put(ReportConstants.ADDITIONAL_INFORMATION,
                            error.getMissingInformations());
                        break;
                    case STP_IMPORT_AGENCIES_ID_DUPLICATION:
                        errorNode.put(ReportConstants.ADDITIONAL_INFORMATION,
                            error.getFileAgenciesModel().getId());
                        break;
                    case STP_IMPORT_AGENCIES_NOT_CSV_FORMAT:
                    case STP_IMPORT_AGENCIES_DELETE_USED_AGENCIES:
                    case STP_IMPORT_AGENCIES_UPDATED_AGENCIES:
                    default:
                        break;
                }
                messagesArrayNode.add(errorNode);
            }
            errors.put(String.format("line %s", line), messagesArrayNode);
        }
        reportFinal.setError(errors);
        return new ByteArrayInputStream(JsonHandler.unprettyPrint(reportFinal).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void close() {
        logBookclient.close();
    }
}
