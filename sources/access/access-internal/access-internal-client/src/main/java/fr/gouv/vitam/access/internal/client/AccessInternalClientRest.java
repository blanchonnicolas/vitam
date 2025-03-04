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
package fr.gouv.vitam.access.internal.client;

import com.fasterxml.jackson.databind.JsonNode;
import fr.gouv.vitam.access.internal.common.exception.AccessInternalClientIllegalOperationException;
import fr.gouv.vitam.access.internal.common.exception.AccessInternalClientNotFoundException;
import fr.gouv.vitam.access.internal.common.exception.AccessInternalClientServerException;
import fr.gouv.vitam.access.internal.common.exception.AccessInternalClientUnavailableDataFromAsyncOfferException;
import fr.gouv.vitam.common.ParametersChecker;
import fr.gouv.vitam.common.client.CustomVitamHttpStatusCode;
import fr.gouv.vitam.common.client.DefaultClient;
import fr.gouv.vitam.common.client.VitamRequestBuilder;
import fr.gouv.vitam.common.database.builder.request.single.Select;
import fr.gouv.vitam.common.exception.AccessUnauthorizedException;
import fr.gouv.vitam.common.exception.BadRequestException;
import fr.gouv.vitam.common.exception.ExpectationFailedClientException;
import fr.gouv.vitam.common.exception.ForbiddenClientException;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.NoWritingPermissionException;
import fr.gouv.vitam.common.exception.PreconditionFailedClientException;
import fr.gouv.vitam.common.exception.VitamClientInternalException;
import fr.gouv.vitam.common.model.DeleteGotVersionsRequest;
import fr.gouv.vitam.common.model.PreservationRequest;
import fr.gouv.vitam.common.model.RequestResponse;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.model.elimination.EliminationRequestBody;
import fr.gouv.vitam.common.model.export.ExportRequest;
import fr.gouv.vitam.common.model.massupdate.MassUpdateUnitRuleRequest;
import fr.gouv.vitam.common.model.revertupdate.RevertUpdateOptions;
import fr.gouv.vitam.common.model.storage.AccessRequestReference;
import fr.gouv.vitam.common.model.storage.StatusByAccessRequest;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.logbook.common.exception.LogbookClientException;
import fr.gouv.vitam.logbook.common.exception.LogbookClientNotFoundException;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static fr.gouv.vitam.common.GlobalDataRest.X_ACCESS_CONTRAT_ID;
import static fr.gouv.vitam.common.GlobalDataRest.X_QUALIFIER;
import static fr.gouv.vitam.common.GlobalDataRest.X_VERSION;
import static fr.gouv.vitam.common.client.VitamRequestBuilder.delete;
import static fr.gouv.vitam.common.client.VitamRequestBuilder.get;
import static fr.gouv.vitam.common.client.VitamRequestBuilder.post;
import static fr.gouv.vitam.common.client.VitamRequestBuilder.put;
import static fr.gouv.vitam.common.thread.VitamThreadUtils.getVitamSession;
import static fr.gouv.vitam.logbook.common.LogbookDataRest.X_CROSS_TENANT;
import static fr.gouv.vitam.logbook.common.LogbookDataRest.X_SLICED_OPERATIONS;
import static fr.gouv.vitam.storage.engine.common.model.DataCategory.STORAGEACCESSLOG;
import static javax.ws.rs.core.Response.Status.Family.SUCCESSFUL;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static javax.ws.rs.core.Response.Status.PRECONDITION_FAILED;

class AccessInternalClientRest extends DefaultClient implements AccessInternalClient {

    private static final String INVALID_PARSE_OPERATION = "Invalid Parse Operation";
    private static final String FORBIDDEN_OPERATION = "Empty query cannot be executed";
    private static final String REQUEST_PRECONDITION_FAILED = "Request precondition failed";
    private static final String NOT_FOUND_EXCEPTION = "Element not found";
    private static final String ACCESS_CONTRACT_EXCEPTION = "Access by Contract Exception";
    private static final String NO_WRITING_PERMISSION = "No Writing Permission";
    private static final String BLANK_DSL = "select DSL is blank";
    private static final String BLANK_UNIT_ID = "unit identifier should be filled";
    private static final String BLANK_OBJECT_ID = "object identifier should be filled";
    private static final String BLANK_OBJECT_GROUP_ID = "object identifier should be filled";
    private static final String BLANK_TRACEABILITY_OPERATION_ID = "traceability operation identifier should be filled";

    private static final String LOGBOOK_OPERATIONS_URL = "/operations";
    private static final String LOGBOOK_UNIT_LIFECYCLE_URL = "/unitlifecycles";
    private static final String LOGBOOK_OBJECT_LIFECYCLE_URL = "/objectgrouplifecycles";
    private static final String LOGBOOK_LINKED_CHECK = "/traceability/linkedcheck";

    private static final String OBJECTS = "objects/";
    private static final String DIPEXPORT = "dipexport/";
    private static final String EXPORT_BY_USAGE_FILTER = "export/usagefilter";
    private static final String TRANSFER_EXPORT = "transferexport/";
    private static final String UNITS = "units/";
    private static final String UNITS_ATOMIC_BULK = "units/atomicbulk/";
    private static final String UNITS_RULES = "/units/rules";
    private static final String UNITS_WITH_INHERITED_RULES = "unitsWithInheritedRules";
    private static final String UNITS_STREAM = "/units/stream";

    private static final Runnable CHECK_REQUEST_ID = () -> VitamThreadUtils.getVitamSession().checkValidRequestId();

    AccessInternalClientRest(AccessInternalClientFactory factory) {
        super(factory);
    }

    @Override
    public RequestResponse<JsonNode> selectUnits(JsonNode selectQuery)
        throws InvalidParseOperationException, AccessInternalClientServerException, AccessInternalClientNotFoundException, AccessUnauthorizedException, BadRequestException {
        try (
            Response response = make(
                get().withBefore(CHECK_REQUEST_ID).withPath(UNITS).withBody(selectQuery, BLANK_DSL).withJson()
            )
        ) {
            check(response);
            return RequestResponse.parseFromResponse(response);
        } catch (
            VitamClientInternalException | PreconditionFailedClientException | ExpectationFailedClientException e
        ) {
            throw new AccessInternalClientServerException(e);
        } catch (ForbiddenClientException e) {
            throw new BadRequestException(e);
        } catch (NoWritingPermissionException | BadRequestException e) {
            throw new InvalidParseOperationException(e);
        }
    }

    @Override
    public Response streamUnits(JsonNode selectQuery)
        throws AccessInternalClientServerException, ExpectationFailedClientException, AccessUnauthorizedException {
        Response response = null;
        try {
            response = make(
                get()
                    .withBefore(CHECK_REQUEST_ID)
                    .withPath(UNITS_STREAM)
                    .withBody(selectQuery, BLANK_DSL)
                    .withJsonContentType()
                    .withOctetAccept()
            );
            Status status = response.getStatusInfo().toEnum();
            if (!SUCCESSFUL.equals(status.getFamily())) {
                switch (status) {
                    case EXPECTATION_FAILED:
                        throw new ExpectationFailedClientException(REQUEST_PRECONDITION_FAILED);
                    case UNAUTHORIZED:
                        throw new AccessUnauthorizedException(status.toString());
                    default:
                        throw new AccessInternalClientServerException(status.toString());
                }
            }
            return response;
        } catch (VitamClientInternalException e) {
            throw new AccessInternalClientServerException(e);
        } finally {
            if (response != null && SUCCESSFUL != response.getStatusInfo().getFamily()) {
                response.close();
            }
        }
    }

    @Override
    public RequestResponse<JsonNode> selectUnitbyId(JsonNode selectQuery, String idUnit)
        throws InvalidParseOperationException, AccessInternalClientServerException, AccessInternalClientNotFoundException, AccessUnauthorizedException {
        ParametersChecker.checkParameter(BLANK_UNIT_ID, idUnit);
        try (
            Response response = make(
                get().withBefore(CHECK_REQUEST_ID).withPath(UNITS + idUnit).withBody(selectQuery, BLANK_DSL).withJson()
            )
        ) {
            check(response);
            return RequestResponse.parseFromResponse(response);
        } catch (
            VitamClientInternalException
            | NoWritingPermissionException
            | PreconditionFailedClientException
            | ExpectationFailedClientException
            | ForbiddenClientException e
        ) {
            throw new AccessInternalClientServerException(e);
        } catch (BadRequestException e) {
            throw new InvalidParseOperationException(e);
        }
    }

    @Override
    public RequestResponse<JsonNode> updateUnitbyId(JsonNode updateQuery, String unitId)
        throws InvalidParseOperationException, AccessInternalClientServerException, AccessInternalClientNotFoundException, NoWritingPermissionException, AccessUnauthorizedException {
        ParametersChecker.checkParameter(BLANK_UNIT_ID, unitId);
        Response response = null;
        try {
            response = make(
                put().withBefore(CHECK_REQUEST_ID).withPath(UNITS + unitId).withBody(updateQuery, BLANK_DSL).withJson()
            );
            check(response);
            return RequestResponse.parseFromResponse(response);
        } catch (BadRequestException e) {
            return RequestResponse.parseVitamError(response);
        } catch (
            VitamClientInternalException
            | ForbiddenClientException
            | ExpectationFailedClientException
            | PreconditionFailedClientException e
        ) {
            throw new AccessInternalClientServerException(e);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    @Override
    public RequestResponse<JsonNode> updateUnits(JsonNode updateQuery)
        throws InvalidParseOperationException, AccessInternalClientServerException, NoWritingPermissionException, AccessUnauthorizedException {
        Response response = null;
        try {
            response = make(
                post().withBefore(CHECK_REQUEST_ID).withPath(UNITS).withBody(updateQuery, BLANK_DSL).withJson()
            );
            check(response);
            return RequestResponse.parseFromResponse(response);
        } catch (BadRequestException e) {
            return RequestResponse.parseVitamError(response);
        } catch (
            VitamClientInternalException
            | AccessInternalClientNotFoundException
            | ForbiddenClientException
            | ExpectationFailedClientException
            | PreconditionFailedClientException e
        ) {
            throw new AccessInternalClientServerException(e);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    @Override
    public RequestResponse<JsonNode> updateUnitsRules(MassUpdateUnitRuleRequest massUpdateUnitRuleRequest)
        throws InvalidParseOperationException, AccessInternalClientServerException, NoWritingPermissionException, AccessUnauthorizedException {
        try (
            Response response = make(
                post()
                    .withBefore(CHECK_REQUEST_ID)
                    .withPath(UNITS_RULES)
                    .withBody(massUpdateUnitRuleRequest, BLANK_DSL)
                    .withJson()
            )
        ) {
            check(response);
            return RequestResponse.parseFromResponse(response);
        } catch (BadRequestException e) {
            throw new InvalidParseOperationException(INVALID_PARSE_OPERATION);
        } catch (
            VitamClientInternalException
            | AccessInternalClientNotFoundException
            | ForbiddenClientException
            | ExpectationFailedClientException
            | PreconditionFailedClientException e
        ) {
            throw new AccessInternalClientServerException(e);
        }
    }

    @Override
    public RequestResponse<JsonNode> bulkAtomicUpdateUnits(JsonNode updateQueries)
        throws InvalidParseOperationException, AccessInternalClientServerException, NoWritingPermissionException, AccessUnauthorizedException {
        Response response = null;
        try {
            response = make(
                post()
                    .withBefore(CHECK_REQUEST_ID)
                    .withPath(UNITS_ATOMIC_BULK)
                    .withBody(updateQueries, BLANK_DSL)
                    .withJson()
            );
            check(response);
            return RequestResponse.parseFromResponse(response);
        } catch (BadRequestException e) {
            return RequestResponse.parseVitamError(response);
        } catch (
            VitamClientInternalException
            | AccessInternalClientNotFoundException
            | ForbiddenClientException
            | ExpectationFailedClientException
            | PreconditionFailedClientException e
        ) {
            throw new AccessInternalClientServerException(e);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    @Override
    public RequestResponse<JsonNode> selectObjectbyId(JsonNode selectObjectQuery, String objectId)
        throws InvalidParseOperationException, AccessInternalClientServerException, AccessInternalClientNotFoundException, AccessUnauthorizedException {
        ParametersChecker.checkParameter(BLANK_OBJECT_ID, objectId);
        try (
            Response response = make(
                get()
                    .withBefore(CHECK_REQUEST_ID)
                    .withPath(OBJECTS + objectId)
                    .withBody(selectObjectQuery, BLANK_DSL)
                    .withJson()
            )
        ) {
            check(response);
            return RequestResponse.parseFromResponse(response);
        } catch (PreconditionFailedClientException e) {
            throw new IllegalArgumentException(PRECONDITION_FAILED.name());
        } catch (BadRequestException e) {
            throw new InvalidParseOperationException(INVALID_PARSE_OPERATION);
        } catch (
            VitamClientInternalException
            | NoWritingPermissionException
            | ForbiddenClientException
            | ExpectationFailedClientException e
        ) {
            throw new AccessInternalClientServerException(e);
        }
    }

    @Override
    public Response getObject(String objectGroupId, String usage, int version, String unitId)
        throws InvalidParseOperationException, AccessInternalClientServerException, AccessInternalClientNotFoundException, AccessUnauthorizedException, AccessInternalClientUnavailableDataFromAsyncOfferException {
        ParametersChecker.checkParameter(BLANK_OBJECT_GROUP_ID, objectGroupId);
        ParametersChecker.checkParameter(BLANK_UNIT_ID, unitId);
        VitamRequestBuilder request = get()
            .withPath(OBJECTS + objectGroupId + "/" + unitId)
            .withHeader(X_QUALIFIER, usage)
            .withHeader(X_VERSION, version)
            .withJsonOctet()
            .withBefore(CHECK_REQUEST_ID);
        Response response = null;
        try {
            response = make(request);

            if (response.getStatus() == CustomVitamHttpStatusCode.UNAVAILABLE_DATA_FROM_ASYNC_OFFER.getStatusCode()) {
                throw new AccessInternalClientUnavailableDataFromAsyncOfferException(
                    "Access to async offer requires valid access request"
                );
            }
            check(response);
            return response;
        } catch (PreconditionFailedClientException e) {
            throw new IllegalArgumentException(e);
        } catch (
            VitamClientInternalException
            | NoWritingPermissionException
            | ForbiddenClientException
            | ExpectationFailedClientException e
        ) {
            throw new AccessInternalClientServerException(e);
        } catch (BadRequestException e) {
            throw new InvalidParseOperationException(e);
        } finally {
            if (response != null && !SUCCESSFUL.equals(response.getStatusInfo().getFamily())) {
                response.close();
            }
        }
    }

    @Override
    public RequestResponse<JsonNode> selectOperation(JsonNode select, boolean isSliced, boolean isCrossTenant)
        throws LogbookClientException, InvalidParseOperationException, AccessUnauthorizedException {
        VitamRequestBuilder request = get()
            .withPath(LOGBOOK_OPERATIONS_URL)
            .withBody(select, "Select cannot be empty or null.")
            .withJson();

        if (isSliced) {
            request.withHeader(X_SLICED_OPERATIONS, true);
        }

        if (isCrossTenant) {
            request.withHeader(X_CROSS_TENANT, true);
        }

        try (Response response = make(request)) {
            check(response);
            return RequestResponse.parseFromResponse(response);
        } catch (BadRequestException e) {
            throw new InvalidParseOperationException(e);
        } catch (
            AccessInternalClientServerException
            | AccessInternalClientNotFoundException
            | NoWritingPermissionException
            | VitamClientInternalException
            | ForbiddenClientException
            | ExpectationFailedClientException
            | PreconditionFailedClientException e
        ) {
            throw new LogbookClientException(e);
        }
    }

    @Override
    public RequestResponse<JsonNode> selectOperationById(
        String processId,
        JsonNode select,
        boolean isSliced,
        boolean isCrossTenant
    ) throws LogbookClientException, InvalidParseOperationException, AccessUnauthorizedException {
        ParametersChecker.checkParameter("processId cannot be blank.", processId);

        VitamRequestBuilder request = get()
            .withBefore(CHECK_REQUEST_ID)
            .withPath(LOGBOOK_OPERATIONS_URL + "/" + processId)
            .withBody(select, "Select cannot be empty or null.")
            .withJson();

        if (isSliced) {
            request.withHeader(X_SLICED_OPERATIONS, true);
        }
        if (isCrossTenant) {
            request.withHeader(X_CROSS_TENANT, true);
        }

        try (Response response = make(request)) {
            check(response);
            return RequestResponse.parseFromResponse(response);
        } catch (BadRequestException e) {
            throw new InvalidParseOperationException(e);
        } catch (AccessInternalClientNotFoundException e) {
            throw new LogbookClientNotFoundException(e);
        } catch (
            AccessInternalClientServerException
            | NoWritingPermissionException
            | VitamClientInternalException
            | ForbiddenClientException
            | ExpectationFailedClientException
            | PreconditionFailedClientException e
        ) {
            throw new LogbookClientException(e);
        }
    }

    @Override
    public RequestResponse<JsonNode> selectOperationById(String processId)
        throws LogbookClientException, InvalidParseOperationException, AccessUnauthorizedException {
        return selectOperationById(processId, new Select().getFinalSelect(), false, false);
    }

    @Override
    public RequestResponse<JsonNode> selectUnitLifeCycleById(String idUnit, JsonNode queryDsl)
        throws LogbookClientException, InvalidParseOperationException, AccessUnauthorizedException {
        ParametersChecker.checkParameter("idUnit cannot be blank.", idUnit);
        try (
            Response response = make(
                get()
                    .withBefore(CHECK_REQUEST_ID)
                    .withPath(LOGBOOK_UNIT_LIFECYCLE_URL + "/" + idUnit)
                    .withBody(queryDsl, "QueryDsl cannot be empty or null.")
                    .withJson()
            )
        ) {
            check(response);
            return RequestResponse.parseFromResponse(response);
        } catch (BadRequestException e) {
            throw new InvalidParseOperationException(e);
        } catch (
            AccessInternalClientServerException
            | AccessInternalClientNotFoundException
            | NoWritingPermissionException
            | VitamClientInternalException
            | ForbiddenClientException
            | ExpectationFailedClientException
            | PreconditionFailedClientException e
        ) {
            throw new LogbookClientException(e);
        }
    }

    @Override
    public RequestResponse<JsonNode> selectObjectGroupLifeCycleById(String idObject, JsonNode queryDsl)
        throws LogbookClientException, InvalidParseOperationException, AccessUnauthorizedException {
        ParametersChecker.checkParameter("idObject cannot be blank.", idObject);
        try (
            Response response = make(
                get()
                    .withBefore(CHECK_REQUEST_ID)
                    .withPath(LOGBOOK_OBJECT_LIFECYCLE_URL + "/" + idObject)
                    .withBody(queryDsl, "QueryDsl cannot be empty or null.")
                    .withJson()
            )
        ) {
            check(response);
            return RequestResponse.parseFromResponse(response);
        } catch (BadRequestException e) {
            throw new InvalidParseOperationException(e);
        } catch (
            AccessInternalClientServerException
            | AccessInternalClientNotFoundException
            | NoWritingPermissionException
            | VitamClientInternalException
            | ForbiddenClientException
            | ExpectationFailedClientException
            | PreconditionFailedClientException e
        ) {
            throw new LogbookClientException(e);
        }
    }

    @Override
    public RequestResponse<JsonNode> linkedCheckTraceability(JsonNode query)
        throws InvalidParseOperationException, LogbookClientException, AccessUnauthorizedException {
        VitamRequestBuilder request = post().withPath(LOGBOOK_LINKED_CHECK).withJson().withBody(query);
        try (Response response = make(request)) {
            check(response);
            return RequestResponse.parseFromResponse(response);
        } catch (BadRequestException e) {
            throw new InvalidParseOperationException(e);
        } catch (
            AccessInternalClientServerException
            | AccessInternalClientNotFoundException
            | NoWritingPermissionException
            | VitamClientInternalException
            | ForbiddenClientException
            | ExpectationFailedClientException
            | PreconditionFailedClientException e
        ) {
            throw new LogbookClientException(e);
        }
    }

    @Override
    public Response downloadTraceabilityFile(String operationId)
        throws AccessInternalClientServerException, AccessInternalClientNotFoundException, InvalidParseOperationException, AccessUnauthorizedException {
        ParametersChecker.checkParameter(BLANK_TRACEABILITY_OPERATION_ID, operationId);
        Response response = null;
        try {
            response = make(
                get()
                    .withBefore(CHECK_REQUEST_ID)
                    .withPath("traceability/" + operationId + "/content")
                    .withOctetAccept()
            );
            check(response);
            return response;
        } catch (
            VitamClientInternalException
            | NoWritingPermissionException
            | ForbiddenClientException
            | ExpectationFailedClientException
            | PreconditionFailedClientException e
        ) {
            throw new AccessInternalClientServerException(e);
        } catch (BadRequestException e) {
            throw new InvalidParseOperationException(e);
        } finally {
            if (response != null && !SUCCESSFUL.equals(response.getStatusInfo().getFamily())) {
                response.close();
            }
        }
    }

    @Override
    public RequestResponse<JsonNode> exportDIP(JsonNode dslRequest) throws AccessInternalClientServerException {
        try (
            Response response = make(
                post().withBefore(CHECK_REQUEST_ID).withPath(DIPEXPORT).withBody(dslRequest, BLANK_DSL).withJson()
            )
        ) {
            check(response);
            return RequestResponse.parseFromResponse(response);
        } catch (
            BadRequestException
            | AccessInternalClientNotFoundException
            | AccessUnauthorizedException
            | NoWritingPermissionException
            | VitamClientInternalException
            | ForbiddenClientException
            | ExpectationFailedClientException
            | PreconditionFailedClientException e
        ) {
            throw new AccessInternalClientServerException(e);
        }
    }

    @Override
    public RequestResponse<JsonNode> exportByUsageFilter(ExportRequest exportRequest)
        throws AccessInternalClientServerException {
        try (
            Response response = make(
                post()
                    .withBefore(CHECK_REQUEST_ID)
                    .withPath(EXPORT_BY_USAGE_FILTER)
                    .withBody(exportRequest, BLANK_DSL)
                    .withJson()
            )
        ) {
            check(response);
            return RequestResponse.parseFromResponse(response);
        } catch (
            BadRequestException
            | AccessInternalClientNotFoundException
            | AccessUnauthorizedException
            | NoWritingPermissionException
            | VitamClientInternalException
            | ForbiddenClientException
            | ExpectationFailedClientException
            | PreconditionFailedClientException e
        ) {
            throw new AccessInternalClientServerException(e);
        }
    }

    @Override
    public Response findExportByID(String id) throws AccessInternalClientServerException {
        ParametersChecker.checkParameter(BLANK_DSL, id);
        try {
            return make(get().withBefore(CHECK_REQUEST_ID).withPath(DIPEXPORT + id + "/dip").withOctetAccept());
        } catch (final VitamClientInternalException e) {
            throw new AccessInternalClientServerException(INTERNAL_SERVER_ERROR.getReasonPhrase(), e); // access-common
        }
    }

    @Override
    public Response findTransferSIPByID(String id) throws AccessInternalClientServerException {
        ParametersChecker.checkParameter(BLANK_DSL, id);
        try {
            return make(get().withBefore(CHECK_REQUEST_ID).withPath(TRANSFER_EXPORT + id + "/sip").withOctetAccept());
        } catch (final VitamClientInternalException e) {
            throw new AccessInternalClientServerException(INTERNAL_SERVER_ERROR.getReasonPhrase(), e); // access-common
        }
    }

    @Override
    public RequestResponse<JsonNode> reclassification(JsonNode reclassificationRequest)
        throws AccessInternalClientServerException {
        try (
            Response response = make(
                post()
                    .withBefore(CHECK_REQUEST_ID)
                    .withPath("/reclassification")
                    .withBody(reclassificationRequest, "Missing reclassification request")
                    .withJson()
            )
        ) {
            check(response);
            return RequestResponse.parseFromResponse(response);
        } catch (
            BadRequestException
            | AccessInternalClientNotFoundException
            | AccessUnauthorizedException
            | NoWritingPermissionException
            | VitamClientInternalException
            | ForbiddenClientException
            | ExpectationFailedClientException
            | PreconditionFailedClientException e
        ) {
            throw new AccessInternalClientServerException(e);
        }
    }

    @Override
    public RequestResponse<JsonNode> selectObjects(JsonNode selectQuery)
        throws InvalidParseOperationException, AccessInternalClientServerException, AccessInternalClientNotFoundException, AccessUnauthorizedException, BadRequestException {
        try (
            Response response = make(
                get().withBefore(CHECK_REQUEST_ID).withPath(OBJECTS).withBody(selectQuery, BLANK_DSL).withJson()
            )
        ) {
            check(response);
            return RequestResponse.parseFromResponse(response);
        } catch (BadRequestException e) {
            throw new InvalidParseOperationException(e);
        } catch (ForbiddenClientException e) {
            throw new BadRequestException(e);
        } catch (
            VitamClientInternalException
            | NoWritingPermissionException
            | ExpectationFailedClientException
            | PreconditionFailedClientException e
        ) {
            throw new AccessInternalClientServerException(e);
        }
    }

    @Override
    public RequestResponse<JsonNode> selectUnitsWithInheritedRules(JsonNode selectQuery)
        throws InvalidParseOperationException, AccessInternalClientServerException, AccessInternalClientNotFoundException, AccessUnauthorizedException, BadRequestException {
        try (
            Response response = make(
                get()
                    .withBefore(CHECK_REQUEST_ID)
                    .withPath(UNITS_WITH_INHERITED_RULES)
                    .withBody(selectQuery, BLANK_DSL)
                    .withJson()
            )
        ) {
            check(response);
            return RequestResponse.parseFromResponse(response);
        } catch (BadRequestException e) {
            throw new InvalidParseOperationException(e);
        } catch (ForbiddenClientException e) {
            throw new BadRequestException(e);
        } catch (
            VitamClientInternalException
            | NoWritingPermissionException
            | ExpectationFailedClientException
            | PreconditionFailedClientException e
        ) {
            throw new AccessInternalClientServerException(e);
        }
    }

    @Override
    public Response downloadAccessLogFile(JsonNode params)
        throws AccessInternalClientServerException, AccessInternalClientNotFoundException, InvalidParseOperationException, AccessUnauthorizedException {
        Response response = null;
        try {
            response = make(
                get()
                    .withBefore(CHECK_REQUEST_ID)
                    .withPath(STORAGEACCESSLOG.getCollectionName())
                    .withBody(params, BLANK_DSL)
                    .withJsonOctet()
            );
            check(response);
            return response;
        } catch (BadRequestException e) {
            throw new InvalidParseOperationException(e);
        } catch (
            VitamClientInternalException
            | NoWritingPermissionException
            | ForbiddenClientException
            | ExpectationFailedClientException
            | PreconditionFailedClientException e
        ) {
            throw new AccessInternalClientServerException(e);
        } finally {
            if (response != null && !SUCCESSFUL.equals(response.getStatusInfo().getFamily())) {
                response.close();
            }
        }
    }

    @Override
    public RequestResponse<JsonNode> startEliminationAnalysis(EliminationRequestBody eliminationRequestBody)
        throws AccessInternalClientServerException {
        try (
            Response response = make(
                post()
                    .withBefore(CHECK_REQUEST_ID)
                    .withPath("/elimination/analysis")
                    .withBody(eliminationRequestBody, "Missing elimination request")
                    .withJson()
            )
        ) {
            check(response);
            return RequestResponse.parseFromResponse(response);
        } catch (
            VitamClientInternalException
            | AccessUnauthorizedException
            | AccessInternalClientNotFoundException
            | NoWritingPermissionException
            | BadRequestException
            | ForbiddenClientException
            | ExpectationFailedClientException
            | PreconditionFailedClientException e
        ) {
            throw new AccessInternalClientServerException(e);
        }
    }

    @Override
    public RequestResponse<JsonNode> startEliminationAction(EliminationRequestBody eliminationRequestBody)
        throws AccessInternalClientServerException {
        try (
            Response response = make(
                post()
                    .withBefore(CHECK_REQUEST_ID)
                    .withPath("/elimination/action")
                    .withBody(eliminationRequestBody, "Missing elimination request")
                    .withJson()
            )
        ) {
            check(response);
            return RequestResponse.parseFromResponse(response);
        } catch (
            VitamClientInternalException
            | AccessUnauthorizedException
            | AccessInternalClientNotFoundException
            | NoWritingPermissionException
            | BadRequestException
            | ForbiddenClientException
            | ExpectationFailedClientException
            | PreconditionFailedClientException e
        ) {
            throw new AccessInternalClientServerException(e);
        }
    }

    @Override
    public RequestResponse<JsonNode> startPreservation(PreservationRequest preservationRequest)
        throws AccessInternalClientServerException {
        VitamRequestBuilder request = post()
            .withBefore(CHECK_REQUEST_ID)
            .withPath("/preservation")
            .withBody(preservationRequest, "Missing request")
            .withJson()
            .withHeader(X_ACCESS_CONTRAT_ID, getVitamSession().getContractId());
        try (Response response = make(request)) {
            check(response);
            return RequestResponse.parseFromResponse(response);
        } catch (
            BadRequestException
            | AccessInternalClientNotFoundException
            | AccessUnauthorizedException
            | NoWritingPermissionException
            | VitamClientInternalException
            | ForbiddenClientException
            | ExpectationFailedClientException
            | PreconditionFailedClientException e
        ) {
            throw new AccessInternalClientServerException(e);
        }
    }

    @Override
    public RequestResponse<JsonNode> deleteGotVersions(DeleteGotVersionsRequest deleteGotVersionsRequest)
        throws AccessInternalClientServerException {
        VitamRequestBuilder request = post()
            .withBefore(CHECK_REQUEST_ID)
            .withPath("/deleteGotVersions")
            .withBody(deleteGotVersionsRequest, "Missing request")
            .withJson()
            .withHeader(X_ACCESS_CONTRAT_ID, getVitamSession().getContractId());
        try (Response response = make(request)) {
            check(response);
            return RequestResponse.parseFromResponse(response);
        } catch (
            BadRequestException
            | AccessInternalClientNotFoundException
            | AccessUnauthorizedException
            | NoWritingPermissionException
            | VitamClientInternalException
            | ForbiddenClientException
            | ExpectationFailedClientException
            | PreconditionFailedClientException e
        ) {
            throw new AccessInternalClientServerException(e);
        }
    }

    @Override
    public RequestResponse<JsonNode> startComputeInheritedRules(JsonNode dslQuery)
        throws AccessInternalClientServerException {
        VitamRequestBuilder request = post()
            .withBefore(CHECK_REQUEST_ID)
            .withPath("/units/computedInheritedRules")
            .withBody(dslQuery, "Missing request")
            .withJson()
            .withHeader(X_ACCESS_CONTRAT_ID, getVitamSession().getContractId());
        try (Response response = make(request)) {
            check(response);
            return RequestResponse.parseFromResponse(response);
        } catch (
            BadRequestException
            | AccessInternalClientNotFoundException
            | AccessUnauthorizedException
            | NoWritingPermissionException
            | VitamClientInternalException
            | ForbiddenClientException
            | ExpectationFailedClientException
            | PreconditionFailedClientException e
        ) {
            throw new AccessInternalClientServerException(e);
        }
    }

    @Override
    public RequestResponse<JsonNode> deleteComputeInheritedRules(JsonNode dslQuery)
        throws AccessInternalClientServerException {
        VitamRequestBuilder request = delete()
            .withBefore(CHECK_REQUEST_ID)
            .withPath("/units/computedInheritedRules")
            .withBody(dslQuery, "Missing request")
            .withJson()
            .withHeader(X_ACCESS_CONTRAT_ID, getVitamSession().getContractId());
        try (Response response = make(request)) {
            check(response);
            return RequestResponse.parseFromResponse(response);
        } catch (
            BadRequestException
            | AccessInternalClientNotFoundException
            | AccessUnauthorizedException
            | NoWritingPermissionException
            | VitamClientInternalException
            | ForbiddenClientException
            | ExpectationFailedClientException
            | PreconditionFailedClientException e
        ) {
            throw new AccessInternalClientServerException(e);
        }
    }

    @Override
    public RequestResponse<JsonNode> startTransferReplyWorkflow(InputStream transferReply)
        throws AccessInternalClientServerException {
        VitamRequestBuilder request = post()
            .withPath("/transfers/reply")
            .withBody(transferReply, "Missing transfer reply")
            .withJsonAccept()
            .withXMLContentType()
            .withBefore(CHECK_REQUEST_ID)
            .withHeader(X_ACCESS_CONTRAT_ID, getVitamSession().getContractId());
        try (Response response = make(request)) {
            check(response);
            return RequestResponse.parseFromResponse(response);
        } catch (
            BadRequestException
            | AccessInternalClientNotFoundException
            | AccessUnauthorizedException
            | NoWritingPermissionException
            | VitamClientInternalException
            | ForbiddenClientException
            | ExpectationFailedClientException
            | PreconditionFailedClientException e
        ) {
            throw new AccessInternalClientServerException(e);
        }
    }

    @Override
    public RequestResponse<JsonNode> revertUnits(RevertUpdateOptions revertUpdateOptions)
        throws AccessInternalClientServerException, InvalidParseOperationException, AccessUnauthorizedException, NoWritingPermissionException {
        Response response = null;
        try {
            response = make(
                post()
                    .withBefore(CHECK_REQUEST_ID)
                    .withPath("/revert/units")
                    .withBody(revertUpdateOptions, BLANK_DSL)
                    .withJson()
            );
            check(response);
            return RequestResponse.parseFromResponse(response);
        } catch (BadRequestException e) {
            return RequestResponse.parseVitamError(response);
        } catch (
            VitamClientInternalException
            | AccessInternalClientNotFoundException
            | ForbiddenClientException
            | ExpectationFailedClientException
            | PreconditionFailedClientException e
        ) {
            throw new AccessInternalClientServerException(e);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    @Override
    public Optional<AccessRequestReference> createObjectAccessRequest(String objectGroupId, String usage, int version)
        throws AccessInternalClientServerException, AccessInternalClientNotFoundException, AccessUnauthorizedException {
        ParametersChecker.checkParameter(BLANK_OBJECT_GROUP_ID, objectGroupId);
        ParametersChecker.checkParameter("Missing usage", usage);
        VitamRequestBuilder request = post()
            .withPath(OBJECTS + URLEncoder.encode(objectGroupId, StandardCharsets.UTF_8) + "/accessRequest")
            .withHeader(X_QUALIFIER, usage)
            .withHeader(X_VERSION, version)
            .withJson();
        try (Response response = make(request)) {
            check(response);
            RequestResponseOK<AccessRequestReference> requestResponse = (RequestResponseOK<
                    AccessRequestReference
                >) RequestResponse.parseFromResponse(response, AccessRequestReference.class);
            return Optional.ofNullable(requestResponse.getFirstResult());
        } catch (
            VitamClientInternalException
            | NoWritingPermissionException
            | ForbiddenClientException
            | ExpectationFailedClientException
            | BadRequestException
            | PreconditionFailedClientException e
        ) {
            throw new AccessInternalClientServerException(e);
        }
    }

    @Override
    public List<StatusByAccessRequest> checkAccessRequestStatuses(List<AccessRequestReference> accessRequestReferences)
        throws AccessInternalClientServerException, AccessInternalClientIllegalOperationException {
        ParametersChecker.checkParameter("Required objectAccessRequests", accessRequestReferences);
        VitamRequestBuilder request = get().withPath("accessRequests/").withJson().withBody(accessRequestReferences);
        try (Response response = make(request)) {
            if (response.getStatus() == Status.NOT_ACCEPTABLE.getStatusCode()) {
                throw new AccessInternalClientIllegalOperationException(
                    "Illegal Access Request operation on synchronous offer strategy"
                );
            }
            check(response);

            RequestResponseOK<StatusByAccessRequest> requestResponse = (RequestResponseOK<
                    StatusByAccessRequest
                >) RequestResponse.parseFromResponse(response, StatusByAccessRequest.class);
            return requestResponse.getResults();
        } catch (
            VitamClientInternalException
            | NoWritingPermissionException
            | ForbiddenClientException
            | ExpectationFailedClientException
            | BadRequestException
            | PreconditionFailedClientException
            | AccessUnauthorizedException
            | AccessInternalClientNotFoundException e
        ) {
            throw new AccessInternalClientServerException(e);
        }
    }

    @Override
    public void removeAccessRequest(AccessRequestReference accessRequestReference)
        throws AccessInternalClientServerException, AccessInternalClientIllegalOperationException {
        ParametersChecker.checkParameter("Access request required", accessRequestReference);
        ParametersChecker.checkParameter("Required accessRequestId", accessRequestReference.getAccessRequestId());
        ParametersChecker.checkParameter("Required storageStrategyId", accessRequestReference.getStorageStrategyId());

        VitamRequestBuilder request = delete().withPath("accessRequests/").withJson().withBody(accessRequestReference);
        try (Response response = make(request)) {
            if (response.getStatus() == Status.NOT_ACCEPTABLE.getStatusCode()) {
                throw new AccessInternalClientIllegalOperationException(
                    "Illegal Access Request operation on synchronous offer strategy"
                );
            }
            check(response);
        } catch (
            VitamClientInternalException
            | NoWritingPermissionException
            | ForbiddenClientException
            | ExpectationFailedClientException
            | BadRequestException
            | PreconditionFailedClientException
            | AccessInternalClientNotFoundException
            | AccessUnauthorizedException e
        ) {
            throw new AccessInternalClientServerException(e);
        }
    }

    private void check(Response response)
        throws AccessInternalClientServerException, AccessUnauthorizedException, AccessInternalClientNotFoundException, NoWritingPermissionException, BadRequestException, ForbiddenClientException, ExpectationFailedClientException, PreconditionFailedClientException {
        Status status = response.getStatusInfo().toEnum();
        if (SUCCESSFUL.equals(status.getFamily())) {
            return;
        }

        switch (status) {
            case INTERNAL_SERVER_ERROR:
                throw new AccessInternalClientServerException(INTERNAL_SERVER_ERROR.getReasonPhrase());
            case NOT_FOUND:
                throw new AccessInternalClientNotFoundException(NOT_FOUND_EXCEPTION);
            case UNAUTHORIZED:
                throw new AccessUnauthorizedException(ACCESS_CONTRACT_EXCEPTION);
            case BAD_REQUEST:
                throw new BadRequestException(INVALID_PARSE_OPERATION);
            case METHOD_NOT_ALLOWED:
                throw new NoWritingPermissionException(NO_WRITING_PERMISSION);
            case FORBIDDEN:
                throw new ForbiddenClientException(FORBIDDEN_OPERATION);
            case EXPECTATION_FAILED:
                throw new ExpectationFailedClientException(REQUEST_PRECONDITION_FAILED);
            case PRECONDITION_FAILED:
                throw new PreconditionFailedClientException(PRECONDITION_FAILED.name());
            default:
                throw new AccessInternalClientServerException(status.toString());
        }
    }
}
