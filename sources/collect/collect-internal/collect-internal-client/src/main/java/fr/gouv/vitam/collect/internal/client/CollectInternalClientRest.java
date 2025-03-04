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
package fr.gouv.vitam.collect.internal.client;

import com.fasterxml.jackson.databind.JsonNode;
import fr.gouv.vitam.collect.common.dto.CriteriaProjectDto;
import fr.gouv.vitam.collect.common.dto.ProjectDto;
import fr.gouv.vitam.collect.common.dto.TransactionDto;
import fr.gouv.vitam.collect.common.enums.TransactionStatus;
import fr.gouv.vitam.collect.internal.client.exceptions.CollectInternalClientInvalidRequestException;
import fr.gouv.vitam.collect.internal.client.exceptions.CollectInternalClientNotFoundException;
import fr.gouv.vitam.common.CommonMediaType;
import fr.gouv.vitam.common.client.DefaultClient;
import fr.gouv.vitam.common.client.VitamClientFactoryInterface;
import fr.gouv.vitam.common.client.VitamRequestBuilder;
import fr.gouv.vitam.common.error.VitamError;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.VitamClientException;
import fr.gouv.vitam.common.model.RequestResponse;
import fr.gouv.vitam.common.model.RequestResponseOK;
import org.apache.commons.lang3.StringUtils;

import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import java.io.InputStream;

import static fr.gouv.vitam.common.client.VitamRequestBuilder.delete;
import static fr.gouv.vitam.common.client.VitamRequestBuilder.get;
import static fr.gouv.vitam.common.client.VitamRequestBuilder.post;
import static fr.gouv.vitam.common.client.VitamRequestBuilder.put;
import static javax.ws.rs.core.Response.Status.Family.SUCCESSFUL;
import static javax.ws.rs.core.Response.Status.fromStatusCode;
import static org.apache.http.HttpHeaders.EXPECT;
import static org.apache.http.protocol.HTTP.EXPECT_CONTINUE;

/**
 * Collect Client implementation for production environment
 */
public class CollectInternalClientRest extends DefaultClient implements CollectInternalClient {

    private static final String TRANSACTION_PATH = "/transactions";
    private static final String PROJECT_PATH = "/projects";
    private static final String UNITS_PATH = "/units";
    private static final String OBJECTS_PATH = "/objects";
    private static final String BINARY_PATH = "/binary";

    private static final String UNITS_WITH_INHERITED_RULES = "/unitsWithInheritedRules";

    private static final String BLANK_DSL = "select DSL is blank";

    public CollectInternalClientRest(VitamClientFactoryInterface<?> factory) {
        super(factory);
    }

    @Override
    public RequestResponse<JsonNode> initProject(ProjectDto projectDto) throws VitamClientException {
        VitamRequestBuilder request = post()
            .withPath(PROJECT_PATH)
            .withHeader(EXPECT, EXPECT_CONTINUE)
            .withBody(projectDto)
            .withJsonContentType()
            .withJsonAccept();

        try (Response response = make(request)) {
            check(response);
            return RequestResponse.parseFromResponse(response, JsonNode.class);
        }
    }

    @Override
    public RequestResponse<JsonNode> updateProject(ProjectDto projectDto) throws VitamClientException {
        VitamRequestBuilder request = put()
            .withPath(PROJECT_PATH)
            .withBody(projectDto)
            .withJsonContentType()
            .withJsonAccept();

        try (Response response = make(request)) {
            check(response);
            return RequestResponse.parseFromResponse(response, JsonNode.class);
        }
    }

    @Override
    public RequestResponse<JsonNode> getProjectById(String projectId) throws VitamClientException {
        VitamRequestBuilder request = get().withPath(PROJECT_PATH + "/" + projectId).withJsonAccept();

        try (Response response = make(request)) {
            check(response);
            return RequestResponse.parseFromResponse(response, JsonNode.class);
        }
    }

    @Override
    public RequestResponse<JsonNode> getTransactionById(String transactionId) throws VitamClientException {
        VitamRequestBuilder request = get().withPath(TRANSACTION_PATH + "/" + transactionId).withJsonAccept();

        try (Response response = make(request)) {
            check(response);
            return RequestResponse.parseFromResponse(response, JsonNode.class);
        }
    }

    @Override
    public RequestResponse<JsonNode> getTransactionByProjectId(String projectId) throws VitamClientException {
        VitamRequestBuilder request = get()
            .withPath(PROJECT_PATH + "/" + projectId + TRANSACTION_PATH)
            .withJsonAccept();

        try (Response response = make(request)) {
            check(response);
            return RequestResponse.parseFromResponse(response, JsonNode.class);
        }
    }

    @Override
    public RequestResponse<JsonNode> deleteProjectById(String projectId) throws VitamClientException {
        VitamRequestBuilder request = delete().withPath(PROJECT_PATH + "/" + projectId).withJsonAccept();

        try (Response response = make(request)) {
            check(response);
            return RequestResponse.parseFromResponse(response, JsonNode.class);
        }
    }

    @Override
    public RequestResponse<JsonNode> deleteTransactionById(String transactionId) throws VitamClientException {
        VitamRequestBuilder request = delete().withPath(TRANSACTION_PATH + "/" + transactionId).withJsonAccept();

        try (Response response = make(request)) {
            check(response);
            return RequestResponse.parseFromResponse(response, JsonNode.class);
        }
    }

    @Override
    public RequestResponse<JsonNode> getProjects() throws VitamClientException {
        VitamRequestBuilder request = get().withPath(PROJECT_PATH).withJsonAccept();

        try (Response response = make(request)) {
            check(response);
            return RequestResponse.parseFromResponse(response, JsonNode.class);
        }
    }

    @Override
    public RequestResponseOK<JsonNode> getUnitById(String unitId) throws VitamClientException {
        VitamRequestBuilder request = get().withPath(UNITS_PATH + "/" + unitId).withJsonAccept();

        try (Response response = make(request)) {
            check(response);
            RequestResponse<JsonNode> result = RequestResponse.parseFromResponse(response, JsonNode.class);
            return (RequestResponseOK<JsonNode>) result;
        }
    }

    @Override
    public RequestResponseOK<JsonNode> getUnitsByTransaction(String transactionId, JsonNode query)
        throws VitamClientException {
        VitamRequestBuilder request = get()
            .withPath(TRANSACTION_PATH + "/" + transactionId + UNITS_PATH)
            .withJson()
            .withBody(query);
        try (Response response = make(request)) {
            check(response);
            RequestResponse<JsonNode> result = RequestResponse.parseFromResponse(response, JsonNode.class);
            return (RequestResponseOK<JsonNode>) result;
        }
    }

    @Override
    public RequestResponseOK<JsonNode> getObjectById(String gotId) throws VitamClientException {
        VitamRequestBuilder request = get().withPath(OBJECTS_PATH + "/" + gotId).withJsonAccept();

        try (Response response = make(request)) {
            check(response);
            RequestResponse<JsonNode> result = RequestResponse.parseFromResponse(response, JsonNode.class);
            return (RequestResponseOK<JsonNode>) result;
        }
    }

    @Override
    public RequestResponse<JsonNode> initTransaction(TransactionDto transactionDto, String projectId)
        throws VitamClientException {
        VitamRequestBuilder request = post()
            .withPath(PROJECT_PATH + "/" + projectId + TRANSACTION_PATH)
            .withBody(transactionDto)
            .withJson();

        try (Response response = make(request)) {
            check(response);
            return RequestResponse.parseFromResponse(response, JsonNode.class);
        }
    }

    @Override
    public RequestResponseOK<JsonNode> uploadArchiveUnit(JsonNode unitJsonNode, String transactionId)
        throws VitamClientException {
        try (
            Response response = make(
                post().withPath(TRANSACTION_PATH + "/" + transactionId + UNITS_PATH).withBody(unitJsonNode).withJson()
            )
        ) {
            check(response);
            RequestResponse<JsonNode> result = RequestResponse.parseFromResponse(response, JsonNode.class);
            return (RequestResponseOK<JsonNode>) result;
        }
    }

    @Override
    public RequestResponseOK<JsonNode> addObjectGroup(
        String unitId,
        Integer version,
        JsonNode objectJsonNode,
        String usage
    ) throws VitamClientException {
        try (
            Response response = make(
                post()
                    .withPath(UNITS_PATH + "/" + unitId + OBJECTS_PATH + "/" + usage + "/" + version)
                    .withBody(objectJsonNode)
                    .withJson()
            )
        ) {
            check(response);
            RequestResponse<JsonNode> result = RequestResponse.parseFromResponse(response, JsonNode.class);
            return (RequestResponseOK<JsonNode>) result;
        }
    }

    @Override
    public RequestResponse<JsonNode> addBinary(
        String unitId,
        Integer version,
        InputStream inputStreamUploaded,
        String usage
    ) throws VitamClientException {
        try (
            Response response = make(
                post()
                    .withPath(UNITS_PATH + "/" + unitId + OBJECTS_PATH + "/" + usage + "/" + version + "/binary")
                    .withBody(inputStreamUploaded)
                    .withJsonAccept()
                    .withOctetContentType()
            )
        ) {
            check(response);
            return RequestResponse.parseFromResponse(response, JsonNode.class);
        }
    }

    @Override
    public Response closeTransaction(String transactionId) throws VitamClientException {
        try (
            Response response = make(
                post().withPath(TRANSACTION_PATH + "/" + transactionId + "/close").withJsonAccept()
            )
        ) {
            check(response);
            return response;
        }
    }

    @Override
    public InputStream generateSip(String transactionId) throws VitamClientException {
        Response response = null;
        try {
            response = make(post().withPath(TRANSACTION_PATH + "/" + transactionId + "/send").withOctetAccept());
            check(response);
            return response.readEntity(InputStream.class);
        } finally {
            if (response != null && !SUCCESSFUL.equals(response.getStatusInfo().getFamily())) {
                response.close();
            }
        }
    }

    @Override
    public Response abortTransaction(String transactionId) throws VitamClientException {
        try (
            Response response = make(put().withPath(TRANSACTION_PATH + "/" + transactionId + "/abort").withJsonAccept())
        ) {
            check(response);
            return response;
        }
    }

    @Override
    public Response reopenTransaction(String transactionId) throws VitamClientException {
        try (
            Response response = make(
                put().withPath(TRANSACTION_PATH + "/" + transactionId + "/reopen").withJsonAccept()
            )
        ) {
            check(response);
            return response;
        }
    }

    @Override
    public void uploadZipToTransaction(String transactionId, InputStream inputStreamUploaded)
        throws VitamClientException {
        try (
            Response response = make(
                post()
                    .withPath(TRANSACTION_PATH + "/" + transactionId + "/upload")
                    .withBody(inputStreamUploaded)
                    .withContentType(CommonMediaType.ZIP_TYPE)
            )
        ) {
            check(response);
        }
    }

    @Override
    public RequestResponseOK<JsonNode> getUnitsByProjectId(String projectId, JsonNode dslQuery)
        throws VitamClientException {
        try (
            Response response = make(
                get().withPath(PROJECT_PATH + "/" + projectId + UNITS_PATH).withBody(dslQuery).withJson()
            )
        ) {
            check(response);
            RequestResponse<JsonNode> result = RequestResponse.parseFromResponse(response, JsonNode.class);
            return (RequestResponseOK<JsonNode>) result;
        }
    }

    @Override
    public Response getObjectStreamByUnitId(String unitId, String usage, int version) throws VitamClientException {
        VitamRequestBuilder request = get()
            .withPath(UNITS_PATH + "/" + unitId + OBJECTS_PATH + "/" + usage + "/" + version + BINARY_PATH)
            .withOctetAccept();
        Response response = null;
        try {
            response = make(request);
            check(response);
            return response;
        } finally {
            if (response != null && SUCCESSFUL != response.getStatusInfo().getFamily()) {
                response.close();
            }
        }
    }

    @Override
    public RequestResponseOK<JsonNode> searchProject(CriteriaProjectDto criteria) throws VitamClientException {
        try (Response response = make(get().withPath(PROJECT_PATH).withBody(criteria).withJson())) {
            check(response);
            RequestResponse<JsonNode> result = RequestResponse.parseFromResponse(response, JsonNode.class);
            return (RequestResponseOK<JsonNode>) result;
        }
    }

    @Override
    public RequestResponseOK<JsonNode> updateUnits(String transactionId, InputStream is) throws VitamClientException {
        try (
            Response response = make(
                put()
                    .withPath(TRANSACTION_PATH + "/" + transactionId + UNITS_PATH)
                    .withBody(is)
                    .withJsonAccept()
                    .withOctetContentType()
            )
        ) {
            check(response);
            RequestResponse<JsonNode> result = RequestResponse.parseFromResponse(response, JsonNode.class);
            return (RequestResponseOK<JsonNode>) result;
        }
    }

    private void check(Response response) throws VitamClientException {
        if (SUCCESSFUL.equals(response.getStatusInfo().toEnum().getFamily())) {
            return;
        }

        final String template = "Error with the response, get status: '%d' and reason '%s'.";
        final String defaultReasonPhrase = fromStatusCode(response.getStatus()).getReasonPhrase();
        final Response.ResponseBuilder responseBuilder = responseBuilderFrom(response);
        String message = String.format(template, response.getStatus(), defaultReasonPhrase);

        try (final Response clonedResponse = responseBuilder.build()) {
            if (clonedResponse.hasEntity()) {
                message = clonedResponse.readEntity(String.class);
            }
        }

        try (final Response clonedResponse = responseBuilder.build()) {
            final VitamError<JsonNode> vitamError = RequestResponse.parseVitamError(clonedResponse);
            if (StringUtils.isNotBlank(vitamError.getDescription())) {
                message = vitamError.getDescription();
            } else if (StringUtils.isNotBlank(vitamError.getMessage())) {
                message = vitamError.getMessage();
            }

            if (response.getStatusInfo().getStatusCode() == Response.Status.BAD_REQUEST.getStatusCode()) {
                throw new CollectInternalClientInvalidRequestException(message);
            }

            if (response.getStatusInfo().getStatusCode() == Response.Status.NOT_FOUND.getStatusCode()) {
                throw new CollectInternalClientNotFoundException(message);
            }

            throw new VitamClientException(message);
        } catch (InvalidParseOperationException e) {
            throw new VitamClientException(message);
        }
    }

    private Response.ResponseBuilder responseBuilderFrom(final Response response) {
        Response.ResponseBuilder responseBuilder = Response.status(response.getStatus());

        // Copy headers
        for (String headerName : response.getHeaders().keySet()) {
            responseBuilder.header(headerName, response.getHeaderString(headerName));
        }

        // Copy cookies
        for (NewCookie cookie : response.getCookies().values()) {
            responseBuilder.cookie(cookie);
        }

        // Copy entity (if exists)
        if (response.hasEntity()) {
            responseBuilder.entity(response.readEntity(String.class));
        }

        return responseBuilder;
    }

    @Override
    public RequestResponse<JsonNode> updateTransaction(TransactionDto transactionDto) throws VitamClientException {
        VitamRequestBuilder request = put()
            .withPath(TRANSACTION_PATH)
            .withBody(transactionDto)
            .withJsonContentType()
            .withJsonAccept();

        try (Response response = make(request)) {
            check(response);
            return RequestResponse.parseFromResponse(response, JsonNode.class);
        }
    }

    @Override
    public Response changeTransactionStatus(String transactionId, TransactionStatus transactionStatus)
        throws VitamClientException {
        try (
            Response response = make(
                put().withPath(TRANSACTION_PATH + "/" + transactionId + "/status/" + transactionStatus).withJsonAccept()
            )
        ) {
            check(response);
            return response;
        }
    }

    @Override
    public RequestResponse<JsonNode> selectUnitsWithInheritedRules(String transactionId, JsonNode selectQuery)
        throws VitamClientException {
        VitamRequestBuilder request = get()
            .withPath(TRANSACTION_PATH + "/" + transactionId + UNITS_WITH_INHERITED_RULES)
            .withBody(selectQuery, BLANK_DSL)
            .withJson();
        try (Response response = make(request)) {
            check(response);
            return RequestResponse.parseFromResponse(response, JsonNode.class);
        }
    }

    @Override
    public Response attachVitamOperationId(String transactionId, String operationId) throws VitamClientException {
        try (
            Response response = make(
                put().withPath(TRANSACTION_PATH + "/" + transactionId + "/operation-id/" + operationId).withJsonAccept()
            )
        ) {
            check(response);
            return response;
        }
    }
}
