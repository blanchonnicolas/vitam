/*
 * Copyright French Prime minister Office/SGMAP/DINSIC/Vitam Program (2015-2022)
 *
 * contact.vitam@culture.gouv.fr
 *
 * This software is a computer program whose purpose is to implement a digital archiving back-office system managing
 * high volumetry securely and efficiently.
 *
 * This software is governed by the CeCILL-C license under French law and abiding by the rules of distribution of free
 * software. You can use, modify and/ or redistribute the software under the terms of the CeCILL-C license as
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
 * The fact that you are presently reading this means that you have had knowledge of the CeCILL-C license and that you
 * accept its terms.
 */
package fr.gouv.vitam.collect.common.exception;

import fr.gouv.vitam.common.error.VitamError;
import fr.gouv.vitam.common.model.RequestResponseOK;

import javax.ws.rs.core.Response;
import java.util.List;

public class CollectRequestResponse {

    public static final String COLLECT = "Collect";

    private CollectRequestResponse() throws IllegalAccessException {
        throw new IllegalAccessException("Utility class!");
    }

    public static Response toResponseOK(Object entity) {
        RequestResponseOK<Object> requestResponse = new RequestResponseOK<>();
        requestResponse.setHttpCode(Response.Status.OK.getStatusCode());
        if (entity instanceof List) {
            requestResponse.addAllResults((List<Object>) entity);
        } else {
            requestResponse.addResult(entity);
        }
        return Response.status(Response.Status.OK).entity(requestResponse).build();
    }

    public static Response toVitamError(Response.Status status, String message) {
        VitamError<Object> vitamError = new VitamError<>(status.name())
            .setContext(COLLECT)
            .setMessage(message)
            .setHttpCode(status.getStatusCode());
        return Response.status(status).entity(vitamError).build();
    }
}
