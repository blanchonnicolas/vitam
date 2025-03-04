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
package fr.gouv.vitam.access.internal.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.gouv.culture.archivesdefrance.seda.v2.DataObjectPackageType;
import fr.gouv.vitam.common.error.VitamCode;
import fr.gouv.vitam.common.error.VitamError;
import fr.gouv.vitam.common.exception.InternalServerException;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.mapping.dip.DipService;
import fr.gouv.vitam.common.mapping.dip.ObjectGroupMapper;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.model.objectgroup.ObjectGroupResponse;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.StringWriter;

/**
 * Object Group Service for retrieve Xml representation of Object Group
 */
public class ObjectGroupDipServiceImpl implements DipService {

    private static final String END_OF_EXECUTION_OF_DSL_VITAM_FROM_ACCESS = "End of execution of DSL Vitam from Access";
    private static final String BAD_REQUEST_EXCEPTION = "Bad request Exception ";

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(ObjectGroupDipServiceImpl.class);
    private ObjectGroupMapper objectGroupMapper;
    private ObjectMapper objectMapper;
    private static JAXBContext jaxbContext;
    private static final String ACCESS_EXTERNAL_MODULE = "AccessExternalModule";

    static {
        try {
            jaxbContext = JAXBContext.newInstance("fr.gouv.culture.archivesdefrance.seda.v2");
        } catch (JAXBException e) {
            LOGGER.error("unable to create jaxb context", e);
        }
    }

    public ObjectGroupDipServiceImpl(ObjectGroupMapper objectGroupMapper, ObjectMapper objectMapper) {
        this.objectGroupMapper = objectGroupMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public Response jsonToXml(JsonNode object, String id) {
        Status status;
        StringWriter writer = new StringWriter();
        try {
            if (object != null) {
                ObjectGroupResponse objectGroup = objectMapper.treeToValue(object, ObjectGroupResponse.class);
                final DataObjectPackageType xmlObject = objectGroupMapper.map(objectGroup);
                Marshaller marshaller = jaxbContext.createMarshaller();
                marshaller.marshal(xmlObject, writer);
                LOGGER.debug(END_OF_EXECUTION_OF_DSL_VITAM_FROM_ACCESS);
                return Response.status(Status.OK).entity(writer.toString()).build();
            } else {
                return Response.status(Status.BAD_REQUEST.getStatusCode())
                    .entity(
                        JsonHandler.unprettyPrint(
                            new VitamError(VitamCode.ACCESS_EXTERNAL_SELECT_UNIT_BY_ID_ERROR.getItem())
                                .setHttpCode(Status.BAD_REQUEST.getStatusCode())
                                .setMessage(VitamCode.ACCESS_EXTERNAL_SELECT_UNIT_BY_ID_ERROR.getMessage())
                                .setState(StatusCode.KO.name())
                                .setContext(ACCESS_EXTERNAL_MODULE)
                                .setDescription(VitamCode.ACCESS_EXTERNAL_SELECT_UNIT_BY_ID_ERROR.getMessage())
                        )
                    )
                    .build();
            }
        } catch (JsonProcessingException | JAXBException e) {
            LOGGER.error(BAD_REQUEST_EXCEPTION, e);
            status = Response.Status.BAD_REQUEST;
            return Response.status(status)
                .entity(JsonHandler.unprettyPrint(getErrorEntity(status, e.getMessage())))
                .build();
        } catch (InternalServerException e) {
            status = Status.BAD_REQUEST;
            return Response.status(status)
                .entity(JsonHandler.unprettyPrint(getErrorEntity(status, e.getMessage())))
                .build();
        }
    }

    private VitamError getErrorEntity(Status status, String message) {
        String aMessage = (message != null && !message.trim().isEmpty())
            ? message
            : (status.getReasonPhrase() != null ? status.getReasonPhrase() : status.name());

        return new VitamError(VitamCode.ACCESS_INTERNAL_DIP_ERROR.getItem())
            .setHttpCode(VitamCode.ACCESS_INTERNAL_DIP_ERROR.getStatus().getStatusCode())
            .setContext(ACCESS_EXTERNAL_MODULE)
            .setState(VitamCode.ACCESS_INTERNAL_DIP_ERROR.getItem())
            .setMessage(VitamCode.ACCESS_INTERNAL_DIP_ERROR.getMessage())
            .setDescription(aMessage);
    }
}
